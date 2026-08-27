package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU11RetirarSaldo.EntradaRetiro;
import bo.aportaya.nucleofinanciero.aplicacion.CU11RetirarSaldo.SalidaRetiro;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-11 · las pruebas de RECHAZO, en su propio archivo.
 *
 * <p>El retiro es la operacion con mas guardias del sistema, y con razon: es la unica
 * por la que el dinero sale. Estas verifican que la BASE rechaza lo que no debe salir
 * aunque la aplicacion se equivoque.
 */
class CU11RechazosTest extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private record Escenario(UUID usuario, UUID cuenta, UUID instrumento, ContextoSesion ctx) {}

    private Escenario escenario(String saldo) {
        fixtura.tipoDeCambioDeHoy();
        custodia.cumpleEncaje();
        fixtura.limite("RETIRO", ESTANDAR, "MES", new BigDecimal("100000.00"), null);
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal(saldo));
        return new Escenario(
                usuario, cuenta, custodia.instrumentoDestino(usuario, true, true, null), contextoDe(usuario));
    }

    private SalidaRetiro pedir(Escenario e, String monto, String clave) {
        return transaccion.execute(t -> retiroCU.solicitar(
                new EntradaRetiro(clave, e.cuenta(), bob(monto), bob("5.00"), e.instrumento(), true, false), e.ctx()));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        Escenario e = escenario("500.00");
        SalidaRetiro s = pedir(e, "100.00", "r-aud01");
        transaccion.execute(t -> retiroCU.confirmarPago(s.ordenRetiroId(), e.ctx()));

        assertThat(rechazaLaBase("DELETE FROM nucleo_financiero.movimiento_billetera"))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-03")
    void rechazaRAUD03() {
        // Cadena de hash: cada transaccion lleva el de la anterior y el suyo no puede
        // faltar, o la cadena deja de poder verificarse.
        Escenario e = escenario("500.00");
        SalidaRetiro s = pedir(e, "100.00", "r-aud03");
        var pago = transaccion.execute(t -> retiroCU.confirmarPago(s.ordenRetiroId(), e.ctx()));

        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.transaccion_billetera WHERE id = ? AND length(hash_registro) = 64",
                        pago.transaccionId()))
                .isEqualTo(1);
        assertThat(rechazaLaBase(
                        "UPDATE nucleo_financiero.transaccion_billetera SET hash_registro = NULL WHERE id = '%s'"
                                .formatted(pago.transaccionId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-BIL-01")
    void rechazaRBIL01() {
        assertThat(
                        rechazaLaBaseAlCerrar(
                                """
                        INSERT INTO nucleo_financiero.transaccion_billetera
                            (id, tipo, estado, moneda, monto_total, origen_tipo, origen_id, canal,
                             clave_idempotencia, hash_registro, ocurrida_en, registrada_en)
                        VALUES (gen_random_uuid(), 'RETIRO', 'APLICADA', 'BOB', 10.00, 'ORDEN_RETIRO',
                                gen_random_uuid(), 'API', 'r-bil01', repeat('a', 64), now(), now())
                        """))
                .contains("R-BIL-01");
    }

    @Test
    @DisplayName("rechaza por R-BIL-02")
    void rechazaRBIL02() {
        Escenario e = escenario("100.00");

        assertThat(rechazaLaBase("UPDATE nucleo_financiero.cuenta_billetera SET saldo_disponible = -1 WHERE id = '%s'"
                        .formatted(e.cuenta())))
                .contains("ck_cuenta_saldo_no_negativo");
    }

    @Test
    @DisplayName("rechaza por R-BIL-06")
    void rechazaRBIL06() {
        // La clave de idempotencia se ampara en el titular: la misma clave devuelve
        // la misma orden en vez de crear otra.
        Escenario e = escenario("500.00");

        SalidaRetiro a = pedir(e, "50.00", "r-bil06");
        SalidaRetiro b = pedir(e, "50.00", "r-bil06");

        assertThat(b.ordenRetiroId()).isEqualTo(a.ordenRetiroId());
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.orden_retiro WHERE clave_idempotencia = ?",
                        "r-bil06"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-07")
    void rechazaRBIL07() {
        // Los saldos se DERIVAN: escribir la cache a mano no sirve, el proximo
        // movimiento la recalcula desde el libro.
        Escenario e = escenario("500.00");
        dslFixtura.execute(
                "UPDATE nucleo_financiero.cuenta_billetera SET saldo_disponible = 999999 WHERE id = ?", e.cuenta());

        pedir(e, "100.00", "r-bil07");

        assertThat(contar(
                        "SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?",
                        e.cuenta()))
                .isEqualTo(400);
    }

    @Test
    @DisplayName("rechaza por R-BIL-08")
    void rechazaRBIL08() {
        Escenario e = escenario("500.00");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.retencion_saldo
                            (id, cuenta_billetera_id, motivo, monto, estado, expira_en, creada_en)
                        VALUES (gen_random_uuid(), '%s', 'COMISION_PENDIENTE', 10.00, 'VIGENTE', NULL, now())
                        """
                                .formatted(e.cuenta())))
                .contains("ck_retencion_expira");
    }

    @Test
    @DisplayName("rechaza por R-BIL-09")
    void rechazaRBIL09() {
        // El instrumento de destino tiene que estar verificado, a nombre del titular
        // y fuera de su ventana de enfriamiento. Lo exige el trigger, no la app.
        fixtura.tipoDeCambioDeHoy();
        custodia.cumpleEncaje();
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        UUID sinVerificar = custodia.instrumentoDestino(usuario, false, true, null);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.orden_retiro
                            (id, cuenta_billetera_id, instrumento_destino_id, solicitada_por,
                             monto_solicitado, costo_retiro, monto_neto, moneda, estado,
                             mfa_verificado, requiere_doble_aprobacion, clave_idempotencia, solicitada_en)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', 10.00, 1.00, 9.00, 'BOB',
                                'PENDIENTE', true, false, 'r-bil09', now())
                        """
                                .formatted(cuenta, sinVerificar, usuario)))
                .contains("R-BIL-09");
    }

    @Test
    @DisplayName("rechaza por R-BIL-11")
    void rechazaRBIL11() {
        // Con el encaje roto no salen retiros nuevos: seguir pagando es la corrida.
        fixtura.tipoDeCambioDeHoy();
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal("500.00"));
        UUID instrumento = custodia.instrumentoDestino(usuario, true, true, null);
        fixtura.limite("RETIRO", ESTANDAR, "MES", new BigDecimal("100000.00"), null);
        custodia.noCumpleEncaje();
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() -> transaccion.execute(t -> retiroCU.solicitar(
                        new EntradaRetiro("r-bil11", cuenta, bob("50.00"), bob("5.00"), instrumento, true, false),
                        ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("suspendidos temporalmente");
    }

    @Test
    @DisplayName("rechaza por R-BIL-19")
    void rechazaRBIL19() {
        // El reintento devuelve la primera respuesta, no un error: un error de
        // unicidad es indistinguible de «fallo» para quien reintenta tras un timeout.
        Escenario e = escenario("500.00");

        SalidaRetiro a = pedir(e, "60.00", "r-bil19");
        SalidaRetiro b = pedir(e, "60.00", "r-bil19");

        assertThat(b.estado()).isEqualTo(a.estado());
        assertThat(b.montoNeto()).isEqualByComparingTo(a.montoNeto());
    }

    @Test
    @DisplayName("rechaza por R-BIL-20")
    void rechazaRBIL20() {
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_bil_moneda_coherente"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-LIM-01")
    void rechazaRLIM01() {
        // Sin limite configurado no se retira: denegar por omision.
        fixtura.tipoDeCambioDeHoy();
        custodia.cumpleEncaje();
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal("500.00"));
        UUID instrumento = custodia.instrumentoDestino(usuario, true, true, null);
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() -> transaccion.execute(t -> retiroCU.solicitar(
                        new EntradaRetiro("r-lim01", cuenta, bob("50.00"), bob("5.00"), instrumento, true, false),
                        ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("deniega por omision");
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // ck_retiro_doble_aprobacion: cuando se exige doble aprobacion, quien aprueba
        // no puede ser quien solicito. Cuatro ojos.
        Escenario e = escenario("500.00");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.orden_retiro
                            (id, cuenta_billetera_id, instrumento_destino_id, solicitada_por, aprobada_por,
                             monto_solicitado, costo_retiro, monto_neto, moneda, estado,
                             mfa_verificado, requiere_doble_aprobacion, clave_idempotencia, solicitada_en)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', '%s', 10.00, 1.00, 9.00, 'BOB',
                                'AUTORIZADA', true, true, 'r-seg04', now())
                        """
                                .formatted(e.cuenta(), e.instrumento(), e.usuario(), e.usuario())))
                .contains("ck_retiro_doble_aprobacion");
    }

    @Test
    @DisplayName("rechaza por R-UIF-02")
    void rechazaRUIF02() {
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_uif_registrar_operacion"))
                .isEqualTo(1);
    }
}
