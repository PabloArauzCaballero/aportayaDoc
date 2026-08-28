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
 * CU-15 · las pruebas de RECHAZO, en su propio archivo.
 *
 * <p>El armado del escenario se repite en los cuatro archivos del cierre y la
 * custodia: el corredor de integracion toma clases `CU*Test`, asi que juntarlas en una
 * sola las dejaba fuera del gate — y una prueba que no corre no prueba nada.
 */
class CU15RechazosTest extends BaseDeBilletera {

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
    @DisplayName("rechaza por R-CON-08")
    void rechazaRCON08() {
        // ck_extracto_cuadra: saldo_final = inicial + creditos - debitos. Un extracto
        // que no cierra su propia aritmetica no puede existir en la tabla.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.estado_cuenta_billetera
                            (id, cuenta_billetera_id, periodo_desde, periodo_hasta, saldo_inicial,
                             total_creditos, total_debitos, saldo_final, cantidad_movimientos,
                             url_archivo, hash_archivo, emitido_en)
                        VALUES (gen_random_uuid(), '%s', current_date - 30, current_date,
                                100.00, 50.00, 20.00, 999.00, 2, 'https://x/e.pdf',
                                repeat('e', 64), now())
                        """
                                .formatted(cuentaConSaldo("100.00"))))
                .contains("ck_extracto_cuadra");
    }

    @Test
    @DisplayName("rechaza por R-AUD-07")
    void rechazaRAUD07() {
        // uq_saldo_diario_cuenta_fecha: un cierre por cuenta y dia. Dos cierres del
        // mismo dia serian dos verdades sobre el mismo saldo.
        UUID cuenta = cuentaConSaldo("100.00");
        fixtura.cierreDelDia(cuenta, hoy(), new BigDecimal("100.00"), 1);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.saldo_diario_billetera
                            (id, cuenta_billetera_id, fecha, saldo_disponible, saldo_retenido,
                             cantidad_movimientos, hash_registro, cerrado_en)
                        VALUES (gen_random_uuid(), '%s', DATE '%s', 200.00, 0, 1, repeat('d', 64), now())
                        """
                                .formatted(cuenta, hoy())))
                .contains("cuenta_billetera_id");
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
        // El acceso a datos sensibles exige justificacion, y el extracto es de los
        // datos mas sensibles que hay.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO auditoria.registro_acceso_datos
                            (id, usuario_id, entidad, entidad_id, campos, justificacion, fecha_hora)
                        VALUES (gen_random_uuid(), gen_random_uuid(), 'cuenta_billetera',
                                gen_random_uuid(), 'saldo', NULL, now())
                        """))
                .isNotEmpty();
    }
}
