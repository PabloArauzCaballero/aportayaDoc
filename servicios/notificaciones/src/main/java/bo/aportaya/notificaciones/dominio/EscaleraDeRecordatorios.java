package bo.aportaya.notificaciones.dominio;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * CU-81 · Cuando recordar, y cuando dejar de hacerlo. Puro.
 *
 * <p>Cuatro escalones alrededor del vencimiento: antes, el dia, dentro de la gracia y
 * pasada la gracia. El desfase de cada uno viene de {@code programacion_recordatorio},
 * no del codigo: un grupo puede querer avisar tres dias antes y otro siete.
 */
public final class EscaleraDeRecordatorios {

    private EscaleraDeRecordatorios() {}

    public enum Escalon {
        PREVIO,
        VENCIMIENTO,
        GRACIA,
        POST_GRACIA
    }

    public record Paso(Escalon escalon, LocalDate fecha) {}

    /**
     * @param desfases dias respecto del vencimiento por escalon, tal como los guarda
     *     {@code programacion_recordatorio.desfase_dias} (negativo = antes)
     */
    public static List<Paso> calcular(LocalDate vencimiento, java.util.Map<Escalon, Integer> desfases) {
        return desfases.entrySet().stream()
                .map(e -> new Paso(e.getKey(), vencimiento.plusDays(e.getValue())))
                .sorted(java.util.Comparator.comparing(Paso::fecha))
                .toList();
    }

    public enum Resultado {
        ENVIADO,
        CANCELADO_YA_PAGADO,
        POSPUESTO_TOPE,
        SUPRIMIDO
    }

    /**
     * Decide que hacer con una obligacion en el dia de hoy.
     *
     * <p>El orden de las preguntas no es casual. **Primero si ya pago**: seguir
     * recordandole a quien ya cumplio es la forma mas rapida de que desactive los
     * avisos y no se entere del proximo. Despues la supresion, que es una decision de
     * la persona. Y recien al final el tope, que es una decision nuestra.
     */
    public static Resultado debeRecordar(
            boolean yaPago, boolean suprimido, int enviosPrevios, int topeDeEnvios, boolean hayPasoHoy) {

        if (yaPago) {
            return Resultado.CANCELADO_YA_PAGADO;
        }
        if (suprimido) {
            return Resultado.SUPRIMIDO;
        }
        if (enviosPrevios >= topeDeEnvios) {
            return Resultado.POSPUESTO_TOPE;
        }
        return hayPasoHoy ? Resultado.ENVIADO : Resultado.POSPUESTO_TOPE;
    }

    /** El escalon que corresponde hoy, si hay alguno. */
    public static Optional<Escalon> escalonDeHoy(List<Paso> pasos, LocalDate hoy) {
        return pasos.stream()
                .filter(p -> p.fecha().equals(hoy))
                .map(Paso::escalon)
                .findFirst();
    }
}
