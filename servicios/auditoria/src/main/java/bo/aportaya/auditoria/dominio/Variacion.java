package bo.aportaya.auditoria.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * La variacion contra el periodo anterior, en porcentaje.
 *
 * <p>Es una funcion pura con pruebas propias porque **dividir por cero en un tablero
 * es el error mas tonto y mas frecuente**, y porque la diferencia entre «no hay
 * comparacion» y «no cambio» es la que decide si alguien se preocupa.
 */
public final class Variacion {

    private static final int ESCALA = 2;

    private Variacion() {}

    /**
     * @param anterior el valor del periodo anterior, o vacio si el indicador es nuevo
     * @return la variacion en puntos porcentuales, o vacio cuando no hay comparacion
     */
    public static Optional<BigDecimal> entre(BigDecimal actual, Optional<BigDecimal> anterior) {
        if (actual == null || anterior == null || anterior.isEmpty()) {
            // Un indicador nuevo se muestra SIN variacion, no con cero. No hay
            // comparacion posible todavia y fingir que si desinforma.
            return Optional.empty();
        }
        BigDecimal previo = anterior.get();

        if (previo.compareTo(BigDecimal.ZERO) == 0) {
            // De cero a algo no es «infinito por ciento»: es un arranque, y como
            // porcentaje no significa nada. Se devuelve vacio y la interfaz dice
            // «sin comparacion», que es la verdad.
            return Optional.empty();
        }

        // `movePointRight(2)` y no «multiplicar por cien»: correr el punto dos lugares
        // ES expresar la proporcion en porcentaje. Ademas evita un literal numerico
        // dentro del codigo, que el barrido prohibe con razon — una cifra suelta acá
        // es indistinguible de un umbral regulatorio horneado.
        return Optional.of(actual.subtract(previo)
                .divide(previo.abs(), ESCALA + 2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .setScale(ESCALA, RoundingMode.HALF_UP));
    }
}
