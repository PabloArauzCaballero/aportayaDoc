package bo.aportaya.cumplimiento.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * CU-06 · Compara lo declarado contra lo observado. Puro.
 *
 * <p>El perfil declarado es la referencia contra la que el monitoreo compara. Sin el,
 * un movimiento grande no se distingue de uno normal: por eso un alta sin perfil deja
 * al monitoreo ciego, y no es burocracia.
 */
public final class DesvioDePerfil {

    /** Escala 4 en el cociente: el porcentaje se muestra con dos decimales. */
    private static final int ESCALA_INTERNA = 4;

    private DesvioDePerfil() {}

    public enum Severidad {
        BAJA,
        MEDIA,
        ALTA,
        CRITICA
    }

    public record Desvio(BigDecimal porcentaje, Severidad severidad, boolean ameritaAlerta) {}

    /**
     * @param umbrales los cortes de severidad, en porcentaje de exceso sobre lo
     *     declarado. Llegan de configuracion: son politica de cumplimiento
     *     (invariante 10), no numeros del programa.
     */
    public record Umbrales(BigDecimal media, BigDecimal alta, BigDecimal critica) {}

    /**
     * El exceso de lo observado sobre lo declarado, en porcentaje.
     *
     * <p>Un declarado en cero **no es un desvio infinito**: es un perfil sin declarar,
     * y ese es otro problema con otro codigo de error. Devolver aca un numero enorme
     * disfrazaria un dato faltante de dato alarmante.
     */
    public static Desvio calcular(BigDecimal observado, BigDecimal declarado, Umbrales umbrales) {
        if (declarado == null || declarado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Sin monto declarado no hay desvio que calcular");
        }
        BigDecimal exceso = observado.subtract(declarado);
        // `movePointRight(2)` en vez de multiplicar por cien: convertir un cociente a
        // porcentaje es correr la coma, no aplicar un umbral. Escrito como
        // multiplicacion, el barrido lo lee —con razon— como una cifra horneada.
        BigDecimal porcentaje = exceso.divide(declarado, ESCALA_INTERNA, RoundingMode.HALF_UP)
                .movePointRight(2)
                .setScale(2, RoundingMode.HALF_UP);

        Severidad severidad = severidadDe(porcentaje, umbrales);
        // Por debajo del primer corte no hay desvio que registrar: registrar todo
        // ahogaria en ruido las pocas alertas que importan.
        return new Desvio(porcentaje, severidad, porcentaje.compareTo(umbrales.media()) >= 0);
    }

    private static Severidad severidadDe(BigDecimal porcentaje, Umbrales umbrales) {
        if (porcentaje.compareTo(umbrales.critica()) >= 0) {
            return Severidad.CRITICA;
        }
        if (porcentaje.compareTo(umbrales.alta()) >= 0) {
            return Severidad.ALTA;
        }
        if (porcentaje.compareTo(umbrales.media()) >= 0) {
            return Severidad.MEDIA;
        }
        return Severidad.BAJA;
    }
}
