package bo.aportaya.plataforma.web.errores;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * De {@code uq_cuenta_billetera_titular_moneda} a {@code R-BIL-04} y a lo que esa
 * regla garantiza, en las palabras de la boveda.
 *
 * <p>Los dos catalogos los genera {@code ./gradlew erroresCatalogo} desde
 * {@code sql/} y {@code docs/Restricciones.md}: cuando la regla cambia, cambia ahi.
 */
public final class TraduccionDeRestricciones {

    /** PostgreSQL nombra la restriccion violada entre comillas dobles. */
    private static final Pattern NOMBRE_EN_EL_MENSAJE = Pattern.compile("\"([a-z][a-z0-9_]*)\"");

    private final Properties reglaPorRestriccion;
    private final Properties mensajePorRegla;

    private TraduccionDeRestricciones(Properties reglas, Properties mensajes) {
        this.reglaPorRestriccion = reglas;
        this.mensajePorRegla = mensajes;
    }

    public static TraduccionDeRestricciones cargar() {
        return new TraduccionDeRestricciones(
                leer("/errores-restricciones.properties"), leer("/errores-reglas.properties"));
    }

    /** La regla detras del rechazo, si el catalogo la conoce. */
    public Optional<Regla> traducir(String mensajeDeLaBase) {
        if (mensajeDeLaBase == null) {
            return Optional.empty();
        }
        Matcher nombres = NOMBRE_EN_EL_MENSAJE.matcher(mensajeDeLaBase);
        while (nombres.find()) {
            String codigo = reglaPorRestriccion.getProperty(nombres.group(1));
            if (codigo != null) {
                return Optional.of(new Regla(codigo, mensajePorRegla.getProperty(codigo, "Operacion no permitida.")));
            }
        }
        return Optional.empty();
    }

    private static Properties leer(String recurso) {
        try (InputStream flujo = TraduccionDeRestricciones.class.getResourceAsStream(recurso)) {
            if (flujo == null) {
                throw new IllegalStateException("Falta " + recurso + ": corre ./gradlew erroresCatalogo");
            }
            Properties propiedades = new Properties();
            propiedades.load(flujo);
            return propiedades;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record Regla(String codigo, String mensaje) {}
}
