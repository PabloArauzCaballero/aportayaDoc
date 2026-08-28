package bo.aportaya.entregas.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * El numero de cuenta, como se guarda y como se muestra.
 *
 * <p><b>Nunca en claro</b> (R-SEG-01): la fila guarda el numero cifrado, un hash con
 * pimienta para poder buscarlo, y una version enmascarada para mostrar. El numero
 * completo en una columna aparece despues en un respaldo, en un volcado de desarrollo y
 * en la pantalla de cualquiera con lectura — y para entonces ya no hay forma de saber
 * quien lo vio.
 *
 * <p>La <b>pimienta</b> va aparte del hash y no se guarda con el: sin ella, un numero
 * de cuenta se descubre probando, porque el espacio de numeros posibles es chico.
 */
public record CuentaEnmascarada(String hash, String enmascarado, int versionLlave) {

    /** Lo que se muestra: los ultimos cuatro digitos y nada mas. */
    private static final int DIGITOS_VISIBLES = 4;

    public static CuentaEnmascarada de(String numeroEnClaro, String pimienta, int versionLlave) {
        String numero = numeroEnClaro == null ? "" : numeroEnClaro.replaceAll("\\s", "");
        if (numero.length() < DIGITOS_VISIBLES) {
            throw new ErrorDeNegocio(CodigoError.de(18, 3), "Ese numero de cuenta es demasiado corto para ser uno.");
        }
        return new CuentaEnmascarada(
                hashCon(numero, pimienta),
                "*".repeat(Math.max(0, numero.length() - DIGITOS_VISIBLES))
                        + numero.substring(numero.length() - DIGITOS_VISIBLES),
                versionLlave);
    }

    /** SHA-256 sobre pimienta + numero. La pimienta es config, no una constante. */
    private static String hashCon(String numero, String pimienta) {
        if (pimienta == null || pimienta.isBlank()) {
            // Sin pimienta el hash es adivinable probando numeros: se para antes de
            // guardar algo que no protege nada.
            throw new IllegalStateException("El hash de busqueda exige pimienta (R-SEG-01)");
        }
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest((pimienta + "|" + numero).getBytes(StandardCharsets.UTF_8));
            StringBuilder texto = new StringBuilder(resumen.length * 2);
            for (byte b : resumen) {
                texto.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return texto.toString();
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("Toda JVM trae SHA-256", imposible);
        }
    }
}
