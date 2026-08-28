package bo.aportaya.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.erp.aplicacion.CU101Presupuestar.EntradaPresupuesto;
import bo.aportaya.erp.aplicacion.CU101Presupuestar.Partida;
import bo.aportaya.erp.aplicacion.CU103FacturaDeProveedor.EntradaFactura;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-101 · Presupuestar por centro de costo. */
class CU101Test extends BaseDeErp {

    private static final AtomicInteger ANIO = new AtomicInteger(2200);

    private int anio;
    private UUID ejercicioId;
    private UUID centroId;
    private UUID cuentaGasto;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        anio = ANIO.incrementAndGet();
        ctx = contextoDe(fixtura.usuario());
        ejercicioId =
                transaccion.execute(t -> periodoCU.abrirEjercicio(anio, ctx)).ejercicioId();
        centroId = fixtura.centroDeCosto("CC-" + anio, "AREA");
        cuentaGasto = fixtura.cuenta("5101-" + anio, "EGRESO", "DEUDORA");
    }

    private UUID periodo(int mes) {
        return dsl.fetchOne(
                        "SELECT id FROM erp.periodo_contable WHERE ejercicio_fiscal_id = ? AND mes = ?",
                        ejercicioId,
                        (short) mes)
                .get(0, UUID.class);
    }

    private EntradaPresupuesto entrada(String monto) {
        return new EntradaPresupuesto(
                centroId,
                ejercicioId,
                "Presupuesto " + anio,
                "BOB",
                List.of(new Partida(cuentaGasto, periodo(1), new BigDecimal(monto))));
    }

    @Test
    @DisplayName(
            "Dado un centro de costo sin presupuesto para el ejercicio vigente · Cuando Contabilidad crea un presupuesto con al menos una partida · Entonces el presupuesto queda en estado BORRADOR hasta que Directorio lo aprueba")
    void criterio1() {
        var salida = transaccion.execute(t -> presupuestoCU.crear(entrada("50000.00"), ctx));

        assertThat(salida.estado()).isEqualTo("BORRADOR");
        assertThat(salida.partidas()).isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.presupuesto WHERE id = ? AND estado = 'BORRADOR' AND aprobado_por IS NULL",
                        salida.presupuestoId()))
                .isEqualTo(1);

        var aprobado = transaccion.execute(t -> presupuestoCU.aprobar(salida.presupuestoId(), ctx));
        assertThat(aprobado.estado()).isEqualTo("APROBADO");
        // ck_presupuesto_aprobacion: firma y fecha. Un presupuesto aprobado sin saber por
        // quien no compromete a nadie.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.presupuesto
                         WHERE id = ? AND aprobado_por IS NOT NULL AND aprobado_en IS NOT NULL
                        """,
                        salida.presupuestoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un presupuesto aprobado con una partida de una cuenta contable · Cuando se registra una factura_proveedor contra esa cuenta y ese centro de costo · Entonces monto_ejecutado de la partida aumenta en el monto de la factura")
    void criterio2() {
        var presupuesto = transaccion.execute(t -> presupuestoCU.crear(entrada("50000.00"), ctx));
        transaccion.execute(t -> presupuestoCU.aprobar(presupuesto.presupuestoId(), ctx));
        UUID proveedor = fixtura.tercero("PROVEEDOR", "NIT-" + anio);
        LocalDate enEnero = dsl.fetchOne("SELECT fecha_inicio FROM erp.periodo_contable WHERE id = ?", periodo(1))
                .get(0, LocalDate.class);

        transaccion.execute(t -> facturaCU.registrar(
                new EntradaFactura(
                        proveedor,
                        null,
                        centroId,
                        "F-001-" + anio,
                        enEnero,
                        enEnero.plusDays(30),
                        new BigDecimal("12000.00"),
                        "BOB",
                        cuentaGasto,
                        ctx.usuarioId()),
                ctx));

        var ejecucion = transaccion.execute(t -> presupuestoCU.ejecucion(presupuesto.presupuestoId(), ctx));
        assertThat(ejecucion).hasSize(1);
        assertThat(ejecucion.get(0).ejecutado()).isEqualByComparingTo("12000.00");
        assertThat(ejecucion.get(0).disponible()).isEqualByComparingTo("38000.00");
        assertThat(ejecucion.get(0).sobreejecutado()).isFalse();
    }

    @Test
    @DisplayName(
            "Dado un presupuesto en estado CERRADO · Cuando se intenta agregar una partida nueva · Entonces el sistema devuelve PARTIDA_SOBRE_PRESUPUESTO_CERRADO")
    void criterio3() {
        var presupuesto = transaccion.execute(t -> presupuestoCU.crear(entrada("50000.00"), ctx));
        dsl.execute("UPDATE erp.presupuesto SET estado = 'CERRADO' WHERE id = ?", presupuesto.presupuestoId());

        // Agregar despues del cierre cambiaria la base contra la que ya se midio la
        // ejecucion: el area cuyo desvio se discutio la semana pasada tendria otro numero.
        assertThatThrownBy(() -> transaccion.execute(t -> presupuestoCU.agregarPartida(
                        presupuesto.presupuestoId(),
                        new Partida(cuentaGasto, periodo(2), new BigDecimal("1000.00")),
                        "BOB",
                        ctx)))
                .hasMessageContaining("cerrado");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.partida_presupuestaria WHERE presupuesto_id = ?",
                        presupuesto.presupuestoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        transaccion.execute(t -> presupuestoCU.crear(entrada("50000.00"), ctx));

        // R-CTB-03: un presupuesto por centro y ejercicio. Dos vigentes dejarian a cada
        // area eligiendo cual mirar.
        assertThatThrownBy(() -> transaccion.execute(t -> presupuestoCU.crear(entrada("60000.00"), ctx)))
                .hasMessageContaining("ya tiene presupuesto");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.presupuesto WHERE centro_costo_id = ? AND ejercicio_fiscal_id = ?",
                        centroId,
                        ejercicioId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        var entrada = entrada("50000.00");

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> presupuestoCU.crear(entrada, ctx));
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
                        "SELECT count(*)::int FROM erp.presupuesto WHERE centro_costo_id = ? AND ejercicio_fiscal_id = ?",
                        centroId,
                        ejercicioId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        var salida = transaccion.execute(t -> presupuestoCU.crear(
                new EntradaPresupuesto(
                        centroId,
                        ejercicioId,
                        "Presupuesto " + anio,
                        "BOB",
                        List.of(
                                new Partida(cuentaGasto, periodo(1), new BigDecimal("10000.00")),
                                new Partida(cuentaGasto, periodo(2), new BigDecimal("15000.50")),
                                new Partida(cuentaGasto, periodo(3), new BigDecimal("4999.50")))),
                ctx));

        // El total del presupuesto es la suma de sus partidas, al centavo: si no
        // cuadrara, el numero que se lleva al directorio no seria el que se ejecuta.
        assertThat(salida.total()).isEqualByComparingTo("30000.00");
        var suma = dsl.fetchOne(
                        "SELECT COALESCE(SUM(monto_presupuestado), 0) FROM erp.partida_presupuestaria WHERE presupuesto_id = ?",
                        salida.presupuestoId())
                .get(0, BigDecimal.class);
        assertThat(suma).isEqualByComparingTo(salida.total());
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        var salida = transaccion.execute(t -> presupuestoCU.crear(entrada("50000.00"), ctx));
        transaccion.execute(t -> presupuestoCU.aprobar(salida.presupuestoId(), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> presupuestoCU.aprobar(salida.presupuestoId(), ctx)))
                .hasMessageContaining("ya no esta en borrador");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.evento_dominio WHERE tipo = 'erp.presupuesto_aprobado' AND agregado_id = ?",
                        salida.presupuestoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: dos partidas de la misma cuenta y el mismo periodo. Sumarian el
        // doble sin que se note en el total.
        assertThatThrownBy(() -> transaccion.execute(t -> presupuestoCU.crear(
                        new EntradaPresupuesto(
                                centroId,
                                ejercicioId,
                                "Presupuesto " + anio,
                                "BOB",
                                List.of(
                                        new Partida(cuentaGasto, periodo(1), new BigDecimal("10000.00")),
                                        new Partida(cuentaGasto, periodo(1), new BigDecimal("5000.00")))),
                        ctx)))
                .hasMessageContaining("misma cuenta y periodo");
        assertThat(contar("SELECT count(*)::int FROM erp.presupuesto WHERE centro_costo_id = ?", centroId))
                .isZero();

        // Con partidas distintas, el mismo camino cierra.
        var buena = transaccion.execute(t -> presupuestoCU.crear(entrada("50000.00"), ctx));
        assertThat(buena.partidas()).isEqualTo(1);
    }
}
