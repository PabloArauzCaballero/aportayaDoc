package bo.aportaya.plataforma.mensajeria;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Un hecho que ya ocurrio, listo para publicarse DESPUES del {@code COMMIT}.
 *
 * <p>La carga lleva **identificadores, no datos derivados**. Un evento que viaja con
 * el saldo calculado obliga a quien lo consume a confiar en una copia que puede haber
 * quedado vieja entre el commit y el relevo; con el identificador, el consumidor
 * pregunta y obtiene la verdad de ahora.
 */
public record EventoDominio(
        String tipo, String agregado, UUID agregadoId, Map<String, Object> carga, UUID correlationId) {

    /** {@code <modulo>.<evento>} — el prefijo no es estilo: es lo que hace unico el tema de Kafka. */
    private static final Pattern FORMA = Pattern.compile("^[a-z_]+\\.[a-z_]+$");

    public EventoDominio {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(agregado, "agregado");
        Objects.requireNonNull(agregadoId, "agregadoId");
        Objects.requireNonNull(correlationId, "correlationId");
        if (!FORMA.matcher(tipo).matches()) {
            throw new IllegalArgumentException(
                    "El tipo de evento es <modulo>.<evento> en minusculas; llego «%s»".formatted(tipo));
        }
        carga = Map.copyOf(Objects.requireNonNull(carga, "carga"));
    }

    /** El tema donde se publica. Se deriva del tipo: no hay una lista que mantener. */
    public String tema() {
        return "aportaya." + tipo;
    }
}
