package bo.aportaya.tarifas.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.tarifas.dominio.CalculoDeComision;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code cotizacion_comision}: la evidencia de que numero se le mostro al usuario.
 *
 * <p>El desglose se guarda entero. Seis meses despues, «se calculo con el tarifario
 * vigente» no responde nada; la lista de lineas si.
 */
@Component
public class CotizacionRepositorio {

    /** La cotizacion de un reintento, si ya existe. */
    public Optional<Cotizacion> porClave(DSLContext dsl, UUID referenciaId, String clave) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("concepto_tarifa_id", UUID.class),
                        DSL.field("monto_base", BigDecimal.class),
                        DSL.field("monto_comision", BigDecimal.class),
                        DSL.field("monto_impuesto", BigDecimal.class),
                        DSL.field("monto_total", BigDecimal.class),
                        DSL.field("moneda", String.class),
                        DSL.field("valida_hasta", OffsetDateTime.class))
                .from(DSL.table(DSL.name("tarifas", "cotizacion_comision")))
                .where(DSL.field("referencia_id", UUID.class)
                        .eq(referenciaId)
                        .and(DSL.field("clave_idempotencia", String.class).eq(clave)))
                .fetchOptional(this::aCotizacion);
    }

    public Optional<Cotizacion> ver(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("concepto_tarifa_id", UUID.class),
                        DSL.field("monto_base", BigDecimal.class),
                        DSL.field("monto_comision", BigDecimal.class),
                        DSL.field("monto_impuesto", BigDecimal.class),
                        DSL.field("monto_total", BigDecimal.class),
                        DSL.field("moneda", String.class),
                        DSL.field("valida_hasta", OffsetDateTime.class))
                .from(DSL.table(DSL.name("tarifas", "cotizacion_comision")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(this::aCotizacion);
    }

    public UUID guardar(
            DSLContext dsl,
            UUID conceptoId,
            UUID tarifarioId,
            String referenciaTipo,
            UUID referenciaId,
            CalculoDeComision.Resultado calculo,
            String desglose,
            OffsetDateTime validaHasta,
            OffsetDateTime mostradaEn,
            String clave) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("tarifas", "cotizacion_comision")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("concepto_tarifa_id", UUID.class), conceptoId)
                .set(DSL.field("tarifario_id", UUID.class), tarifarioId)
                .set(DSL.field("referencia_tipo", String.class), referenciaTipo)
                .set(DSL.field("referencia_id", UUID.class), referenciaId)
                .set(
                        DSL.field("monto_base", BigDecimal.class),
                        calculo.montoBase().monto())
                .set(
                        DSL.field("monto_comision", BigDecimal.class),
                        calculo.montoComision().monto())
                .set(
                        DSL.field("monto_impuesto", BigDecimal.class),
                        calculo.montoImpuesto().monto())
                .set(
                        DSL.field("monto_total", BigDecimal.class),
                        calculo.montoTotal().monto())
                .set(
                        DSL.field("moneda", String.class),
                        calculo.montoTotal().moneda().name())
                .set(DSL.field("desglose", JSONB.class), JSONB.valueOf(desglose))
                .set(DSL.field("valida_hasta", OffsetDateTime.class), validaHasta)
                .set(DSL.field("mostrada_al_usuario_en", OffsetDateTime.class), mostradaEn)
                .set(DSL.field("clave_idempotencia", String.class), clave)
                .execute();
        return id;
    }

    /** Marca que el usuario acepto. Sin esto no hay evidencia de consentimiento. */
    public boolean aceptar(DSLContext dsl, UUID id, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("tarifas", "cotizacion_comision")))
                        .set(DSL.field("aceptada_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("aceptada_en", OffsetDateTime.class)
                                        .isNull()))
                        .execute()
                == 1;
    }

    private Cotizacion aCotizacion(org.jooq.Record f) {
        Moneda moneda = Moneda.valueOf(f.get("moneda", String.class));
        return new Cotizacion(
                f.get("id", UUID.class),
                f.get("concepto_tarifa_id", UUID.class),
                Dinero.de(f.get("monto_base", BigDecimal.class), moneda),
                Dinero.de(f.get("monto_comision", BigDecimal.class), moneda),
                Dinero.de(f.get("monto_impuesto", BigDecimal.class), moneda),
                Dinero.de(f.get("monto_total", BigDecimal.class), moneda),
                f.get("valida_hasta", OffsetDateTime.class));
    }

    public record Cotizacion(
            UUID id,
            UUID conceptoId,
            Dinero montoBase,
            Dinero montoComision,
            Dinero montoImpuesto,
            Dinero montoTotal,
            OffsetDateTime validaHasta) {}
}
