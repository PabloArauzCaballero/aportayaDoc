package bo.aportaya.plataforma.pruebas;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Barrido 13: juntando los catorce contratos, ningun prefijo de ruta aparece en dos
 * servicios.
 *
 * <p>No se puede evaluar dentro de un servicio: ninguno ve las rutas de los otros.
 * Con un solo proceso chocaban al arrancar; partido el despliegue, **nadie se entera**
 * hasta que el gateway enruta mal — y para entonces el usuario ya vio la respuesta de
 * otro servicio.
 */
class PrefijoSinDosDuenosContratoTest {

    @Test
    @DisplayName("Ningun primer segmento de ruta pertenece a dos servicios")
    void ningunPrefijoConDosDuenos() {
        Map<String, List<String>> duenosPorPrefijo = new LinkedHashMap<>();

        for (Path contrato : contratos()) {
            String servicio = contrato.getFileName().toString().replace(".yaml", "");
            for (String ruta : rutasDe(contrato)) {
                String prefijo = primerSegmento(ruta);
                duenosPorPrefijo
                        .computeIfAbsent(prefijo, p -> new ArrayList<>())
                        .add(servicio);
            }
        }

        List<String> compartidos = duenosPorPrefijo.entrySet().stream()
                .filter(e -> e.getValue().stream().distinct().count() > 1)
                .map(e -> "%s lo reclaman %s"
                        .formatted(e.getKey(), e.getValue().stream().distinct().toList()))
                .toList();

        assertThat(compartidos)
                .as("un prefijo con dos duenos es un enrutado ambiguo")
                .isEmpty();
    }

    private String primerSegmento(String ruta) {
        String limpia = ruta.startsWith("/") ? ruta.substring(1) : ruta;
        int corte = limpia.indexOf('/');
        return "/" + (corte < 0 ? limpia : limpia.substring(0, corte));
    }

    @SuppressWarnings("unchecked")
    private List<String> rutasDe(Path contrato) {
        Map<String, Object> documento = new Yaml().load(leer(contrato));
        Object rutas = documento.get("paths");
        return rutas instanceof Map ? List.copyOf(((Map<String, Object>) rutas).keySet()) : List.of();
    }

    private List<Path> contratos() {
        try (Stream<Path> arbol = Files.walk(raiz().resolve("servicios"))) {
            return arbol.filter(Files::isRegularFile)
                    .filter(p -> p.getParent().getFileName().toString().equals("openapi"))
                    .filter(p -> p.toString().endsWith(".yaml"))
                    // El contrato es el de `src`, no la copia que quedo en `build`.
                    // Sin este filtro, una compilacion vieja hace fallar el gate con
                    // rutas que ya no existen — o peor, lo hace pasar con las viejas.
                    .filter(p -> !p.toString().contains("/build/"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path raiz() {
        Path actual = Path.of("").toAbsolutePath();
        while (actual != null && !Files.isDirectory(actual.resolve("servicios"))) {
            actual = actual.getParent();
        }
        return actual;
    }

    private String leer(Path archivo) {
        try {
            return Files.readString(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
