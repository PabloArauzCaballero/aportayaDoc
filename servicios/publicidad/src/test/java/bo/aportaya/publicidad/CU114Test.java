package bo.aportaya.publicidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.publicidad.aplicacion.CU113EntregarAnuncio.Entrada;
import bo.aportaya.publicidad.aplicacion.CU114LiquidarPublicidad;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-114 · Liquidar y facturar el gasto publicitario. */
class CU114Test extends EscenarioDeCampana {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy-MM");

    private String mesVigente() {
        return OffsetDateTime.now().format(MES);
    }

    /** Un mes con consumo: una impresion CPC gratis y un clic que si cuesta. */
    private BigDecimal consumir(String puja) {
        campanaEnAire("500.00", "50.00", puja, "CPC");
        var impresion = transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, null), operaciones));
        var clic = transaccion.execute(t -> entregaCU.registrarClic(impresion.impresionId(), operaciones));
        return clic.costo();
    }

    private CU114LiquidarPublicidad.Entrada liquidacion(UUID facturaElectronicaId, UUID cuentaPorCobrarId) {
        return new CU114LiquidarPublicidad.Entrada(cuentaId, mesVigente(), facturaElectronicaId, cuentaPorCobrarId);
    }

    @Test
    @DisplayName(
            "Dada una cuenta_publicitaria con impresiones y clics con costo en el mes vigente · Cuando corre la liquidación mensual · Entonces se crea factura_publicidad con el monto total y su cuenta_por_cobrar enlazada")
    void criterio1() {
        consumir("125.50");
        UUID cxc = fixtura.cuentaPorCobrar("125.50");
        UUID fiscal = fixtura.facturaElectronica(operaciones.usuarioId(), "125.50");

        var salida = transaccion.execute(t -> liquidacionCU.liquidar(liquidacion(fiscal, cxc), operaciones));

        assertThat(salida.montoTotal()).isEqualByComparingTo("125.50");
        assertThat(salida.estado()).isEqualTo("FACTURADA");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.factura_publicidad
                         WHERE id = ? AND cuenta_publicitaria_id = ? AND periodo = ?
                           AND monto_total = 125.50 AND cuenta_por_cobrar_id = ?
                           AND factura_electronica_id = ? AND estado = 'FACTURADA'
                        """,
                        salida.facturaPublicidadId(),
                        cuentaId,
                        mesVigente(),
                        cxc,
                        fiscal))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una cuenta_publicitaria sin ningún consumo en el mes · Cuando corre la liquidación mensual · Entonces no se genera ninguna factura_publicidad para esa cuenta")
    void criterio2() {
        // La cuenta existe y esta activa, pero nadie vio ni un anuncio suyo.
        assertThatThrownBy(() -> transaccion.execute(t -> liquidacionCU.liquidar(liquidacion(null, null), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("no se factura cero"));

        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.factura_publicidad WHERE cuenta_publicitaria_id = ?",
                        cuentaId))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dada una factura_publicidad ya generada para una cuenta y un período · Cuando se intenta liquidar ese mismo período de nuevo · Entonces el sistema devuelve PERIODO_YA_LIQUIDADO")
    void criterio3() {
        consumir("80.00");
        transaccion.execute(t -> liquidacionCU.liquidar(liquidacion(null, null), operaciones));

        assertThatThrownBy(() -> transaccion.execute(t -> liquidacionCU.liquidar(liquidacion(null, null), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("ya esta liquidado"));

        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.factura_publicidad WHERE cuenta_publicitaria_id = ? AND periodo = ?",
                        cuentaId,
                        mesVigente()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        consumir("45.00");
        var primera = transaccion.execute(t -> liquidacionCU.liquidar(liquidacion(null, null), operaciones));

        // El reintento no crea una segunda factura y devuelve cual es la que hay.
        assertThatThrownBy(() -> transaccion.execute(t -> liquidacionCU.liquidar(liquidacion(null, null), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("ya esta liquidado"));

        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.factura_publicidad WHERE id = ?",
                        primera.facturaPublicidadId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        consumir("60.00");
        var barrera = new CountDownLatch(1);
        var exitos = new AtomicInteger();

        try (var piscina = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                piscina.submit(() -> {
                    try {
                        barrera.await();
                        transaccion.execute(t -> liquidacionCU.liquidar(liquidacion(null, null), operaciones));
                        exitos.incrementAndGet();
                    } catch (RuntimeException | InterruptedException ignorada) {
                        // La que pierde no factura dos veces el mismo mes.
                    }
                });
            }
            barrera.countDown();
            piscina.shutdown();
            assertThat(piscina.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(exitos.get()).isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM publicidad.factura_publicidad WHERE cuenta_publicitaria_id = ? AND periodo = ?",
                        cuentaId,
                        mesVigente()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Tres impresiones CPM con puja de 15: cada una cuesta 0,0150 y las tres suman
        // 0,0450, que al centavo es 0,05. Redondeando impresion por impresion darian
        // 0,02 cada una y 0,06 en total: el redondeo se hace UNA vez, sobre el total.
        campanaEnAire("500.00", "50.00", "15.00", "CPM");
        for (int i = 0; i < 3; i++) {
            transaccion.execute(t -> entregaCU.entregar(new Entrada(espacioId, null), operaciones));
        }

        BigDecimal consumo =
                transaccion.execute(t -> liquidacionCU.consumoDelPeriodo(cuentaId, mesVigente(), operaciones));
        var salida = transaccion.execute(t -> liquidacionCU.liquidar(liquidacion(null, null), operaciones));

        assertThat(consumo).isEqualByComparingTo("0.05");
        assertThat(salida.montoTotal()).isEqualByComparingTo("0.05");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.factura_publicidad f
                         WHERE f.id = ?
                           AND f.monto_total = round((
                                 SELECT COALESCE(SUM(i.costo), 0) + COALESCE(SUM(cl.costo), 0)
                                   FROM publicidad.impresion_anuncio i
                                   JOIN publicidad.anuncio a ON a.id = i.anuncio_id
                                   JOIN publicidad.conjunto_anuncios cj ON cj.id = a.conjunto_anuncios_id
                                   JOIN publicidad.campana_publicitaria c ON c.id = cj.campana_publicitaria_id
                                   LEFT JOIN publicidad.clic_anuncio cl ON cl.impresion_id = i.id
                                  WHERE c.cuenta_publicitaria_id = f.cuenta_publicitaria_id), 2)
                        """,
                        salida.facturaPublicidadId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        consumir("70.00");
        var salida = transaccion.execute(t -> liquidacionCU.liquidar(liquidacion(null, null), operaciones));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM publicidad.evento_dominio
                         WHERE tipo = 'publicidad.factura_publicidad_generada' AND agregado_id = ?
                        """,
                        salida.facturaPublicidadId()))
                .isEqualTo(1);
        // Fuera de orden: liquidar un mes anterior sin consumo no genera nada.
        assertThatThrownBy(() -> transaccion.execute(t -> liquidacionCU.liquidar(
                        new CU114LiquidarPublicidad.Entrada(
                                cuentaId, OffsetDateTime.now().minusYears(1).format(MES), null, null),
                        operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("no se factura cero"));
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: la cuenta publicitaria no existe. No queda ninguna factura.
        int antes = contar("SELECT count(*)::int FROM publicidad.factura_publicidad", new Object[0]);

        assertThatThrownBy(() -> transaccion.execute(t -> liquidacionCU.liquidar(
                        new CU114LiquidarPublicidad.Entrada(UUID.randomUUID(), mesVigente(), null, null), operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("cuenta publicitaria no existe"));

        assertThat(contar("SELECT count(*)::int FROM publicidad.factura_publicidad", new Object[0]))
                .isEqualTo(antes);
    }
}
