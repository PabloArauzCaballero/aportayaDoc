package bo.aportaya.garantia.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.List;

/**
 * Que le toca a cada uno cuando un grupo se disuelve antes de terminar.
 *
 * <p>Disolver un pasanaku a mitad de camino es el peor momento posible: unos ya
 * cobraron su turno y otros no, y todos pusieron. **La liquidacion tiene que cuadrar al
 * centavo** — lo que hay es exactamente lo que se reparte — y quien ya cobro tiene que
 * quedar con menos, porque ya recibio.
 *
 * <p>La posicion de cada uno es <b>lo aportado menos lo recibido</b>. Positiva: le
 * deben. Negativa: debe. Repartir sin mirar eso le devolveria a quien ya cobro lo mismo
 * que a quien nunca cobro, y eso no es disolver: es premiar al que llego primero.
 */
public final class CuadreDeDisolucion {

    private CuadreDeDisolucion() {}

    public record Posicion(java.util.UUID participanteId, Dinero aportado, Dinero recibido) {

        /** Positiva si le deben, negativa si debe. */
        public Dinero neta() {
            return aportado.menos(recibido);
        }
    }

    public record Liquidacion(
            java.util.UUID participanteId, Dinero aportado, Dinero recibido, Dinero aDevolver, Dinero aCobrarle) {}

    public record Resultado(
            Dinero masaARepartir, Dinero totalADevolver, Dinero totalACobrar, List<Liquidacion> liquidaciones) {}

    public static Resultado liquidar(Dinero masaDisponible, List<Posicion> posiciones) {
        if (posiciones.isEmpty()) {
            throw new ErrorDeNegocio(CodigoError.de(67, 2), "No hay participantes que liquidar.");
        }
        var moneda = masaDisponible.moneda();

        var liquidaciones = new java.util.ArrayList<Liquidacion>();
        Dinero totalADevolver = Dinero.cero(moneda);
        Dinero totalACobrar = Dinero.cero(moneda);

        for (var posicion : posiciones) {
            Dinero neta = posicion.neta();
            if (neta.monto().signum() >= 0) {
                liquidaciones.add(new Liquidacion(
                        posicion.participanteId(),
                        posicion.aportado(),
                        posicion.recibido(),
                        neta,
                        Dinero.cero(moneda)));
                totalADevolver = totalADevolver.mas(neta);
            } else {
                // Quien recibio mas de lo que puso queda debiendo la diferencia. No se
                // la perdona el grupo: seria repartirla entre los que menos cobraron.
                Dinero debe = Dinero.cero(moneda).menos(neta);
                liquidaciones.add(new Liquidacion(
                        posicion.participanteId(),
                        posicion.aportado(),
                        posicion.recibido(),
                        Dinero.cero(moneda),
                        debe));
                totalACobrar = totalACobrar.mas(debe);
            }
        }

        // AP-CU67-03: la masa disponible tiene que alcanzar para lo que se debe
        // devolver. Si no alcanza, la disolucion NO procede: repartir de menos sin
        // decirlo es como se pierde la confianza de todos a la vez.
        if (totalADevolver.esMayorQue(masaDisponible.mas(totalACobrar))) {
            throw new ErrorDeNegocio(
                    CodigoError.de(67, 3),
                    "La disolucion no cuadra: hay que devolver " + totalADevolver + " y solo se dispone de "
                            + masaDisponible + " mas " + totalACobrar + " por cobrar.");
        }

        return new Resultado(masaDisponible, totalADevolver, totalACobrar, List.copyOf(liquidaciones));
    }
}
