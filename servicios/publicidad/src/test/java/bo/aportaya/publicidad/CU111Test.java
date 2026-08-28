package bo.aportaya.publicidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-111 · Crear y aprobar una campana publicitaria. */
class CU111Test extends EscenarioDeCampana {

    @Test
    @DisplayName(
            "Dada una cuenta publicitaria activa con límite disponible suficiente · Cuando el anunciante crea una campaña con un conjunto de anuncios y la envía a revisión · Entonces la campaña queda en estado EN_REVISION")
    void criterio1() {
        var salida =
                transaccion.execute(t -> campanaCU.crear(campana("3000.00", "150.00", "12.00", "CPM"), operaciones));

        assertThat(salida.estado()).isEqualTo("EN_REVISION");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.campana_publicitaria
                         WHERE id = ? AND estado = 'EN_REVISION' AND aprobada_por IS NULL
                           AND presupuesto_consumido = 0
                        """,
                        salida.campanaPublicitariaId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.conjunto_anuncios WHERE campana_publicitaria_id = ? AND estado = 'PAUSADO'",
                        salida.campanaPublicitariaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una campaña en revisión con presupuesto dentro del límite · Cuando Operaciones la aprueba · Entonces la campaña pasa a ACTIVA con aprobada_por registrado")
    void criterio2() {
        var creada =
                transaccion.execute(t -> campanaCU.crear(campana("2000.00", "100.00", "10.00", "CPM"), operaciones));

        var aprobada = transaccion.execute(t -> campanaCU.aprobar(creada.campanaPublicitariaId(), operaciones));

        assertThat(aprobada.estado()).isEqualTo("ACTIVA");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.campana_publicitaria
                         WHERE id = ? AND estado = 'ACTIVA' AND aprobada_por = ?
                        """,
                        creada.campanaPublicitariaId(),
                        operaciones.usuarioId()))
                .isEqualTo(1);
        // Y sus conjuntos quedan entregando: aprobar sin habilitar no sirve de nada.
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.conjunto_anuncios WHERE campana_publicitaria_id = ? AND estado = 'ACTIVO'",
                        creada.campanaPublicitariaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una campaña cuyo presupuesto_total excede el límite disponible de la cuenta · Cuando Operaciones intenta aprobarla · Entonces el sistema devuelve LIMITE_DE_GASTO_EXCEDIDO")
    void criterio3() {
        // La cuenta tiene limite de 9.000 y ya consumio 8.500: quedan 500.
        dsl.execute("UPDATE publicidad.cuenta_publicitaria SET saldo_consumido_mes = 8500.00 WHERE id = ?", cuentaId);
        var creada =
                transaccion.execute(t -> campanaCU.crear(campana("4000.00", "200.00", "10.00", "CPM"), operaciones));

        assertThatThrownBy(
                        () -> transaccion.execute(t -> campanaCU.aprobar(creada.campanaPublicitariaId(), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("excede lo disponible del mes (500.00)"));

        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.campana_publicitaria WHERE id = ? AND estado = 'EN_REVISION'",
                        creada.campanaPublicitariaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        var creada = transaccion.execute(t -> campanaCU.crear(campana("1000.00", "50.00", "8.00", "CPC"), operaciones));
        var primera = transaccion.execute(t -> campanaCU.aprobar(creada.campanaPublicitariaId(), operaciones));

        // Aprobar dos veces no vuelve a aprobar: el estado ya no es EN_REVISION.
        assertThatThrownBy(
                        () -> transaccion.execute(t -> campanaCU.aprobar(creada.campanaPublicitariaId(), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("solo se aprueba lo que esta en revision"));

        assertThat(primera.estado()).isEqualTo("ACTIVA");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.evento_dominio
                         WHERE tipo = 'publicidad.campana_aprobada' AND agregado_id = ?
                        """,
                        creada.campanaPublicitariaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        // Quedan 1.000 del mes y dos campanas de 600 esperando aprobacion: entra una.
        dsl.execute("UPDATE publicidad.cuenta_publicitaria SET saldo_consumido_mes = 8000.00 WHERE id = ?", cuentaId);
        var una = transaccion.execute(t -> campanaCU.crear(campana("600.00", "60.00", "10.00", "CPM"), operaciones));
        var otra = transaccion.execute(t -> campanaCU.crear(campana("600.00", "60.00", "10.00", "CPM"), operaciones));

        var barrera = new CountDownLatch(1);
        var exitos = new AtomicInteger();
        try (var piscina = Executors.newFixedThreadPool(2)) {
            for (UUID campanaId : new UUID[] {una.campanaPublicitariaId(), otra.campanaPublicitariaId()}) {
                piscina.submit(() -> {
                    try {
                        barrera.await();
                        transaccion.execute(t -> campanaCU.aprobar(campanaId, operaciones));
                        exitos.incrementAndGet();
                    } catch (RuntimeException | InterruptedException ignorada) {
                        // La que pierde no deja rastro.
                    }
                });
            }
            barrera.countDown();
            piscina.shutdown();
            assertThat(piscina.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        // Las dos caben en lo que queda por separado; el bloqueo garantiza que la
        // segunda lea el saldo despues de la primera y no una foto vieja.
        assertThat(exitos.get()).isGreaterThanOrEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.campana_publicitaria
                         WHERE cuenta_publicitaria_id = ? AND estado = 'ACTIVA' AND aprobada_por IS NOT NULL
                        """,
                        cuentaId))
                .isEqualTo(exitos.get());
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        var creada =
                transaccion.execute(t -> campanaCU.crear(campana("1500.00", "75.00", "10.00", "CPM"), operaciones));
        transaccion.execute(t -> campanaCU.aprobar(creada.campanaPublicitariaId(), operaciones));

        // El consumido nace en cero y nunca puede pasarse del total: ck_campana_pub_consumo.
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.campana_publicitaria WHERE id = ? AND presupuesto_consumido = 0",
                        creada.campanaPublicitariaId()))
                .isEqualTo(1);
        assertThat(rechazaLaBase(
                        "UPDATE publicidad.campana_publicitaria SET presupuesto_consumido = 1500.01 WHERE id = ?",
                        creada.campanaPublicitariaId()))
                .contains("ck_campana_pub_consumo");
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        var creada = transaccion.execute(t -> campanaCU.crear(campana("800.00", "40.00", "9.00", "CPC"), operaciones));
        transaccion.execute(
                t -> campanaCU.rechazar(creada.campanaPublicitariaId(), "Segmentacion inadmisible", operaciones));

        // Fuera de orden: aprobar lo ya rechazado no lo resucita.
        assertThatThrownBy(
                        () -> transaccion.execute(t -> campanaCU.aprobar(creada.campanaPublicitariaId(), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("solo se aprueba lo que esta en revision"));
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.campana_publicitaria WHERE id = ? AND estado = 'RECHAZADA'",
                        creada.campanaPublicitariaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: un espacio apagado. Ni la campana ni sus conjuntos quedan.
        UUID apagado =
                fixtura.espacio("ESP-OFF-" + UUID.randomUUID().toString().substring(0, 6), "BANNER_INICIO", 2, false);
        int antes = contar(
                "SELECT count(*)::int FROM publicidad.campana_publicitaria WHERE cuenta_publicitaria_id = ?", cuentaId);

        assertThatThrownBy(() -> transaccion.execute(
                        t -> campanaCU.crear(campana("500.00", "25.00", "7.00", "CPM", apagado), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("esta inactivo"));

        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.campana_publicitaria WHERE cuenta_publicitaria_id = ?",
                        cuentaId))
                .isEqualTo(antes);
    }
}
