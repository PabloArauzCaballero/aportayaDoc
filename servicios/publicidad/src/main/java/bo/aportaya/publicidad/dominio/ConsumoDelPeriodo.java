package bo.aportaya.publicidad.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Lo que una cuenta publicitaria gasto en un mes: impresiones mas clics.
 *
 * <p>Las dos fuentes se guardan con cuatro decimales porque una impresion suelta cuesta
 * fracciones de centavo. La factura, en cambio, es dinero de verdad y va al centavo: el
 * redondeo se hace **una sola vez, sobre el total**. Redondear cada impresion y despues
 * sumar convertiria un error de milesimas en uno de bolivianos sobre millones de
 * entregas.
 */
public record ConsumoDelPeriodo(BigDecimal porImpresiones, BigDecimal porClics) {

    private static final int CENTAVOS = 2;

    public BigDecimal bruto() {
        return porImpresiones.add(porClics);
    }

    /** El total facturable, al centavo. */
    public BigDecimal aFacturar() {
        return bruto().setScale(CENTAVOS, RoundingMode.HALF_UP);
    }

    /** Cierto si no hubo consumo: no hay obligacion de facturar cero. */
    public boolean estaVacio() {
        return aFacturar().signum() <= 0;
    }
}
