package bo.aportaya.nucleofinanciero.dominio;

import static bo.aportaya.plataforma.dominio.Moneda.BOB;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.List;
import java.util.Map;

/**
 * CU-24 · el átomo del cuadre: {@code SUM(debe) = SUM(haber)}.
 *
 * <p>La base lo hace cumplir con {@code tg_asiento_cuadrado} (R-AUD-05), diferido al
 * {@code COMMIT}. Esta verificación no reemplaza al trigger — lo adelanta, para
 * devolver {@code AP-CU24-01} en vez de esperar a que la transacción entera falle.
 */
public final class CuadrarPartidas {

    private CuadrarPartidas() {}

    /** Verifica el cuadre y devuelve el total de cada lado, ya normalizado a dos decimales. */
    public static Totales verificar(List<Partida> partidas) {
        if (partidas == null || partidas.size() < 2) {
            throw new ErrorDeNegocio(
                    CodigoError.de(24, 1), "Un asiento necesita al menos dos partidas para poder cuadrar.");
        }

        Dinero totalDebe = Dinero.cero(BOB);
        Dinero totalHaber = Dinero.cero(BOB);
        for (Partida p : partidas) {
            totalDebe = totalDebe.mas(Dinero.de(p.debe(), BOB));
            totalHaber = totalHaber.mas(Dinero.de(p.haber(), BOB));
        }

        if (!totalDebe.equals(totalHaber)) {
            throw new ErrorDeNegocio(
                    CodigoError.de(24, 1),
                    "El asiento no cuadra: el debe y el haber no coinciden.",
                    Map.of("totalDebe", totalDebe.toString(), "totalHaber", totalHaber.toString()));
        }

        return new Totales(totalDebe, totalHaber);
    }

    public record Totales(Dinero debe, Dinero haber) {}
}
