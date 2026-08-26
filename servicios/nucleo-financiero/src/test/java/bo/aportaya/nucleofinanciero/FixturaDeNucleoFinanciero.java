package bo.aportaya.nucleofinanciero;

import java.math.BigDecimal;
import java.util.UUID;
import org.jooq.DSLContext;

/**
 * Filas mínimas de {@code cuenta_contable} para probar CU-24 sin depender de
 * {@code sql/60_semillas} (que no está sembrado en la base de las pruebas de
 * integración — ver {@code BaseDePrueba}).
 *
 * <p>Se inserta como dueño de la base, igual que el resto de las fixturas del
 * proyecto: sembrar no es lo que se está probando.
 */
final class FixturaDeNucleoFinanciero {

    private final DSLContext dsl;

    FixturaDeNucleoFinanciero(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Una cuenta de movimiento del plan de cuentas, con saldo inicial en cero. */
    UUID cuentaDeMovimiento(String codigo, String tipo, String naturaleza) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_contable
                    (id, codigo, nombre, tipo, naturaleza, nivel, es_cuenta_de_movimiento, saldo)
                VALUES (?, ?, ?, ?, ?, 1, true, 0.00)
                """,
                id,
                codigo,
                "Cuenta de prueba " + codigo,
                tipo,
                naturaleza);
        return id;
    }

    /** {@code R-CTB-02}: una cuenta sumarizadora no recibe movimientos directos. */
    UUID cuentaSumarizadora(String codigo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_contable
                    (id, codigo, nombre, tipo, naturaleza, nivel, es_cuenta_de_movimiento, saldo)
                VALUES (?, ?, ?, 'ACTIVO', 'DEUDORA', 1, false, 0.00)
                """,
                id,
                codigo,
                "Cuenta sumarizadora " + codigo);
        return id;
    }

    BigDecimal saldoDe(UUID cuentaId) {
        return (BigDecimal) dsl.fetchOne("SELECT saldo FROM nucleo_financiero.cuenta_contable WHERE id = ?", cuentaId)
                .get(0);
    }
}
