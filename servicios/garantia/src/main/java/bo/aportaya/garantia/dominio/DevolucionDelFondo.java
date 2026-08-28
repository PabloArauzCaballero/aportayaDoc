package bo.aportaya.garantia.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Cuanto le vuelve a cada uno cuando el fondo se cierra.
 *
 * <p>**No se devuelve mas de lo aportado ni un importe negativo** (R-GAR-06). Y lo que
 * el fondo gasto en cubrir incumplimientos lo pierden **todos en proporcion**: esa es
 * la idea de un fondo mutual. Descontarselo solo a quien incumplio seria una deuda, no
 * una garantia — y para eso ya esta la subrogacion.
 *
 * <p>El remanente se reparte por lo aportado, y el redondeo **se ajusta en la ultima
 * devolucion** para que la suma cierre exacta contra el saldo. Sin eso, repartir entre
 * tres deja centavos flotando que nadie sabe de quien son.
 */
public final class DevolucionDelFondo {

    private DevolucionDelFondo() {}

    public record Aportante(java.util.UUID participanteId, Dinero aportado) {}

    public record Devolucion(java.util.UUID participanteId, Dinero aportado, Dinero aDevolver) {}

    public record Reparto(Dinero saldoARepartir, Dinero totalAportado, List<Devolucion> devoluciones) {}

    public static Reparto repartir(Dinero saldoDisponible, List<Aportante> aportantes) {
        if (aportantes.isEmpty()) {
            throw new ErrorDeNegocio(CodigoError.de(29, 3), "No hay aportantes a quienes devolver.");
        }
        var moneda = saldoDisponible.moneda();
        Dinero totalAportado = aportantes.stream().map(Aportante::aportado).reduce(Dinero.cero(moneda), Dinero::mas);

        if (totalAportado.monto().signum() == 0) {
            throw new ErrorDeNegocio(CodigoError.de(29, 3), "El total aportado es cero: no hay que devolver.");
        }

        var devoluciones = new java.util.ArrayList<Devolucion>();
        Dinero repartido = Dinero.cero(moneda);

        for (int i = 0; i < aportantes.size(); i++) {
            var aportante = aportantes.get(i);
            Dinero aDevolver;
            if (i == aportantes.size() - 1) {
                // El ultimo se lleva el remanente exacto: asi la suma cierra contra el
                // saldo y no quedan centavos sin dueno.
                aDevolver = saldoDisponible.menos(repartido);
            } else {
                BigDecimal proporcion = aportante
                        .aportado()
                        .monto()
                        .multiply(saldoDisponible.monto())
                        .divide(totalAportado.monto(), 2, RoundingMode.DOWN);
                aDevolver = Dinero.de(proporcion, moneda);
                repartido = repartido.mas(aDevolver);
            }
            // R-GAR-06: nunca mas de lo aportado, nunca negativo.
            if (aDevolver.esMayorQue(aportante.aportado())) {
                aDevolver = aportante.aportado();
            }
            if (aDevolver.monto().signum() < 0) {
                aDevolver = Dinero.cero(moneda);
            }
            devoluciones.add(new Devolucion(aportante.participanteId(), aportante.aportado(), aDevolver));
        }

        return new Reparto(saldoDisponible, totalAportado, List.copyOf(devoluciones));
    }
}
