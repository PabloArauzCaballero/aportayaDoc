package bo.aportaya.plataforma.pruebas.barrido;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Regla {@code sin-umbral-literal} — invariante 10.
 *
 * <p>Umbrales, limites y tarifas son catalogo sembrado, no constantes: una cifra
 * regulatoria escrita dentro del codigo obliga a desplegar para cumplir una
 * circular, y la circular no espera al despliegue.
 *
 * <p>La regla es deliberadamente estrecha —constantes con nombre de dinero y
 * literales de {@code BigDecimal}— para que un hallazgo sea siempre real. Una regla
 * que grita seguido es una regla que se termina apagando.
 */
public final class SinUmbralLiteral {

    private static final Pattern CONSTANTE_DE_DINERO = Pattern.compile(
            "(?i)\\b(UMBRAL|LIMITE|TOPE|TARIFA|COMISION|MONTO|MINIMO|MAXIMO|PORCENTAJE|TASA)[A-Za-z_]*\\s*=\\s*-?\\d");

    private static final Pattern LITERAL_BIG_DECIMAL =
            Pattern.compile("(new\\s+BigDecimal\\s*\\(|BigDecimal\\.valueOf\\s*\\()\\s*\"?-?\\d");

    private SinUmbralLiteral() {}

    public static List<Hallazgo> revisar(Path raiz) {
        List<Hallazgo> hallazgos = new ArrayList<>();
        for (Path archivo : TamanoDeArchivo.fuentesJava(raiz)) {
            // «fuera de seeders/ y PRUEBAS»: una prueba de dinero necesita escribir
            // importes, y prohibirselos la obligaria a armarlos por concatenacion.
            if (TamanoDeArchivo.enBarras(archivo).contains("/src/test/")) {
                continue;
            }
            List<String> lineas = leer(archivo);
            for (int i = 0; i < lineas.size(); i++) {
                String linea = lineas.get(i);
                if (esComentario(linea)) {
                    continue;
                }
                if (CONSTANTE_DE_DINERO.matcher(linea).find()
                        || LITERAL_BIG_DECIMAL.matcher(linea).find()) {
                    hallazgos.add(new Hallazgo(archivo, i + 1, linea.strip()));
                }
            }
        }
        return hallazgos;
    }

    private static boolean esComentario(String linea) {
        String limpia = linea.strip();
        return limpia.startsWith("//") || limpia.startsWith("*") || limpia.startsWith("/*");
    }

    private static List<String> leer(Path archivo) {
        try {
            return Files.readAllLines(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
