package bo.aportaya.entregas.dominio;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Si la cuenta esta a nombre de quien la registra.
 *
 * <p>Una cuenta de destino a nombre de otra persona es la forma mas simple de sacar
 * plata de un grupo hacia afuera, y la que la UIF mira primero. Por eso la comparacion
 * se hace por **documento**, que es unico, y no solo por nombre: dos personas se
 * pueden llamar igual, y una sola letra de diferencia no deberia habilitar un desvio.
 *
 * <p>La comparacion tolera acentos, mayusculas y espacios de mas — no tolera que sea
 * otra persona.
 */
public final class TitularidadDeCuenta {

    private TitularidadDeCuenta() {}

    public static boolean coincide(
            String titularNombre, String titularDocumento, String usuarioNombre, String usuarioDocumento) {

        // El documento manda: es lo unico que identifica sin ambiguedad.
        if (!normalizar(titularDocumento).equals(normalizar(usuarioDocumento))) {
            return false;
        }
        // El nombre se compara igual, para atajar el caso de un documento mal tipeado
        // que por azar coincide con el del titular.
        return normalizar(titularNombre).equals(normalizar(usuarioNombre));
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
