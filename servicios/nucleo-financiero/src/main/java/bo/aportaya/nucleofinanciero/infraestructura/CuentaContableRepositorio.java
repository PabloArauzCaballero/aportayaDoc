package bo.aportaya.nucleofinanciero.infraestructura;

import static bo.aportaya.nucleofinanciero.generado.Tables.CUENTA_CONTABLE;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
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

    /**
     * Para la reversa: ya se tienen los {@code cuentaId} de los movimientos originales,
     * solo falta el signo de cada uno.
     *
     * <p>En bloque y no una consulta por movimiento: un asiento con veinte líneas son
     * veinte viajes a la base dentro de la transacción del hecho económico, y eso es un
     * N+1 sobre la ruta más caliente del sistema.
     */
    public Map<UUID, String> naturalezasDe(DSLContext dsl, Collection<UUID> cuentaIds) {
        if (cuentaIds.isEmpty()) {
            return Map.of();
        }
        return dsl.select(CUENTA_CONTABLE.ID, CUENTA_CONTABLE.NATURALEZA)
                .from(CUENTA_CONTABLE)
                .where(CUENTA_CONTABLE.ID.in(cuentaIds))
                .fetchMap(CUENTA_CONTABLE.ID, CUENTA_CONTABLE.NATURALEZA);
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
