package bo.aportaya.notificaciones.infraestructura;

import bo.aportaya.notificaciones.dominio.Canal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code notificacion}, {@code bandeja_entrada} y {@code canal_vinculado}. */
@Component
public class NotificacionRepositorio {

    /**
     * La notificacion, deduplicada por {@code clave_deduplicacion}.
     *
     * <p>Devuelve la existente si ya estaba: el consumidor del outbox puede recibir el
     * mismo evento dos veces, y dos avisos identicos al mismo destinatario son un
     * defecto visible para la persona, no un detalle interno.
     */
    public Registro registrar(
            DSLContext dsl,
            UUID usuarioId,
            UUID eventoId,
            String prioridad,
            String contextoJson,
            String claveDeduplicacion,
            String estado,
            OffsetDateTime programadaPara,
            UUID correlationId,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        int filas = dsl.insertInto(DSL.table(DSL.name("notificaciones", "notificacion")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("evento_id", UUID.class), eventoId)
                .set(DSL.field("prioridad", String.class), prioridad)
                .set(DSL.field("contexto", JSONB.class), JSONB.valueOf(contextoJson))
                .set(DSL.field("clave_deduplicacion", String.class), claveDeduplicacion)
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("programada_para", OffsetDateTime.class), programadaPara)
                .set(DSL.field("creada_en", OffsetDateTime.class), ahora)
                .set(DSL.field("correlation_id", UUID.class), correlationId)
                .onConflictDoNothing()
                .execute();

        if (filas > 0) {
            return new Registro(id, true);
        }
        UUID existente = dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("notificaciones", "notificacion")))
                .where(DSL.field("clave_deduplicacion").eq(claveDeduplicacion))
                .fetchOne(DSL.field("id", UUID.class));
        return new Registro(existente, false);
    }

    public void cambiarEstado(DSLContext dsl, UUID notificacionId, String estado, OffsetDateTime finalizadaEn) {
        dsl.update(DSL.table(DSL.name("notificaciones", "notificacion")))
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("finalizada_en", OffsetDateTime.class), finalizadaEn)
                .where(DSL.field("id", UUID.class).eq(notificacionId))
                .execute();
    }

    /**
     * La bandeja recibe SIEMPRE, aunque el push no llegue.
     *
     * <p>Es la regla del shell movil y tambien la del backend: un aviso que solo
     * existio como push es un aviso que se perdio si el telefono estaba apagado.
     */
    public UUID guardarEnBandeja(
            DSLContext dsl, UUID usuarioId, UUID notificacionId, String titulo, String resumen, String urlAccion) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("notificaciones", "bandeja_entrada")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("notificacion_id", UUID.class), notificacionId)
                .set(DSL.field("titulo", String.class), titulo)
                .set(DSL.field("resumen", String.class), resumen)
                .set(DSL.field("url_accion", String.class), urlAccion)
                .set(DSL.field("leida", Boolean.class), false)
                .set(DSL.field("archivada", Boolean.class), false)
                .execute();
        return id;
    }

    /** El canal verificado del destinatario, o vacio si no lo tiene. */
    public Optional<CanalDestino> canalVerificado(DSLContext dsl, UUID usuarioId, Canal canal) {
        Record fila = dsl.select(DSL.field("id", UUID.class), DSL.field("identificador", String.class))
                .from(DSL.table(DSL.name("notificaciones", "canal_vinculado")))
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .and(DSL.field("tipo").eq(canal.name()))
                .and(DSL.field("verificado", Boolean.class).isTrue())
                .and(DSL.field("estado").eq("ACTIVO"))
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new CanalDestino(f.get("id", UUID.class), f.get("identificador", String.class)));
    }

    /** El evento notificable y su politica: categoria, prioridad y si es obligatorio. */
    public Optional<Evento> evento(DSLContext dsl, UUID eventoId) {
        Record fila = dsl.select(
                        DSL.field("categoria", String.class),
                        DSL.field("prioridad", String.class),
                        DSL.field("es_obligatorio", Boolean.class),
                        DSL.field("ventana_deduplicacion_min", Short.class))
                .from(DSL.table(DSL.name("notificaciones", "evento_notificable")))
                .where(DSL.field("id", UUID.class).eq(eventoId))
                .and(DSL.field("activo", Boolean.class).isTrue())
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Evento(
                        f.get("categoria", String.class),
                        f.get("prioridad", String.class),
                        f.get("es_obligatorio", Boolean.class),
                        f.get("ventana_deduplicacion_min", Short.class)));
    }

    public record Registro(UUID id, boolean esNueva) {}

    public record CanalDestino(UUID id, String identificador) {}

    public record Evento(String categoria, String prioridad, boolean esObligatorio, short ventanaDedupeMin) {}
}
