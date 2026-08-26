package bo.aportaya.auditoria;

import bo.aportaya.plataforma.pruebas.barrido.Barrido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Las reglas propias de planes/00 §6 aplicadas a las fuentes de este servicio.
 *
 * <p>La implementacion vive en plataforma/comun-pruebas: ningun servicio puede
 * desactivarlas, y el que agrega la regla numero trece la agrega una sola vez.
 */
class BarridoTest {

    private final Barrido barrido = Barrido.delModulo();

    @Test
    @DisplayName("tamano-archivo: ningun archivo llega a 300 lineas")
    void ningunArchivoBloquea() {
        barrido.ningunArchivoBloquea();
    }

    @Test
    @DisplayName("sin-umbral-literal: ninguna cifra regulatoria dentro del codigo")
    void ningunUmbralEnElCodigo() {
        barrido.ningunUmbralEnElCodigo();
    }
}
