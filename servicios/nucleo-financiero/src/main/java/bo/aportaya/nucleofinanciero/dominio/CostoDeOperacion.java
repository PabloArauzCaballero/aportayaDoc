package bo.aportaya.nucleofinanciero.dominio;

import bo.aportaya.plataforma.dominio.Dinero;
import java.math.RoundingMode;

/**
 * CU-10 y CU-11 · Lo que queda despues del costo. Puro.
 *
 * <p>El redondeo va **declarado**, nunca por omision: {@code HALF_UP} en contra de
 * quien cobra y a favor de quien recibe. Dejarlo implicito significa que la moneda se
 * parte distinto segun la version de la biblioteca, y eso en un libro contable es un
 * descuadre que aparece meses despues.
 */
public final class CostoDeOperacion {

    private CostoDeOperacion() {}

    /** Lo que se acredita al recargar: bruto menos lo que se lleva el proveedor. */
    public static Dinero acreditacion(Dinero bruto, Dinero costoProveedor) {
        if (costoProveedor.esMayorQue(bruto) || costoProveedor.equals(bruto)) {
            throw new IllegalArgumentException("El costo del proveedor no puede llevarse la recarga entera: bruto "
                    + bruto + ", costo " + costoProveedor);
        }
        return bruto.menos(costoProveedor);
    }

    /**
     * Lo que sale al retirar: el neto que recibe la persona.
     *
     * <p>El costo lo paga quien retira, asi que se descuenta del monto pedido: pedir
     * 100 con costo 5 deja 95 en la cuenta destino y saca 100 de la billetera. Cobrarlo
     * aparte sacaria 105 sin que la persona lo haya pedido.
     */
    public static Dinero netoDeRetiro(Dinero solicitado, Dinero costo) {
        if (!costo.esMenorQue(solicitado)) {
            throw new IllegalArgumentException(
                    "El costo del retiro no puede igualar ni superar el monto: " + solicitado + " vs " + costo);
        }
        return solicitado.menos(costo);
    }

    /** Un costo porcentual, con su redondeo dicho en voz alta. */
    public static Dinero porcentual(Dinero base, java.math.BigDecimal porcentaje) {
        return base.por(porcentaje, RoundingMode.HALF_UP);
    }
}
