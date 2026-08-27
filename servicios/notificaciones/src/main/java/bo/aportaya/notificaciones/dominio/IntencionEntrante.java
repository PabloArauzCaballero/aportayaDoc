package bo.aportaya.notificaciones.dominio;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;

/**
 * CU-82 · Clasifica lo que la persona contesto. Puro.
 *
 * <p>**Reglas explicitas, no adivinanza.** Ante la duda devuelve {@code DESCONOCIDA} y
 * el mensaje va a un humano. Interpretar de mas es peor que no interpretar: dar por
 * pagada una cuota porque alguien escribio «ya esta» seria confirmar un pago que
 * nadie hizo.
 */
public final class IntencionEntrante {

    private IntencionEntrante() {}

    public enum Intencion {
        YA_PAGUE,
        NO_PUEDO,
        BAJA,
        CONSULTA,
        NO_RECONOZCO,
        DESCONOCIDA
    }

    /**
     * Las frases que disparan cada intencion.
     *
     * <p>Se comparan sobre el texto normalizado —sin tildes, en minusculas— porque la
     * gente escribe «ya pague» y «YA PAGUÉ» con la misma intencion.
     */
    private static final Map<Intencion, List<String>> FRASES = Map.of(
            Intencion.YA_PAGUE, List.of("ya pague", "ya deposite", "ya transferi", "pague"),
            Intencion.NO_PUEDO, List.of("no puedo", "no tengo", "no voy a poder", "mas adelante"),
            Intencion.BAJA, List.of("baja", "no me escriban", "dar de baja", "stop", "no quiero recibir"),
            Intencion.NO_RECONOZCO, List.of("no reconozco", "no soy", "no es mio", "no participo"),
            Intencion.CONSULTA, List.of("cuanto", "como", "cuando", "donde", "?"));

    public static Intencion clasificar(String texto) {
        if (texto == null || texto.isBlank()) {
            return Intencion.DESCONOCIDA;
        }
        String limpio = normalizar(texto);

        // El orden importa: «no reconozco» y «baja» pesan mas que «consulta», porque
        // los dos primeros exigen una accion y el ultimo solo abre un ticket.
        for (Intencion intencion :
                List.of(Intencion.NO_RECONOZCO, Intencion.BAJA, Intencion.YA_PAGUE, Intencion.NO_PUEDO)) {
            if (FRASES.get(intencion).stream().anyMatch(limpio::contains)) {
                return intencion;
            }
        }
        if (FRASES.get(Intencion.CONSULTA).stream().anyMatch(limpio::contains)) {
            return Intencion.CONSULTA;
        }
        return Intencion.DESCONOCIDA;
    }

    /**
     * Como se GUARDA la intencion.
     *
     * <p>**Hueco declarado (H-2).** El contrato del caso de uso enumera seis
     * intenciones —YA_PAGUE, NO_PUEDO, BAJA, CONSULTA, NO_RECONOZCO, DESCONOCIDA— y
     * {@code ck_respuesta_entrante_intencion_detectada} admite otras cinco: AYUDA,
     * BAJA, CONSULTAR_SALDO, DESCONOCIDA, YA_PAGUE. No coinciden.
     *
     * <p>Manda la base, porque es la que rechaza. La clasificacion completa se
     * devuelve al llamador y se conserva en el evento; lo que se persiste en la
     * columna es el valor que la base admite. NO_PUEDO y NO_RECONOZCO **no tienen
     * representacion** y caen en DESCONOCIDA, que es informacion perdida: ampliar el
     * CHECK es una decision de modelo, troncal, no de este carril.
     */
    public static String comoLoGuardaLaBase(Intencion intencion) {
        return switch (intencion) {
            case YA_PAGUE -> "YA_PAGUE";
            case BAJA -> "BAJA";
            case CONSULTA -> "CONSULTAR_SALDO";
            case NO_PUEDO, NO_RECONOZCO, DESCONOCIDA -> "DESCONOCIDA";
        };
    }

    /** La accion que cada intencion dispara, segun el contrato del caso de uso. */
    public static String accionPara(Intencion intencion) {
        return switch (intencion) {
            case YA_PAGUE -> "COMPROBANTE_SOLICITADO";
            case NO_PUEDO -> "PROMESA_REGISTRADA";
            case BAJA -> "SUPRESION_APLICADA";
            case NO_RECONOZCO -> "RECLAMO_ABIERTO";
            case CONSULTA, DESCONOCIDA -> "TICKET_ABIERTO";
        };
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .trim();
    }
}
