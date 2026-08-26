package bo.aportaya.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Una prueba de rechazo por cada {@code R-XXX-nn} que CU-09 cita. */
class CU09RechazosTest extends BaseDeCU09 {

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Toda clave que alguna vez rigio queda en el historial: si se pudiera
        // borrar, «probar que no se reutilizo» dejaria de ser demostrable.
        dejarUnaFilaEnLaBitacora();

        assertThat(rechazaLaBase("DELETE FROM comun.bitacora_evento")).contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        UUID usuario = fixtura.usuario("+59175000001");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO identidad.historial_credencial (id, usuario_id, hash_contrasena)
                        VALUES (gen_random_uuid(), '%s', 'hash')
                        """
                                .formatted(usuario)))
                .contains("reemplazada_en");
    }

    @Test
    @DisplayName("rechaza por R-BIL-13")
    void rechazaRBIL13() {
        // No se cierra una cuenta con obligaciones, retenciones o bloqueos: lo corta
        // tg_cuenta_cierre_valido, del lado de nucleo_financiero. La baja de CU-09
        // se apoya en eso y no lo reimplementa.
        assertThat(triggerExiste("tg_cuenta_cierre_valido")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-CON-05")
    void rechazaRCON05() {
        UUID usuario = fixtura.usuario("+59175000002");

        // Una baja sin motivo escrito no es una baja: es una fila. R-CON-05 exige
        // que todo lo que el consumidor reclama quede conservado y motivado; aca la
        // parte que le toca a identidad es que `motivo` sea obligatorio.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO identidad.solicitud_baja (id, usuario_id, solicitada_en, bloqueada_por_obligaciones)
                        VALUES (gen_random_uuid(), '%s', now(), false)
                        """
                                .formatted(usuario)))
                .contains("motivo");
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO comun.registro_acceso_datos
                            (id, usuario_consultor_id, usuario_afectado_id, tipo_dato, operacion,
                             justificacion, cantidad_registros, ip_origen, fecha_hora)
                        VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'CREDENCIAL',
                                'CONSULTA', '', 1, '127.0.0.1', now())
                        """))
                .contains("ck_acceso_justificacion");
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname NOT IN ('pg_catalog','information_schema','pg_toast')
                           AND c.relkind = 'r' AND NOT c.relrowsecurity
                           AND EXISTS (SELECT 1 FROM pg_attribute a
                                        WHERE a.attrelid = c.oid AND a.attname = 'usuario_id'
                                          AND NOT a.attisdropped)
                        """))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-SEG-07")
    void rechazaRSEG07() {
        // Nadie se restablece a si mismo: la segregacion es la misma que impide
        // asignarse un rol.
        UUID operador = fixtura.usuario("+59175000003");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO identidad.asignacion_rol (id, usuario_id, rol_id, ambito, otorgada_por, otorgada_en)
                        VALUES (gen_random_uuid(), '%s', '%s', 'GLOBAL', '%s', now())
                        """
                                .formatted(operador, fixtura.rolGlobal("CU09SEG7"), operador)))
                .contains("ck_asignacion_no_autoasignada");
    }

    @Test
    @DisplayName("rechaza por R-SEG-10")
    void rechazaRSEG10() {
        // Recuperar la contrasena NO reactiva el segundo factor: son dos secretos
        // distintos, y sin TOTP el operador sigue sin poder abrir sesion.
        UUID operador = fixtura.usuario("+59175000004");
        fixtura.asignarRol(operador, fixtura.rolGlobal("CU09SEG10"));
        UUID dispositivo = fixtura.dispositivoConfiable(operador, "huella-cu09-seg10");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO identidad.sesion
                            (id, usuario_id, dispositivo_id, iniciada_en, ultima_actividad_en, expira_en, ip_origen)
                        VALUES (gen_random_uuid(), '%s', '%s', now(), now(), now() + interval '1 hour', '127.0.0.1'::inet)
                        """
                                .formatted(operador, dispositivo)))
                .contains("R-SEG-10");
    }

    @Test
    @DisplayName("rechaza por R-SEG-11")
    void rechazaRSEG11() {
        // Cambiar la credencial de un operador corta TODAS sus sesiones, y lo hace
        // la base: tg_credencial_operador_corta_sesiones.
        assertThat(triggerExiste("tg_credencial_operador_corta_sesiones")).isTrue();
    }

    private boolean constraintExiste(String nombre) {
        return contar("SELECT count(*)::int FROM pg_constraint WHERE conname = ?", nombre) > 0;
    }

    private boolean triggerExiste(String nombre) {
        return contar("SELECT count(*)::int FROM pg_trigger WHERE tgname = ?", nombre) > 0;
    }

    private int contar(String consulta, Object... ligaduras) {
        return ((Number) dsl.fetchOne(consulta, ligaduras).get(0)).intValue();
    }
}
