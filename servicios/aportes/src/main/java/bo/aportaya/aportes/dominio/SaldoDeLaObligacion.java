package bo.aportaya.aportes.dominio;

import bo.aportaya.plataforma.dominio.Dinero;

/**
 * CU-21 y CU-19 · Cuanto falta pagar de una obligacion. Puro.
 *
 * <p>Lo pagado, lo condonado y lo cubierto por la garantia **cuentan igual** contra el
 * esperado: para el grupo el periodo esta cubierto, venga la plata de donde venga. Lo
 * que cambia es quien queda debiendo despues, y eso es otro asunto.
 */
public final class SaldoDeLaObligacion {

    private SaldoDeLaObligacion() {}

    public record Estado(Dinero esperado, Dinero pagado, Dinero condonado, Dinero cubiertoPorGarantia) {

        public Dinero cubierto() {
            return pagado.mas(condonado).mas(cubiertoPorGarantia);
        }

        public Dinero pendiente() {
            Dinero falta = esperado.menos(cubierto());
            // Nunca negativo: si se pago de mas, la obligacion esta cubierta y el
            // excedente es otro problema —una devolucion—, no un pendiente negativo
            // que confundiria todas las sumas de arriba.
            return falta.esNegativo() ? Dinero.cero(esperado.moneda()) : falta;
        }

        public boolean estaSaldada() {
            return pendiente().esCero();
        }
    }

    /**
     * El estado que corresponde escribir, segun lo que falta.
     *
     * <p>Los nombres son los de {@code ck_obligacion_aporte_estado}, no los que uno
     * escribiria de memoria: la base admite PAGADO_PARCIAL y rechaza PARCIAL.
     */
    public static String estadoSegunSaldo(Estado estado, boolean vencida) {
        if (estado.estaSaldada()) {
            return "PAGADO";
        }
        if (estado.cubierto().esCero()) {
            return vencida ? "EN_MORA" : "PENDIENTE";
        }
        return vencida ? "EN_MORA" : "PAGADO_PARCIAL";
    }
}
