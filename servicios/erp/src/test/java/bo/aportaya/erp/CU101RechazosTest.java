package bo.aportaya.erp;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.erp.aplicacion.CU101Presupuestar.EntradaPresupuesto;
import bo.aportaya.erp.aplicacion.CU101Presupuestar.Partida;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-101 · Lo que la base y el caso de uso rechazan. */
class CU101RechazosTest extends BaseDeErp {

    private static final AtomicInteger ANIO = new AtomicInteger(2700);

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
        centroId = fixtura.centroDeCosto("CC101R-" + anio, "AREA");
        cuentaGasto = fixtura.cuenta("5501-" + anio, "EGRESO", "DEUDORA");
    }

    private UUID periodo(int mes) {
        return dsl.fetchOne(
                        "SELECT id FROM erp.periodo_contable WHERE ejercicio_fiscal_id = ? AND mes = ?",
                        ejercicioId,
                        (short) mes)
                .get(0, UUID.class);
    }

    @Test
    @DisplayName("rechaza por R-CTB-03")
    void rechazaRCTB03() {
        var presupuesto = transaccion.execute(t -> presupuestoCU.crear(
                new EntradaPresupuesto(
                        centroId,
                        ejercicioId,
                        "Presupuesto " + anio,
                        "BOB",
                        List.of(new Partida(cuentaGasto, periodo(1), new BigDecimal("1000.00")))),
                ctx));

        // Un presupuesto por centro y ejercicio: dos vigentes dejan a cada area
        // eligiendo cual mirar.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO erp.presupuesto (centro_costo_id, ejercicio_fiscal_id, nombre, estado)
                        VALUES (?, ?, 'Duplicado', 'BORRADOR')
                        """,
                        centroId,
                        ejercicioId))
                .contains("uq_presupuesto_centro_ejercicio");

        // Una partida por presupuesto, cuenta y periodo: dos sumarian el doble sin que
        // se note en el total.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO erp.partida_presupuestaria
                            (presupuesto_id, cuenta_contable_id, periodo_contable_id, monto_presupuestado, moneda)
                        VALUES (?, ?, ?, 500, 'BOB')
                        """,
                        presupuesto.presupuestoId(),
                        cuentaGasto,
                        periodo(1)))
                .contains("uq_partida_presupuesto_cuenta_periodo");

        // Y aprobado exige firma y fecha: un presupuesto aprobado sin saber por quien no
        // compromete a nadie.
        assertThat(rechazaLaBase(
                        "UPDATE erp.presupuesto SET estado = 'APROBADO' WHERE id = ?", presupuesto.presupuestoId()))
                .contains("ck_presupuesto_aprobacion");

        // Y una partida en cero no es un presupuesto: es no haber presupuestado.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO erp.partida_presupuestaria
                            (presupuesto_id, cuenta_contable_id, periodo_contable_id, monto_presupuestado, moneda)
                        VALUES (?, ?, ?, 0, 'BOB')
                        """,
                        presupuesto.presupuestoId(),
                        cuentaGasto,
                        periodo(2)))
                .contains("ck_partida_presupuestaria_monto_presupuestado");
    }
}
