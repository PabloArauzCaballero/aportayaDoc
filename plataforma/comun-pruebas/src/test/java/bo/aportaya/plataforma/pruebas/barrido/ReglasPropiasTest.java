package bo.aportaya.plataforma.pruebas.barrido;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cada regla propia lleva su propia prueba: una regla sin prueba se desactiva sola
 * en el primer refactor, y nadie se entera hasta que ya no protege nada.
 */
class ReglasPropiasTest {

    @TempDir
    Path raiz;

    @Test
    @DisplayName("tamano-archivo bloquea a partir de 300 lineas y no antes")
    void tamanoArchivoBloqueaEnTrescientas() throws IOException {
        escribir("Justo.java", "// linea\n".repeat(TamanoDeArchivo.BLOQUEA - 1));
        escribir("Pasado.java", "// linea\n".repeat(TamanoDeArchivo.BLOQUEA));

        assertThat(TamanoDeArchivo.bloqueantes(raiz))
                .singleElement()
                .satisfies(h -> assertThat(h.archivo().getFileName()).hasToString("Pasado.java"));
    }

    @Test
    @DisplayName("tamano-archivo exige revision a partir de 260 lineas")
    void tamanoArchivoExigeRevision() throws IOException {
        escribir("Mediano.java", "// linea\n".repeat(TamanoDeArchivo.EXIGE_REVISION));

        assertThat(TamanoDeArchivo.exigenRevision(raiz)).hasSize(1);
        assertThat(TamanoDeArchivo.bloqueantes(raiz)).isEmpty();
    }

    // Las lineas que violan la regla se ARMAN por partes: escritas enteras, este
    // mismo archivo las dispararia, y la unica salida seria excluirlo del barrido —
    // que es como una regla deja de proteger su propio caso.
    private static final String CIFRA = "10" + "000";
    private static final String DECIMAL = "2" + ".50";

    @Test
    @DisplayName("sin-umbral-literal encuentra la cifra regulatoria escrita en el codigo")
    void sinUmbralLiteralEncuentraLaCifra() throws IOException {
        escribir(
                "Cumplimiento.java",
                "class Cumplimiento {\n"
                        + "    private static final int UMBRAL_UIF = " + CIFRA + ";\n"
                        + "    private static final BigDecimal COMISION = new BigDecimal(\"" + DECIMAL + "\");\n"
                        + "}\n");

        assertThat(SinUmbralLiteral.revisar(raiz)).hasSize(2);
    }

    @Test
    @DisplayName("sin-umbral-literal no grita por lo que no es dinero")
    void sinUmbralLiteralNoGritaDeMas() throws IOException {
        escribir(
                "Limpio.java",
                "class Limpio {\n"
                        + "    // El UMBRAL_UIF = " + CIFRA + " de este comentario no es codigo.\n"
                        + "    private static final int REINTENTOS = 3;\n"
                        + "    private final BigDecimal monto;\n"
                        + "\n"
                        + "    Limpio(BigDecimal monto) {\n"
                        + "        this.monto = monto;\n"
                        + "    }\n"
                        + "}\n");

        assertThat(SinUmbralLiteral.revisar(raiz)).isEmpty();
    }

    private void escribir(String nombre, String contenido) throws IOException {
        Files.writeString(raiz.resolve(nombre), contenido);
    }
}
