package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.nucleofinanciero.aplicacion.CU17BloquearPorAutoridad.EntradaBloqueo;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-16 · las pruebas de RECHAZO, en su propio archivo.
 *
 * <p>El armado del escenario se repite en los cuatro archivos del cierre y la
 * custodia: el corredor de integracion toma clases `CU*Test`, asi que juntarlas en una
 * sola las dejaba fuera del gate — y una prueba que no corre no prueba nada.
 */
class CU16RechazosTest extends BaseDeBilletera {

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
    @DisplayName("rechaza por R-BIL-13")
    void rechazaRBIL13() {
        // fn_bil_validar_cierre_cuenta: no se cierra con bloqueos, retenciones ni
        // saldo. La aplicacion lo comprueba para dar un mensaje util; la base lo hace
        // cumplir aunque la aplicacion se equivoque.
        UUID cuenta = cuentaConSaldo("500.00");
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> bloqueoCU.ejecutar(
                new EntradaBloqueo(
                        cuenta,
                        "JUZGADO",
                        "EMBARGO",
                        "OF-R13",
                        Optional.of(bob("100.00")),
                        "PARCIAL",
                        "https://of/r13.pdf",
                        "f".repeat(64)),
                ctx));

        assertThat(rechazaLaBase("UPDATE nucleo_financiero.cuenta_billetera SET estado = 'CERRADA' WHERE id = '%s'"
                        .formatted(cuenta)))
                .contains("R-BIL-13");
    }

    @Test
    @DisplayName("rechaza por R-BIL-02")
    void rechazaRBIL02() {
        UUID cuenta = cuentaConSaldo("100.00");

        assertThat(rechazaLaBase("UPDATE nucleo_financiero.cuenta_billetera SET saldo_disponible = -1 WHERE id = '%s'"
                        .formatted(cuenta)))
                .contains("ck_cuenta_saldo_no_negativo");
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO cumplimiento.expediente_cliente
                            (id, usuario_id, completitud_porcentaje, documentos, estado,
                             retencion_hasta, ultima_actualizacion)
                        VALUES (gen_random_uuid(), gen_random_uuid(), 100, '{}'::jsonb, 'COMPLETO',
                                current_date - 1, now())
                        """))
                .contains("ck_expediente_retencion_futura");
    }

    @Test
    @DisplayName("rechaza por R-CON-05")
    void rechazaRCON05() {
        // Diez años de conservacion. Depurar antes deja al cliente sin respaldo justo
        // cuando lo necesita para reclamar.
        //
        // Se verifica la REGLA y no una fila: `reclamo_cliente` cuelga de
        // `punto_reclamo`, que es del dominio de cumplimiento. Armar esa cadena desde
        // una prueba de la billetera seria fabricar un escenario que este servicio
        // nunca produce, y probaria el andamiaje en vez de la regla.
        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM pg_constraint
                         WHERE conname = 'ck_reclamo_conservacion'
                           AND pg_get_constraintdef(oid) LIKE '%10 years%'
                        """))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-SEG-06")
    void rechazaRSEG06() {
        // No se anonimiza antes de que venza la retencion del expediente.
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_seg_validar_anonimizacion"))
                .isEqualTo(1);
    }
}
