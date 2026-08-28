package bo.aportaya.transparencia.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * El puntaje de una persona, y **de que esta hecho**.
 *
 * <p>Un numero que decide si alguien entra a un grupo, cuanto puede administrar o si se
 * lo restringe, tiene que poder abrirse factor por factor. «El sistema lo calculo» no
 * es una respuesta que alguien pueda apelar, y R-REP-03 lo dice sin rodeos: **el total
 * es la suma de sus componentes**.
 *
 * <p>Puro: sin Spring, sin jOOQ, sin reloj. Los pesos y topes son catalogo (invariante
 * 10) y llegan como datos.
 */
public final class PuntajeDeReputacion {

    /**
     * El codigo del componente que lleva el puntaje de arranque del modelo.
     *
     * <p>No esta en {@code peso_factor} porque no es un factor medido: es la base desde
     * la que se mide. {@code componente_score.codigo_factor} no tiene catalogo cerrado
     * ni clave foranea, asi que la base lo admite.
     */
    public static final String BASE = "PUNTAJE_BASE";

    /** El componente que absorbe el recorte contra el piso o el techo del modelo. */
    public static final String AJUSTE = "AJUSTE_POR_LIMITE";

    private PuntajeDeReputacion() {}

    /**
     * Un factor del modelo, tal como lo aplica el calculo.
     *
     * @param topeDeAporte cuanto puede sumar (o restar) este factor como maximo. Sin
     *     tope, un solo factor puede decidir el puntaje entero y los demas dejan de
     *     importar
     * @param esPenalizador si resta en vez de sumar
     */
    public record Factor(String codigo, BigDecimal peso, BigDecimal topeDeAporte, boolean esPenalizador) {}

    /** Lo medido para un factor, sin normalizar. */
    public record Medicion(String codigo, BigDecimal valorCrudo, BigDecimal valorNormalizado) {}

    public record Componente(
            String codigo,
            BigDecimal valorCrudo,
            BigDecimal valorNormalizado,
            BigDecimal contribucion,
            String tendencia) {}

    public record Resultado(BigDecimal puntaje, List<Componente> componentes, String nivelDeConfianza) {}

    /**
     * @param eventosConsiderados si no llegan al minimo del modelo, el puntaje no se
     *     calcula: un score sobre dos hechos no dice nada de nadie
     */
    public static Resultado calcular(
            BigDecimal puntajeBase,
            BigDecimal minimo,
            BigDecimal maximo,
            int minimoDeEventos,
            int eventosConsiderados,
            List<Factor> factores,
            List<Medicion> mediciones,
            List<Medicion> medicionesAnteriores,
            List<Corte> escala) {

        // El punto de partida entra como componente, no como un numero que aparece de
        // la nada. R-REP-03 pide que el total sea la suma de sus partes, y el arranque
        // es una parte: sin el, la explicacion del puntaje empieza con 500 puntos que
        // nadie puede justificar.
        Componente base = new Componente(
                BASE,
                puntajeBase,
                BigDecimal.ONE.setScale(4),
                puntajeBase.setScale(2, RoundingMode.HALF_EVEN),
                "ESTABLE");

        if (eventosConsiderados < minimoDeEventos) {
            // SIN_HISTORIAL no es un castigo: es decir la verdad. Poner un puntaje bajo
            // a quien todavia no tiene historial lo trata como si hubiera fallado.
            return new Resultado(puntajeBase.setScale(2, RoundingMode.HALF_EVEN), List.of(base), "SIN_HISTORIAL");
        }

        List<Componente> componentes = new ArrayList<>();
        componentes.add(base);
        BigDecimal acumulado = puntajeBase;

        for (Factor factor : factores) {
            var medicion = mediciones.stream()
                    .filter(m -> m.codigo().equals(factor.codigo()))
                    .findFirst();
            if (medicion.isEmpty()) {
                // Un factor sin medicion NO aporta. Suponerle un valor medio le
                // inventaria historial a quien no lo tiene.
                continue;
            }
            // El valor normalizado viene en 0..1 y el peso tambien; llevarlo a puntos
            // es correr la coma dos lugares, no aplicar un umbral.
            BigDecimal bruto = medicion.get()
                    .valorNormalizado()
                    .multiply(factor.peso())
                    .movePointRight(2)
                    .setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal acotado = bruto.abs().compareTo(factor.topeDeAporte()) > 0 ? factor.topeDeAporte() : bruto.abs();
            BigDecimal contribucion = factor.esPenalizador() ? acotado.negate() : acotado;

            acumulado = acumulado.add(contribucion);
            componentes.add(new Componente(
                    factor.codigo(),
                    medicion.get().valorCrudo(),
                    medicion.get().valorNormalizado(),
                    contribucion,
                    tendenciaDe(medicion.get(), medicionesAnteriores)));
        }

        BigDecimal puntaje = acumulado.max(minimo).min(maximo).setScale(2, RoundingMode.HALF_EVEN);
        if (puntaje.compareTo(acumulado.setScale(2, RoundingMode.HALF_EVEN)) != 0) {
            // El recorte por piso o techo tambien se explica: si el total quedara
            // recortado y los componentes sumaran otra cosa, R-REP-03 lo rechazaria y,
            // peor, el puntaje dejaria de poder abrirse. El ajuste es un componente
            // mas, con su signo.
            componentes.add(new Componente(
                    AJUSTE,
                    acumulado.setScale(2, RoundingMode.HALF_EVEN),
                    BigDecimal.ZERO.setScale(4),
                    puntaje.subtract(acumulado.setScale(2, RoundingMode.HALF_EVEN)),
                    "ESTABLE"));
        }
        return new Resultado(puntaje, List.copyOf(componentes), nivelDe(puntaje, maximo, escala));
    }

    /**
     * Si el factor mejoro, empeoro o se mantuvo.
     *
     * <p>Ver la tendencia importa mas que el numero: alguien con 60 subiendo esta en
     * mejor situacion que alguien con 65 cayendo, y quien lo mire tiene que poder
     * distinguirlos.
     */
    private static String tendenciaDe(Medicion actual, List<Medicion> anteriores) {
        return anteriores.stream()
                .filter(m -> m.codigo().equals(actual.codigo()))
                .findFirst()
                .map(previa -> {
                    int comparacion = actual.valorNormalizado().compareTo(previa.valorNormalizado());
                    return comparacion > 0 ? "SUBE" : comparacion < 0 ? "BAJA" : "ESTABLE";
                })
                .orElse("ESTABLE");
    }

    /**
     * El nivel de confianza, contra la escala que llega como dato.
     *
     * <p>Los cortes NO estan escritos aca (invariante 10). Donde empieza «confiable» es
     * una decision de politica que cambia sin desplegar: subirla o bajarla decide a
     * quien se le abre un grupo, y esa palanca no puede vivir dentro de un {@code if}.
     */
    private static String nivelDe(BigDecimal puntaje, BigDecimal maximo, List<Corte> escala) {
        BigDecimal porcentaje = puntaje.movePointRight(2).divide(maximo, 2, RoundingMode.HALF_EVEN);
        for (Corte corte : escala) {
            if (porcentaje.compareTo(corte.porcentajeMinimo()) >= 0) {
                return corte.nivel();
            }
        }
        // Denegar por omision (invariante 9): sin corte que alcance, el nivel mas bajo.
        return "RESTRINGIDO";
    }

    /**
     * Un corte de la escala: de que porcentaje del maximo en adelante rige ese nivel.
     * La lista llega ordenada de mayor a menor y sus nombres son los siete que admite
     * {@code ck_puntaje_reputacion_nivel_confianza}.
     */
    public record Corte(String nivel, BigDecimal porcentajeMinimo) {}
}
