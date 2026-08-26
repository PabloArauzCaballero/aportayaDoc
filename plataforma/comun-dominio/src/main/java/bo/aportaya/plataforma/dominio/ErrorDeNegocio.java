package bo.aportaya.plataforma.dominio;

import java.util.Map;
import java.util.Objects;

/**
 * Una regla del caso de uso que no se cumplio, con su codigo {@code AP-CU<NN>-<nn>}.
 *
 * <p>Sale como {@code 422} y no como {@code 400}: el {@code 400} es del esquema, el
 * {@code 422} es de la regla. Confundirlos hace que el cliente no pueda distinguir
 * «mandaste mal el formulario» de «no tenes saldo».
 *
 * <p>El {@code detalle} lleva lo que el usuario necesita para actuar —cuanto le
 * falta, hasta cuando tiene plazo— y nunca nombres de tabla, SQL ni identificadores
 * de otra persona.
 */
public class ErrorDeNegocio extends ErrorDeDominio {

    private static final long serialVersionUID = 1L;

    private final transient CodigoError codigo;
    private final transient Map<String, Object> detalle;

    public ErrorDeNegocio(CodigoError codigo, String mensaje) {
        this(codigo, mensaje, Map.of());
    }

    public ErrorDeNegocio(CodigoError codigo, String mensaje, Map<String, Object> detalle) {
        super(mensaje);
        this.codigo = Objects.requireNonNull(codigo, "codigo");
        this.detalle = Map.copyOf(Objects.requireNonNull(detalle, "detalle"));
    }

    public CodigoError codigo() {
        return codigo;
    }

    public Map<String, Object> detalle() {
        return detalle;
    }
}
