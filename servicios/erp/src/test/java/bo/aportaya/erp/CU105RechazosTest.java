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

/** CU-105 · Lo que la base y el caso de uso rechazan. */
class CU105RechazosTest extends BaseDeErp {

    private static final AtomicInteger ANIO = new AtomicInteger(2900);

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
        UUID cuentaActivo = fixtura.cuenta("1202-" + anio, "ACTIVO", "DEUDORA");
        UUID cuentaDep = fixtura.cuenta("1292-" + anio, "ACTIVO", "ACREEDORA");
        UUID cuentaGasto = fixtura.cuenta("5202-" + anio, "EGRESO", "DEUDORA");
        categoriaId = fixtura.categoriaDeActivo("EQR-" + anio, 12, cuentaActivo, cuentaDep, cuentaGasto);
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
        UUID activo = fixtura.activo(categoriaId, "12000.00", "0.00", "0.00");
        var corrida = transaccion.execute(t -> depreciacionCU.depreciar(activo, periodo(1), ctx));

        // La cuota depreciada es append-only: no se reescribe ni se borra. Si se pudiera
        // retocar, el valor en libros de cualquier activo seria opinable.
        assertThat(rechazaLaBase("UPDATE erp.depreciacion_activo SET monto = 1 WHERE id = ?", corrida.depreciacionId()))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM erp.depreciacion_activo WHERE id = ?", corrida.depreciacionId()))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        UUID activo = fixtura.activo(categoriaId, "12000.00", "0.00", "0.00");
        var corrida = transaccion.execute(t -> depreciacionCU.depreciar(activo, periodo(1), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.activo_depreciado' AND agregado_id = ?
                           AND payload->>'activoId' = ? AND payload->>'monto' = '1000.00'
                        """,
                        corrida.depreciacionId(),
                        activo.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-06")
    void rechazaRAUD06() {
        UUID activo = fixtura.activo(categoriaId, "12000.00", "0.00", "0.00");
        var corrida = transaccion.execute(t -> depreciacionCU.depreciar(activo, periodo(1), ctx));

        // La cuota queda atada a su periodo y a su fecha de calculo: una depreciacion sin
        // periodo no se puede imputar a ningun mes.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.depreciacion_activo
                         WHERE id = ? AND periodo_contable_id = ? AND calculada_en IS NOT NULL
                        """,
                        corrida.depreciacionId(),
                        periodo(1)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-CTB-07")
    void rechazaRCTB07() {
        UUID activo = fixtura.activo(categoriaId, "12000.00", "0.00", "0.00");
        UUID enero = periodo(1);
        transaccion.execute(t -> depreciacionCU.depreciar(activo, enero, ctx));

        // Una corrida por activo y periodo: depreciar dos veces el mismo mes duplica el
        // gasto y hunde el valor en libros sin que se note en el mayor.
        assertThatThrownBy(() -> transaccion.execute(t -> depreciacionCU.depreciar(activo, enero, ctx)))
                .satisfies(e -> assertThat(raizDe(e)).contains("ya se deprecio en el periodo"));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO erp.depreciacion_activo
                            (activo_fijo_id, periodo_contable_id, monto, moneda, calculada_en)
                        VALUES (?, ?, 1000, 'BOB', now())
                        """,
                        activo,
                        enero))
                .contains("uq_depreciacion_activo_periodo");

        // Y la acumulada nunca pasa del costo menos el residual: mas alla de eso, el
        // activo estaria valiendo menos que nada.
        assertThat(rechazaLaBase(
                        "UPDATE erp.activo_fijo SET depreciacion_acumulada = costo_adquisicion + 1 WHERE id = ?",
                        activo))
                .contains("ck_activo_fijo_depreciacion");

        // Un periodo cerrado tampoco admite depreciaciones. Se cierra enero — los meses
        // se cierran en orden — y el activo siguiente ya no encuentra donde imputarse.
        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), ctx));
        UUID otro = fixtura.activo(categoriaId, "6000.00", "0.00", "0.00");
        assertThatThrownBy(() -> transaccion.execute(t -> depreciacionCU.depreciar(otro, enero, ctx)))
                .satisfies(e -> assertThat(raizDe(e)).contains("periodo esta cerrado"));
    }
}
