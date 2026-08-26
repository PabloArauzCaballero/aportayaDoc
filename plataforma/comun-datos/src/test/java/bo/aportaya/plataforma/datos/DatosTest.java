package bo.aportaya.plataforma.datos;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DatosTest {

    private final Datos datos = new Datos(mock(DSLContext.class));
    private final ContextoSesion ctx = ContextoSesion.de(UUID.randomUUID(), "participante", new Traza("t-1"));

    @Test
    @DisplayName("Sin transaccion abierta no se ejecuta: SET LOCAL suelto no fija nada")
    void sinTransaccionNoSeEjecuta() {
        assertThatThrownBy(() -> datos.conContexto(ctx, dsl -> "no deberia llegar"))
                .isInstanceOf(SinTransaccion.class)
                .hasMessageContaining("transaccion abierta");
    }

    @Test
    @DisplayName("Ni el contexto ni la consulta son opcionales")
    void niElContextoNiLaConsultaSonOpcionales() {
        assertThatThrownBy(() -> datos.conContexto(null, dsl -> "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> datos.conContexto(ctx, null)).isInstanceOf(NullPointerException.class);
    }
}
