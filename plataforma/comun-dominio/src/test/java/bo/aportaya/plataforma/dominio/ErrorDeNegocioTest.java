package bo.aportaya.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorDeNegocioTest {

    private final CodigoError codigo = CodigoError.de(21, 3);

    @Test
    @DisplayName("Lleva su codigo y el detalle que el usuario necesita para actuar")
    void llevaCodigoYDetalle() {
        ErrorDeNegocio error =
                new ErrorDeNegocio(codigo, "No tenes saldo suficiente para este aporte.", Map.of("faltante", "45.00"));

        assertThat(error.codigo()).isEqualTo(codigo);
        assertThat(error.getMessage()).isEqualTo("No tenes saldo suficiente para este aporte.");
        assertThat(error.detalle()).containsEntry("faltante", "45.00");
        assertThat(error).isInstanceOf(ErrorDeDominio.class);
    }

    @Test
    @DisplayName("Sin detalle, el detalle es vacio y no nulo")
    void sinDetalleElDetalleEsVacio() {
        assertThat(new ErrorDeNegocio(codigo, "Regla incumplida.").detalle()).isEmpty();
    }

    @Test
    @DisplayName("El detalle es inmutable: un error ya ocurrio, no se edita")
    void elDetalleEsInmutable() {
        ErrorDeNegocio error = new ErrorDeNegocio(codigo, "Regla incumplida.", Map.of("a", 1));

        assertThatThrownBy(() -> error.detalle().put("b", 2)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Ni el codigo ni el detalle son opcionales")
    void niElCodigoNiElDetalleSonOpcionales() {
        assertThatThrownBy(() -> new ErrorDeNegocio(null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ErrorDeNegocio(codigo, "x", null)).isInstanceOf(NullPointerException.class);
    }
}
