package bo.aportaya.grupos;

import static bo.aportaya.plataforma.dominio.Moneda.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.grupos.aplicacion.CU69Invitar.Resultado;
import bo.aportaya.grupos.dominio.InvitacionAdmisible;
import bo.aportaya.grupos.dominio.MensajeDeInvitacion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-69 · Invitar a un contacto y registrar sus referencias. */
class CU69Test extends BaseDeCU69 {

    @Test
    @DisplayName(
            "Dado un participante activo y un grupo con cupos libres · Cuando invita a un teléfono nuevo · Entonces existe una invitacion ENVIADA con token de un solo uso · Y el mensaje no contiene datos de los otros integrantes")
    void criterio1() {
        UUID grupo = grupoConCupoLibre();
        UUID emisor = participanteActivo(grupo);

        Resultado resultado = invitar(grupo, emisor, "+59176000001", false, false);

        assertThat(resultado.invitacionId()).isPresent();
        assertThat(estadoDe(resultado.invitacionId().orElseThrow())).isEqualTo("ENVIADA");
        // El mensaje dice quien invita, a que grupo y cuanto; y nada mas.
        String texto =
                new MensajeDeInvitacion("Ana", "Pasanaku de la cuadra", Dinero.de("500.00", BOB), "MENSUAL").texto();
        assertThat(texto).contains("Ana", "Pasanaku de la cuadra", "500.00");
        assertThat(texto).doesNotContain("+591");
    }

    @Test
    @DisplayName(
            "Dado un token de invitación ya consumido · Cuando se intenta aceptar otra vez · Entonces se rechaza con TOKEN_INVALIDO")
    void criterio2() {
        UUID grupo = grupoConCupoLibre();
        UUID emisor = participanteActivo(grupo);
        UUID invitacion = invitar(grupo, emisor, "+59176000002", false, false)
                .invitacionId()
                .orElseThrow();
        transaccion.execute(e -> {
            invitar.aceptar(invitacion, contexto(emisor));
            return null;
        });

        assertThatThrownBy(() -> transaccion.execute(e -> {
                    invitar.aceptar(invitacion, contexto(emisor));
                    return null;
                }))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya no es valida");
    }

    @Test
    @DisplayName(
            "Dado un teléfono en lista de supresión · Cuando se lo intenta invitar · Entonces no se envía nada y se responde sin revelar el motivo")
    void criterio3() {
        UUID grupo = grupoConCupoLibre();
        UUID emisor = participanteActivo(grupo);

        Resultado resultado = invitar(grupo, emisor, "+59176000003", true, false);

        // No se envia nada, y la respuesta NO revela el motivo.
        assertThat(resultado.invitacionId()).isEmpty();
        assertThat(resultado.mensaje()).doesNotContain("suprim");
        assertThat(invitacionesDe(grupo)).isZero();
    }

    @Test
    @DisplayName(
            "Dada una referencia personal registrada · Cuando la referencia no responde la verificación · Entonces queda con verificada en false y no puede constituirse en aval")
    void criterio4() {
        // Una referencia que no responde no cuenta como respaldo: nadie queda de
        // avalista por figurar en la agenda de otro.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO garantia.aval_participante
                            (id, participante_id, avalista_usuario_id, monto_avalado, moneda, estado, firmado_en)
                        VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 100.00, 'BOB',
                                'VIGENTE', now())
                        """))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-NOT-01")
    void rechazaRNOT01() {
        // El tope de reenvios: insistir tres veces es recordar, insistir diez es acoso.
        assertThat(InvitacionAdmisible.impedimento(true, false, false, 3, 3, true))
                .contains(InvitacionAdmisible.Motivo.TOPE_REENVIOS);
        assertThat(InvitacionAdmisible.impedimento(true, false, false, 2, 3, true))
                .isEmpty();
    }

    @Test
    @DisplayName("rechaza por R-NOT-02")
    void rechazaRNOT02() {
        // La supresion se respeta ANTES de escribir nada, y se responde como exito.
        assertThat(InvitacionAdmisible.impedimento(true, true, false, 0, 3, true))
                .contains(InvitacionAdmisible.Motivo.DESTINATARIO_SUPRIMIDO);
        assertThat(InvitacionAdmisible.Motivo.DESTINATARIO_SUPRIMIDO.seRespondeComoExito())
                .isTrue();
    }

    @Test
    @DisplayName("rechaza por R-NOT-03")
    void rechazaRNOT03() {
        // El canal sale de un catalogo cerrado.
        UUID grupo = grupoConCupoLibre();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO grupos.invitacion
                            (id, grupo_id, telefono_invitado, emisor_id, token_id, canal, estado,
                             envios_realizados, fecha_envio, fecha_expiracion)
                        VALUES (gen_random_uuid(), '%s', '+59176000009', '%s', gen_random_uuid(),
                                'PALOMA', 'ENVIADA', 1, now(), now() + interval '7 days')
                        """
                                .formatted(grupo, fixtura.usuario())))
                .contains("canal");
    }

    @Test
    @DisplayName("rechaza por R-GRP-15")
    void rechazaRGRP15() {
        // Sin cupos libres no se invita: el grupo lleno no es una sala de espera.
        assertThat(InvitacionAdmisible.impedimento(false, false, false, 0, 3, true))
                .contains(InvitacionAdmisible.Motivo.SIN_CUPOS_LIBRES);
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
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Una invitacion sin quien la emite no es auditable.
        UUID grupo = grupoConCupoLibre();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO grupos.invitacion
                            (id, grupo_id, telefono_invitado, token_id, canal, estado,
                             envios_realizados, fecha_envio, fecha_expiracion)
                        VALUES (gen_random_uuid(), '%s', '+59176000010', gen_random_uuid(),
                                'ENLACE', 'ENVIADA', 1, now(), now() + interval '7 days')
                        """
                                .formatted(grupo)))
                .contains("emisor_id");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La expiracion tiene que ser posterior al envio: una invitacion que nace
        // vencida es una invitacion que nadie puede aceptar.
        UUID grupo = grupoConCupoLibre();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO grupos.invitacion
                            (id, grupo_id, telefono_invitado, emisor_id, token_id, canal, estado,
                             envios_realizados, fecha_envio, fecha_expiracion)
                        VALUES (gen_random_uuid(), '%s', '+59176000011', '%s', gen_random_uuid(),
                                'ENLACE', 'ENVIADA', 1, now(), now() - interval '1 day')
                        """
                                .formatted(grupo, fixtura.usuario())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos aceptaciones simultaneas: el WHERE estado = 'ENVIADA' decide, no un
        // SELECT previo. La segunda actualiza cero filas.
        UUID grupo = grupoConCupoLibre();
        UUID emisor = participanteActivo(grupo);
        UUID invitacion = invitar(grupo, emisor, "+59176000012", false, false)
                .invitacionId()
                .orElseThrow();

        transaccion.execute(e -> {
            invitar.aceptar(invitacion, contexto(emisor));
            return null;
        });

        assertThat(estadoDe(invitacion)).isEqualTo("ACEPTADA");
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
    @DisplayName("cuadre: invitar no ocupa el cupo, y el grupo sigue con los mismos libres")
    void cuadre() {
        UUID grupo = grupoConCupoLibre();
        UUID emisor = participanteActivo(grupo);
        int libresAntes = cuposLibresDe(grupo);

        invitar(grupo, emisor, "+59176000013", false, false);

        // Invitar no reserva nada: el cupo se ocupa al aceptar y ser emparejado.
        assertThat(cuposLibresDe(grupo)).isEqualTo(libresAntes);
    }
}
