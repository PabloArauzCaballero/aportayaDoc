package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.tarifas.aplicacion.CU35CerrarLiquidacion.EntradaLiquidacion;
import bo.aportaya.tarifas.aplicacion.CU35CerrarLiquidacion.SalidaLiquidacion;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-35 · Cerrar la liquidacion mensual de ingresos. */
class CU35Test extends BaseDeTarifas {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Un periodo propio por prueba.
     *
     * <p>{@code liquidacion_ingresos.periodo} es unico y los devengos son append-only:
     * si dos pruebas usaran el mismo mes, la segunda contaria los devengos de la
     * primera y el fallo diria algo que no es.
     */
    private String periodoDe(int indice) {
        return "20%02d-%02d".formatted(10 + indice / 12, indice % 12 + 1);
    }

    /** Devenga y cobra `cuantos` por Bs 18 en el periodo. Devuelve lo cobrado. */
    private BigDecimal cobrados(String periodo, int cuantos) {
        UUID tarifario = fixtura.tarifarioVigente("TAR-" + corto());
        UUID hecho = fixtura.hechoGenerador("ENTREGA-" + corto());
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + corto(), "0.01", "BANCARIO");
        UUID concepto = fixtura.conceptoPorcentual(
                tarifario, hecho, redondeo, fixtura.cuentaDeIngreso(), "COM-SERV", "0.0030", null, null, false, false);
        fixtura.activar(tarifario);
        for (int i = 0; i < cuantos; i++) {
            fixtura.devengoCobrado(concepto, tarifario, fixtura.usuario(), "18.00", periodo);
        }
        return new BigDecimal("18.00").multiply(BigDecimal.valueOf(cuantos));
    }

    @Test
    @DisplayName(
            "Dado un mes con todos los cierres diarios cuadrados · Cuando se cierra la liquidación · Entonces total_cobrado coincide con el saldo de la cuenta de ingresos")
    void criterio1() {
        String periodo = periodoDe(1);
        BigDecimal cobrado = cobrados(periodo, 3);
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        SalidaLiquidacion salida =
                transaccion.execute(t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 0, 0, cobrado), ctx));

        assertThat(salida.cuadraContraMayor()).isTrue();
        assertThat(salida.totalCobrado()).isEqualByComparingTo(cobrado);
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.liquidacion_ingresos WHERE periodo = ? AND estado = 'CERRADA'",
                        periodo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una diferencia entre la liquidación y el mayor · Cuando se intenta cerrar · Entonces el cierre se rechaza y queda un hallazgo abierto")
    void criterio2() {
        String periodo = periodoDe(2);
        BigDecimal cobrado = cobrados(periodo, 2);
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(t -> liquidacionCU.cerrar(
                        new EntradaLiquidacion(periodo, 0, 0, cobrado.add(new BigDecimal("0.01"))), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no cuadra contra el mayor");

        // El mes queda ABIERTO: firmar un total que no cuadra es peor que no cerrar.
        assertThat(contar("SELECT count(*)::int FROM tarifas.liquidacion_ingresos WHERE periodo = ?", periodo))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un mes con un cierre diario faltante · Cuando se intenta cerrar la liquidación · Entonces el cierre se rechaza y el mes queda abierto")
    void criterio3() {
        String periodo = periodoDe(3);
        BigDecimal cobrado = cobrados(periodo, 1);
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        // La liquidacion mensual se APOYA en dias cuadrados, no los reemplaza.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 1, 0, cobrado), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin cerrar");
        assertThat(contar("SELECT count(*)::int FROM tarifas.liquidacion_ingresos WHERE periodo = ?", periodo))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dada una liquidación mensual ya cerrada · Cuando se reintenta el cierre con la misma clave de idempotencia · Entonces se devuelve la liquidación existente y no se duplican asientos")
    void criterio4() {
        String periodo = periodoDe(4);
        BigDecimal cobrado = cobrados(periodo, 2);
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        SalidaLiquidacion primera =
                transaccion.execute(t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 0, 0, cobrado), ctx));
        SalidaLiquidacion segunda =
                transaccion.execute(t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 0, 0, cobrado), ctx));

        assertThat(segunda.liquidacionId()).isEqualTo(primera.liquidacionId());
        assertThat(segunda.yaExistia()).isTrue();
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "tarifas.liquidacion_cerrada",
                        primera.liquidacionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La clave del cierre es el PERIODO: el planificador reintenta y no puede
        // haber dos liquidaciones del mismo mes.
        String periodo = periodoDe(5);
        BigDecimal cobrado = cobrados(periodo, 1);
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        var a = transaccion.execute(t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 0, 0, cobrado), ctx));
        var b = transaccion.execute(t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 0, 0, cobrado), ctx));

        assertThat(b.liquidacionId()).isEqualTo(a.liquidacionId());
        assertThat(contar("SELECT count(*)::int FROM tarifas.liquidacion_ingresos WHERE periodo = ?", periodo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos cierres del mismo mes: la BASE decide. Con dos liquidaciones del mismo
        // periodo no hay forma de saber cual es el resultado del mes.
        String periodo = periodoDe(6);
        BigDecimal cobrado = cobrados(periodo, 1);
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 0, 0, cobrado), ctx));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO tarifas.liquidacion_ingresos
                            (id, periodo, fecha_inicio, fecha_fin, total_devengado, total_cobrado,
                             total_exonerado, total_devuelto, total_incobrable, total_impuestos,
                             total_costo_proveedores, cantidad_operaciones, estado)
                        VALUES (gen_random_uuid(), '%s', current_date, current_date, 0, 0, 0, 0, 0, 0, 0, 0,
                                'CERRADA')
                        """
                                .formatted(periodo)))
                .contains("uq_liquidacion_ingresos_periodo");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El ingreso neto es lo cobrado menos lo devuelto, los impuestos y el costo de
        // los proveedores. Sin esa resta, «ganamos X» cuenta plata que ya salio.
        String periodo = periodoDe(7);
        BigDecimal cobrado = cobrados(periodo, 4);
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        SalidaLiquidacion salida =
                transaccion.execute(t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 0, 0, cobrado), ctx));

        assertThat(salida.consolidado().operaciones()).isEqualTo(4);
        assertThat(salida.consolidado().cobrado()).isEqualByComparingTo(cobrado);
        assertThat(salida.ingresoNeto())
                .isEqualByComparingTo(cobrado.subtract(salida.consolidado().devuelto())
                        .subtract(salida.consolidado().impuestos()));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "liquidador"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "liquidador"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Con excepciones de conciliacion abiertas no se cierra, y no queda nada a
        // medias: ni liquidacion ni asiento de cierre.
        String periodo = periodoDe(8);
        BigDecimal cobrado = cobrados(periodo, 2);
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(
                        t -> liquidacionCU.cerrar(new EntradaLiquidacion(periodo, 0, 3, cobrado), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("excepcion(es) de conciliacion");
        assertThat(contar("SELECT count(*)::int FROM tarifas.liquidacion_ingresos WHERE periodo = ?", periodo))
                .isZero();
    }
}
