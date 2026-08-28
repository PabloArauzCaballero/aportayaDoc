package bo.aportaya.tarifas.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code devengo_comision} y {@code cargo_comision}.
 *
 * <p>El devengo es **append-only** ({@code tg_devengo_comision_append_only}): ganar y
 * cobrar son cosas distintas, y el registro de lo ganado no se borra porque el cobro
 * haya fallado. Lo unico que se mueve es el estado.
 */
@Component
public class DevengoRepositorio {

    public Optional<Devengo> porClave(DSLContext dsl, UUID grupoId, String clave) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("tarifas", "devengo_comision")))
                .where(DSL.coalesce(
                                DSL.field("grupo_id", UUID.class),
                                DSL.inline(UUID.fromString("00000000-0000-0000-0000-000000000000")))
                        .eq(grupoId == null ? UUID.fromString("00000000-0000-0000-0000-000000000000") : grupoId)
                        .and(DSL.field("clave_idempotencia", String.class).eq(clave)))
                .fetchOptional(this::aDevengo);
    }

    public Optional<Devengo> ver(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("tarifas", "devengo_comision")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(this::aDevengo);
    }

    public UUID registrar(
            DSLContext dsl,
            UUID conceptoId,
            UUID tarifarioId,
            UUID cotizacionId,
            UUID grupoId,
            UUID usuarioObligadoId,
            String referenciaTipo,
            UUID referenciaId,
            Dinero base,
            Dinero comision,
            Dinero descuento,
            Dinero impuesto,
            Dinero total,
            String estado,
            String periodoContable,
            String clave,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("tarifas", "devengo_comision")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("concepto_tarifa_id", UUID.class), conceptoId)
                .set(DSL.field("tarifario_id", UUID.class), tarifarioId)
                .set(DSL.field("cotizacion_id", UUID.class), cotizacionId)
                .set(DSL.field("grupo_id", UUID.class), grupoId)
                .set(DSL.field("usuario_obligado_id", UUID.class), usuarioObligadoId)
                .set(DSL.field("referencia_tipo", String.class), referenciaTipo)
                .set(DSL.field("referencia_id", UUID.class), referenciaId)
                .set(DSL.field("monto_base", BigDecimal.class), base.monto())
                .set(DSL.field("monto_comision", BigDecimal.class), comision.monto())
                .set(DSL.field("monto_descuento", BigDecimal.class), descuento.monto())
                .set(DSL.field("monto_impuesto", BigDecimal.class), impuesto.monto())
                .set(DSL.field("monto_total", BigDecimal.class), total.monto())
                .set(DSL.field("moneda", String.class), total.moneda().name())
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("fecha_devengo", OffsetDateTime.class), ahora)
                .set(DSL.field("periodo_contable", String.class), periodoContable)
                .set(DSL.field("clave_idempotencia", String.class), clave)
                .execute();
        return id;
    }

    /**
     * Toma el devengo con candado de fila.
     *
     * <p>No hay UPDATE que sirva de barrera —la tabla es append-only—, asi que la
     * exclusion la da el {@code FOR UPDATE}: dos cobros del mismo devengo se ponen en
     * fila y el segundo ve el cargo que dejo el primero.
     */
    public Optional<Devengo> bloquear(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("tarifas", "devengo_comision")))
                .where(DSL.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOptional(this::aDevengo);
    }

    /** Cuantos cargos fallidos lleva: es lo que decide si pasa a cobranza. */
    public int fallidosDe(DSLContext dsl, UUID devengoId) {
        return dsl.fetchCount(
                DSL.table(DSL.name("tarifas", "cargo_comision")),
                DSL.field("devengo_id", UUID.class)
                        .eq(devengoId)
                        .and(DSL.field("estado", String.class).eq("FALLIDO")));
    }

    public UUID registrarCargo(
            DSLContext dsl,
            UUID devengoId,
            String formaCobro,
            Dinero monto,
            String estado,
            int intentos,
            String ultimoError,
            OffsetDateTime cobradoEn) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("tarifas", "cargo_comision")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("devengo_id", UUID.class), devengoId)
                .set(DSL.field("forma_cobro", String.class), formaCobro)
                .set(DSL.field("monto_cobrado", BigDecimal.class), monto.monto())
                .set(DSL.field("moneda", String.class), monto.moneda().name())
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("intentos", Short.class), (short) intentos)
                .set(DSL.field("ultimo_error", String.class), ultimoError)
                .set(DSL.field("cobrado_en", OffsetDateTime.class), cobradoEn)
                .execute();
        return id;
    }

    /** Lo efectivamente cobrado de un devengo: la base de cuanto se puede devolver. */
    public Dinero cobradoDe(DSLContext dsl, UUID devengoId, Moneda moneda) {
        BigDecimal suma = dsl.select(
                        DSL.coalesce(DSL.sum(DSL.field("monto_cobrado", BigDecimal.class)), BigDecimal.ZERO))
                .from(DSL.table(DSL.name("tarifas", "cargo_comision")))
                .where(DSL.field("devengo_id", UUID.class)
                        .eq(devengoId)
                        .and(DSL.field("estado", String.class).eq("COBRADO")))
                .fetchOne(0, BigDecimal.class);
        return Dinero.de(suma, moneda);
    }

    public int intentosDe(DSLContext dsl, UUID devengoId) {
        return dsl.fetchCount(
                DSL.table(DSL.name("tarifas", "cargo_comision")),
                DSL.field("devengo_id", UUID.class).eq(devengoId));
    }

    /** La cuenta por cobrar de un devengo incobrable. Una por devengo. */
    public UUID abrirCuentaPorCobrar(
            DSLContext dsl, UUID devengoId, UUID usuarioId, Dinero monto, java.time.LocalDate vence) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("tarifas", "cuenta_por_cobrar_comision")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("devengo_id", UUID.class), devengoId)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("monto", BigDecimal.class), monto.monto())
                .set(DSL.field("saldo", BigDecimal.class), monto.monto())
                .set(DSL.field("dias_vencido", Short.class), (short) 0)
                .set(DSL.field("estado", String.class), "VIGENTE")
                .set(DSL.field("vence_en", java.time.LocalDate.class), vence)
                .execute();
        return id;
    }

    public void registrarImpuesto(
            DSLContext dsl,
            UUID devengoId,
            UUID impuestoId,
            BigDecimal baseImponible,
            BigDecimal alicuota,
            BigDecimal monto,
            boolean incluidoEnPrecio,
            String periodoFiscal) {

        dsl.insertInto(DSL.table(DSL.name("tarifas", "calculo_impuesto")))
                .set(DSL.field("id", UUID.class), UUID.randomUUID())
                .set(DSL.field("devengo_id", UUID.class), devengoId)
                .set(DSL.field("impuesto_id", UUID.class), impuestoId)
                .set(DSL.field("base_imponible", BigDecimal.class), baseImponible)
                .set(DSL.field("alicuota_aplicada", BigDecimal.class), alicuota)
                .set(DSL.field("monto_impuesto", BigDecimal.class), monto)
                .set(DSL.field("incluido_en_precio", Boolean.class), incluidoEnPrecio)
                .set(DSL.field("periodo_fiscal", String.class), periodoFiscal)
                .execute();
    }

    private java.util.List<org.jooq.Field<?>> campos() {
        return java.util.List.of(
                DSL.field("id", UUID.class),
                DSL.field("concepto_tarifa_id", UUID.class),
                DSL.field("usuario_obligado_id", UUID.class),
                DSL.field("monto_comision", BigDecimal.class),
                DSL.field("monto_total", BigDecimal.class),
                DSL.field("moneda", String.class),
                DSL.field("estado", String.class),
                DSL.field("periodo_contable", String.class));
    }

    private Devengo aDevengo(org.jooq.Record f) {
        Moneda moneda = Moneda.valueOf(f.get("moneda", String.class));
        return new Devengo(
                f.get("id", UUID.class),
                f.get("concepto_tarifa_id", UUID.class),
                f.get("usuario_obligado_id", UUID.class),
                Dinero.de(f.get("monto_comision", BigDecimal.class), moneda),
                Dinero.de(f.get("monto_total", BigDecimal.class), moneda),
                f.get("estado", String.class),
                f.get("periodo_contable", String.class));
    }

    public record Devengo(
            UUID id,
            UUID conceptoId,
            UUID usuarioObligadoId,
            Dinero montoComision,
            Dinero montoTotal,
            String estado,
            String periodoContable) {}
}
