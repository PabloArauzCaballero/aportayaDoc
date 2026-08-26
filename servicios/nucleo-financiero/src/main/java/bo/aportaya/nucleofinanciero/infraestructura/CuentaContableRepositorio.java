package bo.aportaya.nucleofinanciero.infraestructura;

import static bo.aportaya.nucleofinanciero.generado.Tables.CUENTA_CONTABLE;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/** El plan de cuentas: resolver un código a su cuenta, y mover su saldo. */
@Component
public class CuentaContableRepositorio {

    /** {@code R-CTB-02} vive en la base; acá solo se lee lo que hace falta para decidir el signo. */
    public record CuentaEncontrada(UUID id, String naturaleza) {}

    public Optional<CuentaEncontrada> porCodigo(DSLContext dsl, String codigo) {
        return dsl.select(CUENTA_CONTABLE.ID, CUENTA_CONTABLE.NATURALEZA)
                .from(CUENTA_CONTABLE)
                .where(CUENTA_CONTABLE.CODIGO.eq(codigo))
                .fetchOptional(r -> new CuentaEncontrada(r.value1(), r.value2()));
    }

    /** Para la reversa: ya se tiene el {@code cuentaId} del movimiento original, solo falta el signo. */
    public String naturalezaDe(DSLContext dsl, UUID cuentaId) {
        return dsl.select(CUENTA_CONTABLE.NATURALEZA)
                .from(CUENTA_CONTABLE)
                .where(CUENTA_CONTABLE.ID.eq(cuentaId))
                .fetchOne(CUENTA_CONTABLE.NATURALEZA);
    }

    /**
     * El saldo de {@code cuenta_contable} no tiene trigger que lo derive —a diferencia
     * de {@code cuenta_billetera.saldo_*}—, así que CU-24 lo actualiza acá, en la misma
     * transacción del asiento, tal como dice su paso 4.
     */
    public void sumarAlSaldo(DSLContext dsl, UUID cuentaId, BigDecimal delta) {
        dsl.update(CUENTA_CONTABLE)
                .set(CUENTA_CONTABLE.SALDO, CUENTA_CONTABLE.SALDO.add(delta))
                .where(CUENTA_CONTABLE.ID.eq(cuentaId))
                .execute();
    }
}
