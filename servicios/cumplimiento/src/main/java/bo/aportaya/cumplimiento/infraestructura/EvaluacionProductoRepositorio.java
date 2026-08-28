package bo.aportaya.cumplimiento.infraestructura;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code evaluacion_riesgo_producto}.
 *
 * <p>Cada cambio material es una **version nueva** ({@code uq_evaluacion_producto_version}),
 * y la anterior conserva su historico. Editar la evaluacion vigente borraria la razon
 * por la que el producto se aprobo como estaba antes, que es justo lo que un supervisor
 * pide ver cuando algo sale mal.
 */
@Component
public class EvaluacionProductoRepositorio {

    public int ultimaVersion(DSLContext dsl, String producto) {
        Short maximo = dsl.select(DSL.max(DSL.field("version", Short.class)).as("v"))
                .from(DSL.table(DSL.name("cumplimiento", "evaluacion_riesgo_producto")))
                .where(DSL.field("producto", String.class).eq(producto))
                .fetchOne(f -> f.get("v", Short.class));
        return maximo == null ? 0 : maximo;
    }

    public UUID crear(
            DSLContext dsl,
            String producto,
            int version,
            String riesgosJson,
            String nivelLft,
            String controlesJson,
            boolean requiereNoObjecion) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "evaluacion_riesgo_producto")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("producto", String.class), producto)
                .set(DSL.field("version", Short.class), (short) version)
                .set(DSL.field("riesgos_identificados", JSONB.class), JSONB.valueOf(riesgosJson))
                .set(DSL.field("nivel_riesgo_lft", String.class), nivelLft)
                .set(DSL.field("controles_definidos", JSONB.class), JSONB.valueOf(controlesJson))
                .set(DSL.field("requiere_no_objecion", Boolean.class), requiereNoObjecion)
                .set(DSL.field("estado", String.class), "BORRADOR")
                .execute();
        return id;
    }

    /**
     * Aprueba la evaluacion.
     *
     * <p>La aprobacion la firma quien preside el comite con quorum, y queda con fecha:
     * {@code ck_evaluacion_vigente_aprobada} lo exigiria si el estado VIGENTE existiera
     * en el catalogo. No existe, asi que **quien lo exige es este metodo**.
     */
    public boolean aprobar(DSLContext dsl, UUID evaluacionId, UUID aprobadaPor, LocalDate fecha) {
        return dsl.update(DSL.table(DSL.name("cumplimiento", "evaluacion_riesgo_producto")))
                        .set(DSL.field("estado", String.class), "APROBADA")
                        .set(DSL.field("aprobada_por", UUID.class), aprobadaPor)
                        .set(DSL.field("fecha_aprobacion", LocalDate.class), fecha)
                        .where(DSL.field("id", UUID.class)
                                .eq(evaluacionId)
                                .and(DSL.field("estado", String.class).in("BORRADOR", "EN_EVALUACION")))
                        .execute()
                == 1;
    }

    public Optional<Evaluacion> porId(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("producto", String.class),
                        DSL.field("version", Short.class),
                        DSL.field("nivel_riesgo_lft", String.class),
                        DSL.field("requiere_no_objecion", Boolean.class),
                        DSL.field("estado", String.class),
                        DSL.field("aprobada_por", UUID.class),
                        DSL.field("fecha_aprobacion", LocalDate.class))
                .from(DSL.table(DSL.name("cumplimiento", "evaluacion_riesgo_producto")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Evaluacion(
                        f.get("id", UUID.class),
                        f.get("producto", String.class),
                        f.get("version", Short.class).intValue(),
                        f.get("nivel_riesgo_lft", String.class),
                        f.get("requiere_no_objecion", Boolean.class),
                        f.get("estado", String.class),
                        f.get("aprobada_por", UUID.class),
                        f.get("fecha_aprobacion", LocalDate.class)));
    }

    public record Evaluacion(
            UUID id,
            String producto,
            int version,
            String nivelLft,
            boolean requiereNoObjecion,
            String estado,
            UUID aprobadaPor,
            LocalDate fechaAprobacion) {}
}
