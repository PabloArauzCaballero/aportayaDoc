package bo.aportaya.plataforma.pruebas;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Barrido 12: el README de cada servicio dice los CU que el servicio realmente tiene.
 *
 * <p>Una tabla de casos de uso que se quedo atras no es un detalle de prolijidad: es
 * la primera cosa que lee quien llega al servicio dentro de seis meses, y la unica
 * que no puede comprobar por su cuenta.
 */
class DocumentacionVivaContratoTest {

    private static final Pattern CU_EN_CODIGO = Pattern.compile("\\bCU(\\d{2})[A-Z]");
    private static final Pattern CU_EN_README = Pattern.compile("\\bCU-(\\d{2})\\b");

    @Test
    @DisplayName("La tabla de CU del README coincide con los CU implementados")
    void elReadmeDiceLosCuQueElServicioTiene() {
        List<String> divergencias = new ArrayList<>();

        for (Path servicio : servicios()) {
            Set<String> enCodigo = casosEnElCodigo(servicio);
            Set<String> enReadme = casosEnElReadme(servicio);

            Set<String> sinDocumentar = new TreeSet<>(enCodigo);
            sinDocumentar.removeAll(enReadme);
            if (!sinDocumentar.isEmpty()) {
                divergencias.add(
                        "%s: implementa %s y su README no los nombra".formatted(servicio.getFileName(), sinDocumentar));
            }
        }

        assertThat(divergencias).isEmpty();
    }

    private Set<String> casosEnElCodigo(Path servicio) {
        Path aplicacion = servicio.resolve("src/main/java");
        if (!Files.isDirectory(aplicacion)) {
            return Set.of();
        }
        Set<String> encontrados = new TreeSet<>();
        try (Stream<Path> arbol = Files.walk(aplicacion)) {
            arbol.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("CU\\d{2}.*\\.java"))
                    .forEach(archivo -> {
                        Matcher cu = CU_EN_CODIGO.matcher(archivo.getFileName().toString());
                        if (cu.find()) {
                            encontrados.add(cu.group(1));
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return encontrados;
    }

    private Set<String> casosEnElReadme(Path servicio) {
        Path readme = servicio.resolve("README.md");
        if (!Files.isRegularFile(readme)) {
            return Set.of();
        }
        Set<String> encontrados = new TreeSet<>();
        Matcher cu = CU_EN_README.matcher(leer(readme));
        while (cu.find()) {
            encontrados.add(cu.group(1));
        }
        return encontrados;
    }

    private List<Path> servicios() {
        try (Stream<Path> hijos = Files.list(raiz().resolve("servicios"))) {
            return hijos.filter(Files::isDirectory).sorted().toList();
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
