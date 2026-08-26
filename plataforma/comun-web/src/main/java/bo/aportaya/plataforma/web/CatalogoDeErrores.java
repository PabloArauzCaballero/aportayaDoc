package bo.aportaya.plataforma.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Traduce el nombre de la restriccion que devuelve PostgreSQL a la regla de negocio
 * que la origina.
 *
 * <p>Cuando la base rechaza una escritura devuelve {@code uq_cuenta_billetera_...},
 * no {@code R-BIL-04}. Sin esta tabla, el mensaje que llega al usuario es el nombre
 * de un indice — que ademas le ensena la forma del esquema a cualquiera.
 *
 * <p>El archivo lo genera {@code ./gradlew erroresCatalogo} desde {@code sql/}: la
 * fuente es el SQL, no una lista que alguien tiene que acordarse de actualizar.
 */
public final class CatalogoDeErrores {

    private static final String RECURSO = "/errores-restricciones.properties";

    private final Map<String, String> reglaPorRestriccion;

    private CatalogoDeErrores(Map<String, String> reglaPorRestriccion) {
        this.reglaPorRestriccion = reglaPorRestriccion;
    }

    public static CatalogoDeErrores cargar() {
        try (InputStream recurso = CatalogoDeErrores.class.getResourceAsStream(RECURSO)) {
            if (recurso == null) {
                throw new IllegalStateException(
                        "Falta " + RECURSO + ": corre ./gradlew erroresCatalogo antes de empaquetar");
            }
            Properties propiedades = new Properties();
            propiedades.load(recurso);
            return new CatalogoDeErrores(propiedades.stringPropertyNames().stream()
                    .collect(Collectors.toMap(nombre -> nombre, propiedades::getProperty)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** La regla {@code R-XXX-nn} de una restriccion, si el catalogo la conoce. */
    public Optional<String> reglaDe(String restriccion) {
        return Optional.ofNullable(restriccion).map(reglaPorRestriccion::get);
    }

    public int tamano() {
        return reglaPorRestriccion.size();
    }
}
