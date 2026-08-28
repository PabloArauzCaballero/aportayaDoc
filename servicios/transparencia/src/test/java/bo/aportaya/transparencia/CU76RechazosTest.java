package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.transparencia.aplicacion.CU76PublicarResena.EntradaModeracion;
import bo.aportaya.transparencia.aplicacion.CU76PublicarResena.EntradaResena;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-76 · Lo que la base y el caso de uso rechazan. */
class CU76RechazosTest extends BaseDeTransparencia {

    private record Caso(UUID grupo, UUID autor, UUID autorParticipante, UUID evaluado, ContextoSesion ctx) {}

    private Caso caso() {
        UUID grupo = fixtura.grupo();
        UUID autor = fixtura.usuario();
        UUID evaluado = fixtura.usuario();
        UUID autorParticipante = fixtura.participante(grupo, autor);
        fixtura.participante(grupo, evaluado);
        return new Caso(grupo, autor, autorParticipante, evaluado, contextoDe(autor));
    }

    private EntradaResena resena(Caso c, String dimension, String comentario) {
        return new EntradaResena(
                c.grupo(),
                c.autorParticipante(),
                c.evaluado(),
                5,
                dimension,
                comentario,
                true,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
                30,
                false,
                0);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El evento de reputacion que nace de una resena publicada no se borra: sin el,
        // el puntaje de quien fue resenado no se puede reconstruir.
        Caso c = caso();
        UUID participanteEvaluado = fixtura.participante(fixtura.grupo(), c.evaluado());
        var registrado = transaccion.execute(t -> eventoCU.registrar(
                new bo.aportaya.transparencia.aplicacion.CU70RegistrarEventoReputacion.EntradaEvento(
                        c.evaluado(),
                        null,
                        participanteEvaluado,
                        "APORTE_PUNTUAL",
                        "RESENA_PARTICIPANTE",
                        UUID.randomUUID(),
                        "Resena publicada",
                        true,
                        OffsetDateTime.now(ZoneOffset.UTC)),
                c.ctx()));

        assertThat(rechazaLaBase("DELETE FROM transparencia.evento_reputacion WHERE id = ?", registrado.eventoId()))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // La resena creada avisa en la misma transaccion, con lo que la moderacion
        // automatica marco: la cola humana necesita saber por que llego algo antes de
        // abrirlo.
        Caso c = caso();
        var salida = transaccion.execute(
                t -> resenaCU.publicar(resena(c, "PUNTUALIDAD", "Escribime al correo juan@ejemplo.bo"), c.ctx()));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.resena_creada' AND agregado_id = ?
                           AND payload->>'marcas' = 'CORREO'
                        """,
                        salida.resenaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-REP-01")
    void rechazaRREP01() {
        // Una resena por autor, evaluado, grupo y dimension. Resenar dos veces a la
        // misma persona duplicaria su voto sin que se note.
        Caso c = caso();
        transaccion.execute(t -> resenaCU.publicar(resena(c, "PUNTUALIDAD", "Cumplido"), c.ctx()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO transparencia.resena_participante
                            (grupo_id, autor_participante_id, evaluado_usuario_id, calificacion,
                             comentario, dimension, estado_moderacion)
                        VALUES (?, ?, ?, 1, 'Duplicada', 'PUNTUALIDAD', 'PENDIENTE')
                        """,
                        c.grupo(),
                        c.autorParticipante(),
                        c.evaluado()))
                .contains("uq_resena_autor_evaluado");
    }

    @Test
    @DisplayName("rechaza por R-REP-03")
    void rechazaRREP03() {
        // Una opinion pesa menos que un pago. Si no, tres personas enojadas pesarian mas
        // que un ano de aportes puntuales, y el puntaje dejaria de medir conducta.
        Caso c = caso();
        var salida = transaccion.execute(t -> resenaCU.publicar(resena(c, "PUNTUALIDAD", "Cumplido"), c.ctx()));
        var moderada = transaccion.execute(t ->
                resenaCU.moderar(new EntradaModeracion(salida.resenaId(), "APROBAR", "Contenido correcto"), c.ctx()));

        var pesoDelPago = dsl.fetchOne(
                        "SELECT peso FROM transparencia.peso_factor WHERE codigo_factor = 'PUNTUALIDAD_DE_APORTE'")
                .get(0, BigDecimal.class);
        assertThat(moderada.pesoEnReputacion()).isLessThan(pesoDelPago);
        // Y el peso de un autor expulsado cae a la mitad: no se lo silencia, pero opina
        // desde el conflicto.
        var expulsado = new EntradaResena(
                c.grupo(),
                c.autorParticipante(),
                c.evaluado(),
                1,
                "COMUNICACION",
                "Mala persona",
                true,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
                30,
                true,
                0);
        var conConflicto = transaccion.execute(t -> resenaCU.publicar(expulsado, c.ctx()));
        assertThat(conConflicto.pesoEnReputacion()).isLessThan(salida.pesoEnReputacion());
    }

    @Test
    @DisplayName("rechaza por R-REP-06")
    void rechazaRREP06() {
        // Nadie se resena a si mismo, y solo resena quien convivio. Sin esas dos, la
        // resena es autobombo o represalia.
        Caso c = caso();

        var aSiMismo = new EntradaResena(
                c.grupo(),
                c.autorParticipante(),
                c.autor(),
                5,
                "PUNTUALIDAD",
                "Soy excelente",
                true,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
                30,
                false,
                0);
        assertThatThrownBy(() -> transaccion.execute(t -> resenaCU.publicar(aSiMismo, c.ctx())))
                .hasMessageContaining("a si mismo");

        UUID extranio = fixtura.usuario();
        var aUnExtranio = new EntradaResena(
                c.grupo(),
                c.autorParticipante(),
                extranio,
                1,
                "PUNTUALIDAD",
                "No lo conozco",
                true,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
                30,
                false,
                0);
        assertThatThrownBy(() -> transaccion.execute(t -> resenaCU.publicar(aUnExtranio, c.ctx())))
                .hasMessageContaining("No compartiste grupo");

        assertThat(contar("SELECT count(*)::int FROM transparencia.resena_participante WHERE grupo_id = ?", c.grupo()))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        // Nada se publica sin moderar, y el estado publicado exige quedar firmado por
        // quien lo decidio: una cola de moderacion sin acta es una discusion.
        Caso c = caso();
        var salida = transaccion.execute(t -> resenaCU.publicar(resena(c, "PUNTUALIDAD", "Cumplido"), c.ctx()));

        assertThat(rechazaLaBase(
                        "UPDATE transparencia.resena_participante SET estado_moderacion = 'PUBLICADA' WHERE id = ?",
                        salida.resenaId()))
                .contains("ck_resena_moderada");

        var moderada = transaccion.execute(t ->
                resenaCU.moderar(new EntradaModeracion(salida.resenaId(), "APROBAR", "Contenido correcto"), c.ctx()));
        assertThat(moderada.estadoModeracion()).isEqualTo("PUBLICADA");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.resena_participante WHERE id = ? AND moderada_por IS NOT NULL",
                        salida.resenaId()))
                .isEqualTo(1);
    }
}
