package bo.aportaya.plataforma.mensajeria;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lee el outbox y publica DESPUES del {@code COMMIT}. Es **al menos una vez**, y por
 * eso todo consumidor es idempotente.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} es lo que permite que varias replicas releven a
 * la vez sin pisarse ni bloquearse: la que llega segunda salta las filas tomadas en
 * vez de esperarlas.
 *
 * <p>Con Kafka caido no se pierde nada: los eventos se acumulan en {@code PENDIENTE}
 * y la metrica {@code aportaya.outbox.edad_mas_viejo} lo muestra antes de que alguien
 * pregunte.
 */
public class Relevo {

    private static final Logger BITACORA = LoggerFactory.getLogger(Relevo.class);
    private static final int POR_TANDA = 100;

    private final String esquema;
    private final DSLContext dsl;
    private final KafkaTemplate<String, String> kafka;
    private final AtomicLong edadDelMasViejo = new AtomicLong();

    public Relevo(String esquema, DSLContext dsl, KafkaTemplate<String, String> kafka, MeterRegistry metricas) {
        this.esquema = esquema;
        this.dsl = dsl;
        this.kafka = kafka;
        metricas.gauge("aportaya.outbox.edad_mas_viejo_segundos", edadDelMasViejo);
    }

    @Scheduled(fixedDelayString = "${aportaya.outbox.intervalo:PT1S}")
    @SchedulerLock(name = "outbox.relevo", lockAtMostFor = "PT30S")
    @Transactional
    public void relevar() {
        List<Record> pendientes = dsl.select(
                        DSL.field("id"),
                        DSL.field("tipo"),
                        DSL.field("agregado_id"),
                        DSL.field("payload"),
                        DSL.field("ocurrido_en"))
                .from(DSL.table(DSL.name(esquema, "evento_dominio")))
                .where(DSL.field("estado").eq("PENDIENTE"))
                .orderBy(DSL.field("ocurrido_en").asc())
                .limit(POR_TANDA)
                .forUpdate()
                .skipLocked()
                .fetch()
                .map(fila -> fila);

        medirEdad(pendientes);
        pendientes.forEach(this::publicar);
    }

    private void publicar(Record evento) {
        UUID id = (UUID) evento.get("id");
        String tipo = String.valueOf(evento.get("tipo"));
        try {
            // La clave de particion es el agregado: lo de una billetera llega en orden.
            kafka.send(
                            "aportaya." + tipo,
                            String.valueOf(evento.get("agregado_id")),
                            String.valueOf(evento.get("payload")))
                    .get();
            marcar(id, "PUBLICADO");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("relevo interrumpido", e);
        } catch (Exception e) {
            // No se marca FALLIDO en el primer tropiezo: queda PENDIENTE y se
            // reintenta. Un evento perdido en silencio es peor que uno que insiste.
            BITACORA.warn("no se pudo publicar el evento {} ({}): queda pendiente", id, tipo, e);
        }
    }

    private void marcar(UUID id, String estado) {
        dsl.update(DSL.table(DSL.name(esquema, "evento_dominio")))
                .set(DSL.field("estado", String.class), DSL.val(estado))
                .set(DSL.field("publicado_en", OffsetDateTime.class), DSL.field("now()", OffsetDateTime.class))
                .set(DSL.field("intentos", Short.class), DSL.field("intentos + 1", Short.class))
                .where(DSL.field("id").eq(id))
                .execute();
    }

    private void medirEdad(List<Record> pendientes) {
        if (pendientes.isEmpty()) {
            edadDelMasViejo.set(0);
            return;
        }
        OffsetDateTime masViejo = (OffsetDateTime) pendientes.get(0).get("ocurrido_en");
        edadDelMasViejo.set(
                Duration.between(masViejo.toInstant(), java.time.Instant.now()).toSeconds());
    }
}
