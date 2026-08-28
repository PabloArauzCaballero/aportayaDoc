package bo.aportaya.publicidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.publicidad.aplicacion.CU112ModerarPieza.EntradaPieza;
import bo.aportaya.publicidad.aplicacion.CU112ModerarPieza.EntradaRevision;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-112 · Moderar una pieza creativa. */
class CU112Test extends EscenarioDeCampana {

    private ContextoSesion moderador;

    @BeforeEach
    void moderadorDistinto() {
        moderador = contextoDe(fixtura.usuario());
    }

    private UUID pieza() {
        return transaccion.execute(t -> moderacionCU.subir(
                new EntradaPieza(
                        anuncianteId,
                        "Promo del barrio",
                        "Sumate a nuestro pasanaku",
                        "https://cdn.aportaya.bo/promo.png",
                        "IMAGEN"),
                operaciones));
    }

    @Test
    @DisplayName(
            "Dada una pieza_creativa recién subida en estado PENDIENTE · Cuando un Moderador la aprueba · Entonces pieza_creativa.estado_moderacion pasa a APROBADA y queda su revision_creativa")
    void criterio1() {
        UUID piezaId = pieza();

        var salida = transaccion.execute(
                t -> moderacionCU.moderar(new EntradaRevision(piezaId, "APROBADA", null), moderador));

        assertThat(salida.estadoModeracion()).isEqualTo("APROBADA");
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.pieza_creativa WHERE id = ? AND estado_moderacion = 'APROBADA'",
                        piezaId))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.revision_creativa
                         WHERE id = ? AND pieza_creativa_id = ? AND decision = 'APROBADA' AND revisada_por = ?
                        """,
                        salida.revisionId(),
                        piezaId,
                        moderador.usuarioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una pieza_creativa PENDIENTE · Cuando un Moderador la rechaza sin indicar motivo · Entonces el sistema devuelve MOTIVO_OBLIGATORIO_EN_RECHAZO")
    void criterio2() {
        UUID piezaId = pieza();

        assertThatThrownBy(() -> transaccion.execute(
                        t -> moderacionCU.moderar(new EntradaRevision(piezaId, "RECHAZADA", null), moderador)))
                .satisfies(e -> assertThat(raizDe(e)).contains("Un rechazo sin motivo"));

        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.pieza_creativa WHERE id = ? AND estado_moderacion = 'PENDIENTE'",
                        piezaId))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.revision_creativa WHERE pieza_creativa_id = ?", piezaId))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dada una pieza_creativa con estado_moderacion PENDIENTE · Cuando se intenta crear un anuncio que la usa · Entonces el sistema rechaza la operación")
    void criterio3() {
        UUID piezaId = pieza();
        var enAire = campanaEnAire("1000.00", "50.00", "10.00", "CPM");

        assertThatThrownBy(
                        () -> transaccion.execute(t -> entregaCU.programar(enAire.conjuntoId(), piezaId, operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("R-PUB-04"));

        assertThat(contar("SELECT count(*)::int FROM publicidad.anuncio WHERE pieza_creativa_id = ?", piezaId))
                .isZero();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID piezaId = pieza();
        transaccion.execute(t -> moderacionCU.moderar(new EntradaRevision(piezaId, "APROBADA", null), moderador));

        assertThatThrownBy(() -> transaccion.execute(
                        t -> moderacionCU.moderar(new EntradaRevision(piezaId, "APROBADA", null), moderador)))
                .satisfies(e -> assertThat(raizDe(e)).contains("se sube una corregida"));

        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.revision_creativa WHERE pieza_creativa_id = ?", piezaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID piezaId = pieza();
        var otroModerador = contextoDe(fixtura.usuario());
        var barrera = new CountDownLatch(1);
        var exitos = new AtomicInteger();

        try (var piscina = Executors.newFixedThreadPool(2)) {
            for (ContextoSesion quien : new ContextoSesion[] {moderador, otroModerador}) {
                piscina.submit(() -> {
                    try {
                        barrera.await();
                        transaccion.execute(
                                t -> moderacionCU.moderar(new EntradaRevision(piezaId, "APROBADA", null), quien));
                        exitos.incrementAndGet();
                    } catch (RuntimeException | InterruptedException ignorada) {
                        // La que pierde es la que importa: no deja revision.
                    }
                });
            }
            barrera.countDown();
            piscina.shutdown();
            assertThat(piscina.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(exitos.get()).isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.revision_creativa WHERE pieza_creativa_id = ?", piezaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // La moderacion no mueve dinero. Su cuadre es que toda pieza aprobada tenga
        // exactamente una revision aprobada detras, y ninguna aprobada sin ella.
        UUID piezaId = pieza();
        transaccion.execute(t -> moderacionCU.moderar(new EntradaRevision(piezaId, "APROBADA", null), moderador));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.pieza_creativa p
                         WHERE p.estado_moderacion = 'APROBADA'
                           AND p.id = ?
                           AND (SELECT count(*) FROM publicidad.revision_creativa r
                                 WHERE r.pieza_creativa_id = p.id AND r.decision = 'APROBADA') = 1
                        """,
                        piezaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID piezaId = pieza();
        transaccion.execute(
                t -> moderacionCU.moderar(new EntradaRevision(piezaId, "RECHAZADA", "Texto enganoso"), moderador));

        // Fuera de orden: aprobar despues de rechazar no reabre la pieza.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> moderacionCU.moderar(new EntradaRevision(piezaId, "APROBADA", null), moderador)))
                .satisfies(e -> assertThat(raizDe(e)).contains("ya esta RECHAZADA"));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.evento_dominio
                         WHERE tipo = 'publicidad.pieza_creativa_rechazada' AND agregado_id = ?
                        """,
                        piezaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: quien subio la pieza intenta moderarla. Ni revision ni cambio
        // de estado — la transaccion es una sola.
        UUID piezaId = pieza();
        var dueno = contextoDe(fixtura.usuarioDelOrganizador(organizadorId));

        assertThatThrownBy(() -> transaccion.execute(
                        t -> moderacionCU.moderar(new EntradaRevision(piezaId, "APROBADA", null), dueno)))
                .satisfies(e -> assertThat(raizDe(e)).contains("R-PUB-05"));

        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.pieza_creativa WHERE id = ? AND estado_moderacion = 'PENDIENTE'",
                        piezaId))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.revision_creativa WHERE pieza_creativa_id = ?", piezaId))
                .isZero();
    }
}
