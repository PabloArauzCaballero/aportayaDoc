package bo.aportaya.publicidad;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.publicidad.aplicacion.CU110AltaDeAnunciante.EntradaAnunciante;
import bo.aportaya.publicidad.aplicacion.CU111CrearCampana.Conjunto;
import bo.aportaya.publicidad.aplicacion.CU111CrearCampana.Entrada;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;

/**
 * El escenario que comparten las pruebas de campana y entrega: un anunciante con su
 * cuenta, un espacio con cupo y un segmento.
 *
 * <p>El anunciante es siempre un organizador porque es el unico caso en que la base
 * puede comprobar R-PUB-05 sola: el trigger {@code fn_pub_moderador_distinto} llega al
 * usuario por la clave del organizador, y sobre un socio comercial no tiene por donde.
 */
abstract class EscenarioDeCampana extends BaseDePublicidad {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(1);

    protected ContextoSesion operaciones;
    protected UUID organizadorId;
    protected UUID anuncianteId;
    protected UUID cuentaId;
    protected UUID espacioId;
    protected UUID segmentoId;

    @BeforeEach
    void escenarioDeCampana() {
        operaciones = contextoDe(fixtura.usuario());
        organizadorId = fixtura.organizador("HABILITADO");
        var alta = transaccion.execute(t -> anuncianteCU.darDeAlta(
                new EntradaAnunciante(
                        "ORGANIZADOR", organizadorId, null, "Pasanaku del barrio", new BigDecimal("9000.00"), "BOB"),
                operaciones));
        anuncianteId = alta.anuncianteId();
        cuentaId = alta.cuentaPublicitariaId();
        espacioId = fixtura.espacio(
                "ESP-" + SECUENCIA.getAndIncrement() + "-"
                        + UUID.randomUUID().toString().substring(0, 6),
                "BANNER_INICIO",
                3,
                true);
        segmentoId = fixtura.segmento(operaciones.usuarioId());
    }

    protected Entrada campana(String presupuestoTotal, String presupuestoDiario, String puja, String modeloPuja) {
        return campana(presupuestoTotal, presupuestoDiario, puja, modeloPuja, espacioId);
    }

    protected Entrada campana(
            String presupuestoTotal, String presupuestoDiario, String puja, String modeloPuja, UUID espacio) {
        return new Entrada(
                cuentaId,
                "Campana " + SECUENCIA.getAndIncrement(),
                "VISIBILIDAD_MARCA",
                new BigDecimal(presupuestoTotal),
                "BOB",
                OffsetDateTime.now().minusDays(1),
                OffsetDateTime.now().plusDays(30),
                List.of(new Conjunto(
                        segmentoId,
                        espacio,
                        "Conjunto principal",
                        new BigDecimal(presupuestoDiario),
                        new BigDecimal(puja),
                        modeloPuja)));
    }

    /** Una campana ya aprobada, con un anuncio programado y su pieza aprobada. */
    protected Entregable campanaEnAire(String presupuestoTotal, String presupuestoDiario, String puja, String modelo) {
        var creada = transaccion.execute(
                t -> campanaCU.crear(campana(presupuestoTotal, presupuestoDiario, puja, modelo), operaciones));
        transaccion.execute(t -> campanaCU.aprobar(creada.campanaPublicitariaId(), operaciones));
        UUID conjuntoId = dsl.fetchOne(
                        "SELECT id FROM publicidad.conjunto_anuncios WHERE campana_publicitaria_id = ?",
                        creada.campanaPublicitariaId())
                .get(0, UUID.class);
        UUID piezaId = fixtura.piezaAprobada(anuncianteId);
        UUID anuncioId = transaccion.execute(t -> entregaCU.programar(conjuntoId, piezaId, operaciones));
        return new Entregable(creada.campanaPublicitariaId(), conjuntoId, piezaId, anuncioId);
    }

    protected record Entregable(UUID campanaId, UUID conjuntoId, UUID piezaId, UUID anuncioId) {}
}
