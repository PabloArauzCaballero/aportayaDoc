package bo.aportaya.organizador.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Que tan bien lo esta haciendo un organizador.
 *
 * <p>El puntaje se calcula con **pesos declarados**, no con una formula escondida en
 * el codigo. Si un organizador va a perder su habilitacion por un numero, ese numero
 * tiene que poder explicarse metrica por metrica — «el sistema lo calculo» no es una
 * respuesta ante una apelacion.
 *
 * <p>Cada metrica guarda su valor, su meta y su peso. Por eso se devuelven las tres,
 * y no solo el total.
 */
public final class PuntajeDeDesempeno {

    private PuntajeDeDesempeno() {}

    /**
     * @param mayorEsMejor false para las metricas donde conviene el numero bajo
     *     (morosidad, tiempo de respuesta): sin esto, un organizador con la cartera
     *     sana puntuaria como uno con la cartera rota
     */
    public record Metrica(String codigo, BigDecimal valor, BigDecimal meta, BigDecimal peso, boolean mayorEsMejor) {

        public boolean cumple() {
            return mayorEsMejor ? valor.compareTo(meta) >= 0 : valor.compareTo(meta) <= 0;
        }
    }

    public record Resultado(BigDecimal puntajeGlobal, List<Metrica> metricas, int cumplidas) {}

    public static Resultado calcular(List<Metrica> metricas) {
        if (metricas.isEmpty()) {
            // Sin metricas no hay evaluacion. Devolver cero seria decir que lo hizo
            // pesimo, cuando lo cierto es que no se sabe.
            throw new IllegalArgumentException("Una evaluacion sin metricas no es una evaluacion");
        }

        BigDecimal sumaDePesos = metricas.stream().map(Metrica::peso).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal acumulado = BigDecimal.ZERO;

        for (Metrica metrica : metricas) {
            // Cuanto de la meta se cumplio, entre 0 y 1. Superar la meta no da mas de
            // 1: un mes excelente en una metrica no compensa un desastre en otra.
            BigDecimal razon = metrica.mayorEsMejor()
                    ? proporcion(metrica.valor(), metrica.meta())
                    : proporcion(metrica.meta(), maximo(metrica.valor(), metrica.meta()));
            acumulado = acumulado.add(razon.multiply(metrica.peso()));
        }

        BigDecimal global = acumulado
                .divide(sumaDePesos, 6, RoundingMode.HALF_EVEN)
                // Una fraccion se pasa a porcentaje corriendo la coma, no multiplicando
                // por un cien escrito a mano: 100 no es un umbral de negocio.
                .movePointRight(2)
                .setScale(2, RoundingMode.HALF_EVEN);

        int cumplidas = (int) metricas.stream().filter(Metrica::cumple).count();
        return new Resultado(global, List.copyOf(metricas), cumplidas);
    }

    private static BigDecimal proporcion(BigDecimal numerador, BigDecimal denominador) {
        if (denominador.signum() == 0) {
            return BigDecimal.ONE;
        }
        BigDecimal razon = numerador.divide(denominador, 6, RoundingMode.HALF_EVEN);
        return razon.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : razon.max(BigDecimal.ZERO);
    }

    private static BigDecimal maximo(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
