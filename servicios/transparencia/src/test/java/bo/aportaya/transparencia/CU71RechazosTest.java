package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.transparencia.aplicacion.CU70RegistrarEventoReputacion.EntradaEvento;
import bo.aportaya.transparencia.aplicacion.CU71RecalcularPuntaje.EntradaPuntaje;
import bo.aportaya.transparencia.dominio.PuntajeDeReputacion;
import bo.aportaya.transparencia.infraestructura.ReputacionRepositorio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-71 · Lo que la base y el caso de uso rechazan. */
class CU71RechazosTest extends BaseDeTransparencia {

    private static final ReputacionRepositorio.Indicadores INDICADORES = new ReputacionRepositorio.Indicadores(
            new BigDecimal("0.90"), new BigDecimal("0.05"), new BigDecimal("6000.00"), 2, 0, 0, 14);

    private record Caso(UUID usuario, UUID grupo, UUID participante, ContextoSesion ctx) {}

    private Caso conHistorial() {
        UUID usuario = fixtura.usuario();
        UUID grupo = fixtura.grupo();
        UUID participante = fixtura.participante(grupo, usuario);
        var ctx = contextoDe(usuario);
        String[] tipos = {"APORTE_ANTICIPADO", "APORTE_PUNTUAL", "DEUDA_CASTIGADA"};
        String[] referencias = {"OBLIGACION_APORTE", "OBLIGACION_APORTE", "REGISTRO_INCUMPLIMIENTO"};
        for (int i = 0; i < 3; i++) {
            int indice = i;
            transaccion.execute(t -> eventoCU.registrar(
                    new EntradaEvento(
                            usuario,
                            grupo,
                            participante,
                            tipos[indice],
                            referencias[indice],
                            UUID.randomUUID(),
                            "Hecho " + indice,
                            true,
                            OffsetDateTime.now(ZoneOffset.UTC)),
                    ctx));
        }
        return new Caso(usuario, grupo, participante, ctx);
    }

    private List<PuntajeDeReputacion.Medicion> mediciones() {
        return List.of(
                new PuntajeDeReputacion.Medicion(
                        "PUNTUALIDAD_DE_APORTE", new BigDecimal("18"), new BigDecimal("0.9000")),
                new PuntajeDeReputacion.Medicion("MORA_ACUMULADA", new BigDecimal("3"), new BigDecimal("0.1000")));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El puntaje se recalcula y se reemplaza —no es append-only—, pero los eventos
        // de los que sale si lo son. Es lo que hace que un puntaje viejo se pueda
        // reconstruir aunque la fila ya no este.
        Caso c = conHistorial();
        transaccion.execute(t ->
                puntajeCU.recalcular(new EntradaPuntaje(c.usuario(), mediciones(), List.of(), INDICADORES), c.ctx()));

        assertThat(rechazaLaBase("DELETE FROM transparencia.evento_reputacion WHERE usuario_id = ?", c.usuario()))
                .contains("R-AUD-01");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_reputacion WHERE usuario_id = ?", c.usuario()))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("rechaza por R-REP-02")
    void rechazaRREP02() {
        // Un solo puntaje vigente por usuario. Dos harian que dos consultas del mismo
        // dia dieran numeros distintos, y no habria forma de decir cual manda.
        Caso c = conHistorial();
        transaccion.execute(t ->
                puntajeCU.recalcular(new EntradaPuntaje(c.usuario(), mediciones(), List.of(), INDICADORES), c.ctx()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO transparencia.puntaje_reputacion
                            (usuario_id, modelo_id, puntaje, nivel_confianza, indice_puntualidad,
                             tasa_incumplimiento, grupos_completados, grupos_abandonados,
                             incumplimientos_abiertos, antiguedad_meses, eventos_considerados,
                             modelo_version, vigente_desde, calculado_en, proximo_recalculo_en)
                        SELECT usuario_id, modelo_id, 999.00, nivel_confianza, indice_puntualidad,
                               tasa_incumplimiento, grupos_completados, grupos_abandonados,
                               incumplimientos_abiertos, antiguedad_meses, eventos_considerados,
                               modelo_version, now(), now(), now() + interval '30 days'
                          FROM transparencia.puntaje_reputacion WHERE usuario_id = ?
                        """,
                        c.usuario()))
                .contains("uq_puntaje_reputacion_usuario_id");
    }

    @Test
    @DisplayName("rechaza por R-REP-03")
    void rechazaRREP03() {
        // El total es la suma de sus componentes, y lo verifica la base al confirmar.
        // Es lo que hace que el numero se pueda abrir factor por factor y discutir.
        Caso c = conHistorial();
        var salida = transaccion.execute(t ->
                puntajeCU.recalcular(new EntradaPuntaje(c.usuario(), mediciones(), List.of(), INDICADORES), c.ctx()));

        assertThat(rechazaAlConfirmar(
                        "UPDATE transparencia.puntaje_reputacion SET puntaje = puntaje + 100 WHERE id = ?",
                        salida.puntajeId()))
                .contains("R-REP-03");
        // HUECO H-3: tg_puntaje_cuadra es AFTER INSERT OR UPDATE **sobre
        // puntaje_reputacion**, asi que borrar un componente no lo dispara: se puede
        // recortar la explicacion y dejar el numero intacto. La regla se sostiene al
        // escribir el puntaje, no despues. Se afirma lo que es cierto.
        assertThat(rechazaAlConfirmar(
                        """
                        DELETE FROM transparencia.componente_score
                         WHERE puntaje_id = ? AND codigo_factor = 'MORA_ACUMULADA'
                        """,
                        salida.puntajeId()))
                .isEmpty();
        // Lo que si vuelve a verificarse es todo UPDATE del puntaje: tocar el total
        // sin tocar sus partes se rechaza siempre.
        assertThat(rechazaAlConfirmar(
                        "UPDATE transparencia.puntaje_reputacion SET puntaje = 1.00 WHERE id = ?", salida.puntajeId()))
                .contains("R-REP-03");
    }
}
