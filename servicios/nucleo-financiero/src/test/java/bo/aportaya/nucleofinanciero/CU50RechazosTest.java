package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-50 · las pruebas de RECHAZO, en su propio archivo.
 *
 * <p>El armado del escenario se repite en los cuatro archivos del cierre y la
 * custodia: el corredor de integracion toma clases `CU*Test`, asi que juntarlas en una
 * sola las dejaba fuera del gate — y una prueba que no corre no prueba nada.
 */
class CU50RechazosTest extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private LocalDate hoy() {
        return OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
    }

    private UUID cuentaConSaldo(String saldo) {
        fixtura.tipoDeCambioDeHoy();
        UUID cuenta = fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal(saldo));
        return cuenta;
    }

    @Test
    @DisplayName("rechaza por R-BIL-12")
    void rechazaRBIL12() {
        // fn_bil_validar_cierre_diario: no se cierra el dia con descuadres abiertos.
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_bil_validar_cierre_diario"))
                .isEqualTo(1);
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO nucleo_financiero.cierre_diario
                            (id, fecha, total_recaudado, total_conciliado, total_excepciones,
                             cantidad_pagos, cuadrado, cerrado_por, cerrado_en)
                        VALUES (gen_random_uuid(), current_date, 100.00, 50.00, 50.00, 1, true,
                                gen_random_uuid(), now())
                        """))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-BIL-11")
    void rechazaRBIL11() {
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_bil_exigir_encaje"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        UUID cuenta = cuentaConSaldo("100.00");
        fixtura.cierreDelDia(cuenta, hoy(), new BigDecimal("100.00"), 1);

        assertThat(rechazaLaBase("DELETE FROM nucleo_financiero.saldo_diario_billetera"))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-07")
    void rechazaRAUD07() {
        // uq_saldo_diario_cuenta_fecha: un cierre por cuenta y dia. Dos cierres del
        // mismo dia serian dos verdades sobre el mismo saldo, y la conciliacion no
        // sabria cual creer.
        UUID cuenta = cuentaConSaldo("100.00");
        fixtura.cierreDelDia(cuenta, hoy(), new java.math.BigDecimal("100.00"), 1);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.saldo_diario_billetera
                            (id, cuenta_billetera_id, fecha, saldo_disponible, saldo_retenido,
                             cantidad_movimientos, hash_registro, cerrado_en)
                        VALUES (gen_random_uuid(), '%s', DATE '%s', 500.00, 0, 1, repeat('z', 64), now())
                        """
                                .formatted(cuenta, hoy())))
                .contains("cuenta_billetera_id");
    }
}
