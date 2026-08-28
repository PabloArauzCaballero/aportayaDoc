package bo.aportaya.transparencia.dominio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Convierte metricas en senales, y senales en frases que una persona entiende.
 *
 * <p>**El mensaje al usuario habla de hechos, nunca de probabilidades.** Decirle a
 * alguien «tu riesgo de incumplimiento es 0.73» no le sirve para nada y lo etiqueta;
 * decirle «te vencen dos aportes el viernes y uno quedo pendiente del mes pasado» le
 * dice que hacer. Ademas, el puntaje del modelo no se expone: quien lo conoce lo
 * puede jugar.
 *
 * <p>**Sin historial no hay riesgo alto.** La ausencia de datos es ausencia de datos.
 * Tratar a quien recien llega como probable incumplidor es exactamente la exclusion
 * que este producto existe para no repetir.
 */
public final class SenalDeRiesgo {

    public static final String SIN_DATOS = "SIN_DATOS";

    private SenalDeRiesgo() {}

    /**
     * Evalua las metricas contra sus umbrales. Una metrica sin umbral configurado
     * **no dispara nada**: sin umbral vigente no hay decision (invariante 9 al reves —
     * lo que se deniega por omision es la alerta, no el acceso).
     */
    public static List<Metrica> enAlerta(List<Metrica> metricas) {
        var alertadas = new ArrayList<Metrica>();
        for (var m : metricas) {
            if (m.umbral() != null && m.superaUmbral()) {
                alertadas.add(m);
            }
        }
        return List.copyOf(alertadas);
    }

    /**
     * El nivel de riesgo, o {@code SIN_DATOS} si no alcanza para decir nada.
     *
     * <p>Los cortes llegan como dato (invariante 10). Donde empieza «riesgo alto» decide
     * a quien se acompaña y a quien se restringe: es una palanca de politica, no una
     * cifra del codigo.
     */
    public static String nivel(int observaciones, int minimoDeObservaciones, BigDecimal puntaje, Escala escala) {
        if (observaciones < minimoDeObservaciones || puntaje == null) {
            return SIN_DATOS;
        }
        if (puntaje.compareTo(escala.hastaAlto()) < 0) {
            return "ALTO";
        }
        if (puntaje.compareTo(escala.hastaMedio()) < 0) {
            return "MEDIO";
        }
        return "BAJO";
    }

    /**
     * Los cortes de riesgo y de severidad, juntos porque se calibran juntos.
     *
     * @param hastaAlto por debajo de este puntaje, el riesgo es ALTO
     * @param hastaMedio por debajo de este, MEDIO; de ahi para arriba, BAJO
     * @param excesoCritico cuanto hay que pasarse del umbral, en proporcion, para que
     *     la severidad sea CRITICA
     */
    public record Escala(
            BigDecimal hastaAlto,
            BigDecimal hastaMedio,
            BigDecimal excesoCritico,
            BigDecimal excesoAlto,
            BigDecimal excesoMedio) {}

    /** La severidad sale de cuanto se paso del umbral, no de una tabla de humor. */
    public static String severidad(Metrica metrica, Escala escala) {
        if (metrica.umbral() == null || metrica.umbral().signum() == 0) {
            return "BAJA";
        }
        BigDecimal exceso = metrica.distanciaRelativaAlUmbral();
        if (exceso.compareTo(escala.excesoCritico()) >= 0) {
            return "CRITICA";
        }
        if (exceso.compareTo(escala.excesoAlto()) >= 0) {
            return "ALTA";
        }
        if (exceso.compareTo(escala.excesoMedio()) >= 0) {
            return "MEDIA";
        }
        return "BAJA";
    }

    /**
     * La frase que ve la persona: **hechos, con numeros, sin el modelo dentro**.
     * Si alguien puede leer el puntaje en el mensaje, el mensaje esta mal.
     */
    public static String mensajeEnHechos(Metrica metrica) {
        return switch (metrica.codigo()) {
            case "TASA_PAGO_EN_TERMINO" ->
                "Este mes %s de cada 10 aportes llegaron despues de la fecha."
                        .formatted(diezmos(BigDecimal.ONE.subtract(metrica.valor())));
            case "MORA_CONCENTRADA" ->
                "El %s%% de lo pendiente del grupo esta en una sola persona.".formatted(porcentaje(metrica.valor()));
            case "RETIRO_MASIVO" ->
                "%s participantes pidieron salir en los ultimos treinta dias."
                        .formatted(metrica.valor()
                                .setScale(0, java.math.RoundingMode.DOWN)
                                .toPlainString());
            case "CAIDA_ABRUPTA_SCORE" ->
                "Tu puntaje bajo %s puntos desde el mes pasado."
                        .formatted(metrica.valor()
                                .abs()
                                .setScale(0, java.math.RoundingMode.HALF_UP)
                                .toPlainString());
            default ->
                "%s: %s (limite %s)"
                        .formatted(
                                metrica.codigo(),
                                metrica.valor().toPlainString(),
                                metrica.umbral().toPlainString());
        };
    }

    public static String accionSugerida(String codigo) {
        return switch (codigo) {
            case "TASA_PAGO_EN_TERMINO" -> "Revisar el calendario de aportes con el grupo y adelantar el recordatorio.";
            case "MORA_CONCENTRADA" -> "Ofrecer plan de regularizacion a quien concentra lo pendiente.";
            case "RETIRO_MASIVO" -> "Conversar con el grupo antes de que la salida se vuelva irreversible.";
            case "CAIDA_ABRUPTA_SCORE" -> "Ver que aportes quedaron pendientes y ponerlos al dia.";
            default -> "Revisar la metrica con el equipo de riesgos.";
        };
    }

    /** De proporcion a porcentaje: correr la coma, no aplicar un umbral. */
    private static String porcentaje(BigDecimal proporcion) {
        return proporcion
                .movePointRight(2)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static String diezmos(BigDecimal proporcion) {
        return proporcion
                .multiply(BigDecimal.TEN)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }

    /**
     * @param mayorEsPeor cuando la metrica empeora al subir (mora) o al bajar (tasa de
     *     pago en termino). Sin este dato una tasa de pago del 100% se leeria como
     *     alerta.
     */
    public record Metrica(String codigo, BigDecimal valor, String unidad, BigDecimal umbral, boolean mayorEsPeor) {

        public boolean superaUmbral() {
            return mayorEsPeor ? valor.compareTo(umbral) > 0 : valor.compareTo(umbral) < 0;
        }

        /** Cuanto se paso, en proporcion del umbral. Nunca negativo. */
        public BigDecimal distanciaRelativaAlUmbral() {
            BigDecimal diferencia = mayorEsPeor ? valor.subtract(umbral) : umbral.subtract(valor);
            if (diferencia.signum() <= 0) {
                return BigDecimal.ZERO;
            }
            return diferencia.divide(umbral.abs(), 4, java.math.RoundingMode.HALF_EVEN);
        }
    }
}
