package bo.aportaya.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.identidad.aplicacion.CU08AsignarRol.EntradaAsignacion;
import bo.aportaya.identidad.aplicacion.CU08AsignarRol.SalidaAsignacion;
import bo.aportaya.identidad.dominio.PermisosEfectivos;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-08 · Asignar y revocar roles de operador. */
class CU08Test extends BaseDeCU08 {

    @Test
    @DisplayName(
            "Dado un rol de ámbito GLOBAL con permisos de cumplimiento · Cuando el administrador se lo asigna a un analista con MFA configurado · Entonces existe una asignacion_rol vigente · Y sus permisos efectivos incluyen los del rol")
    void criterio1() {
        UUID administrador = fixtura.usuario("+59172000001");
        UUID analista = fixtura.usuario("+59172000002");
        fixtura.factor(analista, "TOTP", true, true);
        UUID rol = rolConPermiso("CUMP1", "CUMPLIMIENTO_EVALUAR", "cumplimiento", "LEER");

        SalidaAsignacion salida = asignarComo(administrador, analista, rol, "GLOBAL", Optional.empty());

        assertThat(salida.permisosEfectivos()).contains("CUMPLIMIENTO_EVALUAR");
        assertThat(salida.requiereMfa()).isFalse();
        assertThat(asignacionesDe(analista)).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un usuario que ya tiene el permiso de autorizar entregas · Cuando se le intenta asignar el rol que ejecuta entregas · Entonces se rechaza con INCOMPATIBILIDAD_DE_FUNCIONES")
    void criterio2() {
        UUID administrador = fixtura.usuario("+59172000003");
        UUID operador = fixtura.usuario("+59172000004");
        UUID autoriza = rolConPermiso("AUT1", "ENTREGAS_AUTORIZAR", "entregas", "AUTORIZAR");
        UUID ejecuta = rolConPermiso("EJE1", "ENTREGAS_EJECUTAR", "entregas", "EJECUTAR");
        asignarComo(administrador, operador, autoriza, "GLOBAL", Optional.empty());

        assertThatThrownBy(() -> asignarComo(administrador, operador, ejecuta, "GLOBAL", Optional.empty()))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("autoriza y ejecuta");
    }

    @Test
    @DisplayName(
            "Dada una asignación vigente con una sesión abierta · Cuando se la revoca · Entonces la asignación queda con revocada_en y motivo · Y la sesión del usuario deja de ser válida")
    void criterio3() {
        UUID administrador = fixtura.usuario("+59172000005");
        UUID operador = fixtura.usuario("+59172000006");
        UUID rol = rolConPermiso("SOP1", "SOPORTE_LEER", "soporte", "LEER");
        // Con rol GLOBAL y sin TOTP la base no deja abrir sesion (R-SEG-10), asi que
        // para tener una sesion que revocar el operador tiene que estar enrolado.
        fixtura.factor(operador, "TOTP", true, true);
        SalidaAsignacion salida = asignarComo(administrador, operador, rol, "GLOBAL", Optional.empty());
        fixtura.sesionAbierta(operador);

        transaccion.execute(e ->
                revocar.ejecutar(salida.asignacionId(), "cambio de funcion", true, comoAdministrador(administrador)));

        assertThat(revocadaEn(salida.asignacionId())).isNotNull();
        assertThat(motivoDe(salida.asignacionId())).isEqualTo("cambio de funcion");
        assertThat(sesionesVivasDe(operador)).isZero();
    }

    @Test
    @DisplayName(
            "Dada una asignación con vigente_hasta en el pasado · Cuando corre el trabajo diario de caducidad · Entonces deja de contar en los permisos efectivos sin que nadie la borre")
    void criterio4() {
        OffsetDateTime ayer = OffsetDateTime.now().minusDays(1);
        var vencida = new PermisosEfectivos.AsignacionVigente(
                ayer.minusDays(10), Optional.of(ayer), Optional.empty(), Set.of("SOPORTE_LEER"));

        assertThat(PermisosEfectivos.de(List.of(vencida), OffsetDateTime.now())).isEmpty();
        assertThat(PermisosEfectivos.de(List.of(vencida), ayer.minusHours(1))).contains("SOPORTE_LEER");
    }

    @Test
    @DisplayName(
            "Dado un usuario sin factor TOTP · Cuando se le asigna un rol de ámbito GLOBAL · Entonces la asignacion_rol queda escrita y vigente · Y al intentar abrir su sesion la base la rechaza por R-SEG-10")
    void criterio5() {
        UUID administrador = fixtura.usuario("+59172000007");
        UUID sinFactor = fixtura.usuario("+59172000008");
        UUID rol = rolConPermiso("GLOB1", "BACKOFFICE_ENTRAR", "backoffice", "LEER");

        SalidaAsignacion salida = asignarComo(administrador, sinFactor, rol, "GLOBAL", Optional.empty());

        assertThat(salida.requiereMfa()).isTrue();
        assertThat(asignacionesDe(sinFactor)).isEqualTo(1);
        assertThat(rechazaLaBase(sqlSesion(sinFactor))).contains("R-SEG-10");
    }

    @Test
    @DisplayName(
            "Dado un administrador de plataforma · Cuando intenta asignarse a sí mismo un rol · Entonces la restricción ck_asignacion_no_autoasignada lo rechaza (R-SEG-07)")
    void criterio6() {
        UUID administrador = fixtura.usuario("+59172000009");
        UUID rol = rolConPermiso("AUTO1", "ACCESOS_ADMINISTRAR", "accesos", "ADMINISTRAR");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO identidad.asignacion_rol (id, usuario_id, rol_id, ambito, otorgada_por, otorgada_en)
                        VALUES (gen_random_uuid(), '%s', '%s', 'GLOBAL', '%s', now())
                        """
                                .formatted(administrador, rol, administrador)))
                .contains("ck_asignacion_no_autoasignada");
    }

    @Test
    @DisplayName("reintento: la misma asignación dos veces la corta uq_asignacion_vigente, no la aplicación")
    void reintento() {
        // CU-08 no lleva clave de idempotencia porque no la necesita: la unicidad
        // parcial `WHERE revocada_en IS NULL` ya garantiza una sola asignacion
        // vigente por (usuario, rol, ambito). Reintentar no puede duplicar nada.
        UUID administrador = fixtura.usuario("+59172000010");
        UUID operador = fixtura.usuario("+59172000011");
        UUID rol = rolConPermiso("REP1", "SOPORTE_ESCRIBIR", "soporte", "ESCRIBIR");
        asignarComo(administrador, operador, rol, "GLOBAL", Optional.empty());

        assertThatThrownBy(() -> asignarComo(administrador, operador, rol, "GLOBAL", Optional.empty()))
                .hasMessageContaining("uq_asignacion_vigente");
        assertThat(asignacionesDe(operador)).isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: revocar dos veces la misma asignación no la revoca dos veces")
    void concurrencia() {
        UUID administrador = fixtura.usuario("+59172000012");
        UUID operador = fixtura.usuario("+59172000013");
        UUID rol = rolConPermiso("CON1", "SOPORTE_CERRAR", "soporte", "CERRAR");
        SalidaAsignacion salida = asignarComo(administrador, operador, rol, "GLOBAL", Optional.empty());

        transaccion.execute(
                e -> revocar.ejecutar(salida.asignacionId(), "primera", true, comoAdministrador(administrador)));
        OffsetDateTime primera = revocadaEn(salida.asignacionId());
        transaccion.execute(
                e -> revocar.ejecutar(salida.asignacionId(), "segunda", true, comoAdministrador(administrador)));

        assertThat(revocadaEn(salida.asignacionId())).isEqualTo(primera);
        assertThat(motivoDe(salida.asignacionId())).isEqualTo("primera");
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "cumplimiento"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "cumplimiento"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    private SalidaAsignacion asignarComo(
            UUID administrador, UUID destinatario, UUID rol, String ambito, Optional<UUID> ambitoId) {
        return transaccion.execute(e -> asignar.ejecutar(
                new EntradaAsignacion(destinatario, rol, ambito, ambitoId, Optional.empty(), "justificacion de prueba"),
                comoAdministrador(administrador)));
    }

    private UUID rolConPermiso(String codigoRol, String codigoPermiso, String recurso, String accion) {
        UUID rol = fixtura.rolGlobal(codigoRol);
        // Las acciones de decision exigen MFA: lo impone ck_permiso_decision_exige_mfa.
        boolean esDecision = Set.of("AUTORIZAR", "APROBAR", "EJECUTAR", "REVERSAR", "PUBLICAR", "ENVIAR", "CERRAR")
                .contains(accion);
        fixtura.darPermisoAlRol(rol, fixtura.permiso(codigoPermiso, recurso, accion, esDecision));
        return rol;
    }

    private String sqlSesion(UUID usuario) {
        UUID dispositivo = fixtura.dispositivoConfiable(usuario, "huella-sesion-" + usuario);
        return """
               INSERT INTO identidad.sesion
                   (id, usuario_id, dispositivo_id, iniciada_en, ultima_actividad_en, expira_en, ip_origen)
               VALUES (gen_random_uuid(), '%s', '%s', now(), now(), now() + interval '1 hour', '127.0.0.1'::inet)
               """
                .formatted(usuario, dispositivo);
    }

    private OffsetDateTime revocadaEn(UUID asignacionId) {
        return (OffsetDateTime)
                dsl.fetchOne("SELECT revocada_en FROM identidad.asignacion_rol WHERE id = ?", asignacionId)
                        .get(0);
    }

    private String motivoDe(UUID asignacionId) {
        return (String)
                dsl.fetchOne("SELECT motivo_revocacion FROM identidad.asignacion_rol WHERE id = ?", asignacionId)
                        .get(0);
    }
}
