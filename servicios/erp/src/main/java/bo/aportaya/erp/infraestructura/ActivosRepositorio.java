package bo.aportaya.erp.infraestructura;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code activo_fijo}, {@code categoria_activo_fijo} y {@code depreciacion_activo}.
 *
 * <p>**Una depreciacion por activo y periodo** ({@code uq_depreciacion_activo_periodo}).
 * Correr la depreciacion dos veces el mismo mes duplicaria el gasto y bajaria el
 * resultado del ejercicio por un error de operacion, no por el negocio.
 */
@Component
public class ActivosRepositorio {

    /** Los activos vivos de una categoria, con lo que hace falta para depreciarlos. */
    public List<Activo> depreciables(DSLContext dsl) {
        var a = DSL.table(DSL.name("erp", "activo_fijo")).as("a");
        var c = DSL.table(DSL.name("erp", "categoria_activo_fijo")).as("c");
        return dsl.select(
                        DSL.field("a.id", UUID.class).as("id"),
                        DSL.field("a.costo_adquisicion", BigDecimal.class).as("costo"),
                        DSL.field("a.valor_residual", BigDecimal.class).as("residual"),
                        DSL.field("a.depreciacion_acumulada", BigDecimal.class).as("acumulada"),
                        DSL.field("a.moneda", String.class).as("moneda"),
                        DSL.field("c.vida_util_meses", Short.class).as("vida"),
                        DSL.field("c.metodo_depreciacion", String.class).as("metodo"))
                .from(a)
                .join(c)
                .on(DSL.field("c.id", UUID.class).eq(DSL.field("a.categoria_activo_fijo_id", UUID.class)))
                .where(DSL.field("a.estado", String.class).eq("ACTIVO"))
                .orderBy(DSL.field("a.codigo_inventario"))
                .fetch(f -> new Activo(
                        f.get("id", UUID.class),
                        f.get("costo", BigDecimal.class),
                        f.get("residual", BigDecimal.class),
                        f.get("acumulada", BigDecimal.class),
                        f.get("moneda", String.class),
                        f.get("vida", Short.class).intValue(),
                        f.get("metodo", String.class)));
    }

    public Optional<Activo> porId(DSLContext dsl, UUID id) {
        return depreciables(dsl).stream().filter(a -> a.id().equals(id)).findFirst();
    }

    public Optional<UUID> depreciacionDe(DSLContext dsl, UUID activoId, UUID periodoId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("erp", "depreciacion_activo")))
                .where(DSL.field("activo_fijo_id", UUID.class)
                        .eq(activoId)
                        .and(DSL.field("periodo_contable_id", UUID.class).eq(periodoId)))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID depreciar(
            DSLContext dsl,
            UUID activoId,
            UUID periodoId,
            BigDecimal monto,
            String moneda,
            BigDecimal acumuladaNueva,
            BigDecimal costoAdquisicion,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "depreciacion_activo")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("activo_fijo_id", UUID.class), activoId)
                .set(DSL.field("periodo_contable_id", UUID.class), periodoId)
                .set(DSL.field("monto", BigDecimal.class), monto)
                .set(DSL.field("moneda", String.class), moneda)
                .set(DSL.field("calculada_en", OffsetDateTime.class), ahora)
                .execute();

        dsl.update(DSL.table(DSL.name("erp", "activo_fijo")))
                .set(DSL.field("depreciacion_acumulada", BigDecimal.class), acumuladaNueva)
                .where(DSL.field("id", UUID.class).eq(activoId))
                .execute();
        return id;
    }

    public record Activo(
            UUID id,
            BigDecimal costoAdquisicion,
            BigDecimal valorResidual,
            BigDecimal acumulada,
            String moneda,
            int vidaUtilMeses,
            String metodo) {}
}
