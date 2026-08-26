package bo.aportaya.grupos.dominio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Por que se le propuso ese grupo, en palabras.
 *
 * <p>El usuario ve **el criterio, nunca los datos de los demas**: «monto compatible»,
 * «gente de tu zona», «riesgo parecido al tuyo». Un puntaje sin explicacion es un
 * numero que nadie puede discutir, y un emparejamiento que nadie puede discutir es
 * uno que se acepta por resignacion.
 */
public final class MotivoDelPuntaje {

    private MotivoDelPuntaje() {}

    public static List<String> de(
            CriterioDeEmparejamiento criterio,
            BigDecimal reputacion,
            BigDecimal monto,
            BigDecimal geografia,
            BigDecimal historialComun) {
        // El corte NO es una constante: es el promedio de las cuatro dimensiones.
        // Un umbral fijo seria un dato de producto escondido en el codigo, y la
        // regla `sin-umbral-literal` tiene razon en no dejarlo pasar. Ademas asi el
        // motivo dice lo que de verdad DISTINGUIO a este grupo de los otros.
        BigDecimal corte = promedio(reputacion, monto, geografia, historialComun);

        List<String> motivos = new ArrayList<>();
        if (aporta(criterio.pesoMonto(), monto, corte)) {
            motivos.add("el monto del aporte encaja con lo que buscas");
        }
        if (aporta(criterio.pesoGeografia(), geografia, corte)) {
            motivos.add("hay gente de tu zona");
        }
        if (aporta(criterio.pesoReputacion(), reputacion, corte)) {
            motivos.add("el riesgo del grupo se parece al tuyo");
        }
        if (aporta(criterio.pesoHistorialComun(), historialComun, corte)) {
            motivos.add("ya compartiste grupo con alguien de ahi");
        }
        if (motivos.isEmpty()) {
            motivos.add("es lo mas cercano a lo que pediste");
        }
        return List.copyOf(motivos);
    }

    private static boolean aporta(BigDecimal peso, BigDecimal valor, BigDecimal corte) {
        return peso.signum() > 0 && valor.compareTo(corte) >= 0;
    }

    private static BigDecimal promedio(BigDecimal... valores) {
        BigDecimal suma = BigDecimal.ZERO;
        for (BigDecimal valor : valores) {
            suma = suma.add(valor);
        }
        return suma.divide(BigDecimal.valueOf(valores.length), 4, java.math.RoundingMode.HALF_UP);
    }
}
