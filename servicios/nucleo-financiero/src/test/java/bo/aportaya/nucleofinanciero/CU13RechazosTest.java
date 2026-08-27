package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo.EntradaRetencion;
import bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo.SalidaRetencion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-13 · las pruebas de RECHAZO, en su propio archivo.
 *
 * <p>Otra pregunta que las de {@link CU13Test}: aquellas verifican que el caso de uso
 * hace lo que promete; estas, que la BASE rechaza lo que no debe entrar aunque la
 * aplicacion se equivoque.
 */
class CU13RechazosTest extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private UUID billeteraCon(String saldo, UUID usuario) {
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal(saldo));
        return cuenta;
    }

    @Test
    @DisplayName("rechaza por R-BIL-02")
    void rechazaRBIL02() {
        UUID usuario = fixtura.usuario();
        UUID cuenta = billeteraCon("100.00", usuario);

        assertThat(rechazaLaBase("UPDATE nucleo_financiero.cuenta_billetera SET saldo_disponible = -1 WHERE id = '%s'"
                        .formatted(cuenta)))
                .contains("ck_cuenta_saldo_no_negativo");
    }

    @Test
    @DisplayName("rechaza por R-BIL-03")
    void rechazaRBIL03() {
        // El retenido no baja de cero.
        //
        // HALLAZGO (el tercero de su clase): `generar_ddl.py` ya emite
        // `ck_cuenta_billetera_saldo_retenido` y sql/40_reglas declara ademas
        // `ck_cuenta_retenido_no_negativo`. Son la misma regla escrita dos veces, y
        // la que salta es la generada. Se afirma que la base RECHAZA y por que, sin
        // atarse a cual de las dos gano: quitar la duplicada es decision troncal.
        UUID usuario = fixtura.usuario();
        UUID cuenta = billeteraCon("100.00", usuario);

        assertThat(rechazaLaBase("UPDATE nucleo_financiero.cuenta_billetera SET saldo_retenido = -1 WHERE id = '%s'"
                        .formatted(cuenta)))
                .contains("saldo_retenido");
    }

    @Test
    @DisplayName("rechaza por R-BIL-07")
    void rechazaRBIL07() {
        // Los saldos se DERIVAN. Escribir la cache a mano no sirve: el proximo
        // movimiento la recalcula desde el libro y borra la mentira.
        UUID usuario = fixtura.usuario();
        UUID cuenta = billeteraCon("100.00", usuario);

        dslFixtura.execute(
                "UPDATE nucleo_financiero.cuenta_billetera SET saldo_disponible = 999999 WHERE id = ?", cuenta);
        fixtura.acreditar(cuenta, new BigDecimal("1.00"));

        assertThat(contar("SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuenta))
                .isEqualTo(101);
    }

    @Test
    @DisplayName("rechaza por R-BIL-08")
    void rechazaRBIL08() {
        UUID usuario = fixtura.usuario();
        UUID cuenta = billeteraCon("500.00", usuario);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.retencion_saldo
                            (id, cuenta_billetera_id, motivo, monto, estado, expira_en, creada_en)
                        VALUES (gen_random_uuid(), '%s', 'ANTIFRAUDE', 10.00, 'VIGENTE', NULL, now())
                        """
                                .formatted(cuenta)))
                .contains("ck_retencion_expira");
    }

    @Test
    @DisplayName("rechaza por R-BIL-16")
    void rechazaRBIL16() {
        // Retenido = suma de las VIGENTES. Cerrar una lo baja sin que nadie escriba
        // la columna.
        UUID usuario = fixtura.usuario();
        UUID cuenta = billeteraCon("1000.00", usuario);
        ContextoSesion ctx = contextoDe(usuario);
        SalidaRetencion a = transaccion.execute(
                e -> retencionCU.retener(EntradaRetencion.simple(cuenta, bob("200.00"), "DISPUTA"), ctx));
        transaccion.execute(e -> retencionCU.retener(EntradaRetencion.simple(cuenta, bob("300.00"), "DISPUTA"), ctx));

        assertThat(contar("SELECT saldo_retenido::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuenta))
                .isEqualTo(500);

        transaccion.execute(e -> retencionCU.liberar(a.retencionId(), ctx));

        assertThat(contar("SELECT saldo_retenido::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuenta))
                .isEqualTo(300);
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO comun.bitacora_evento
                            (id, secuencia, entidad, entidad_id, accion, origen, correlation_id,
                             hash_registro, hash_anterior, fecha_hora)
                        VALUES (gen_random_uuid(),
                                nextval(pg_get_serial_sequence('comun.bitacora_evento','secuencia')),
                                'retencion', gen_random_uuid(), 'CREACION', 'USUARIO',
                                gen_random_uuid(), repeat('a', 64), repeat('0', 64), now())
                        """))
                .isNotEmpty();
    }
}
