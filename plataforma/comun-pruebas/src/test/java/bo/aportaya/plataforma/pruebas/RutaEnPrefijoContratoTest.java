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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Ninguna ruta publicada cae fuera del prefijo de su servicio.
 *
 * <p>Es la prohibicion numero uno del contrato de implementacion: si el primer
 * segmento no esta en {@code PREFIJOS}, el endpoint no existe. Verificarlo en el
 * contrato —y no en revision— es lo que impide que catorce carriles inventen rutas
 * en paralelo y que dos servicios se peleen el mismo camino.
 *
 * <p>Los prefijos se leen de {@code scripts/modelo.py}, que es la fuente de verdad
 * (contrato §1.5). Copiarlos aca crearia una segunda verdad, que es el defecto que
 * esta prueba existe para evitar.
 */
class RutaEnPrefijoContratoTest {

    private static final Pattern ENTRADA = Pattern.compile("\"([a-z_]+)\":\\s*\\[([^\\]]*)\\]", Pattern.DOTALL);
    private static final Pattern RUTA = Pattern.compile("\"(/[a-z-]+)\"");

    @Test
    @DisplayName("Toda ruta de todo contrato cae en un prefijo real de su servicio")
    void ningunaRutaFueraDeSuPrefijo() {
        Map<String, List<String>> prefijos = prefijosDelModelo();
        assertThat(prefijos).as("PREFIJOS no se pudo leer de scripts/modelo.py").hasSize(14);

        List<String> fuera = new ArrayList<>();
        for (Path contrato : contratos()) {
            String servicio = contrato.getFileName().toString().replace(".yaml", "");
            List<String> suyos = prefijos.get(servicio.replace("-", "_"));
            assertThat(suyos).as("%s no figura en PREFIJOS", servicio).isNotNull();

            for (String ruta : rutasDe(contrato)) {
                if (suyos.stream().noneMatch(p -> ruta.equals(p) || ruta.startsWith(p + "/"))) {
                    fuera.add("%s  %s  (prefijos: %s)".formatted(servicio, ruta, suyos));
                }
            }
        }

        assertThat(fuera)
                .as("Una ruta fuera del prefijo de su servicio es un rechazo automatico")
                .isEmpty();
    }

    private Map<String, List<String>> prefijosDelModelo() {
        String fuente = leer(raiz().resolve("scripts/modelo.py"));
        int inicio = fuente.indexOf("PREFIJOS = {");
        int fin = fuente.indexOf("\n}", inicio);
        Map<String, List<String>> prefijos = new LinkedHashMap<>();
        Matcher entrada = ENTRADA.matcher(fuente.substring(inicio, fin));
        while (entrada.find()) {
            List<String> rutas = new ArrayList<>();
            Matcher ruta = RUTA.matcher(entrada.group(2));
            while (ruta.find()) {
                rutas.add(ruta.group(1));
            }
            prefijos.put(entrada.group(1), rutas);
        }
        return prefijos;
    }

    @SuppressWarnings("unchecked")
    private List<String> rutasDe(Path contrato) {
        Map<String, Object> documento = new Yaml().load(leer(contrato));
        Object rutas = documento.get("paths");
        if (!(rutas instanceof Map)) {
            return List.of();
        }
        return List.copyOf(((Map<String, Object>) rutas).keySet());
    }

    private List<Path> contratos() {
        Path servicios = raiz().resolve("servicios");
        try (Stream<Path> arbol = Files.walk(servicios)) {
            return arbol.filter(Files::isRegularFile)
                    .filter(p -> p.getParent().getFileName().toString().equals("openapi"))
                    .filter(p -> p.toString().endsWith(".yaml"))
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
