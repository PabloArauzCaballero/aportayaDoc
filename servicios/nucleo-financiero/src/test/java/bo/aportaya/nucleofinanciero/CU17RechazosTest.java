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
 * CU-17 · las pruebas de RECHAZO, en su propio archivo.
 *
 * <p>El armado del escenario se repite en los cuatro archivos del cierre y la
 * custodia: el corredor de integracion toma clases `CU*Test`, asi que juntarlas en una
 * sola las dejaba fuera del gate — y una prueba que no corre no prueba nada.
 */
class CU17RechazosTest extends BaseDeBilletera {

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
    @DisplayName("rechaza por R-BIL-08")
    void rechazaRBIL08() {
        UUID cuenta = cuentaConSaldo("100.00");

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
                                'bloqueo_saldo', gen_random_uuid(), 'CREACION', 'USUARIO',
                                gen_random_uuid(), repeat('a', 64), repeat('0', 64), now())
                        """))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-BIL-13")
    void rechazaRBIL13() {
        // Con un bloqueo vigente la cuenta no se cierra: la base lo hace cumplir
        // aunque la aplicacion se distraiga. Es la contracara del bloqueo — no sirve
        // inmovilizar la plata si despues se puede cerrar la cuenta y llevarsela.
        UUID cuenta = cuentaConSaldo("500.00");
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> bloqueoCU.ejecutar(
                new EntradaBloqueo(
                        cuenta,
                        "UIF",
                        "RETENCION",
                        "OF-17-R13",
                        Optional.of(bob("100.00")),
                        "PARCIAL",
                        "https://of/r13b.pdf",
                        "9".repeat(64)),
                ctx));

        assertThat(rechazaLaBase("UPDATE nucleo_financiero.cuenta_billetera SET estado = 'CERRADA' WHERE id = '%s'"
                        .formatted(cuenta)))
                .contains("R-BIL-13");
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
        // Consultar el saldo bloqueado de alguien es acceso a dato sensible, y exige
        // justificacion escrita.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO auditoria.registro_acceso_datos
                            (id, usuario_id, entidad, entidad_id, campos, justificacion, fecha_hora)
                        VALUES (gen_random_uuid(), gen_random_uuid(), 'bloqueo_saldo',
                                gen_random_uuid(), 'monto_bloqueado', NULL, now())
                        """))
                .isNotEmpty();
    }
}
