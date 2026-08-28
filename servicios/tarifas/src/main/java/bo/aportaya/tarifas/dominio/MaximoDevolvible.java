package bo.aportaya.tarifas.dominio;

import bo.aportaya.plataforma.dominio.Dinero;

/**
 * Cuanto queda por devolver de un devengo.
 *
 * <p>Se mide contra **lo ya devuelto**, no contra lo cobrado a secas (R-TAR-11): dos
 * devoluciones parciales que juntas superan el cobro devuelven de mas, y la diferencia
 * sale del bolsillo de la plataforma sin que nadie la haya autorizado.
 */
public record MaximoDevolvible(Dinero cobrado, Dinero yaDevuelto) {

    public Dinero disponible() {
        return cobrado.menos(yaDevuelto);
    }

    public boolean admite(Dinero solicitado) {
        return !solicitado.esMayorQue(disponible());
    }
}
