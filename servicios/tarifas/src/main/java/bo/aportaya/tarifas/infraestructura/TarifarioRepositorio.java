package bo.aportaya.tarifas.infraestructura;

import bo.aportaya.tarifas.dominio.CalculoDeComision;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * El tarifario vigente y sus conceptos.
 *
 * <p>{@code catalogo.tarifario} y {@code catalogo.impuesto} son del esquema
 * compartido: se leen, no se inventan. Los conceptos y sus reglas si son de este
 * servicio.
 */
@Component
public class TarifarioRepositorio {

    /** El tarifario vigente para un codigo, en un momento dado. */
    public Optional<UUID> vigente(DSLContext dsl, String codigo, OffsetDateTime momento) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("catalogo", "tarifario")))
                .where(DSL.field("codigo", String.class)
                        .eq(codigo)
                        .and(DSL.field("estado", String.class).eq("VIGENTE"))
                        .and(DSL.field("vigente_desde", OffsetDateTime.class).le(momento))
                        .and(DSL.field("vigente_hasta", OffsetDateTime.class)
                                .isNull()
                                .or(DSL.field("vigente_hasta", OffsetDateTime.class)
                                        .gt(momento))))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    /**
     * El concepto aplicable a un hecho generador dentro de un tarifario.
     *
     * <p>Devuelve vacio cuando no hay ninguno: eso no es un error, es que la operacion
     * es gratuita. Inventar un concepto por omision cobraria algo que nadie publico.
     */
    public Optional<Concepto> concepto(DSLContext dsl, UUID tarifarioId, String hechoGenerador) {
        return dsl.select(
                        DSL.field("c.id", UUID.class).as("concepto_id"),
                        DSL.field("c.codigo", String.class).as("concepto_codigo"),
                        DSL.field("c.nombre_comercial", String.class).as("nombre_comercial"),
                        DSL.field("c.metodo_calculo", String.class).as("metodo_calculo"),
                        DSL.field("c.valor_porcentual", BigDecimal.class).as("valor_porcentual"),
                        DSL.field("c.valor_fijo", BigDecimal.class).as("valor_fijo"),
                        DSL.field("c.monto_minimo", BigDecimal.class).as("monto_minimo"),
                        DSL.field("c.monto_maximo", BigDecimal.class).as("monto_maximo"),
                        DSL.field("c.gravado_iva", Boolean.class).as("gravado_iva"),
                        DSL.field("c.gravado_it", Boolean.class).as("gravado_it"),
                        DSL.field("c.precio_incluye_impuesto", Boolean.class).as("precio_incluye_impuesto"),
                        DSL.field("c.forma_cobro", String.class).as("forma_cobro"),
                        DSL.field("c.cuenta_ingreso_id", UUID.class).as("cuenta_ingreso_id"),
                        DSL.field("c.politica_redondeo_id", UUID.class).as("politica_redondeo_id"))
                .from(DSL.table(DSL.name("tarifas", "concepto_tarifa")).as("c"))
                .join(DSL.table(DSL.name("tarifas", "catalogo_hecho_generador")).as("h"))
                .on(DSL.field("h.id").eq(DSL.field("c.hecho_generador_id")))
                .where(DSL.field("c.tarifario_id", UUID.class)
                        .eq(tarifarioId)
                        .and(DSL.field("h.codigo", String.class).eq(hechoGenerador))
                        .and(DSL.field("c.activo", Boolean.class).isTrue())
                        .and(DSL.field("h.activo", Boolean.class).isTrue()))
                .orderBy(DSL.field("c.orden_aplicacion").asc())
                .limit(1)
                .fetchOptional(this::aConcepto);
    }

    /** El concepto por su identificador: lo que necesita el devengo de una cotizacion. */
    public Optional<Concepto> conceptoPorId(DSLContext dsl, UUID conceptoId) {
        return dsl.select(
                        DSL.field("id", UUID.class).as("concepto_id"),
                        DSL.field("codigo", String.class).as("concepto_codigo"),
                        DSL.field("nombre_comercial", String.class),
                        DSL.field("metodo_calculo", String.class),
                        DSL.field("valor_porcentual", BigDecimal.class),
                        DSL.field("valor_fijo", BigDecimal.class),
                        DSL.field("monto_minimo", BigDecimal.class),
                        DSL.field("monto_maximo", BigDecimal.class),
                        DSL.field("gravado_iva", Boolean.class),
                        DSL.field("gravado_it", Boolean.class),
                        DSL.field("precio_incluye_impuesto", Boolean.class),
                        DSL.field("forma_cobro", String.class),
                        DSL.field("cuenta_ingreso_id", UUID.class),
                        DSL.field("politica_redondeo_id", UUID.class))
                .from(DSL.table(DSL.name("tarifas", "concepto_tarifa")))
                .where(DSL.field("id", UUID.class).eq(conceptoId))
                .fetchOptional(this::aConcepto);
    }

    /**
     * La regla que gana para un monto, si hay alguna.
     *
     * <p>Gana la primera por {@code orden} cuyo tramo contiene el monto. El orden es
     * del tarifario, no del azar: dos reglas que se solapan tienen que resolverse
     * siempre igual o el mismo usuario paga distinto segun el dia.
     */
    public Optional<Regla> regla(DSLContext dsl, UUID conceptoId, BigDecimal montoBase, OffsetDateTime momento) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("valor_porcentual", BigDecimal.class),
                        DSL.field("valor_fijo", BigDecimal.class),
                        DSL.field("monto_minimo", BigDecimal.class),
                        DSL.field("monto_maximo", BigDecimal.class))
                .from(DSL.table(DSL.name("tarifas", "regla_tarifa")))
                .where(DSL.field("concepto_tarifa_id", UUID.class)
                        .eq(conceptoId)
                        .and(DSL.field("vigente_desde", OffsetDateTime.class).le(momento))
                        .and(DSL.field("vigente_hasta", OffsetDateTime.class)
                                .isNull()
                                .or(DSL.field("vigente_hasta", OffsetDateTime.class)
                                        .gt(momento)))
                        .and(DSL.field("monto_base_desde", BigDecimal.class)
                                .isNull()
                                .or(DSL.field("monto_base_desde", BigDecimal.class)
                                        .le(montoBase)))
                        .and(DSL.field("monto_base_hasta", BigDecimal.class)
                                .isNull()
                                .or(DSL.field("monto_base_hasta", BigDecimal.class)
                                        .gt(montoBase))))
                .orderBy(DSL.field("orden").asc())
                .limit(1)
                .fetchOptional(f -> new Regla(
                        f.get("id", UUID.class),
                        f.get("valor_porcentual", BigDecimal.class),
                        f.get("valor_fijo", BigDecimal.class),
                        f.get("monto_minimo", BigDecimal.class),
                        f.get("monto_maximo", BigDecimal.class)));
    }

    /** Los impuestos vigentes del catalogo. Nunca constantes (invariante 10). */
    public List<CalculoDeComision.Impuesto> impuestosVigentes(DSLContext dsl, java.time.LocalDate fecha) {
        return dsl.select(DSL.field("codigo", String.class), DSL.field("alicuota", BigDecimal.class))
                .from(DSL.table(DSL.name("catalogo", "impuesto")))
                .where(DSL.field("vigente_desde", java.time.LocalDate.class)
                        .le(fecha)
                        .and(DSL.field("vigente_hasta", java.time.LocalDate.class)
                                .isNull()
                                .or(DSL.field("vigente_hasta", java.time.LocalDate.class)
                                        .ge(fecha))))
                .fetch(f -> new CalculoDeComision.Impuesto(
                        f.get("codigo", String.class), f.get("alicuota", BigDecimal.class)));
    }

    /** La politica de redondeo de un concepto, si tiene una. */
    public Optional<bo.aportaya.tarifas.dominio.PoliticaDeRedondeo> redondeo(DSLContext dsl, UUID politicaId) {
        if (politicaId == null) {
            return Optional.empty();
        }
        return dsl.select(
                        DSL.field("codigo", String.class),
                        DSL.field("unidad_minima", BigDecimal.class),
                        DSL.field("modo", String.class))
                .from(DSL.table(DSL.name("tarifas", "politica_redondeo")))
                .where(DSL.field("id", UUID.class).eq(politicaId))
                .fetchOptional(f -> new bo.aportaya.tarifas.dominio.PoliticaDeRedondeo(
                        f.get("codigo", String.class),
                        f.get("unidad_minima", BigDecimal.class),
                        bo.aportaya.tarifas.dominio.PoliticaDeRedondeo.Modo.valueOf(f.get("modo", String.class))));
    }

    /** El snapshot congelado de un grupo, si lo tiene (R-TAR-07). */
    public Optional<UUID> tarifarioCongelado(DSLContext dsl, UUID grupoId) {
        return dsl.select(DSL.field("tarifario_id", UUID.class))
                .from(DSL.table(DSL.name("tarifas", "tarifa_congelada_grupo")))
                .where(DSL.field("grupo_id", UUID.class).eq(grupoId))
                .fetchOptional(f -> f.get("tarifario_id", UUID.class));
    }

    private Concepto aConcepto(Record f) {
        return new Concepto(
                f.get("concepto_id", UUID.class),
                f.get("concepto_codigo", String.class),
                f.get("nombre_comercial", String.class),
                f.get("metodo_calculo", String.class),
                f.get("valor_porcentual", BigDecimal.class),
                f.get("valor_fijo", BigDecimal.class),
                f.get("monto_minimo", BigDecimal.class),
                f.get("monto_maximo", BigDecimal.class),
                f.get("gravado_iva", Boolean.class),
                f.get("gravado_it", Boolean.class),
                f.get("precio_incluye_impuesto", Boolean.class),
                f.get("forma_cobro", String.class),
                f.get("cuenta_ingreso_id", UUID.class),
                f.get("politica_redondeo_id", UUID.class));
    }

    public record Concepto(
            UUID id,
            String codigo,
            String nombreComercial,
            String metodoCalculo,
            BigDecimal valorPorcentual,
            BigDecimal valorFijo,
            BigDecimal montoMinimo,
            BigDecimal montoMaximo,
            boolean gravadoIva,
            boolean gravadoIt,
            boolean precioIncluyeImpuesto,
            String formaCobro,
            UUID cuentaIngresoId,
            UUID politicaRedondeoId) {

        /** El concepto tal como lo usa el calculo, con los valores de la regla que gano. */
        public CalculoDeComision.Concepto paraCalculo(Regla regla) {
            return new CalculoDeComision.Concepto(
                    codigo,
                    nombreComercial,
                    metodoCalculo,
                    regla == null || regla.valorPorcentual() == null ? valorPorcentual : regla.valorPorcentual(),
                    regla == null || regla.valorFijo() == null ? valorFijo : regla.valorFijo(),
                    regla == null || regla.montoMinimo() == null ? montoMinimo : regla.montoMinimo(),
                    regla == null || regla.montoMaximo() == null ? montoMaximo : regla.montoMaximo(),
                    gravadoIva,
                    gravadoIt,
                    precioIncluyeImpuesto);
        }
    }

    public record Regla(
            UUID id,
            BigDecimal valorPorcentual,
            BigDecimal valorFijo,
            BigDecimal montoMinimo,
            BigDecimal montoMaximo) {}
}
