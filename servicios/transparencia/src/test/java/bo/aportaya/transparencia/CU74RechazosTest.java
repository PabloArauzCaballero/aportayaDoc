package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.transparencia.aplicacion.CU74EvaluarInsignias.EntradaEvaluacion;
import bo.aportaya.transparencia.dominio.CriterioDeInsignia;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-74 · Lo que la base y el caso de uso rechazan. */
class CU74RechazosTest extends BaseDeTransparencia {

    private static CriterioDeInsignia.Hechos cicloLimpio() {
        return new CriterioDeInsignia.Hechos(1, 1, 12, 400, 0, 0, 0, 0, null, 0, 6, true, false);
    }

    private record Caso(UUID usuario, ContextoSesion ctx) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        return new Caso(usuario, contextoDe(usuario));
    }

    private UUID otorgarPrimerPasanaku(Caso c) {
        return transaccion
                .execute(t -> insigniaCU.evaluar(
                        new EntradaEvaluacion(c.usuario(), "CICLO_COMPLETADO", List.of(), cicloLimpio()), c.ctx()))
                .get(0)
                .otorgadaId();
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El evento de reputacion que sustenta una insignia no se borra. Sin el, nadie
        // podria explicar despues por que se otorgo.
        Caso c = caso();
        otorgarPrimerPasanaku(c);
        UUID grupo = fixtura.grupo();
        UUID participante = fixtura.participante(grupo, c.usuario());
        var registrado = transaccion.execute(t -> eventoCU.registrar(
                new bo.aportaya.transparencia.aplicacion.CU70RegistrarEventoReputacion.EntradaEvento(
                        c.usuario(),
                        grupo,
                        participante,
                        "APORTE_PUNTUAL",
                        "OBLIGACION_APORTE",
                        UUID.randomUUID(),
                        "Aporte en fecha",
                        true,
                        java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC)),
                c.ctx()));

        assertThat(rechazaLaBase("DELETE FROM transparencia.evento_reputacion WHERE id = ?", registrado.eventoId()))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // El aviso sale en la misma transaccion que el otorgamiento, con el motivo
        // dentro: una insignia que aparece sin explicacion es un adorno.
        Caso c = caso();
        otorgarPrimerPasanaku(c);

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.insignia_otorgada'
                           AND payload->>'usuarioId' = ? AND payload->>'insigniaCodigo' = 'PRIMER_PASANAKU'
                           AND payload->>'motivoLegible' IS NOT NULL
                        """,
                        c.usuario().toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-REP-01")
    void rechazaRREP01() {
        // Un hecho otorga una sola vez. Reprocesar el mismo cierre no duplica la
        // insignia ni vuelve a avisar como si fuera nueva.
        Caso c = caso();
        otorgarPrimerPasanaku(c);
        var repetida = transaccion.execute(t -> insigniaCU.evaluar(
                new EntradaEvaluacion(c.usuario(), "CICLO_COMPLETADO", List.of(), cicloLimpio()), c.ctx()));

        assertThat(repetida).noneMatch(o -> o.esNueva());
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.insignia_otorgada' AND payload->>'usuarioId' = ?
                        """,
                        c.usuario().toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-REP-05")
    void rechazaRREP05() {
        // Una insignia por usuario, y revocar no borra. Las dos mitades de la regla las
        // sostiene la base: el indice unico y el CHECK que exige motivo.
        Caso c = caso();
        UUID otorgadaId = otorgarPrimerPasanaku(c);
        UUID insigniaId = fixtura.insignia("PRIMER_PASANAKU");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO transparencia.insignia_otorgada (usuario_id, insignia_id, otorgada_en)
                        VALUES (?, ?, now())
                        """,
                        c.usuario(),
                        insigniaId))
                .contains("uq_insignia_usuario");

        assertThat(rechazaLaBase(
                        "UPDATE transparencia.insignia_otorgada SET revocada_en = now() WHERE id = ?", otorgadaId))
                .contains("ck_insignia_revocacion_motivada");

        // Revocada con motivo, la fila sigue estando: es la explicacion que le queda a
        // la persona de por que perdio algo que tenia.
        transaccion.execute(t -> insigniaCU.revocar(otorgadaId, "El ciclo se anulo por fraude", c.ctx()));
        assertThat(contar("SELECT count(*)::int FROM transparencia.insignia_otorgada WHERE id = ?", otorgadaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        // No hay endpoint de otorgamiento manual, y una insignia sin criterio evaluable
        // no se otorga: denegar por omision (invariante 9). Pedirla no la gana.
        Caso c = caso();

        var sinRegla = transaccion.execute(t -> insigniaCU.evaluar(
                new EntradaEvaluacion(c.usuario(), null, List.of("REFERENTE"), cicloLimpio()), c.ctx()));
        assertThat(sinRegla).isEmpty();
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.insignia_otorgada WHERE usuario_id = ?", c.usuario()))
                .isZero();

        // Y revocar algo que no existe no revoca nada de nadie.
        assertThatThrownBy(() ->
                        transaccion.execute(t -> insigniaCU.revocar(UUID.randomUUID(), "Motivo cualquiera", c.ctx())))
                .hasMessageContaining("no esta vigente");
    }
}
