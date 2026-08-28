package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.tarifas.aplicacion.CU35CerrarLiquidacion.EntradaLiquidacion;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-35 · las pruebas de RECHAZO, una por restriccion citada. */
class CU35RechazosTest extends BaseDeTarifas {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String periodoDe(int indice) {
        return "21%02d-%02d".formatted(indice / 12, indice % 12 + 1);
    }

    private BigDecimal cobrados(String periodo, int cuantos) {
        UUID tarifario = fixtura.tarifarioVigente("TAR-" + corto());
        UUID hecho = fixtura.hechoGenerador("ENTREGA-" + corto());
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + corto(), "0.01", "BANCARIO");
        UUID concepto = fixtura.conceptoPorcentual(
                tarifario,
                hecho,
                redondeo,
                facturacion.cuentaDeIngreso(),
                "COM-SERV",
                "0.0030",
                null,
                null,
                false,
                false);
        fixtura.activar(tarifario);
        for (int i = 0; i < cuantos; i++) {
            facturacion.devengoCobrado(concepto, tarifario, fixtura.usuario(), "18.00", periodo);
        }
        return new BigDecimal("18.00").multiply(BigDecimal.valueOf(cuantos));
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // La liquidacion tiene que cuadrar contra el mayor. Si no cuadra NO se cierra:
        // un cierre que no cuadra es un cierre que alguien va a tener que explicar sin
        // datos, y para entonces el mes ya paso.
        String periodo = periodoDe(1);
        BigDecimal cobrado = cobrados(periodo, 2);
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(t -> liquidacionCU.cerrar(
                        new EntradaLiquidacion(periodo, 0, 0, cobrado.subtract(BigDecimal.ONE)), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no cuadra contra el mayor");
        assertThat(contar("SELECT count(*)::int FROM tarifas.liquidacion_ingresos WHERE periodo = ?", periodo))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-AUD-06")
    void rechazaRAUD06() {
        // Un periodo cerrado no se reescribe: se reabre con autorizacion registrada.
        // Reintentar el cierre devuelve el que hay y no duplica el asiento.
        String periodo = periodoDe(2);
        BigDecimal cobrado = cobrados(periodo, 1);
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        var primera =
                transaccion.execute(t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 0, 0, cobrado), ctx));

        var segunda =
                transaccion.execute(t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 0, 0, cobrado), ctx));

        assertThat(segunda.yaExistia()).isTrue();
        assertThat(segunda.liquidacionId()).isEqualTo(primera.liquidacionId());
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "tarifas.liquidacion_cerrada",
                        primera.liquidacionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-12")
    void rechazaRBIL12() {
        // No se cierra con dias abiertos ni con excepciones pendientes. La liquidacion
        // mensual se APOYA en dias cuadrados; no los reemplaza.
        String periodo = periodoDe(3);
        BigDecimal cobrado = cobrados(periodo, 1);
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(
                        t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 2, 0, cobrado), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin cerrar");
        assertThatThrownBy(() -> transaccion.execute(
                        t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 0, 1, cobrado), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("excepcion(es) de conciliacion");
        assertThat(contar("SELECT count(*)::int FROM tarifas.liquidacion_ingresos WHERE periodo = ?", periodo))
                .isZero();
    }
}
