package bo.aportaya.publicidad;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.publicidad.aplicacion.CU113EntregarAnuncio.Entrada;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-113 · Lo que la base y el caso de uso rechazan. */
class CU113RechazosTest extends EscenarioDeCampana {

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        campanaEnAire("500.00", "50.00", "6.00", "CPC");
        var impresion = transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, null), operaciones));
        var clic = transaccion.execute(t -> entregaCU.registrarClic(impresion.impresionId(), operaciones));
        UUID conversionId = transaccion.execute(t -> entregaCU.registrarConversion(
                clic.clicId(), impresion.impresionId(), "REGISTRO", UUID.randomUUID(), operaciones));

        // Los tres hechos que se le facturan al anunciante son append-only. Si el costo
        // de una impresion se pudiera editar, la factura del mes seria opinable.
        assertThat(rechazaLaBase(
                        "UPDATE publicidad.impresion_anuncio SET costo = 0 WHERE id = ?", impresion.impresionId()))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM publicidad.impresion_anuncio WHERE id = ?", impresion.impresionId()))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("UPDATE publicidad.clic_anuncio SET costo = 0 WHERE id = ?", clic.clicId()))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM publicidad.clic_anuncio WHERE id = ?", clic.clicId()))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase(
                        "UPDATE publicidad.conversion_anuncio SET tipo = 'DESCARGA_APP' WHERE id = ?", conversionId))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM publicidad.conversion_anuncio WHERE id = ?", conversionId))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-PUB-04")
    void rechazaRPUB04() {
        var enAire = campanaEnAire("500.00", "50.00", "6.00", "CPM");

        // Solo se entrega lo que un moderador aprobo. El anuncio que ya esta en el aire
        // tiene su pieza APROBADA; una pieza pendiente no llega a tener anuncio.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.anuncio a
                          JOIN publicidad.pieza_creativa p ON p.id = a.pieza_creativa_id
                         WHERE a.id = ? AND p.estado_moderacion = 'APROBADA'
                        """,
                        enAire.anuncioId()))
                .isEqualTo(1);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.pieza_creativa
                            (id, anunciante_id, titulo, url_recurso, tipo_recurso, estado_moderacion, creada_en)
                        VALUES (gen_random_uuid(), ?, 'Sin revisar', 'https://cdn.aportaya.bo/x.png', 'IMAGEN',
                                'INVENTADO', now())
                        """,
                        anuncianteId))
                .contains("pieza_creativa");

        // Y la conversion solo admite los tipos del catalogo: un tipo libre haria que
        // el desempeno de una campana signifique lo que cada quien quiera.
        var impresion = transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, null), operaciones));
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.conversion_anuncio (impresion_id, tipo, ocurrida_en)
                        VALUES (?, 'LO_QUE_SEA', now())
                        """,
                        impresion.impresionId()))
                .contains("conversion_anuncio");
    }
}
