package bo.aportaya.organizador.dominio;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * La clave que impide que una tarea automatizada corra dos veces.
 *
 * <p>R-ORG-07: una tarea por clave de idempotencia. Se compone de la regla, el grupo y
 * el momento programado — **determinista**: el mismo disparo produce la misma clave.
 * Si dependiera de un azar o del reloj de la corrida, cada reintento del planificador
 * generaria una tarea nueva, y una regla de «aplicar mora» cobraria el recargo tantas
 * veces como se reintente.
 */
public record ClaveDeTarea(String valor) {

    /** Al minuto: dos disparos del mismo cron dentro del mismo minuto son el mismo. */
    private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public static ClaveDeTarea de(UUID reglaId, UUID grupoId, OffsetDateTime programadaPara) {
        return new ClaveDeTarea("%s|%s|%s"
                .formatted(
                        reglaId.toString().substring(0, 8),
                        grupoId.toString().substring(0, 8),
                        programadaPara.format(MOMENTO)));
    }
}
