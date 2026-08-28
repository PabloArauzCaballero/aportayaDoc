package bo.aportaya.cumplimiento.infraestructura;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code plan_continuidad} y {@code prueba_continuidad}.
 *
 * <p>{@code ck_prueba_resultado} no deja registrar una prueba EXITOSA sin acta de
 * comite. Es la unica forma de que «la prueba salio bien» sea algo que alguien firmo y
 * no algo que alguien escribio.
 */
@Component
public class ContinuidadRepositorio {

    public Optional<Plan> planPorId(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("proceso_critico", String.class),
                        DSL.field("rto_minutos", Integer.class),
                        DSL.field("rpo_minutos", Integer.class),
                        DSL.field("periodicidad_prueba_meses", Short.class),
                        DSL.field("proxima_prueba", LocalDate.class),
                        DSL.field("responsable_id", UUID.class))
                .from(DSL.table(DSL.name("cumplimiento", "plan_continuidad")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Plan(
                        f.get("id", UUID.class),
                        f.get("proceso_critico", String.class),
                        f.get("rto_minutos", Integer.class),
                        f.get("rpo_minutos", Integer.class),
                        f.get("periodicidad_prueba_meses", Short.class).intValue(),
                        f.get("proxima_prueba", LocalDate.class),
                        f.get("responsable_id", UUID.class)));
    }

    public UUID registrarPrueba(
            DSLContext dsl,
            UUID planId,
            UUID actaComiteId,
            UUID ejecutadaPor,
            String tipo,
            LocalDate fecha,
            int rtoObtenido,
            int rpoObtenido,
            String resultado,
            String hallazgos,
            String evidenciaUrl) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "prueba_continuidad")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("plan_continuidad_id", UUID.class), planId)
                .set(DSL.field("acta_comite_id", UUID.class), actaComiteId)
                .set(DSL.field("ejecutada_por", UUID.class), ejecutadaPor)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("fecha", LocalDate.class), fecha)
                .set(DSL.field("rto_obtenido_minutos", Integer.class), rtoObtenido)
                .set(DSL.field("rpo_obtenido_minutos", Integer.class), rpoObtenido)
                .set(DSL.field("resultado", String.class), resultado)
                .set(DSL.field("hallazgos", String.class), hallazgos)
                .set(DSL.field("evidencia_url", String.class), evidenciaUrl)
                .execute();
        return id;
    }

    /** Solo una prueba que salio bien corre la fecha de la proxima. */
    public void moverProximaPrueba(DSLContext dsl, UUID planId, LocalDate proxima) {
        dsl.update(DSL.table(DSL.name("cumplimiento", "plan_continuidad")))
                .set(DSL.field("proxima_prueba", LocalDate.class), proxima)
                .where(DSL.field("id", UUID.class).eq(planId))
                .execute();
    }

    /** Planes cuya prueba vencio: lo que el control diario convierte en hallazgo. */
    public List<Plan> conPruebaVencida(DSLContext dsl, LocalDate corte) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("proceso_critico", String.class),
                        DSL.field("rto_minutos", Integer.class),
                        DSL.field("rpo_minutos", Integer.class),
                        DSL.field("periodicidad_prueba_meses", Short.class),
                        DSL.field("proxima_prueba", LocalDate.class),
                        DSL.field("responsable_id", UUID.class))
                .from(DSL.table(DSL.name("cumplimiento", "plan_continuidad")))
                .where(DSL.field("proxima_prueba", LocalDate.class).lt(corte))
                .fetch(f -> new Plan(
                        f.get("id", UUID.class),
                        f.get("proceso_critico", String.class),
                        f.get("rto_minutos", Integer.class),
                        f.get("rpo_minutos", Integer.class),
                        f.get("periodicidad_prueba_meses", Short.class).intValue(),
                        f.get("proxima_prueba", LocalDate.class),
                        f.get("responsable_id", UUID.class)));
    }

    /**
     * Los procesos criticos que **no tienen plan**.
     *
     * <p>Es el control que mas sirve y el que mas se olvida: revisar que los planes
     * existentes esten probados no dice nada de los procesos para los que nunca se
     * escribio uno. La lista de procesos criticos llega como dato — vive en la politica
     * interna, no en este codigo.
     */
    public List<String> procesosSinPlan(DSLContext dsl, List<String> procesosCriticos) {
        if (procesosCriticos.isEmpty()) {
            return List.of();
        }
        var conPlan = dsl.select(DSL.field("proceso_critico", String.class))
                .from(DSL.table(DSL.name("cumplimiento", "plan_continuidad")))
                .where(DSL.field("proceso_critico", String.class).in(procesosCriticos))
                .fetchSet(f -> f.get("proceso_critico", String.class));
        return procesosCriticos.stream().filter(p -> !conPlan.contains(p)).toList();
    }

    public record Plan(
            UUID id,
            String procesoCritico,
            int rtoMinutos,
            int rpoMinutos,
            int periodicidadMeses,
            LocalDate proximaPrueba,
            UUID responsableId) {}
}
