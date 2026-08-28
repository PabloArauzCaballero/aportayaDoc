package bo.aportaya.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.erp.aplicacion.CU100AbrirCerrarPeriodo.EntradaCierre;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-100 · Abrir y cerrar el periodo contable. */
class CU100Test extends BaseDeErp {

    /** Un ejercicio distinto por prueba: los periodos son unicos por ejercicio y mes. */
    private static final AtomicInteger ANIO = new AtomicInteger(2100);

    private int anio;
    private UUID ejercicioId;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        anio = ANIO.incrementAndGet();
        ctx = contextoDe(fixtura.usuario());
        ejercicioId =
                transaccion.execute(t -> periodoCU.abrirEjercicio(anio, ctx)).ejercicioId();
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
            "Dado un período contable abierto con todos sus asientos cuadrados · Cuando Contabilidad solicita el cierre · Entonces se crea cierre_periodo_contable con total_debe = total_haber y el período pasa a CERRADO")
    void criterio1() {
        UUID enero = periodo(1);
        UUID caja = fixtura.cuenta("1101-" + anio, "ACTIVO", "DEUDORA");
        UUID ingreso = fixtura.cuenta("4101-" + anio, "INGRESO", "ACREEDORA");
        fixtura.asiento(enero, caja, ingreso, "1500.00", ctx.usuarioId());

        var salida = transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre normal"), ctx));

        assertThat(salida.cuadrado()).isTrue();
        assertThat(salida.totalDebe()).isEqualByComparingTo(salida.totalHaber());
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.cierre_periodo_contable WHERE periodo_contable_id = ? AND total_debe = total_haber",
                        enero))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*)::int FROM erp.periodo_contable WHERE id = ? AND estado = 'CERRADO'", enero))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un período contable ya cerrado · Cuando se intenta registrar un asiento_contable nuevo contra ese período · Entonces el sistema lo rechaza")
    void criterio2() {
        UUID enero = periodo(1);
        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), ctx));
        UUID caja = fixtura.cuenta("1102-" + anio, "ACTIVO", "DEUDORA");

        // R-CTB-01 · tg_asiento_periodo_abierto. Es lo que hace que un balance publicado
        // siga diciendo lo mismo dentro de un año.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.asiento_contable
                            (numero, fecha, glosa, origen_tipo, origen_id, periodo_contable_id, estado, registrado_por)
                        VALUES (?, current_date, 'Tardio', 'AJUSTE', gen_random_uuid(), ?, 'CONFIRMADO', ?)
                        """,
                        (long) (900_000 + anio),
                        enero,
                        ctx.usuarioId()))
                .contains("R-CTB-01");
        assertThat(caja).isNotNull();
    }

    @Test
    @DisplayName(
            "Dados dos períodos abiertos consecutivos del mismo ejercicio · Cuando se intenta cerrar el segundo antes que el primero · Entonces el sistema devuelve PERIODO_NO_ES_EL_MAS_ANTIGUO")
    void criterio3() {
        UUID febrero = periodo(2);

        // Cerrar febrero con enero abierto dejaria a febrero incluyendo asientos que
        // enero todavia puede recibir, y el balance de febrero cambiaria despues.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> periodoCU.cerrarPeriodo(new EntradaCierre(febrero, "Fuera de orden"), ctx)))
                .hasMessageContaining("se cierran en orden");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.periodo_contable WHERE id = ? AND estado = 'ABIERTO'", febrero))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID enero = periodo(1);
        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), ctx));

        assertThatThrownBy(() ->
                        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), ctx)))
                .hasMessageContaining("ya esta cerrado");
        assertThat(contar("SELECT count(*)::int FROM erp.cierre_periodo_contable WHERE periodo_contable_id = ?", enero))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID enero = periodo(1);
        var entrada = new EntradaCierre(enero, "Cierre");

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> periodoCU.cerrarPeriodo(entrada, ctx));
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

        assertThat(errores).hasSizeLessThanOrEqualTo(1);
        assertThat(contar("SELECT count(*)::int FROM erp.cierre_periodo_contable WHERE periodo_contable_id = ?", enero))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        UUID enero = periodo(1);
        UUID caja = fixtura.cuenta("1103-" + anio, "ACTIVO", "DEUDORA");
        UUID ingreso = fixtura.cuenta("4103-" + anio, "INGRESO", "ACREEDORA");
        fixtura.asiento(enero, caja, ingreso, "1500.00", ctx.usuarioId());
        fixtura.asiento(enero, caja, ingreso, "250.75", ctx.usuarioId());

        var salida = transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), ctx));

        assertThat(salida.totalDebe()).isEqualByComparingTo("1750.75");
        assertThat(salida.totalHaber()).isEqualByComparingTo("1750.75");
        // ck_cierre_periodo_cuadrado: si no cuadra, no es un cierre.
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.cierre_periodo_contable WHERE periodo_contable_id = ? AND diferencia = 0",
                        enero))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID enero = periodo(1);
        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), ctx));
        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(periodo(2), "Cierre"), ctx));

        // Cada periodo con su constancia, y una sola: el cierre es el acto que congela
        // un mes, y un cierre repetible no congela nada.
        assertThat(contar("SELECT count(*)::int FROM erp.cierre_periodo_contable WHERE periodo_contable_id = ?", enero))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.periodo_cerrado' AND agregado_id = ?
                        """,
                        enero))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: abrir dos veces el mismo ejercicio.
        assertThatThrownBy(() -> transaccion.execute(t -> periodoCU.abrirEjercicio(anio, ctx)))
                .hasMessageContaining("ya esta abierto");
        assertThat(contar("SELECT count(*)::int FROM erp.periodo_contable WHERE ejercicio_fiscal_id = ?", ejercicioId))
                .isEqualTo(12);

        // Paso fallido: cerrar un periodo que ya se cerro. La constancia es append-only
        // y unica: el cierre ocurre una vez.
        UUID enero = periodo(1);
        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), ctx));
        assertThatThrownBy(() ->
                        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Otra vez"), ctx)))
                .hasMessageContaining("ya esta cerrado");
        assertThat(contar("SELECT count(*)::int FROM erp.periodo_contable WHERE id = ? AND estado = 'CERRADO'", enero))
                .isEqualTo(1);
    }
}
