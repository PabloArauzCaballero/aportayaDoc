package bo.aportaya.plataforma.dominio;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * {@code AP-CU<NN>-<nn>}, validado al construirlo.
 *
 * <p>Un codigo no se reutiliza nunca: uno retirado queda retirado, porque reusarlo
 * mezcla incidentes viejos con nuevos en el soporte y nadie lo nota hasta que hay que
 * explicar una cifra.
 */
public record CodigoError(String valor) {

    private static final Pattern FORMA = Pattern.compile("^AP-CU\\d{2}-\\d{2}$");

    public CodigoError {
        Objects.requireNonNull(valor, "codigo de error");
        if (!FORMA.matcher(valor).matches()) {
            throw new ErrorDeDominio("«%s» no tiene la forma AP-CU<NN>-<nn>".formatted(valor));
        }
    }

    public static CodigoError de(int casoDeUso, int numero) {
        return new CodigoError("AP-CU%02d-%02d".formatted(casoDeUso, numero));
    }

    @Override
    public String toString() {
        return valor;
    }
}
