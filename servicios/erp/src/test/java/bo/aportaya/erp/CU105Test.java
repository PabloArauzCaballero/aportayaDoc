package bo.aportaya.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-105 · Depreciar un activo fijo. */
class CU105Test extends BaseDeErp {

    private static final AtomicInteger ANIO = new AtomicInteger(2400);

    private int anio;
    private UUID ejercicioId;
    private UUID categoriaId;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        anio = ANIO.incrementAndGet();
        ctx = contextoDe(fixtura.usuario());
        ejercicioId =
                transaccion.execute(t -> periodoCU.abrirEjercicio(anio, ctx)).ejercicioId();
        UUID cuentaActivo = fixtura.cuenta("1201-" + anio, "ACTIVO", "DEUDORA");
        UUID cuentaDep = fixtura.cuenta("1291-" + anio, "ACTIVO", "ACREEDORA");
        UUID cuentaGasto = fixtura.cuenta("5201-" + anio, "EGRESO", "DEUDORA");
        // Vida util de 12 meses: la cuota de un activo de 12.000 sin residual es 1.000.
        categoriaId = fixtura.categoriaDeActivo("EQ-" + anio, 12, cuentaActivo, cuentaDep, cuentaGasto);
    }

    private UUID periodo(int mes) {
        return dsl.fetchOne(
                        "SELECT id FROM erp.periodo_contable WHERE ejercicio_fiscal_id = ? AND mes = ?",
                        ejercicioId,
                        (short) mes)
                .get(0, UUID.class);
    }

    @Test
    @DisplayName(
            "Dado un activo fijo con costo 12000, sin valor residual y vida útil de 12 meses · Cuando se cierra un período completo · Entonces se crea depreciacion_activo por 1000 y valor_en_libros baja a 11000")
    void criterio1() {
        UUID activo = fixtura.activo(categoriaId, "12000.00", "0.00", "0.00");

        var corrida = transaccion.execute(t -> depreciacionCU.depreciar(activo, periodo(1), ctx));

        assertThat(corrida.monto()).isEqualByComparingTo("1000.00");
        assertThat(corrida.valorEnLibros()).isEqualByComparingTo("11000.00");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.depreciacion_activo WHERE activo_fijo_id = ? AND monto = 1000.00",
                        activo))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.activo_fijo WHERE id = ? AND depreciacion_acumulada = 1000.00",
                        activo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un activo fijo cuyo valor_en_libros ya llegó a su valor_residual · Cuando se intenta calcular una depreciación nueva · Entonces el sistema devuelve ACTIVO_YA_AGOTADO")
    void criterio2() {
        // Ya depreciado hasta el residual: 12.000 de costo, 2.000 de residual, 10.000
        // acumulados.
        UUID activo = fixtura.activo(categoriaId, "12000.00", "2000.00", "10000.00");

        assertThatThrownBy(() -> transaccion.execute(t -> depreciacionCU.depreciar(activo, periodo(1), ctx)))
                .hasMessageContaining("valor residual");
        assertThat(contar("SELECT count(*)::int FROM erp.depreciacion_activo WHERE activo_fijo_id = ?", activo))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un activo fijo ya depreciado en el período vigente · Cuando se intenta calcular la depreciación de ese mismo período otra vez · Entonces el sistema devuelve DEPRECIACION_YA_CALCULADA")
    void criterio3() {
        UUID activo = fixtura.activo(categoriaId, "12000.00", "0.00", "0.00");
        transaccion.execute(t -> depreciacionCU.depreciar(activo, periodo(1), ctx));

        // Correr dos veces el mismo mes duplica el gasto y baja el resultado del
        // ejercicio por un error de operacion, no por el negocio.
        assertThatThrownBy(() -> transaccion.execute(t -> depreciacionCU.depreciar(activo, periodo(1), ctx)))
                .hasMessageContaining("ya se deprecio");
        assertThat(contar("SELECT count(*)::int FROM erp.depreciacion_activo WHERE activo_fijo_id = ?", activo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID activo = fixtura.activo(categoriaId, "12000.00", "0.00", "0.00");
        transaccion.execute(t -> depreciacionCU.correr(periodo(1), ctx));
        var segunda = transaccion.execute(t -> depreciacionCU.correr(periodo(1), ctx));

        // La corrida mensual es idempotente: la segunda no vuelve a depreciar lo que ya
        // deprecio, y lo dice en vez de fallar.
        assertThat(segunda.depreciaciones()).isEmpty();
        assertThat(segunda.yaCorridos()).isPositive();
        assertThat(contar("SELECT count(*)::int FROM erp.depreciacion_activo WHERE activo_fijo_id = ?", activo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID activo = fixtura.activo(categoriaId, "12000.00", "0.00", "0.00");
        UUID enero = periodo(1);

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> depreciacionCU.depreciar(activo, enero, ctx));
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

        // uq_depreciacion_activo_periodo sostiene R-CTB-07 cuando los dos leen a la vez.
        assertThat(errores).hasSize(1);
        assertThat(contar("SELECT count(*)::int FROM erp.depreciacion_activo WHERE activo_fijo_id = ?", activo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Un activo de 10.000 con residual 1.000 y vida de 12: la cuota teorica es 750
        // y la ultima se ajusta para cerrar exactamente en el residual.
        UUID activo = fixtura.activo(categoriaId, "10000.00", "1000.00", "8500.00");

        var corrida = transaccion.execute(t -> depreciacionCU.depreciar(activo, periodo(1), ctx));

        // Lo que faltaba eran 500, no los 750 teoricos: repetir la cuota dejaria el
        // activo valiendo menos que su residual y la base lo rechazaria.
        assertThat(corrida.monto()).isEqualByComparingTo("500.00");
        assertThat(corrida.totalmenteDepreciado()).isTrue();
        assertThat(corrida.valorEnLibros()).isEqualByComparingTo("1000.00");
        // Acumulada + valor en libros = costo, al centavo.
        var acumulada = dsl.fetchOne("SELECT depreciacion_acumulada FROM erp.activo_fijo WHERE id = ?", activo)
                .get(0, BigDecimal.class);
        assertThat(acumulada.add(corrida.valorEnLibros())).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID activo = fixtura.activo(categoriaId, "12000.00", "0.00", "0.00");
        transaccion.execute(t -> depreciacionCU.depreciar(activo, periodo(1), ctx));
        transaccion.execute(t -> depreciacionCU.depreciar(activo, periodo(2), ctx));

        // Dos meses son dos depreciaciones: lo que no puede repetirse es el mismo mes.
        assertThat(contar("SELECT count(*)::int FROM erp.depreciacion_activo WHERE activo_fijo_id = ?", activo))
                .isEqualTo(2);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.activo_depreciado' AND payload->>'activoId' = ?
                        """,
                        activo.toString()))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        UUID activo = fixtura.activo(categoriaId, "12000.00", "0.00", "0.00");

        // Paso fallido: el periodo esta cerrado. Depreciar sobre un mes cerrado cambiaria
        // un resultado ya publicado.
        UUID enero = periodo(1);
        transaccion.execute(t -> periodoCU.cerrarPeriodo(
                new bo.aportaya.erp.aplicacion.CU100AbrirCerrarPeriodo.EntradaCierre(enero, "Cierre"), ctx));
        assertThatThrownBy(() -> transaccion.execute(t -> depreciacionCU.depreciar(activo, enero, ctx)))
                .hasMessageContaining("cerrado");

        // Paso fallido: un activo que no existe.
        assertThatThrownBy(() -> transaccion.execute(t -> depreciacionCU.depreciar(UUID.randomUUID(), periodo(2), ctx)))
                .hasMessageContaining("no existe");
        assertThat(contar("SELECT count(*)::int FROM erp.depreciacion_activo WHERE activo_fijo_id = ?", activo))
                .isZero();

        // Con el periodo abierto y el activo vivo, el mismo camino cierra.
        var buena = transaccion.execute(t -> depreciacionCU.depreciar(activo, periodo(2), ctx));
        assertThat(buena.monto()).isEqualByComparingTo("1000.00");
    }
}
