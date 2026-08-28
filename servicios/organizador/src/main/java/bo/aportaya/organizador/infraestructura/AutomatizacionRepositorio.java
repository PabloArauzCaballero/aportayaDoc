package bo.aportaya.organizador.infraestructura;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code regla_automatizacion}, {@code tarea_automatizada} y {@code ejecucion_tarea}. */
@Component
public class AutomatizacionRepositorio {

    public boolean existeCodigoDeRegla(DSLContext dsl, String codigo) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("organizador", "regla_automatizacion")),
                        DSL.field("codigo", String.class).eq(codigo))
                > 0;
    }

    /** Si ya hay una regla activa con ese disparador y prioridad (uq_regla_automatizacion_prioridad). */
    public boolean hayPrioridadActiva(DSLContext dsl, String disparador, int prioridad) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("organizador", "regla_automatizacion")),
                        DSL.field("disparador", String.class)
                                .eq(disparador)
                                .and(DSL.field("prioridad", Short.class).eq((short) prioridad))
                                .and(DSL.field("activa", Boolean.class).isTrue()))
                > 0;
    }

    public UUID crearRegla(
            DSLContext dsl,
            String codigo,
            String descripcion,
            String disparador,
            String expresionDisparo,
            String condicion,
            String accion,
            boolean requiereConfirmacionHumana,
            int prioridad,
            boolean activa) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("organizador", "regla_automatizacion")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("codigo", String.class), codigo)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("disparador", String.class), disparador)
                .set(DSL.field("expresion_disparo", String.class), expresionDisparo)
                .set(DSL.field("condicion", String.class), condicion)
                .set(DSL.field("accion", String.class), accion)
                .set(DSL.field("requiere_confirmacion_humana", Boolean.class), requiereConfirmacionHumana)
                .set(DSL.field("prioridad", Short.class), (short) prioridad)
                .set(DSL.field("activa", Boolean.class), activa)
                .execute();
        return id;
    }

    public Optional<Regla> verRegla(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("codigo", String.class),
                        DSL.field("accion", String.class),
                        DSL.field("disparador", String.class),
                        DSL.field("requiere_confirmacion_humana", Boolean.class),
                        DSL.field("prioridad", Short.class),
                        DSL.field("activa", Boolean.class))
                .from(DSL.table(DSL.name("organizador", "regla_automatizacion")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Regla(
                        f.get("id", UUID.class),
                        f.get("codigo", String.class),
                        f.get("accion", String.class),
                        f.get("disparador", String.class),
                        f.get("requiere_confirmacion_humana", Boolean.class),
                        f.get("prioridad", Short.class),
                        f.get("activa", Boolean.class)));
    }

    /** La tarea de una clave, si ya existe. Es la barrera de R-ORG-07. */
    public Optional<Tarea> porClave(DSLContext dsl, String clave) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("organizador", "tarea_automatizada")))
                .where(DSL.field("clave_idempotencia", String.class).eq(clave))
                .fetchOptional(this::aTarea);
    }

    /** Con candado: dos corridas del planificador no ejecutan la misma tarea. */
    public Optional<Tarea> bloquear(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("organizador", "tarea_automatizada")))
                .where(DSL.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOptional(this::aTarea);
    }

    public UUID programar(
            DSLContext dsl,
            UUID reglaId,
            UUID grupoId,
            String tipo,
            OffsetDateTime programadaPara,
            String estado,
            String clave) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("organizador", "tarea_automatizada")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("regla_id", UUID.class), reglaId)
                .set(DSL.field("grupo_id", UUID.class), grupoId)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("programada_para", OffsetDateTime.class), programadaPara)
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("intentos", Short.class), (short) 0)
                .set(DSL.field("clave_idempotencia", String.class), clave)
                .execute();
        return id;
    }

    public boolean cambiarEstadoDeTarea(DSLContext dsl, UUID id, List<String> desde, String hacia, int intentos) {
        return dsl.update(DSL.table(DSL.name("organizador", "tarea_automatizada")))
                        .set(DSL.field("estado", String.class), hacia)
                        .set(DSL.field("intentos", Short.class), (short) intentos)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).in(desde)))
                        .execute()
                == 1;
    }

    public UUID registrarEjecucion(
            DSLContext dsl,
            UUID tareaId,
            OffsetDateTime iniciada,
            OffsetDateTime finalizada,
            String resultado,
            int registrosAfectados,
            String detalleJson,
            String mensajeError) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("organizador", "ejecucion_tarea")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("tarea_id", UUID.class), tareaId)
                .set(DSL.field("iniciada_en", OffsetDateTime.class), iniciada)
                .set(DSL.field("finalizada_en", OffsetDateTime.class), finalizada)
                .set(DSL.field("resultado", String.class), resultado)
                .set(DSL.field("registros_afectados", Integer.class), registrosAfectados)
                .set(DSL.field("detalle", JSONB.class), JSONB.valueOf(detalleJson))
                .set(DSL.field("mensaje_error", String.class), mensajeError)
                .execute();
        return id;
    }

    public int ejecucionesDe(DSLContext dsl, UUID tareaId) {
        return dsl.fetchCount(
                DSL.table(DSL.name("organizador", "ejecucion_tarea")),
                DSL.field("tarea_id", UUID.class).eq(tareaId));
    }

    private List<org.jooq.Field<?>> campos() {
        return List.of(
                DSL.field("id", UUID.class),
                DSL.field("regla_id", UUID.class),
                DSL.field("grupo_id", UUID.class),
                DSL.field("tipo", String.class),
                DSL.field("estado", String.class),
                DSL.field("intentos", Short.class),
                DSL.field("clave_idempotencia", String.class));
    }

    private Tarea aTarea(org.jooq.Record f) {
        return new Tarea(
                f.get("id", UUID.class),
                f.get("regla_id", UUID.class),
                f.get("grupo_id", UUID.class),
                f.get("tipo", String.class),
                f.get("estado", String.class),
                f.get("intentos", Short.class),
                f.get("clave_idempotencia", String.class));
    }

    public record Regla(
            UUID id,
            String codigo,
            String accion,
            String disparador,
            boolean requiereConfirmacionHumana,
            int prioridad,
            boolean activa) {}

    public record Tarea(
            UUID id, UUID reglaId, UUID grupoId, String tipo, String estado, int intentos, String claveIdempotencia) {}
}
