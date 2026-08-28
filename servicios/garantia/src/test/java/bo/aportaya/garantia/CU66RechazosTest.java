package bo.aportaya.garantia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.garantia.aplicacion.CU23CubrirIncumplimiento.EntradaCobertura;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.EntradaDeclaracion;
import bo.aportaya.garantia.aplicacion.CU66ReemplazarParticipante.EntradaReemplazo;
import bo.aportaya.garantia.aplicacion.CU66ReemplazarParticipante.SalidaReemplazo;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-66 · las pruebas de RECHAZO, una por restriccion citada. */
class CU66RechazosTest extends BaseDeGarantia {

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

    private record Caso(FixturaDeGarantia.Escenario escenario, UUID expedienteId, ContextoSesion gestor) {}

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
                        "ABANDONO_DE_GRUPO",
                        "CRITICA",
                        "REPORTE_DE_ORGANIZADOR",
                        bob("1000.00"),
                        60,
                        true,
                        "ACTA_ACUERDO",
                        "Acta de expulsion",
                        null,
                        null),
                gestor));
        transaccion.execute(t ->
                coberturaCU.cubrir(new EntradaCobertura(expediente.expedienteId(), bob("1000.00"), 60, null), gestor));
        return new Caso(escenario, expediente.expedienteId(), gestor);
    }

    private SalidaReemplazo proponer(Caso c, UUID entrante, String asume) {
        return transaccion.execute(t -> reemplazoCU.proponer(
                new EntradaReemplazo(c.expedienteId(), c.escenario().cupoId(), entrante, bob(asume), true),
                c.gestor()));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El expediente del saliente no se borra al reemplazarlo: si se borrara, la
        // deuda quedaria sin origen y nadie podria explicar de donde salio.
        Caso c = caso();
        proponer(c, fixtura.otroParticipante(c.escenario().grupoId()), "0.00");

        assertThat(rechazaLaBase(
                        "DELETE FROM garantia.registro_incumplimiento WHERE id = '%s'".formatted(c.expedienteId())))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        Caso c = caso();
        SalidaReemplazo salida =
                proponer(c, fixtura.otroParticipante(c.escenario().grupoId()), "0.00");

        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "garantia.reemplazo_propuesto",
                        salida.reemplazoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-GRP-10")
    void rechazaRGRP10() {
        // El entrante no puede asumir mas de lo que hay: seria cobrarle una deuda que
        // no existe. Y no puede ser el mismo que sale — un reemplazo consigo mismo no
        // resuelve nada y borra el rastro del incumplimiento.
        Caso c = caso();

        assertThatThrownBy(
                        () -> proponer(c, fixtura.otroParticipante(c.escenario().grupoId()), "1500.00"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede asumir");
        assertThatThrownBy(() -> proponer(c, c.escenario().participanteId(), "0.00"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede ser el mismo participante");
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.reemplazo_participante WHERE registro_id = ?",
                        c.expedienteId()))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-GRP-11")
    void rechazaRGRP11() {
        // Lo que asume el entrante mas lo que retiene el saliente iguala la deuda, al
        // centavo: si no cuadrara, una parte quedaria sin dueno y la absorberian los
        // demas participantes sin enterarse.
        Caso c = caso();
        SalidaReemplazo salida =
                proponer(c, fixtura.otroParticipante(c.escenario().grupoId()), "300.00");

        assertThat(salida.deudaAsumidaPorElEntrante().mas(salida.deudaRetenidaPorElSaliente()))
                .isEqualByComparingTo(bob("1000.00"));
        // Y el estado sale de una lista cerrada: uno inventado no se puede contar.
        assertThat(rechazaLaBase("UPDATE garantia.reemplazo_participante SET estado = 'EXPULSADO' WHERE id = '%s'"
                        .formatted(salida.reemplazoId())))
                .contains("ck_reemplazo_participante_estado");
    }
}
