package bo.aportaya.plataforma.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reparte un importe sin perder el residuo.
 *
 * <p>El centavo que sobra existe siempre: repartir 100,00 entre tres da 33,33 tres
 * veces y falta uno. La pregunta no es si aparece sino a quien le toca, y aca la
 * respuesta es deliberada: se reparte por TOTAL ACUMULADO, de modo que cada parte es
 * la diferencia entre lo que corresponde hasta ella y lo ya asignado. El centavo cae
 * donde el redondeo acumulado lo pone —de forma determinista, no arbitraria—, la suma
 * iguala el total exactamente, y ninguna parte se aleja mas de un centavo de su
 * proporcion justa.
 *
 * <p>Redondear cada parte por separado y volcar el residuo en una sola no cumple lo
 * segundo: 90,00 entre tres daria 30,02 · 29,99 · 29,99. Cuadra, y aun asi esta mal.
 */
public final class Prorrateo {

    private Prorrateo() {}

    /** En partes iguales, con el centavo sobrante repartido por el redondeo acumulado. */
    public static List<Dinero> enPartesIguales(Dinero total, int partes) {
        if (partes <= 0) {
            throw new ErrorDeDominio("No se reparte entre %d partes".formatted(partes));
        }
        return porPesos(total, java.util.Collections.nCopies(partes, BigDecimal.ONE));
    }

    /** Proporcional a los pesos dados. La suma de las partes iguala el total, siempre. */
    public static List<Dinero> porPesos(Dinero total, List<BigDecimal> pesos) {
        Objects.requireNonNull(total, "total");
        Objects.requireNonNull(pesos, "pesos");
        if (pesos.isEmpty()) {
            throw new ErrorDeDominio("No se reparte sin partes");
        }
        BigDecimal suma = pesos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.signum() <= 0) {
            throw new ErrorDeDominio("Los pesos de un prorrateo suman cero o menos");
        }

        List<Dinero> partes = new ArrayList<>(pesos.size());
        BigDecimal acumulado = BigDecimal.ZERO;
        Dinero asignado = Dinero.cero(total.moneda());
        for (BigDecimal peso : pesos) {
            acumulado = acumulado.add(peso);
            // Lo que corresponde HASTA esta parte, en una sola division exacta.
            BigDecimal hasta = total.monto().multiply(acumulado).divide(suma, Dinero.ESCALA, RoundingMode.HALF_UP);
            Dinero corte = Dinero.de(hasta, total.moneda());
            partes.add(corte.menos(asignado));
            asignado = corte;
        }
        return List.copyOf(partes);
    }
}
