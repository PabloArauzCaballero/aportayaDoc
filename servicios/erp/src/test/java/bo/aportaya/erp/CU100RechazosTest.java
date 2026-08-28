package bo.aportaya.erp;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.erp.aplicacion.CU100AbrirCerrarPeriodo.EntradaCierre;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-100 · Lo que la base y el caso de uso rechazan. */
class CU100RechazosTest extends BaseDeErp {

    private static final AtomicInteger ANIO = new AtomicInteger(2600);

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
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El cierre es append-only: la constancia del cuadre no se reescribe ni se
        // borra. Un cierre que se puede corregir despues no congela nada.
        UUID enero = periodo(1);
        var cierre = transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), ctx));

        assertThat(rechazaLaBase(
                        "UPDATE erp.cierre_periodo_contable SET total_debe = 1 WHERE id = ?", cierre.cierreId()))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM erp.cierre_periodo_contable WHERE id = ?", cierre.cierreId()))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // Todo cierre deja su evento en la misma transaccion, con las cifras contra las
        // que se cerro: sin eso, discutir un balance seria discutir de memoria.
        UUID enero = periodo(1);
        UUID caja = fixtura.cuenta("1401-" + anio, "ACTIVO", "DEUDORA");
        UUID ingreso = fixtura.cuenta("4401-" + anio, "INGRESO", "ACREEDORA");
        fixtura.asiento(enero, caja, ingreso, "700.00", ctx.usuarioId());
        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.periodo_cerrado' AND agregado_id = ?
                           AND payload->>'totalDebe' = '700.00' AND payload->>'totalHaber' = '700.00'
                        """,
                        enero))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-06")
    void rechazaRAUD06() {
        // La constancia guarda quien cerro y cuando: un cierre sin responsable no se le
        // puede atribuir a nadie ante el regulador.
        UUID enero = periodo(1);
        var cierre = transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.cierre_periodo_contable
                         WHERE id = ? AND cerrado_por IS NOT NULL AND cerrado_en IS NOT NULL
                        """,
                        cierre.cierreId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-CTB-01")
    void rechazaRCTB01() {
        // Un periodo por ejercicio y mes, y nada se asienta en uno cerrado.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO erp.periodo_contable (ejercicio_fiscal_id, mes, fecha_inicio, fecha_fin, estado)
                        VALUES (?, 1, current_date, current_date + 30, 'ABIERTO')
                        """,
                        ejercicioId))
                .contains("uq_periodo_contable_ejercicio_mes");

        UUID enero = periodo(1);
        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), ctx));
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.asiento_contable
                            (numero, fecha, glosa, origen_tipo, origen_id, periodo_contable_id, estado, registrado_por)
                        VALUES (?, current_date, 'Tardio', 'AJUSTE', gen_random_uuid(), ?, 'CONFIRMADO', ?)
                        """,
                        (long) (800_000 + anio),
                        enero,
                        ctx.usuarioId()))
                .contains("R-CTB-01");

        // Y un cierre con debe distinto de haber no se puede guardar: si no cuadra, no
        // es un cierre.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO erp.cierre_periodo_contable
                            (periodo_contable_id, cerrado_en, cerrado_por, total_debe, total_haber)
                        VALUES (?, now(), ?, 100, 90)
                        """,
                        periodo(2),
                        ctx.usuarioId()))
                .contains("ck_cierre_periodo_cuadrado");
    }
}
