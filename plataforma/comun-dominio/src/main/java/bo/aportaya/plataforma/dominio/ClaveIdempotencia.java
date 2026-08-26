package bo.aportaya.plataforma.dominio;

import java.util.Objects;

/**
 * Derivada del HECHO, no del reintento.
 *
 * <p>Si la clave la genera el cliente en cada intento, dos toques del boton son dos
 * operaciones distintas y la red gana. Derivarla del hecho —esta obligacion, este
 * periodo, este beneficiario— hace que reintentar sea seguro por construccion.
 */
public record ClaveIdempotencia(String valor) {

    private static final int LARGO_MAXIMO = 120;

    public ClaveIdempotencia {
        Objects.requireNonNull(valor, "clave de idempotencia");
        if (valor.isBlank()) {
            throw new ErrorDeDominio("Una clave de idempotencia en blanco no distingue nada");
        }
        if (valor.length() > LARGO_MAXIMO) {
            throw new ErrorDeDominio(
                    "La clave de idempotencia no puede pasar de %d caracteres".formatted(LARGO_MAXIMO));
        }
    }

    /** {@code aporte:<id>} — el tipo del hecho y el agregado que lo identifica. */
    public static ClaveIdempotencia deHecho(String tipo, Object identificador) {
        Objects.requireNonNull(tipo, "tipo de hecho");
        Objects.requireNonNull(identificador, "identificador del hecho");
        return new ClaveIdempotencia(tipo + ":" + identificador);
    }

    @Override
    public String toString() {
        return valor;
    }
}
