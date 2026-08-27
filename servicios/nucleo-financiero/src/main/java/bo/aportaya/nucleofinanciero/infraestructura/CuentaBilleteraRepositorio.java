package bo.aportaya.nucleofinanciero.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code cuenta_billetera}: el saldo, su estado y su nivel.
 *
 * <p>El saldo **no se guarda como verdad**: se deriva del libro append-only y las
 * columnas son un cache que la base recalcula. Por eso aca no hay ningun metodo que
 * sume o reste saldo — quien mueve plata escribe movimientos, y el saldo sale de
 * ellos. Un {@code UPDATE saldo = saldo + x} seria una cifra que nadie puede
 * reconstruir despues.
 */
@Component
public class CuentaBilleteraRepositorio {

    /**
     * La cuenta, **bloqueada** para la operacion.
     *
     * <p>{@code FOR UPDATE} y no una lectura suelta: dos operaciones sobre la misma
     * billetera tienen que serializarse, o las dos leen el mismo saldo y las dos
     * pasan.
     */
    public Optional<Cuenta> bloquear(DSLContext dsl, UUID cuentaId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("moneda", String.class),
                        DSL.field("estado", String.class),
                        DSL.field("nivel_debida_diligencia", String.class),
                        DSL.field("saldo_disponible", BigDecimal.class),
                        DSL.field("saldo_retenido", BigDecimal.class),
                        DSL.field("permite_saldo_negativo", Boolean.class),
                        DSL.field("version", Integer.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "cuenta_billetera")))
                .where(DSL.field("id", UUID.class).eq(cuentaId))
                .forUpdate()
                .fetchOne();
        return Optional.ofNullable(fila).map(CuentaBilleteraRepositorio::mapear);
    }

    /** Lectura sin bloqueo, para consultar. Nunca para decidir sobre plata. */
    public Optional<Cuenta> ver(DSLContext dsl, UUID cuentaId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("moneda", String.class),
                        DSL.field("estado", String.class),
                        DSL.field("nivel_debida_diligencia", String.class),
                        DSL.field("saldo_disponible", BigDecimal.class),
                        DSL.field("saldo_retenido", BigDecimal.class),
                        DSL.field("permite_saldo_negativo", Boolean.class),
                        DSL.field("version", Integer.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "cuenta_billetera")))
                .where(DSL.field("id", UUID.class).eq(cuentaId))
                .fetchOne();
        return Optional.ofNullable(fila).map(CuentaBilleteraRepositorio::mapear);
    }

    private static Cuenta mapear(Record fila) {
        Moneda moneda = Moneda.valueOf(fila.get("moneda", String.class));
        return new Cuenta(
                fila.get("id", UUID.class),
                fila.get("usuario_id", UUID.class),
                moneda,
                fila.get("estado", String.class),
                fila.get("nivel_debida_diligencia", String.class),
                Dinero.de(fila.get("saldo_disponible", BigDecimal.class), moneda),
                Dinero.de(fila.get("saldo_retenido", BigDecimal.class), moneda),
                fila.get("permite_saldo_negativo", Boolean.class),
                fila.get("version", Integer.class));
    }

    public record Cuenta(
            UUID id,
            UUID usuarioId,
            Moneda moneda,
            String estado,
            String nivelDiligencia,
            Dinero disponible,
            Dinero retenido,
            boolean permiteNegativo,
            int version) {

        /** Solo una cuenta activa opera. El resto —cerrada, limitada— no. */
        public boolean operativa() {
            return "ACTIVA".equals(estado);
        }
    }
}
