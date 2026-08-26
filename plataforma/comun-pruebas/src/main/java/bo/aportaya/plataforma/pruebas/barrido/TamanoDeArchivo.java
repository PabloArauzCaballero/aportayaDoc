package bo.aportaya.plataforma.pruebas.barrido;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Regla {@code tamano-archivo}: 220 advierte, 260 exige revision, 300 bloquea.
 *
 * <p>Un archivo de 400 lineas no es un archivo grande: son tres piezas que nadie
 * separo, y el nivel de cada una dejo de ser verificable. El limite no esta para
 * castigar el tamano sino para que la composicion atomica siga siendo comprobable.
 */
public final class TamanoDeArchivo {

    public static final int ADVIERTE = 220;
    public static final int EXIGE_REVISION = 260;
    public static final int BLOQUEA = 300;

    private TamanoDeArchivo() {}

    /** Los archivos que superan el limite que bloquea. */
    public static List<Hallazgo> bloqueantes(Path raiz) {
        return revisar(raiz, BLOQUEA);
    }

    /** Los archivos que superan el limite que exige revision humana. */
    public static List<Hallazgo> exigenRevision(Path raiz) {
        return revisar(raiz, EXIGE_REVISION);
    }

    private static List<Hallazgo> revisar(Path raiz, int limite) {
        List<Hallazgo> hallazgos = new ArrayList<>();
        for (Path archivo : fuentesJava(raiz)) {
            int lineas = contarLineas(archivo);
            if (lineas >= limite) {
                hallazgos.add(new Hallazgo(archivo, 0, "%d lineas (limite %d)".formatted(lineas, limite)));
            }
        }
        return hallazgos;
    }

    static List<Path> fuentesJava(Path raiz) {
        if (!Files.isDirectory(raiz)) {
            return List.of();
        }
        try (Stream<Path> arbol = Files.walk(raiz)) {
            return arbol.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    // Lo generado no se revisa: se regenera.
                    .filter(p -> !enBarras(p).contains("/build/"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * La ruta con barras normales, venga de donde venga.
     *
     * <p>En Windows {@code Path.toString()} devuelve contrabarras, y un filtro escrito
     * con {@code "/build/"} no acierta ni una vez: el barrido pasa a revisar el codigo
     * generado y a denunciar lo que no debe. Es el mismo defecto que tenia el
     * resolvedor de wikilinks, en otro archivo.
     */
    static String enBarras(Path ruta) {
        return ruta.toString().replace('\\', '/');
    }

    private static int contarLineas(Path archivo) {
        try {
            return Files.readAllLines(archivo).size();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
