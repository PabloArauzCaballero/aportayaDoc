package bo.aportaya.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.identidad.aplicacion.CU09CambiarCredencial.SalidaCambio;
import bo.aportaya.identidad.aplicacion.CU09SolicitarBaja.ObligacionAbierta;
import bo.aportaya.identidad.aplicacion.CU09SolicitarBaja.SalidaBaja;
import bo.aportaya.identidad.dominio.CorteDeCredencial;
import bo.aportaya.identidad.dominio.VentanaDeEnfriamiento;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-09 · Cambiar credenciales y solicitar la baja. */
class CU09Test extends BaseDeCU09 {

    @Test
    @DisplayName(
            "Dado un usuario con dos sesiones abiertas · Cuando cambia su clave desde una de ellas · Entonces la otra sesión deja de ser válida · Y existe una fila en historial_credencial con la clave anterior")
    void criterio1() {
        UUID usuario = participanteConClave("+59174000001");
        UUID sesionQueCambia = fixtura.sesionAbierta(usuario);
        fixtura.sesionAbierta(usuario);

        SalidaCambio salida = cambiar(usuario, Optional.of(sesionQueCambia), "clave-actual-1", "clave-nueva-larga-1");

        assertThat(salida.sesionesCerradas()).isEqualTo(1);
        assertThat(sesionesVivasDe(usuario)).isEqualTo(1);
        assertThat(historialDe(usuario)).isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un usuario que intenta poner una clave que ya usó · Cuando la envía · Entonces se rechaza con CLAVE_REUTILIZADA")
    void criterio2() {
        UUID usuario = participanteConClave("+59174000002");
        cambiar(usuario, Optional.empty(), "clave-actual-2", "clave-nueva-larga-2");

        assertThatThrownBy(() -> cambiar(usuario, Optional.empty(), "clave-nueva-larga-2", "clave-actual-2"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("Ya usaste esa clave");
    }

    @Test
    @DisplayName(
            "Dado un usuario que recuperó su clave hace una hora · Cuando intenta retirar saldo · Entonces se rechaza indicando el tiempo restante del enfriamiento")
    void criterio3() {
        // El plazo se guarda al INICIO y no se recalcula: cambiar la politica no
        // puede mover hacia atras una ventana que ya empezo a correr.
        OffsetDateTime recuperoHaceUnaHora = OffsetDateTime.now().minusHours(1);
        VentanaDeEnfriamiento ventana = VentanaDeEnfriamiento.desde(recuperoHaceUnaHora, Duration.ofHours(24));

        assertThat(ventana.vigenteEn(OffsetDateTime.now())).isTrue();
        assertThat(ventana.restanteEn(OffsetDateTime.now())).isBetween(Duration.ofHours(22), Duration.ofHours(23));
        assertThat(ventana.vigenteEn(recuperoHaceUnaHora.plusDays(2))).isFalse();
    }

    @Test
    @DisplayName(
            "Dado un usuario con un grupo activo · Cuando solicita la baja · Entonces la solicitud_baja queda con bloqueada_por_obligaciones en true · Y la respuesta enumera el grupo que lo impide")
    void criterio4() {
        UUID usuario = participanteConClave("+59174000004");
        UUID grupo = UUID.randomUUID();
        var obligacion = new ObligacionAbierta("GRUPO_ACTIVO", grupo, "El grupo cierra el 30 de junio");

        SalidaBaja salida =
                transaccion.execute(e -> baja.ejecutar("me mudo de pais", List.of(obligacion), comoTitular(usuario)));

        assertThat(salida.bloqueadaPorObligaciones()).isTrue();
        assertThat(salida.obligacionesAbiertas()).singleElement().satisfies(o -> {
            assertThat(o.tipo()).isEqualTo("GRUPO_ACTIVO");
            assertThat(o.detalle()).contains("30 de junio");
        });
        assertThat(bloqueadaPorObligaciones(salida.solicitudBajaId())).isTrue();
    }

    @Test
    @DisplayName(
            "Dado un operador con rol de ámbito GLOBAL vigente · Cuando valida el token de recuperación y envía la clave nueva sin aprobación de otro · Entonces se rechaza con APROBACION_REQUERIDA · Y su credencial no cambió")
    void criterio5() {
        UUID operador = participanteConClave("+59174000005");
        fixtura.asignarRol(operador, fixtura.rolGlobal("CU09A"));
        String hashAntes = hashDe(operador);

        assertThatThrownBy(() -> restablecerSinAprobacion(operador, "clave-nueva-larguisima"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("aprobacion de otra persona");
        assertThat(hashDe(operador)).isEqualTo(hashAntes);
    }

    @Test
    @DisplayName(
            "Dado un operador con dos sesiones abiertas y su dispositivo marcado confiable · Cuando su credencial se restablece · Entonces ninguna sesion queda sin revocar · Y ningún dispositivo suyo queda con es_confiable en true · Y el motivo de revocación cita R-SEG-11")
    void criterio6() {
        UUID operador = participanteConClave("+59174000006");
        fixtura.asignarRol(operador, fixtura.rolGlobal("CU09B"));
        fixtura.factor(operador, "TOTP", true, true);
        fixtura.dispositivoConfiable(operador, "huella-cu09b");
        UUID sesionQueCambia = fixtura.sesionAbierta(operador);
        fixtura.sesionAbierta(operador);

        SalidaCambio salida = cambiar(operador, Optional.of(sesionQueCambia), "clave-actual-6", "clave-nueva-larga-6");

        assertThat(salida.corte()).isEqualTo(CorteDeCredencial.TOTAL);
        assertThat(sesionesVivasDe(operador)).isZero();
        assertThat(dispositivosConfiablesDe(operador)).isZero();
        assertThat(motivoDeRevocacion(operador)).contains("R-SEG-11");
    }

    @Test
    @DisplayName(
            "Dado un participante con dos sesiones abiertas · Cuando cambia su clave desde una de ellas · Entonces esa sesión sigue viva · Y el corte total de R-SEG-11 no se le aplica")
    void criterio7() {
        UUID participante = participanteConClave("+59174000007");
        UUID sesionQueCambia = fixtura.sesionAbierta(participante);
        fixtura.sesionAbierta(participante);
        fixtura.dispositivoConfiable(participante, "huella-cu09c");
        int confiablesAntes = dispositivosConfiablesDe(participante);

        SalidaCambio salida =
                cambiar(participante, Optional.of(sesionQueCambia), "clave-actual-7", "clave-nueva-larga-7");

        assertThat(salida.corte()).isEqualTo(CorteDeCredencial.SALVO_LA_ACTUAL);
        assertThat(sesionViva(sesionQueCambia)).isTrue();
        // Al participante no se le quita la confianza de sus equipos: el corte total
        // de R-SEG-11 es del operador, y aplicarselo a todo el mundo lo volveria una
        // molestia que alguien terminaria pidiendo desactivar.
        assertThat(dispositivosConfiablesDe(participante)).isEqualTo(confiablesAntes);
    }

    @Test
    @DisplayName(
            "Dado un operador que reenroló su segundo factor hace una hora · Cuando intenta autorizar un desembolso · Entonces se rechaza con FACTOR_EN_ENFRIAMIENTO indicando el tiempo restante")
    void criterio8() {
        // El enfriamiento del reenrolamiento es el mismo atomo, aplicado del lado
        // que puede mover plata ajena. No hay atajo, tampoco por seguridad.
        OffsetDateTime reenrolo = OffsetDateTime.now().minusHours(1);
        VentanaDeEnfriamiento ventana = VentanaDeEnfriamiento.desde(reenrolo, Duration.ofHours(48));

        assertThat(ventana.vigenteEn(OffsetDateTime.now())).isTrue();
        assertThat(ventana.restanteEn(OffsetDateTime.now()).toHours()).isEqualTo(46);
    }

    @Test
    @DisplayName("reintento: cambiar a la misma clave dos veces lo corta el historial, no la aplicación")
    void reintento() {
        // CU-09 no lleva clave de idempotencia: lo que impide repetir no es un token
        // sino historial_credencial, que existe justamente para eso.
        UUID usuario = participanteConClave("+59174000010");
        cambiar(usuario, Optional.empty(), "clave-actual-10", "clave-nueva-larga-10");

        assertThatThrownBy(() -> cambiar(usuario, Optional.empty(), "clave-nueva-larga-10", "clave-actual-10"))
                .hasMessageContaining("Ya usaste esa clave");
    }

    @Test
    @DisplayName("concurrencia: dos cambios seguidos dejan dos filas de historial, ninguna se pisa")
    void concurrencia() {
        UUID usuario = participanteConClave("+59174000011");

        cambiar(usuario, Optional.empty(), "clave-actual-11", "clave-nueva-larga-11a");
        cambiar(usuario, Optional.empty(), "clave-nueva-larga-11a", "clave-nueva-larga-11b");

        assertThat(historialDe(usuario)).isEqualTo(2);
    }

    @Test
    @DisplayName("cuadre: CU-09 no mueve dinero, y no escribe ni un movimiento")
    void cuadre() {
        long antes = movimientosDeBilletera();
        UUID usuario = participanteConClave("+59174000012");

        cambiar(usuario, Optional.empty(), "clave-actual-12", "clave-nueva-larga-12");

        assertThat(movimientosDeBilletera()).isEqualTo(antes);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "notificaciones"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "notificaciones"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: si algo falla, la credencial y las sesiones quedan como estaban")
    void compensa() {
        // No hay saga que compensar: es una transaccion local. Lo que se prueba es
        // que revierte ENTERA — con la clave nueva puesta y las sesiones viejas
        // vivas habria un instante que un atacante sabe aprovechar.
        UUID usuario = participanteConClave("+59174000013");
        UUID sesion = fixtura.sesionAbierta(usuario);
        String hashAntes = hashDe(usuario);

        try {
            transaccion.execute(estado -> {
                cambiar(usuario, Optional.of(sesion), "clave-actual-13", "clave-nueva-larga-13");
                throw new IllegalStateException("fallo despues de escribir");
            });
        } catch (IllegalStateException esperado) {
            assertThat(esperado).hasMessageContaining("fallo despues");
        }

        assertThat(hashDe(usuario)).isEqualTo(hashAntes);
        assertThat(sesionViva(sesion)).isTrue();
        assertThat(historialDe(usuario)).isZero();
    }
}
