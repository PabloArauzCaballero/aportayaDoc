package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-24 · R-CTB-09 — el saldo de una cuenta contable lo deriva el motor desde
 * {@code movimiento_contable}, igual que el de billetera desde R-BIL-16.
 *
 * <p>Todo acá se ejercita saltándose {@code CU24RegistrarAsiento}: lo que se prueba es
 * que la garantía está en la base, no que la aplicación se acuerde de mantenerla.
 */
class CU24SaldoDerivadoTest extends BaseDeCU24 {

    @Test
    @DisplayName("rechaza R-CTB-09: nadie escribe el saldo — insertar el libro alcanza para que quede correcto")
    void elMotorDerivaElSaldo() {
        UUID deudora = fixtura.cuentaDeMovimiento(codigoCorto(), "ACTIVO", "DEUDORA");
        UUID acreedora = fixtura.cuentaDeMovimiento(codigoCorto(), "INGRESO", "ACREEDORA");

        asientoCuadrado(deudora, acreedora, "45.00", "para el saldo");

        // El signo lo da la naturaleza: en la deudora suma el debe, en la acreedora el
        // haber. Las dos quedan en 45, no una en 45 y otra en -45.
        assertThat(fixtura.saldoDe(deudora)).isEqualByComparingTo("45.00");
        assertThat(fixtura.saldoDe(acreedora)).isEqualByComparingTo("45.00");
    }

    @Test
    @DisplayName("rechaza R-CTB-09: un saldo apartado a mano vuelve al libro en el siguiente movimiento")
    void elSaldoEsCacheNoVerdad() {
        UUID deudora = fixtura.cuentaDeMovimiento(codigoCorto(), "ACTIVO", "DEUDORA");
        UUID acreedora = fixtura.cuentaDeMovimiento(codigoCorto(), "INGRESO", "ACREEDORA");
        asientoCuadrado(deudora, acreedora, "45.00", "primer asiento");

        transaccion.execute(estado -> {
            dsl.execute("UPDATE nucleo_financiero.cuenta_contable SET saldo = 999.00 WHERE id = ?", deudora);
            return null;
        });
        assertThat(fixtura.saldoDe(deudora)).isEqualByComparingTo("999.00");

        asientoCuadrado(deudora, acreedora, "5.00", "segundo asiento");

        // 45 + 5, no 999 + 5: el trigger recalcula desde el libro entero, no incrementa
        // lo que hubiera en la caché.
        assertThat(fixtura.saldoDe(deudora)).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("cuadre: la consulta de verificación 12 devuelve cero filas — ningún saldo se apartó del mayor")
    void ningunSaldoDivergeDelLibro() {
        UUID deudora = fixtura.cuentaDeMovimiento(codigoCorto(), "ACTIVO", "DEUDORA");
        UUID acreedora = fixtura.cuentaDeMovimiento(codigoCorto(), "INGRESO", "ACREEDORA");
        asientoCuadrado(deudora, acreedora, "12.34", "para verificar");

        var divergentes = dsl.fetch(
                """
                SELECT c.id
                  FROM nucleo_financiero.cuenta_contable c
                  LEFT JOIN LATERAL (
                        SELECT SUM(CASE WHEN c.naturaleza = 'DEUDORA' THEN m.debe - m.haber
                                        ELSE m.haber - m.debe END) AS derivado
                          FROM nucleo_financiero.movimiento_contable m
                         WHERE m.cuenta_id = c.id) l ON TRUE
                 WHERE c.saldo <> COALESCE(l.derivado, 0)
                """);

        assertThat(divergentes).isEmpty();
    }
}
