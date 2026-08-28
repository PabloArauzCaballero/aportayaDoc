package bo.aportaya.publicidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.publicidad.aplicacion.CU112ModerarPieza.EntradaPieza;
import bo.aportaya.publicidad.aplicacion.CU112ModerarPieza.EntradaRevision;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-112 · Lo que la base y el caso de uso rechazan. */
class CU112RechazosTest extends EscenarioDeCampana {

    private ContextoSesion moderador;

    @BeforeEach
    void moderadorDistinto() {
        moderador = contextoDe(fixtura.usuario());
    }

    private UUID pieza() {
        return transaccion.execute(t -> moderacionCU.subir(
                new EntradaPieza(anuncianteId, "Promo", "Sumate", "https://cdn.aportaya.bo/promo.png", "IMAGEN"),
                operaciones));
    }

    @Test
    @DisplayName("rechaza por R-PUB-04")
    void rechazaRPUB04() {
        var enAire = campanaEnAire("1000.00", "50.00", "10.00", "CPM");

        // Moderacion previa: una pieza pendiente no llega a pantalla.
        UUID pendiente = pieza();
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.anuncio (conjunto_anuncios_id, pieza_creativa_id, estado)
                        VALUES (?, ?, 'PROGRAMADO')
                        """,
                        enAire.conjuntoId(),
                        pendiente))
                .contains("R-PUB-04");

        // Ni una rechazada.
        UUID rechazada = pieza();
        transaccion.execute(
                t -> moderacionCU.moderar(new EntradaRevision(rechazada, "RECHAZADA", "Texto enganoso"), moderador));
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.anuncio (conjunto_anuncios_id, pieza_creativa_id, estado)
                        VALUES (?, ?, 'PROGRAMADO')
                        """,
                        enAire.conjuntoId(),
                        rechazada))
                .contains("R-PUB-04");

        // Y la impresion, el clic y la conversion son append-only: el desempeno que se
        // le factura al anunciante no se retoca.
        var impresion = transaccion.execute(t -> entregaCU.entregar(
                new bo.aportaya.publicidad.aplicacion.CU113EntregarAnuncio.Entrada(espacioId, null), operaciones));
        assertThat(rechazaLaBase(
                        "UPDATE publicidad.impresion_anuncio SET costo = 0 WHERE id = ?", impresion.impresionId()))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-PUB-05")
    void rechazaRPUB05() {
        UUID piezaId = pieza();

        // Quien sube no se autoaprueba. El caso de uso lo dice con nombre propio...
        var dueno = contextoDe(fixtura.usuarioDelOrganizador(organizadorId));
        assertThatThrownBy(() -> transaccion.execute(
                        t -> moderacionCU.moderar(new EntradaRevision(piezaId, "APROBADA", null), dueno)))
                .satisfies(e -> assertThat(raizDe(e)).contains("R-PUB-05"));

        // ...y el trigger lo sostiene aunque alguien escriba la revision por fuera.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.revision_creativa
                            (pieza_creativa_id, revisada_por, decision, revisada_en)
                        VALUES (?, ?, 'APROBADA', now())
                        """,
                        piezaId,
                        dueno.usuarioId()))
                .contains("R-PUB-05");

        // Y un rechazo sin motivo no entra ni por la puerta de atras.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.revision_creativa
                            (pieza_creativa_id, revisada_por, decision, motivo, revisada_en)
                        VALUES (?, ?, 'RECHAZADA', NULL, now())
                        """,
                        piezaId,
                        moderador.usuarioId()))
                .contains("ck_revision_creativa_motivo");
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // La misma segregacion de R-SEG-04 —quien ejecuta no autoriza—, aplicada aca a
        // quien sube una pieza y quien la modera. La diferencia con R-PUB-05 es de
        // origen, no de fondo: R-SEG-04 es la regla general del sistema y esta es su
        // forma en publicidad.
        UUID piezaId = pieza();
        var dueno = contextoDe(fixtura.usuarioDelOrganizador(organizadorId));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.revision_creativa
                            (pieza_creativa_id, revisada_por, decision, revisada_en)
                        VALUES (?, ?, 'APROBADA', now())
                        """,
                        piezaId,
                        dueno.usuarioId()))
                .contains("no puede moderarla");

        // Y la pieza sigue pendiente: el intento no dejo rastro.
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.pieza_creativa WHERE id = ? AND estado_moderacion = 'PENDIENTE'",
                        piezaId))
                .isEqualTo(1);
    }
}
