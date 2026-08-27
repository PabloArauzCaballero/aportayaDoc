package bo.aportaya.auditoria.infraestructura;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** Escribe y lee {@code auditoria.solicitud_datos_personales}. */
@Component
public class SolicitudDatosRepositorio {

    private static final org.jooq.Name TABLA = DSL.name("auditoria", "solicitud_datos_personales");

    /**
     * Abre la solicitud con su plazo YA CALCULADO.
     *
     * <p>El plazo se persiste al crear y no se recalcula al consultar (invariante 8).
     * Un plazo legal que se recalcula es un plazo que se mueve solo: basta que alguien
     * declare un feriado despues para que el vencimiento cambie sin que nadie lo
     * decida.
     */
    public UUID abrir(
            DSLContext dsl,
            UUID usuarioId,
            String tipo,
            String descripcion,
            OffsetDateTime limite,
            OffsetDateTime ahora) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(TABLA))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("tipo", String.class),
                        DSL.field("descripcion", String.class),
                        DSL.field("estado", String.class),
                        DSL.field("fecha_limite_legal", OffsetDateTime.class),
                        DSL.field("recibida_en", OffsetDateTime.class))
                .values(id, usuarioId, tipo, descripcion, "RECIBIDA", limite, ahora)
                .execute();
        return id;
    }

    /** Cierra la solicitud con su desenlace. Sin respuesta escrita no se cierra. */
    public void cerrar(DSLContext dsl, UUID solicitudId, String estado, String respuesta, OffsetDateTime ahora) {
        dsl.update(DSL.table(TABLA))
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("respuesta", String.class), respuesta)
                .set(DSL.field("atendida_en", OffsetDateTime.class), ahora)
                .where(DSL.field("id").eq(solicitudId))
                .execute();
    }

    /** Una solicitud abierta del mismo tipo bloquea otra: no se duplican expedientes. */
    public boolean tieneAbierta(DSLContext dsl, UUID usuarioId, String tipo) {
        Number cuantas = (Number) dsl.selectCount()
                .from(DSL.table(TABLA))
                .where(DSL.field("usuario_id").eq(usuarioId))
                .and(DSL.field("tipo").eq(tipo))
                .and(DSL.field("estado").in("RECIBIDA", "EN_PROCESO"))
                .fetchOne(0);
        return cuantas.intValue() > 0;
    }
}
