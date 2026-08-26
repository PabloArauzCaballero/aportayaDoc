package bo.aportaya.plataforma.web.errores;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * La forma exacta que sale por la API, y la unica.
 *
 * <p>Nunca aparece: SQL, nombres de tabla o columna, trazas, rutas de archivo,
 * identificadores de otra persona, ni el motivo real de un bloqueo por inteligencia
 * financiera —eso ultimo es deber de reserva, no prolijidad—.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorApi(String codigo, String mensaje, Map<String, Object> detalle, String trazaId) {

    public static ErrorApi de(String codigo, String mensaje, String trazaId) {
        return new ErrorApi(codigo, mensaje, null, trazaId);
    }
}
