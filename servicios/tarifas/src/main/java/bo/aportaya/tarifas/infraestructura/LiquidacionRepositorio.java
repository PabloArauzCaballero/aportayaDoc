package bo.aportaya.tarifas.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code liquidacion_ingresos}: el resultado del mes, reproducible desde los devengos. */
@Component
public class LiquidacionRepositorio {

    public Optional<Liquidacion> delPeriodo(DSLContext dsl, String periodo) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("estado", String.class),
                        DSL.field("total_cobrado", BigDecimal.class),
                        DSL.field("ingreso_neto", BigDecimal.class))
                .from(DSL.table(DSL.name("tarifas", "liquidacion_ingresos")))
                .where(DSL.field("periodo", String.class).eq(periodo))
                .fetchOptional(f -> new Liquidacion(
                        f.get("id", UUID.class),
                        f.get("estado", String.class),
                        f.get("total_cobrado", BigDecimal.class),
                        f.get("ingreso_neto", BigDecimal.class)));
    }

    /**
     * Consolida los devengos del periodo.
     *
     * <p>Criterio devengado: se agrupa por {@code periodo_contable}, que es el mes del
     * hecho, no el del cobro. Un devengo de marzo cobrado en mayo sigue siendo de marzo,
     * y mover el ingreso al mes de cobro haria que el cierre de marzo deje de ser cierto.
     *
     * <p>Lo cobrado y lo devuelto **se suman de los cargos y las devoluciones**, no de
     * la columna {@code estado}: el devengo es append-only y esa columna guarda el
     * estado al devengar, no el de hoy. Leerla daria un mes que cobro cero.
     */
    public Consolidado consolidar(DSLContext dsl, String periodo) {
        var fila = dsl.fetchOne(
                """
                SELECT
                  COALESCE(SUM(d.monto_total), 0)                                    AS devengado,
                  COALESCE(SUM(c.cobrado), 0)                                        AS cobrado,
                  COALESCE(SUM(d.monto_total) FILTER (WHERE d.estado = 'EXONERADO'), 0) AS exonerado,
                  COALESCE(SUM(v.devuelto), 0)                                       AS devuelto,
                  COALESCE(SUM(d.monto_total) FILTER (
                      WHERE cpc.id IS NOT NULL AND COALESCE(c.cobrado, 0) = 0), 0)   AS incobrable,
                  COALESCE(SUM(d.monto_impuesto), 0)                                 AS impuestos,
                  count(*)::int                                                      AS operaciones
                  FROM tarifas.devengo_comision d
                  LEFT JOIN LATERAL (
                      SELECT COALESCE(SUM(monto_cobrado), 0) AS cobrado
                        FROM tarifas.cargo_comision
                       WHERE devengo_id = d.id AND estado = 'COBRADO') c ON true
                  LEFT JOIN LATERAL (
                      SELECT COALESCE(SUM(monto_devuelto), 0) AS devuelto
                        FROM tarifas.devolucion_comision
                       WHERE devengo_id = d.id AND estado = 'EJECUTADA') v ON true
                  LEFT JOIN tarifas.cuenta_por_cobrar_comision cpc ON cpc.devengo_id = d.id
                 WHERE d.periodo_contable = ?
                """,
                periodo);
        return new Consolidado(
                fila.get("devengado", BigDecimal.class),
                fila.get("cobrado", BigDecimal.class),
                fila.get("exonerado", BigDecimal.class),
                fila.get("devuelto", BigDecimal.class),
                fila.get("incobrable", BigDecimal.class),
                fila.get("impuestos", BigDecimal.class),
                fila.get("operaciones", Integer.class));
    }

    /** Lo que cobraron los proveedores en el mes: sale del ingreso neto. */
    public BigDecimal costoDeProveedores(DSLContext dsl, String periodo) {
        return dsl.fetchOne(
                        """
                        SELECT COALESCE(SUM(costo_total), 0) AS costo
                          FROM tarifas.costo_proveedor_operacion
                         WHERE periodo = ?
                        """,
                        periodo)
                .get("costo", BigDecimal.class);
    }

    public UUID cerrar(
            DSLContext dsl,
            String periodo,
            LocalDate desde,
            LocalDate hasta,
            Consolidado consolidado,
            BigDecimal costoProveedores,
            String estado,
            UUID cerradaPor,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("tarifas", "liquidacion_ingresos")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("periodo", String.class), periodo)
                .set(DSL.field("fecha_inicio", LocalDate.class), desde)
                .set(DSL.field("fecha_fin", LocalDate.class), hasta)
                .set(DSL.field("total_devengado", BigDecimal.class), consolidado.devengado())
                .set(DSL.field("total_cobrado", BigDecimal.class), consolidado.cobrado())
                .set(DSL.field("total_exonerado", BigDecimal.class), consolidado.exonerado())
                .set(DSL.field("total_devuelto", BigDecimal.class), consolidado.devuelto())
                .set(DSL.field("total_incobrable", BigDecimal.class), consolidado.incobrable())
                .set(DSL.field("total_impuestos", BigDecimal.class), consolidado.impuestos())
                .set(DSL.field("total_costo_proveedores", BigDecimal.class), costoProveedores)
                .set(DSL.field("cantidad_operaciones", Integer.class), consolidado.operaciones())
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("cerrada_por", UUID.class), cerradaPor)
                .set(DSL.field("cerrada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public record Consolidado(
            BigDecimal devengado,
            BigDecimal cobrado,
            BigDecimal exonerado,
            BigDecimal devuelto,
            BigDecimal incobrable,
            BigDecimal impuestos,
            int operaciones) {}

    public record Liquidacion(UUID id, String estado, BigDecimal totalCobrado, BigDecimal ingresoNeto) {}
}
