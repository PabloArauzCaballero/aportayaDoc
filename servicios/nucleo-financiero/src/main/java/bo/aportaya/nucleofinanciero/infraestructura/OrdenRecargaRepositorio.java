package bo.aportaya.nucleofinanciero.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code orden_recarga}: el pedido de ingreso de fondos y su ciclo. */
@Component
public class OrdenRecargaRepositorio {

    public UUID crear(
            DSLContext dsl,
            UUID cuentaId,
            Optional<UUID> instrumentoId,
            Dinero bruto,
            Dinero costoProveedor,
            Dinero acreditado,
            String claveIdempotencia,
            OffsetDateTime ahora,
            OffsetDateTime expiraEn) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("nucleo_financiero", "orden_recarga")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("cuenta_billetera_id", UUID.class), cuentaId)
                .set(DSL.field("instrumento_fondeo_id", UUID.class), instrumentoId.orElse(null))
                .set(DSL.field("monto_bruto", BigDecimal.class), bruto.monto())
                .set(DSL.field("costo_proveedor", BigDecimal.class), costoProveedor.monto())
                .set(DSL.field("monto_acreditado", BigDecimal.class), acreditado.monto())
                .set(DSL.field("moneda", String.class), bruto.moneda().name())
                .set(DSL.field("estado", String.class), "PENDIENTE")
                .set(DSL.field("clave_idempotencia", String.class), claveIdempotencia)
                .set(DSL.field("solicitada_en", OffsetDateTime.class), ahora)
                .set(DSL.field("expira_en", OffsetDateTime.class), expiraEn)
                .execute();
        return id;
    }

    public Optional<Orden> ver(DSLContext dsl, UUID ordenId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("cuenta_billetera_id", UUID.class),
                        DSL.field("monto_bruto", BigDecimal.class),
                        DSL.field("monto_acreditado", BigDecimal.class),
                        DSL.field("moneda", String.class),
                        DSL.field("estado", String.class),
                        DSL.field("expira_en", OffsetDateTime.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "orden_recarga")))
                .where(DSL.field("id", UUID.class).eq(ordenId))
                .fetchOne();
        return Optional.ofNullable(fila).map(f -> {
            Moneda moneda = Moneda.valueOf(f.get("moneda", String.class));
            return new Orden(
                    f.get("id", UUID.class),
                    f.get("cuenta_billetera_id", UUID.class),
                    Dinero.de(f.get("monto_bruto", BigDecimal.class), moneda),
                    Dinero.de(f.get("monto_acreditado", BigDecimal.class), moneda),
                    f.get("estado", String.class),
                    f.get("expira_en", OffsetDateTime.class));
        });
    }

    public Optional<UUID> porClaveIdempotencia(DSLContext dsl, String clave) {
        return Optional.ofNullable(dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "orden_recarga")))
                .where(DSL.field("clave_idempotencia").eq(clave))
                .fetchOne(DSL.field("id", UUID.class)));
    }

    /**
     * Acredita la orden, **solo si sigue pendiente**.
     *
     * <p>El {@code WHERE estado = 'PENDIENTE'} es lo que impide acreditar dos veces
     * cuando el proveedor reenvia la confirmacion: sin el, el mismo pago sumaria saldo
     * dos veces y no habria forma de saber cual de los dos fue el bueno.
     */
    public boolean acreditar(DSLContext dsl, UUID ordenId, UUID transaccionId, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("nucleo_financiero", "orden_recarga")))
                        .set(DSL.field("estado", String.class), "ACREDITADA")
                        .set(DSL.field("transaccion_id", UUID.class), transaccionId)
                        .set(DSL.field("acreditada_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class).eq(ordenId))
                        .and(DSL.field("estado").eq("PENDIENTE"))
                        .execute()
                > 0;
    }

    public int expirarVencidas(DSLContext dsl, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("nucleo_financiero", "orden_recarga")))
                .set(DSL.field("estado", String.class), "EXPIRADA")
                .where(DSL.field("estado").eq("PENDIENTE"))
                .and(DSL.field("expira_en", OffsetDateTime.class).lt(ahora))
                .execute();
    }

    /** El instrumento de fondeo, si esta verificado y es del titular. */
    public Optional<Instrumento> instrumento(DSLContext dsl, UUID instrumentoId) {
        Record fila = dsl.select(
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("estado_verificacion", String.class),
                        DSL.field("titular_coincide", Boolean.class),
                        DSL.field("bloqueado_hasta", OffsetDateTime.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "instrumento_fondeo")))
                .where(DSL.field("id", UUID.class).eq(instrumentoId))
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Instrumento(
                        f.get("usuario_id", UUID.class),
                        "VERIFICADO".equals(f.get("estado_verificacion", String.class)),
                        f.get("titular_coincide", Boolean.class),
                        Optional.ofNullable(f.get("bloqueado_hasta", OffsetDateTime.class))));
    }

    public record Orden(
            UUID id, UUID cuentaId, Dinero bruto, Dinero acreditado, String estado, OffsetDateTime expiraEn) {}

    public record Instrumento(
            UUID usuarioId, boolean verificado, boolean titularCoincide, Optional<OffsetDateTime> bloqueadoHasta) {}
}
