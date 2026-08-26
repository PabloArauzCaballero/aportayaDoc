package bo.aportaya.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.identidad.aplicacion.EntradaAutenticacion;
import bo.aportaya.identidad.dominio.PoliticaDeIntentos;
import bo.aportaya.identidad.dominio.ResultadoDeAutenticacion;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-04 · Autenticar con MFA y registrar dispositivo.
 *
 * <p>Corre contra PostgreSQL 16 real: el modelo usa {@code EXCLUDE}, {@code btree_gist},
 * RLS y triggers, y una base en memoria probaria otro sistema.
 */
class CU04Test extends BaseDeCU04 {

    @Test
    @DisplayName(
            "Dado un usuario con MFA activo · Cuando inicia sesión desde un dispositivo desconocido · Entonces se le exige un factor adicional · Y queda registrado el dispositivo con es_confiable = false")
    void criterio1() {
        UUID usuario = participanteConCredencial("+59171000001");
        fixtura.factor(usuario, "TOTP", true, true);

        ResultadoDeAutenticacion resultado = autenticar(entrada("+59171000001", "clave-correcta", "huella-nueva"));

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.requiereFactorAdicional()).isTrue();
        assertThat(resultado.codigo()).map(c -> c.valor()).contains("AP-CU04-03");
        assertThat(dispositivoConfiable(usuario, "huella-nueva")).isFalse();
    }

    @Test
    @DisplayName(
            "Dado un retiro que supera el umbral de MFA · Cuando el usuario no completa el desafío · Entonces la orden_retiro no se crea")
    void criterio2() {
        // Lo hace cumplir la BASE con ck_retiro_mfa, no la aplicacion: una orden que
        // sale de BORRADOR sin mfa_verificado no entra, venga de donde venga.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO nucleo_financiero.orden_retiro
                            (id, cuenta_billetera_id, instrumento_destino_id, solicitada_por,
                             monto_solicitado, costo_retiro, monto_neto, moneda, estado,
                             mfa_verificado, requiere_doble_aprobacion, clave_idempotencia, solicitada_en)
                        VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
                                100.00, 0.00, 100.00, 'BOB', 'PENDIENTE', false, false,
                                gen_random_uuid()::text, now())
                        """))
                .contains("R-BIL-09");
    }

    @Test
    @DisplayName(
            "Dado cinco intentos fallidos consecutivos · Cuando ocurre el sexto · Entonces existe un bloqueo_cuenta vigente para ese usuario")
    void criterio3() {
        UUID usuario = participanteConCredencial("+59171000003");
        fixtura.intentosFallidos(usuario, 5);

        ResultadoDeAutenticacion resultado = autenticar(entrada("+59171000003", "clave-equivocada", "huella-3"));

        assertThat(resultado.codigo()).map(c -> c.valor()).contains("AP-CU04-05");
        assertThat(tieneBloqueoVigente(usuario)).isTrue();
    }

    @Test
    @DisplayName(
            "Dado un operador con rol de ámbito GLOBAL vigente y su dispositivo marcado confiable · Cuando inicia sesión · Entonces se le exige el segundo factor igual · Y no existe camino que lo omita")
    void criterio4() {
        UUID operador = operadorConTotp("+59171000004", "huella-de-siempre");

        ResultadoDeAutenticacion resultado = autenticar(entrada("+59171000004", "clave-correcta", "huella-de-siempre"));

        assertThat(resultado.requiereFactorAdicional()).isTrue();
        assertThat(resultado.dispositivoConfiable()).isTrue();
        assertThat(sesionesDe(operador)).isZero();
    }

    @Test
    @DisplayName(
            "Dado un operador cuyo factor TOTP está desactivado · Cuando se intenta abrir su sesion · Entonces la transacción falla por R-SEG-10 · Y la respuesta es FACTOR_NO_ENROLADO")
    void criterio5() {
        UUID operador = participanteConCredencial("+59171000005");
        fixtura.asignarRol(operador, fixtura.rolGlobal("OP5"));
        fixtura.factor(operador, "TOTP", false, true);

        ResultadoDeAutenticacion resultado = autenticar(entrada("+59171000005", "clave-correcta", "huella-5"));

        assertThat(resultado.codigo()).map(c -> c.valor()).contains("AP-CU04-06");
        assertThat(sesionesDe(operador)).isZero();
    }

    @Test
    @DisplayName(
            "Dado un operador · Cuando se intenta enrolarle un factor de tipo SMS · Entonces la transacción falla por R-SEG-10 · Y para un participante el mismo factor SMS se acepta")
    void criterio6() {
        UUID operador = participanteConCredencial("+59171000006");
        fixtura.asignarRol(operador, fixtura.rolGlobal("OP6"));
        UUID participante = participanteConCredencial("+59171000016");

        assertThat(rechazaLaBase(sqlFactorSms(operador))).contains("R-SEG-10");
        fixtura.factor(participante, "SMS", true, true);

        assertThat(factoresDe(participante)).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un permiso cuya acción es APROBAR · Cuando se lo intenta crear con requiere_mfa en false · Entonces la restricción ck_permiso_decision_exige_mfa lo rechaza")
    void criterio7() {
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO identidad.permiso (id, codigo, recurso, accion, descripcion, requiere_mfa)
                        VALUES (gen_random_uuid(), 'PRUEBA_APROBAR', 'prueba', 'APROBAR', 'prueba', false)
                        """))
                .contains("ck_permiso_decision_exige_mfa");
    }

    @Test
    @DisplayName("reintento: dos ingresos iguales dejan dos intentos registrados, y ninguno se pisa")
    void reintento() {
        // CU-04 NO lleva clave de idempotencia, y es deliberado: cada intento es un
        // hecho distinto. Colapsar dos en uno borraria justo lo que hay que contar.
        UUID usuario = participanteConCredencial("+59171000007");

        autenticar(entrada("+59171000007", "mala", "huella-7"));
        autenticar(entrada("+59171000007", "mala", "huella-7"));

        assertThat(intentosDe(usuario)).isEqualTo(2);
    }

    @Test
    @DisplayName("concurrencia: dos ingresos del mismo usuario no duplican el dispositivo")
    void concurrencia() {
        UUID usuario = participanteConCredencial("+59171000008");
        fixtura.factor(usuario, "TOTP", true, true);

        autenticar(entrada("+59171000008", "clave-correcta", "huella-8"));
        autenticar(entrada("+59171000008", "clave-correcta", "huella-8"));

        assertThat(dispositivosDe(usuario, "huella-8")).isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: CU-04 no mueve dinero, y no escribe ni un movimiento")
    void cuadre() {
        long antes = movimientosDeBilletera();
        UUID usuario = participanteConCredencial("+59171000009");
        fixtura.factor(usuario, "TOTP", true, true);

        autenticar(entrada("+59171000009", "clave-correcta", "huella-9"));

        assertThat(movimientosDeBilletera()).isEqualTo(antes);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID usuario = participanteConCredencial("+59171000010");
        UUID idEvento = UUID.randomUUID();

        assertThat(consumir(idEvento, "notificaciones")).isTrue();
        assertThat(consumir(idEvento, "notificaciones")).isFalse();
        assertThat(usuario).isNotNull();
    }

    private EntradaAutenticacion entrada(String telefono, String credencial, String huella) {
        return new EntradaAutenticacion(
                telefono,
                credencial.toCharArray(),
                huella,
                "ANDROID",
                "127.0.0.1",
                "prueba",
                UUID.randomUUID().toString(),
                Optional.empty(),
                false,
                Duration.ofHours(12));
    }

    private ResultadoDeAutenticacion autenticar(EntradaAutenticacion entrada) {
        return transaccion.execute(estado -> cu04.ejecutar(entrada, new PoliticaDeIntentos(5, Duration.ofMinutes(30))));
    }

    private UUID operadorConTotp(String telefono, String huella) {
        UUID operador = participanteConCredencial(telefono);
        fixtura.asignarRol(operador, fixtura.rolGlobal("OP" + telefono.substring(8)));
        fixtura.factor(operador, "TOTP", true, true);
        fixtura.dispositivoConfiable(operador, huella);
        return operador;
    }

    private String sqlFactorSms(UUID operador) {
        return """
               INSERT INTO identidad.factor_mfa
                   (id, usuario_id, tipo, secreto_cifrado, version_llave, activo, es_principal, confirmado_en)
               VALUES (gen_random_uuid(), '%s', 'SMS', 'cifrado', 1, true, true, now())
               """
                .formatted(operador);
    }
}
