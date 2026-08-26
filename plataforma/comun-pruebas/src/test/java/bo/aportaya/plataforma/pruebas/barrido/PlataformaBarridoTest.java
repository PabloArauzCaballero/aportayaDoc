package bo.aportaya.plataforma.pruebas.barrido;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El barrido sobre plataforma/ entera. El piso se somete a las mismas reglas que lo
 * que se para encima: una regla que el troncal no cumple no la va a cumplir nadie.
 */
class PlataformaBarridoTest {

    private final Barrido barrido = Barrido.de(Path.of("..").toAbsolutePath().normalize());

    @Test
    @DisplayName("tamano-archivo: ningun archivo de plataforma llega a 300 lineas")
    void ningunArchivoBloquea() {
        barrido.ningunArchivoBloquea();
    }

    @Test
    @DisplayName("sin-umbral-literal: ninguna cifra regulatoria en plataforma")
    void ningunUmbralEnElCodigo() {
        barrido.ningunUmbralEnElCodigo();
    }
}
