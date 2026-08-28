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

/** CU-76 · Resenar a un participante y moderar la resena. */
class CU76Test extends BaseDeTransparencia {

    /** Peso de la puntualidad de aporte en el modelo v1: 0,30. Una opinion pesa menos. */
    private static final BigDecimal PESO_DEL_PAGO = new BigDecimal("0.3000");

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
    @DisplayName(
            "Dado un autor que compartió período con el evaluado y un ciclo cerrado · Cuando publica una reseña de dimensión PUNTUALIDAD · Entonces queda PENDIENTE de moderación y no impacta la reputación todavía")
    void criterio1() {
        Caso c = caso();

        var salida = transaccion.execute(
                t -> resenaCU.publicar(resena(c, "PUNTUALIDAD", "Siempre puso su parte a tiempo"), c.ctx()));

        assertThat(salida.estadoModeracion()).isEqualTo("PENDIENTE");
        // Nada se publica sin moderar, ni siquiera lo que la maquina no marco: la
        // primera pasada es una ayuda, no una autorizacion.
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.resena_participante WHERE id = ? AND estado_moderacion = 'PENDIENTE'",
                        salida.resenaId()))
                .isEqualTo(1);
        // Y todavia no toco la reputacion de nadie.
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_reputacion WHERE usuario_id = ?", c.evaluado()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un autor que nunca compartió grupo con el evaluado · Cuando intenta reseñarlo · Entonces se rechaza con SIN_CONVIVENCIA")
    void criterio2() {
        Caso c = caso();
        UUID extranio = fixtura.usuario();
        var aUnExtranio = new EntradaResena(
                c.grupo(),
                c.autorParticipante(),
                extranio,
                4,
                "COMUNICACION",
                "No lo conozco",
                true,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(2),
                30,
                false,
                0);

        // R-REP-06, y lo sostiene tg_resena_convivencia: sin convivencia la resena es
        // una opinion sobre un desconocido, o una represalia.
        assertThatThrownBy(() -> transaccion.execute(t -> resenaCU.publicar(aUnExtranio, c.ctx())))
                .hasMessageContaining("No compartiste grupo");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.resena_participante WHERE evaluado_usuario_id = ?",
                        extranio))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un comentario que incluye un número de teléfono · Cuando pasa la moderación automática · Entonces se retiene para revisión humana y no se publica con el dato")
    void criterio3() {
        Caso c = caso();

        var salida = transaccion.execute(t -> resenaCU.publicar(
                resena(c, "PUNTUALIDAD", "Buen companero, llamalo al 71234567 si necesitas"), c.ctx()));

        assertThat(salida.marcas()).contains("TELEFONO");
        assertThat(salida.estadoModeracion()).isEqualTo("PENDIENTE");
        // Se retiene, no se recorta: recortar deja el dato en la base y la sospecha en
        // el aire.
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.resena_participante WHERE id = ? AND estado_moderacion <> 'PUBLICADA'",
                        salida.resenaId()))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.resena_creada' AND agregado_id = ?
                           AND payload->>'retenidaPorRevision' = 'true'
                        """,
                        salida.resenaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una reseña aprobada · Cuando se recalcula la reputación · Entonces su peso es menor que el de los factores de pago")
    void criterio4() {
        Caso c = caso();
        var salida = transaccion.execute(t -> resenaCU.publicar(resena(c, "PUNTUALIDAD", "Muy cumplido"), c.ctx()));

        var moderada = transaccion.execute(t -> resenaCU.moderar(
                new EntradaModeracion(salida.resenaId(), "APROBAR", "Sin datos personales ni agresiones"), c.ctx()));

        assertThat(moderada.estadoModeracion()).isEqualTo("PUBLICADA");
        // Tres personas enojadas no pueden pesar mas que un ano de aportes puntuales.
        assertThat(moderada.pesoEnReputacion()).isLessThan(PESO_DEL_PAGO);
        var pesoDelPagoEnElModelo = dsl.fetchOne(
                        "SELECT peso FROM transparencia.peso_factor WHERE codigo_factor = 'PUNTUALIDAD_DE_APORTE'")
                .get(0, BigDecimal.class);
        assertThat(moderada.pesoEnReputacion()).isLessThan(pesoDelPagoEnElModelo);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        Caso c = caso();
        var entrada = resena(c, "PUNTUALIDAD", "Muy cumplido");

        transaccion.execute(t -> resenaCU.publicar(entrada, c.ctx()));

        // Una resena por autor, evaluado, grupo y dimension (R-REP-06). El reintento no
        // deja una segunda: resenar dos veces a la misma persona duplicaria su voto.
        assertThatThrownBy(() -> transaccion.execute(t -> resenaCU.publicar(entrada, c.ctx())))
                .hasMessageContaining("Ya resenaste");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.resena_participante WHERE grupo_id = ? AND autor_participante_id = ?",
                        c.grupo(),
                        c.autorParticipante()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        Caso c = caso();
        var entrada = resena(c, "COMUNICACION", "Se comunica bien");

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> resenaCU.publicar(entrada, c.ctx()));
            } catch (Exception e) {
                errores.add(e);
            }
        };
        var uno = new Thread(intento);
        var dos = new Thread(intento);
        uno.start();
        dos.start();
        uno.join();
        dos.join();

        assertThat(errores).hasSize(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.resena_participante WHERE grupo_id = ? AND autor_participante_id = ? AND dimension = 'COMUNICACION'",
                        c.grupo(),
                        c.autorParticipante()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        Caso c = caso();

        // Las tres dimensiones que admite la base son independientes: la misma persona
        // puede resenar las tres al mismo evaluado, y ni una mas.
        transaccion.execute(t -> resenaCU.publicar(resena(c, "PUNTUALIDAD", "Cumplido"), c.ctx()));
        transaccion.execute(t -> resenaCU.publicar(resena(c, "COMUNICACION", "Claro"), c.ctx()));
        transaccion.execute(t -> resenaCU.publicar(resena(c, "ORGANIZACION", "Ordenado"), c.ctx()));

        assertThat(contar(
                        "SELECT count(DISTINCT dimension)::int FROM transparencia.resena_participante WHERE grupo_id = ? AND autor_participante_id = ?",
                        c.grupo(),
                        c.autorParticipante()))
                .isEqualTo(3)
                .isEqualTo(contar(
                        "SELECT count(*)::int FROM transparencia.resena_participante WHERE grupo_id = ? AND autor_participante_id = ?",
                        c.grupo(),
                        c.autorParticipante()));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        Caso c = caso();
        var salida = transaccion.execute(t -> resenaCU.publicar(resena(c, "PUNTUALIDAD", "Cumplido"), c.ctx()));

        transaccion.execute(t ->
                resenaCU.moderar(new EntradaModeracion(salida.resenaId(), "APROBAR", "Contenido correcto"), c.ctx()));

        // Una segunda moderacion llega tarde: la decision ya se tomo y no se pisa. Que
        // un segundo moderador cambie lo resuelto sin dejar rastro es lo que convierte
        // una cola de moderacion en una discusion sin acta.
        assertThatThrownBy(() -> transaccion.execute(t -> resenaCU.moderar(
                        new EntradaModeracion(salida.resenaId(), "RECHAZAR", "Cambio de opinion"), c.ctx())))
                .hasMessageContaining("ya fue moderada");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.resena_participante WHERE id = ? AND estado_moderacion = 'PUBLICADA'",
                        salida.resenaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        Caso c = caso();

        // Paso fallido: el ciclo sigue corriendo. Resenar a quien todavia te debe plata
        // convierte la resena en una herramienta de presion.
        var enCurso = new EntradaResena(
                c.grupo(),
                c.autorParticipante(),
                c.evaluado(),
                1,
                "PUNTUALIDAD",
                "Todavia me debe",
                false,
                null,
                30,
                false,
                0);
        assertThatThrownBy(() -> transaccion.execute(t -> resenaCU.publicar(enCurso, c.ctx())))
                .hasMessageContaining("al cerrar el ciclo");

        // Paso fallido: la ventana vencio.
        var tarde = new EntradaResena(
                c.grupo(),
                c.autorParticipante(),
                c.evaluado(),
                4,
                "PUNTUALIDAD",
                "Me acorde recien",
                true,
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(90),
                30,
                false,
                0);
        assertThatThrownBy(() -> transaccion.execute(t -> resenaCU.publicar(tarde, c.ctx())))
                .hasMessageContaining("plazo para resenar vencio");

        assertThat(contar("SELECT count(*)::int FROM transparencia.resena_participante WHERE grupo_id = ?", c.grupo()))
                .isZero();

        // Dentro de la ventana y con el ciclo cerrado, el mismo camino cierra.
        var bueno = transaccion.execute(t -> resenaCU.publicar(resena(c, "PUNTUALIDAD", "Cumplido"), c.ctx()));
        assertThat(bueno.resenaId()).isNotNull();
    }
}
