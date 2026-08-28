package bo.aportaya.garantia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.garantia.aplicacion.CU23CubrirIncumplimiento.EntradaCobertura;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.EntradaDeclaracion;
import bo.aportaya.garantia.aplicacion.CU27RestringirDeudor.EntradaRestriccion;
import bo.aportaya.garantia.aplicacion.CU27RestringirDeudor.SalidaRestriccion;
import bo.aportaya.garantia.dominio.RestriccionInterna;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-27 · las pruebas de RECHAZO, una por restriccion citada. */
class CU27RechazosTest extends BaseDeGarantia {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(UUID usuario, UUID expedienteId, ContextoSesion suyo, ContextoSesion gestor) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        var escenario = fixtura.escenario(usuario);
        ContextoSesion gestor = contextoDe(fixtura.usuario());
        UUID politica = fixtura.politica(escenario.grupoId(), "100.00", "50000.00", "50000.00", 3, "100000.00", 15);
        UUID fondo = fixtura.fondo(escenario.grupoId(), politica, "50000.00");
        fixtura.aportarAlFondo(fondo, escenario.participanteId(), "50000.00", "50000.00");

        var expediente = transaccion.execute(t -> expedienteCU.declarar(
                new EntradaDeclaracion(
                        "EXP-" + corto(),
                        usuario,
                        escenario.participanteId(),
                        escenario.grupoId(),
                        escenario.periodoId(),
                        escenario.cupoId(),
                        escenario.obligacionId(),
                        "APORTE_IMPAGO",
                        "GRAVE",
                        "AUTOMATICO_VENCIMIENTO",
                        bob("800.00"),
                        30,
                        true,
                        "LOG_SISTEMA",
                        "Sin pago",
                        null,
                        null),
                gestor));
        transaccion.execute(t ->
                coberturaCU.cubrir(new EntradaCobertura(expediente.expedienteId(), bob("800.00"), 30, null), gestor));
        return new Caso(usuario, expediente.expedienteId(), contextoDe(usuario), gestor);
    }

    private SalidaRestriccion restringir(Caso c, String nivel) {
        return transaccion.execute(t -> restriccionCU.restringir(
                new EntradaRestriccion(c.expedienteId(), nivel, "Deuda sin regularizar", Optional.empty()),
                c.gestor()));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La restriccion conserva su motivo y su fecha: sin ellos no hay forma de
        // saber por que se aplico ni desde cuando.
        Caso c = caso();
        SalidaRestriccion restriccion = restringir(c, "LIMITADO");

        assertThat(rechazaLaBase("UPDATE garantia.lista_restriccion_interna SET motivo = NULL WHERE id = '%s'"
                        .formatted(restriccion.restriccionId())))
                .isNotEmpty();
        assertThat(rechazaLaBase("UPDATE garantia.lista_restriccion_interna SET incluido_en = NULL WHERE id = '%s'"
                        .formatted(restriccion.restriccionId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        Caso c = caso();
        SalidaRestriccion restriccion = restringir(c, "LIMITADO");

        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "garantia.usuario_restringido",
                        restriccion.restriccionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-CON-05")
    void rechazaRCON05() {
        // El expediente se conserva: es lo que respalda la restriccion y lo que la
        // persona puede pedir para defenderse. No se borra ni se edita.
        Caso c = caso();
        restringir(c, "LIMITADO");

        assertThat(rechazaLaBase(
                        "DELETE FROM garantia.registro_incumplimiento WHERE id = '%s'".formatted(c.expedienteId())))
                .contains("R-AUD-01");
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.evidencia_incumplimiento WHERE registro_id = ?",
                        c.expedienteId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-GAR-05")
    void rechazaRGAR05() {
        // Una restriccion vigente por usuario, y su levantamiento SE MOTIVA. Sin
        // motivo escrito, levantarla se convierte en un favor que nadie puede auditar.
        Caso c = caso();
        SalidaRestriccion primera = restringir(c, "LIMITADO");
        SalidaRestriccion segunda = restringir(c, "VETADO");

        assertThat(segunda.restriccionId()).isEqualTo(primera.restriccionId());
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.lista_restriccion_interna WHERE usuario_id = ? AND retirado_en IS NULL",
                        c.usuario()))
                .isEqualTo(1);

        assertThatThrownBy(() ->
                        transaccion.execute(t -> restriccionCU.levantar(primera.restriccionId(), "   ", c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin motivo escrito");
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.lista_restriccion_interna WHERE id = ? AND retirado_en IS NULL",
                        primera.restriccionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        // El nivel sale de una lista cerrada: un nivel inventado no se puede contar, y
        // lo que no se cuenta no aparece en ningun reporte de riesgo.
        Caso c = caso();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO garantia.lista_restriccion_interna
                            (id, usuario_id, motivo, nivel_restriccion, monto_adeudado, incluido_en)
                        VALUES (gen_random_uuid(), '%s', 'inventado', 'DESTIERRO', 100.00, now())
                        """
                                .formatted(c.usuario())))
                .contains("ck_lista_restriccion_interna_nivel_restriccion");

        // Y pagar la deuda NUNCA se restringe: cerrarle esa puerta a quien debe es
        // asegurarse de que no vuelva.
        assertThat(RestriccionInterna.esTipoValido("PAGAR_DEUDA")).isFalse();
        assertThat(RestriccionInterna.esTipoValido("VER_ESTADO")).isFalse();
        assertThat(RestriccionInterna.esTipoValido("CREAR_GRUPO")).isTrue();
        // Una restriccion sin fecha de fin es indefinida, no vencida: solo se levanta
        // con motivo escrito.
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        assertThat(RestriccionInterna.vigenteEn(ahora.minusDays(1), null, ahora))
                .isTrue();
        assertThat(RestriccionInterna.vigenteEn(ahora.minusDays(10), ahora.minusDays(1), ahora))
                .isFalse();
        assertThat(RestriccionInterna.venceEn(ahora, Duration.ofDays(180))).isEqualTo(ahora.plusDays(180));
        assertThat(RestriccionInterna.venceEn(ahora, null)).isNull();
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // Nadie se restringe a si mismo, ni el imputado decide su propia sancion.
        Caso c = caso();

        assertThatThrownBy(() -> transaccion.execute(t -> restriccionCU.restringir(
                        new EntradaRestriccion(c.expedienteId(), "LIMITADO", "Me restrinjo", Optional.empty()),
                        c.suyo())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede ser el restringido");
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.lista_restriccion_interna WHERE usuario_id = ?",
                        c.usuario()))
                .isZero();
    }
}
