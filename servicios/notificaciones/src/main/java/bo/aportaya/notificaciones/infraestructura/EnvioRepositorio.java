package bo.aportaya.notificaciones.infraestructura;

import bo.aportaya.notificaciones.dominio.Canal;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code envio_notificacion}, {@code cola_envio} y {@code cola_muerta}. */
@Component
public class EnvioRepositorio {

    public UUID encolar(
            DSLContext dsl,
            UUID notificacionId,
            UUID proveedorId,
            UUID versionPlantillaId,
            Optional<UUID> canalVinculadoId,
            Canal canal,
            String destinatario,
            String claveIdempotencia,
            String contenido,
            int orden,
            int maxIntentos,
            BigDecimal costo,
            String moneda,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("notificaciones", "envio_notificacion")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("notificacion_id", UUID.class), notificacionId)
                .set(DSL.field("proveedor_id", UUID.class), proveedorId)
                .set(DSL.field("version_plantilla_id", UUID.class), versionPlantillaId)
                .set(DSL.field("canal_vinculado_id", UUID.class), canalVinculadoId.orElse(null))
                .set(DSL.field("canal", String.class), canal.name())
                .set(DSL.field("destinatario", String.class), destinatario)
                .set(DSL.field("clave_idempotencia", String.class), claveIdempotencia)
                .set(DSL.field("encolado_en", OffsetDateTime.class), ahora)
                .set(DSL.field("contenido_enviado", String.class), contenido)
                .set(DSL.field("estado", String.class), "PENDIENTE")
                .set(DSL.field("orden", Short.class), (short) orden)
                .set(DSL.field("intentos", Short.class), (short) 0)
                .set(DSL.field("max_intentos", Short.class), (short) maxIntentos)
                .set(DSL.field("costo", BigDecimal.class), costo)
                .set(DSL.field("moneda", String.class), moneda)
                .execute();
        return id;
    }

    /**
     * ¿Ya se proceso ese envio con esa clave? R-NOT-01.
     *
     * <p>No es una falla: es la red haciendo lo que hace. Se responde lo mismo que la
     * primera vez en vez de mandar el mensaje otra vez.
     */
    public Optional<UUID> porClaveIdempotencia(DSLContext dsl, String clave) {
        return Optional.ofNullable(dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("notificaciones", "envio_notificacion")))
                .where(DSL.field("clave_idempotencia").eq(clave))
                .fetchOne(DSL.field("id", UUID.class)));
    }

    public void ponerEnCola(DSLContext dsl, UUID envioId, String particion, OffsetDateTime disponibleEn) {
        dsl.insertInto(DSL.table(DSL.name("notificaciones", "cola_envio")))
                .set(DSL.field("id", UUID.class), UUID.randomUUID())
                .set(DSL.field("envio_id", UUID.class), envioId)
                .set(DSL.field("particion", String.class), particion)
                .set(DSL.field("disponible_en", OffsetDateTime.class), disponibleEn)
                .set(DSL.field("intentos", Short.class), (short) 0)
                .execute();
    }

    /**
     * Toma un lote de la cola con {@code FOR UPDATE SKIP LOCKED}.
     *
     * <p>Sin {@code SKIP LOCKED}, dos replicas se bloquean entre si y el despacho
     * pasa a ser secuencial aunque haya diez procesos. Con el, cada replica se lleva
     * lo que nadie tomo y **el mismo mensaje no sale dos veces**.
     */
    public List<EnCola> tomar(DSLContext dsl, String particion, OffsetDateTime ahora, int cuantos) {
        return dsl.select(
                        DSL.field("c.id", UUID.class).as("cola_id"),
                        DSL.field("c.envio_id", UUID.class).as("envio_id"),
                        DSL.field("c.intentos", Short.class).as("intentos"),
                        DSL.field("e.canal", String.class).as("canal"),
                        DSL.field("e.destinatario", String.class).as("destinatario"),
                        DSL.field("e.max_intentos", Short.class).as("max_intentos"))
                .from(DSL.table(DSL.name("notificaciones", "cola_envio")).as("c"))
                .join(DSL.table(DSL.name("notificaciones", "envio_notificacion"))
                        .as("e"))
                .on(DSL.field("c.envio_id", UUID.class).eq(DSL.field("e.id", UUID.class)))
                .where(DSL.field("c.particion").eq(particion))
                .and(DSL.field("c.disponible_en", OffsetDateTime.class).le(ahora))
                .and(DSL.field("c.bloqueada_hasta")
                        .isNull()
                        .or(DSL.field("c.bloqueada_hasta", OffsetDateTime.class).lt(ahora)))
                .orderBy(DSL.field("c.disponible_en").asc())
                .limit(cuantos)
                .forUpdate()
                .skipLocked()
                .fetch(fila -> new EnCola(
                        fila.get("cola_id", UUID.class),
                        fila.get("envio_id", UUID.class),
                        fila.get("intentos", Short.class),
                        Canal.valueOf(fila.get("canal", String.class)),
                        fila.get("destinatario", String.class),
                        fila.get("max_intentos", Short.class)));
    }

    public void marcarEnviado(DSLContext dsl, UUID envioId, String idMensajeProveedor, OffsetDateTime momento) {
        dsl.update(DSL.table(DSL.name("notificaciones", "envio_notificacion")))
                .set(DSL.field("estado", String.class), "ENVIADO")
                .set(DSL.field("id_mensaje_proveedor", String.class), idMensajeProveedor)
                .set(DSL.field("enviado_en", OffsetDateTime.class), momento)
                .set(
                        DSL.field("intentos", Short.class),
                        DSL.field("intentos", Short.class).plus(1))
                .where(DSL.field("id", UUID.class).eq(envioId))
                .execute();
    }

    public void reprogramar(DSLContext dsl, UUID colaId, UUID envioId, OffsetDateTime proximoIntento, String error) {
        dsl.update(DSL.table(DSL.name("notificaciones", "cola_envio")))
                .set(DSL.field("disponible_en", OffsetDateTime.class), proximoIntento)
                .set(
                        DSL.field("intentos", Short.class),
                        DSL.field("intentos", Short.class).plus(1))
                .where(DSL.field("id", UUID.class).eq(colaId))
                .execute();
        dsl.update(DSL.table(DSL.name("notificaciones", "envio_notificacion")))
                .set(DSL.field("codigo_error", String.class), error)
                .set(DSL.field("proximo_reintento_en", OffsetDateTime.class), proximoIntento)
                .set(
                        DSL.field("intentos", Short.class),
                        DSL.field("intentos", Short.class).plus(1))
                .where(DSL.field("id", UUID.class).eq(envioId))
                .execute();
    }

    /** Agotados los reintentos: sale de la cola viva y queda para que alguien lo mire. */
    public void aColaMuerta(
            DSLContext dsl, UUID colaId, UUID envioId, String motivo, String payloadJson, OffsetDateTime momento) {
        dsl.deleteFrom(DSL.table(DSL.name("notificaciones", "cola_envio")))
                .where(DSL.field("id", UUID.class).eq(colaId))
                .execute();
        dsl.update(DSL.table(DSL.name("notificaciones", "envio_notificacion")))
                .set(DSL.field("estado", String.class), "FALLIDO")
                .set(DSL.field("codigo_error", String.class), motivo)
                .where(DSL.field("id", UUID.class).eq(envioId))
                .execute();
        dsl.insertInto(DSL.table(DSL.name("notificaciones", "cola_muerta")))
                .set(DSL.field("id", UUID.class), UUID.randomUUID())
                .set(DSL.field("envio_id", UUID.class), envioId)
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("payload", JSONB.class), JSONB.valueOf(payloadJson))
                .set(DSL.field("fecha", OffsetDateTime.class), momento)
                .execute();
    }

    public void sacarDeLaCola(DSLContext dsl, UUID colaId) {
        dsl.deleteFrom(DSL.table(DSL.name("notificaciones", "cola_envio")))
                .where(DSL.field("id", UUID.class).eq(colaId))
                .execute();
    }

    public Optional<Estado> estadoDe(DSLContext dsl, UUID envioId) {
        Record fila = dsl.select(
                        DSL.field("estado", String.class),
                        DSL.field("intentos", Short.class),
                        DSL.field("costo", BigDecimal.class))
                .from(DSL.table(DSL.name("notificaciones", "envio_notificacion")))
                .where(DSL.field("id", UUID.class).eq(envioId))
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Estado(
                        f.get("estado", String.class),
                        f.get("intentos", Short.class),
                        f.get("costo", BigDecimal.class)));
    }

    public record EnCola(
            UUID colaId, UUID envioId, short intentos, Canal canal, String destinatario, short maxIntentos) {}

    public record Estado(String estado, short intentos, BigDecimal costo) {}
}
