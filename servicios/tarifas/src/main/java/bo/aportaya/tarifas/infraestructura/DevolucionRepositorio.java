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
 * {@code devolucion_comision} y {@code nota_credito_debito}.
 *
 * <p>La plata y el papel van atados: devolver sin nota de credito deja una factura
 * declarando un ingreso que ya no existe, y eso lo paga la plataforma en impuestos.
 */
@Component
public class DevolucionRepositorio {

    public Dinero devueltoDe(DSLContext dsl, UUID devengoId, Moneda moneda) {
        BigDecimal suma = dsl.select(
                        DSL.coalesce(DSL.sum(DSL.field("monto_devuelto", BigDecimal.class)), BigDecimal.ZERO))
                .from(DSL.table(DSL.name("tarifas", "devolucion_comision")))
                .where(DSL.field("devengo_id", UUID.class)
                        .eq(devengoId)
                        .and(DSL.field("estado", String.class).eq("EJECUTADA")))
                .fetchOne(0, BigDecimal.class);
        return Dinero.de(suma, moneda);
    }

    public UUID registrar(
            DSLContext dsl,
            UUID devengoId,
            UUID autorizadaPor,
            String motivo,
            String detalle,
            Dinero monto,
            String forma,
            UUID reclamoId,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("tarifas", "devolucion_comision")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("devengo_id", UUID.class), devengoId)
                .set(DSL.field("reclamo_id", UUID.class), reclamoId)
                .set(DSL.field("autorizada_por", UUID.class), autorizadaPor)
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("detalle", String.class), detalle)
                .set(DSL.field("monto_devuelto", BigDecimal.class), monto.monto())
                .set(DSL.field("forma", String.class), forma)
                .set(DSL.field("estado", String.class), "EJECUTADA")
                .set(DSL.field("solicitada_en", OffsetDateTime.class), ahora)
                .set(DSL.field("ejecutada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /** La factura del devengo, si tiene. Sin ella no hay nota de credito que emitir. */
    public Optional<Factura> facturaDe(DSLContext dsl, UUID devengoId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("cuf", String.class),
                        DSL.field("estado_fiscal", String.class),
                        DSL.field("lote_envio_sin_id", UUID.class),
                        DSL.field("monto_total", BigDecimal.class),
                        DSL.field("moneda", String.class))
                .from(DSL.table(DSL.name("tarifas", "factura_electronica")))
                .where(DSL.field("devengo_id", UUID.class).eq(devengoId))
                .fetchOptional(f -> new Factura(
                        f.get("id", UUID.class),
                        f.get("cuf", String.class),
                        f.get("estado_fiscal", String.class),
                        f.get("lote_envio_sin_id", UUID.class),
                        Dinero.de(
                                f.get("monto_total", BigDecimal.class),
                                Moneda.valueOf(f.get("moneda", String.class)))));
    }

    public UUID emitirNotaDeCredito(
            DSLContext dsl,
            UUID facturaId,
            UUID devolucionId,
            String motivo,
            Dinero monto,
            String cuf,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("tarifas", "nota_credito_debito")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("factura_id", UUID.class), facturaId)
                .set(DSL.field("devolucion_comision_id", UUID.class), devolucionId)
                .set(DSL.field("tipo", String.class), "CREDITO")
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("monto", BigDecimal.class), monto.monto())
                .set(DSL.field("cuf", String.class), cuf)
                .set(DSL.field("fecha_emision", OffsetDateTime.class), ahora)
                .set(DSL.field("estado_fiscal", String.class), "VALIDADA")
                .execute();
        return id;
    }

    public record Factura(UUID id, String cuf, String estadoFiscal, UUID loteEnvioId, Dinero montoTotal) {}
}
