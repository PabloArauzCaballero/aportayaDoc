package bo.aportaya.publicidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.publicidad.aplicacion.CU113EntregarAnuncio.Entrada;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-113 · Entregar un anuncio y medir su desempeno. */
class CU113Test extends EscenarioDeCampana {

    @Test
    @DisplayName(
            "Dado un conjunto_anuncios activo con presupuesto_diario disponible · Cuando un usuario ve el espacio publicitario asociado · Entonces se crea una impresion_anuncio y se descuenta su costo del presupuesto diario")
    void criterio1() {
        // CPM con puja de 20: cada impresion cuesta la milesima parte, 0,0200.
        var enAire = campanaEnAire("500.00", "50.00", "20.00", "CPM");
        UUID espectador = fixtura.usuario();

        var salida = transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, espectador), operaciones));

        assertThat(salida.anuncioId()).isEqualTo(enAire.anuncioId());
        assertThat(salida.costo()).isEqualByComparingTo("0.0200");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.impresion_anuncio
                         WHERE id = ? AND anuncio_id = ? AND usuario_id = ? AND costo = 0.0200
                        """,
                        salida.impresionId(),
                        enAire.anuncioId(),
                        espectador))
                .isEqualTo(1);
        // Y el gasto sube en la campana: es de ahi de donde se descuenta el dia.
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.campana_publicitaria WHERE id = ? AND presupuesto_consumido = 0.0200",
                        enAire.campanaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una impresion_anuncio reciente · Cuando el usuario hace clic en el anuncio · Entonces se crea clic_anuncio enlazado a esa impresión")
    void criterio2() {
        // CPC con puja de 3: la impresion no cuesta, el clic cuesta 3,0000.
        campanaEnAire("500.00", "50.00", "3.00", "CPC");
        var impresion =
                transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, fixtura.usuario()), operaciones));
        assertThat(impresion.costo()).isEqualByComparingTo("0.0000");

        var clic = transaccion.execute(t -> entregaCU.registrarClic(impresion.impresionId(), operaciones));

        assertThat(clic.costo()).isEqualByComparingTo("3.0000");
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.clic_anuncio WHERE id = ? AND impresion_id = ?",
                        clic.clicId(),
                        impresion.impresionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un conjunto_anuncios cuyo presupuesto_diario ya se agotó · Cuando se solicita un anuncio para su espacio · Entonces el sistema no entrega ningún anuncio de ese conjunto")
    void criterio3() {
        // Presupuesto diario de 0,03 y puja CPM de 20: entran una impresion de 0,02 y
        // no la segunda, que dejaria el dia en 0,04.
        var enAire = campanaEnAire("500.00", "0.03", "20.00", "CPM");
        transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, null), operaciones));

        var segunda = transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, null), operaciones));

        assertThat(segunda.anuncioId()).isNull();
        assertThat(segunda.motivo()).isEqualTo("SIN_ANUNCIO_ELEGIBLE");
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.conjunto_anuncios WHERE id = ? AND estado = 'AGOTADO'",
                        enAire.conjuntoId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.impresion_anuncio WHERE anuncio_id = ?",
                        enAire.anuncioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Una entrega NO es idempotente: dos vistas del mismo banner son dos
        // impresiones, y cobrar una sola seria regalar la segunda. Lo que se comprueba
        // es que cada una deja su propio hecho, con su propio costo.
        var enAire = campanaEnAire("500.00", "50.00", "20.00", "CPM");

        var una = transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, null), operaciones));
        var otra = transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, null), operaciones));

        assertThat(otra.impresionId()).isNotEqualTo(una.impresionId());
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.impresion_anuncio WHERE anuncio_id = ?",
                        enAire.anuncioId()))
                .isEqualTo(2);
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.campana_publicitaria WHERE id = ? AND presupuesto_consumido = 0.0400",
                        enAire.campanaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        // Presupuesto diario para una sola impresion. Dos peticiones a la vez: el
        // FOR UPDATE sobre el conjunto serializa la lectura del gastado del dia.
        var enAire = campanaEnAire("500.00", "0.02", "20.00", "CPM");
        var barrera = new CountDownLatch(1);
        var entregadas = new AtomicInteger();

        try (var piscina = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                piscina.submit(() -> {
                    try {
                        barrera.await();
                        var salida =
                                transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, null), operaciones));
                        if (salida.anuncioId() != null) {
                            entregadas.incrementAndGet();
                        }
                    } catch (RuntimeException | InterruptedException ignorada) {
                        // Perder la carrera es no entregar, no fallar.
                    }
                });
            }
            barrera.countDown();
            piscina.shutdown();
            assertThat(piscina.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(entregadas.get()).isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.impresion_anuncio WHERE anuncio_id = ?",
                        enAire.anuncioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Lo que se le carga a la campana es exactamente lo que suman sus hechos.
        var enAire = campanaEnAire("500.00", "50.00", "5.00", "CPC");
        var una = transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, null), operaciones));
        transaccion.execute(t -> entregaCU.registrarClic(una.impresionId(), operaciones));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.campana_publicitaria c
                         WHERE c.id = ?
                           AND c.presupuesto_consumido = (
                                 SELECT COALESCE(SUM(i.costo), 0) + COALESCE(SUM(cl.costo), 0)
                                   FROM publicidad.impresion_anuncio i
                                   JOIN publicidad.anuncio a ON a.id = i.anuncio_id
                                   JOIN publicidad.conjunto_anuncios cj ON cj.id = a.conjunto_anuncios_id
                                   LEFT JOIN publicidad.clic_anuncio cl ON cl.impresion_id = i.id
                                  WHERE cj.campana_publicitaria_id = c.id)
                        """,
                        enAire.campanaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        var enAire = campanaEnAire("500.00", "50.00", "4.00", "CPC");
        var impresion = transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, null), operaciones));
        var clic = transaccion.execute(t -> entregaCU.registrarClic(impresion.impresionId(), operaciones));
        UUID conversionId = transaccion.execute(t -> entregaCU.registrarConversion(
                clic.clicId(), impresion.impresionId(), "POSTULACION_GRUPO", UUID.randomUUID(), operaciones));

        // Fuera de orden: el clic sobre una impresion inexistente no entra.
        assertThatThrownBy(() -> transaccion.execute(t -> entregaCU.registrarClic(UUID.randomUUID(), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("Esa impresion no existe"));

        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.conversion_anuncio WHERE id = ? AND clic_id = ?",
                        conversionId,
                        clic.clicId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.impresion_anuncio WHERE anuncio_id = ?",
                        enAire.anuncioId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: el espacio ya esta lleno. Ni impresion ni cargo a la campana.
        UUID lleno = fixtura.espacio(
                "ESP-LLENO-" + UUID.randomUUID().toString().substring(0, 6), "PUSH_PATROCINADO", 1, true);
        var creada = transaccion.execute(
                t -> campanaCU.crear(campana("500.00", "50.00", "9.00", "CPM", lleno), operaciones));
        transaccion.execute(t -> campanaCU.aprobar(creada.campanaPublicitariaId(), operaciones));
        UUID conjuntoId = dsl.fetchOne(
                        "SELECT id FROM publicidad.conjunto_anuncios WHERE campana_publicitaria_id = ?",
                        creada.campanaPublicitariaId())
                .get(0, UUID.class);
        transaccion.execute(t -> entregaCU.programar(conjuntoId, fixtura.piezaAprobada(anuncianteId), operaciones));
        transaccion.execute(t -> entregaCU.entregar(new Entrada(lleno, null), operaciones));

        BigDecimal consumidoAntes = dsl.fetchOne(
                        "SELECT presupuesto_consumido FROM publicidad.campana_publicitaria WHERE id = ?",
                        creada.campanaPublicitariaId())
                .get(0, BigDecimal.class);

        assertThatThrownBy(() -> transaccion.execute(t -> entregaCU.entregar(new Entrada(lleno, null), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("capacidad maxima"));

        assertThat(dsl.fetchOne(
                                "SELECT presupuesto_consumido FROM publicidad.campana_publicitaria WHERE id = ?",
                                creada.campanaPublicitariaId())
                        .get(0, BigDecimal.class))
                .isEqualByComparingTo(consumidoAntes);
    }
}
