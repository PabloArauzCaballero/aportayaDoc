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

/** CU-106 · Lo que la base y el caso de uso rechazan. */
class CU106RechazosTest extends BaseDeErp {

    private static final AtomicInteger ANIO = new AtomicInteger(3000);

    private int anio;
    private UUID ejercicioId;
    private UUID activo;
    private UUID pasivo;
    private UUID patrimonio;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        anio = ANIO.incrementAndGet();
        ctx = contextoDe(fixtura.usuario());
        ejercicioId =
                transaccion.execute(t -> periodoCU.abrirEjercicio(anio, ctx)).ejercicioId();
        activo = fixtura.cuenta("1302-" + anio, "ACTIVO", "DEUDORA");
        pasivo = fixtura.cuenta("2302-" + anio, "PASIVO", "ACREEDORA");
        patrimonio = fixtura.cuenta("3302-" + anio, "PATRIMONIO", "ACREEDORA");
    }

    private UUID periodo(int mes) {
        return dsl.fetchOne(
                        "SELECT id FROM erp.periodo_contable WHERE ejercicio_fiscal_id = ? AND mes = ?",
                        ejercicioId,
                        (short) mes)
                .get(0, UUID.class);
    }

    private UUID mesCerrado(int mes) {
        UUID p = periodo(mes);
        fixtura.asiento(p, activo, pasivo, "6000.00", ctx.usuarioId());
        fixtura.asiento(p, activo, patrimonio, "4000.00", ctx.usuarioId());
        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(p, "Cierre"), ctx));
        return p;
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        UUID enero = mesCerrado(1);
        var estado = transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx));

        // El estado publicado es append-only: su hash no se reescribe ni se borra. Un
        // balance que se puede retocar despues de emitido no vale como constancia.
        assertThat(rechazaLaBase(
                        "UPDATE erp.estado_financiero_generado SET hash_contenido = 'otro' WHERE id = ?",
                        estado.estadoId()))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM erp.estado_financiero_generado WHERE id = ?", estado.estadoId()))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        UUID enero = mesCerrado(1);
        var estado = transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx));

        // El evento sale con el hash del contenido: quien lo reciba puede comprobar que
        // el balance que tiene es el que se emitio.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.estado_financiero_generado' AND agregado_id = ?
                           AND payload->>'hashContenido' = ?
                        """,
                        estado.estadoId(),
                        estado.hashContenido()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-CTB-02")
    void rechazaRCTB02() {
        // Una sumarizadora es un total, no un destino: imputarle un asiento rompe la
        // suma de sus hijas.
        UUID sumarizadora = fixtura.cuentaSumarizadora("1000-" + anio, "ACTIVO", "DEUDORA");
        UUID plantilla = fixtura.plantillaDeAsiento("PL-R02-" + anio, sumarizadora, pasivo, ctx.usuarioId());

        assertThatThrownBy(() -> transaccion.execute(t -> estadoCU.validarPlantilla(plantilla, ctx)))
                .satisfies(e -> assertThat(raizDe(e)).contains("R-CTB-02"));

        UUID enero = mesCerrado(1);
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.movimiento_contable (asiento_id, cuenta_id, debe, haber, descripcion)
                        SELECT a.id, ?, 100, 0, 'Contra sumarizadora'
                          FROM nucleo_financiero.asiento_contable a
                         WHERE a.periodo_contable_id = ? LIMIT 1
                        """,
                        sumarizadora,
                        enero))
                .contains("R-CTB-02");
    }

    @Test
    @DisplayName("rechaza por R-CTB-08")
    void rechazaRCTB08() {
        UUID enero = mesCerrado(1);
        var primero = transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx));

        // Un estado por periodo y tipo: dos balances del mismo mes son dos verdades, y
        // el regulador se queda con la que le convenga a quien la presente.
        assertThatThrownBy(() -> transaccion.execute(t -> estadoCU.generar(enero, "BALANCE_GENERAL", ctx)))
                .satisfies(e -> assertThat(raizDe(e)).contains("Ya existe un BALANCE_GENERAL"));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO erp.estado_financiero_generado
                            (periodo_contable_id, tipo, generado_en, generado_por, hash_contenido, datos)
                        VALUES (?, 'BALANCE_GENERAL', now(), ?, 'clon', '{}'::jsonb)
                        """,
                        enero,
                        ctx.usuarioId()))
                .contains("uq_estado_financiero_periodo_tipo");
        assertThat(primero.hashContenido()).isNotBlank();
    }
}
