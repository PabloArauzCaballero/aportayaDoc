package bo.aportaya.erp.infraestructura;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code cuenta_por_cobrar} y {@code cobro_cuenta_por_cobrar}.
 *
 * <p>**No se cobra por encima del saldo** (R-CTB-06). Cobrar de mas no es un error
 * contable menor: es plata que el cliente reclama y que ya no figura como deuda de la
 * empresa, asi que nadie la ve hasta que llama.
 */
@Component
public class CobranzasRepositorio {

    public UUID abrir(
            DSLContext dsl,
            String origenTipo,
            UUID origenId,
            UUID terceroId,
            BigDecimal monto,
            String moneda,
            java.time.LocalDate vencimiento) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "cuenta_por_cobrar")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("origen_tipo", String.class), origenTipo)
                .set(DSL.field("origen_id", UUID.class), origenId)
                .set(DSL.field("tercero_comercial_id", UUID.class), terceroId)
                .set(DSL.field("monto", BigDecimal.class), monto)
                .set(DSL.field("moneda", String.class), moneda)
                .set(DSL.field("monto_cobrado", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("fecha_vencimiento", java.time.LocalDate.class), vencimiento)
                .set(DSL.field("estado", String.class), "PENDIENTE")
                .execute();
        return id;
    }

    /**
     * La cuenta con bloqueo y **lo cobrado derivado de sus cobros**.
     *
     * <p>{@code cuenta_por_cobrar} es append-only (R-AUD-01), asi que
     * {@code monto_cobrado} se queda en cero para siempre. Lo cobrado es la suma de
     * {@code cobro_cuenta_por_cobrar}, que es donde esta el hecho.
     *
     * <p>{@code FOR UPDATE} porque el saldo se decide leyendo esa suma: sin bloquear,
     * dos cobros simultaneos leen el mismo total y los dos pasan.
     */
    public Optional<Cuenta> bloqueada(DSLContext dsl, UUID id) {
        var cuenta = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("monto", BigDecimal.class),
                        DSL.field("moneda", String.class),
                        DSL.field("estado", String.class))
                .from(DSL.table(DSL.name("erp", "cuenta_por_cobrar")))
                .where(DSL.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOne();
        if (cuenta == null) {
            return Optional.empty();
        }
        BigDecimal cobrado = dsl.select(DSL.coalesce(DSL.sum(DSL.field("monto", BigDecimal.class)), BigDecimal.ZERO)
                        .as("cobrado"))
                .from(DSL.table(DSL.name("erp", "cobro_cuenta_por_cobrar")))
                .where(DSL.field("cuenta_por_cobrar_id", UUID.class).eq(id))
                .fetchOne(f -> f.get("cobrado", BigDecimal.class));

        return Optional.of(new Cuenta(
                cuenta.get("id", UUID.class),
                cuenta.get("monto", BigDecimal.class),
                cobrado,
                cuenta.get("moneda", String.class),
                cuenta.get("estado", String.class)));
    }

    public UUID cobrar(
            DSLContext dsl,
            UUID cuentaId,
            BigDecimal monto,
            String moneda,
            String formaCobro,
            BigDecimal cobradoNuevo,
            BigDecimal montoTotal,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "cobro_cuenta_por_cobrar")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("cuenta_por_cobrar_id", UUID.class), cuentaId)
                .set(DSL.field("monto", BigDecimal.class), monto)
                .set(DSL.field("moneda", String.class), moneda)
                .set(DSL.field("fecha_cobro", OffsetDateTime.class), ahora)
                .set(DSL.field("forma_cobro", String.class), formaCobro)
                .execute();

        // La cuenta NO se toca: es append-only. Su estado se deriva de la suma de sus
        // cobros, que es la unica cifra que se mueve de verdad.
        return id;
    }

    public record Cuenta(UUID id, BigDecimal monto, BigDecimal montoCobrado, String moneda, String estado) {}
}
