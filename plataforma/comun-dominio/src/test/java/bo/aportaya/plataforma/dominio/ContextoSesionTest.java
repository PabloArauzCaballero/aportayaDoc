package bo.aportaya.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContextoSesionTest {

    private final Traza traza = new Traza("01J8X-traza");
    private final UUID usuario = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    @DisplayName("Sin rol no hay contexto, y la ausencia es un defecto")
    void sinRolNoHayContexto() {
        assertThatThrownBy(() -> ContextoSesion.de(usuario, "  ", traza)).isInstanceOf(SinContextoDeSesion.class);
        assertThatThrownBy(() -> ContextoSesion.de(usuario, null, traza)).isInstanceOf(SinContextoDeSesion.class);
    }

    @Test
    @DisplayName("Sin usuario ni traza tampoco")
    void sinUsuarioNiTrazaTampoco() {
        assertThatThrownBy(() -> ContextoSesion.de(null, "participante", traza))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ContextoSesion.de(usuario, "participante", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("El contexto de sistema es un rol, no una excepcion a las politicas")
    void elSistemaEsUnRol() {
        ContextoSesion sistema = ContextoSesion.deSistema(usuario, traza);

        assertThat(sistema.esSistema()).isTrue();
        assertThat(sistema.rol()).isEqualTo(ContextoSesion.ROL_SISTEMA);
        assertThat(ContextoSesion.de(usuario, "participante", traza).esSistema())
                .isFalse();
    }

    @Test
    @DisplayName("El dispositivo es opcional y se pregunta, no se asume")
    void elDispositivoEsOpcional() {
        assertThat(ContextoSesion.de(usuario, "participante", traza).dispositivoUsado())
                .isEmpty();
        assertThat(new ContextoSesion(usuario, "participante", traza, "android-9f").dispositivoUsado())
                .contains("android-9f");
    }
}
