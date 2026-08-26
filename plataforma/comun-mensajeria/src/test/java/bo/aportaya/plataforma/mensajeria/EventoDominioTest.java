package bo.aportaya.plataforma.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventoDominioTest {

    @Test
    @DisplayName("El tema se deriva del tipo: no hay una lista de temas que mantener")
    void elTemaSeDerivaDelTipo() {
        EventoDominio evento =
                new EventoDominio("aportes.aporte_confirmado", "pago", UUID.randomUUID(), Map.of(), UUID.randomUUID());

        assertThat(evento.tema()).isEqualTo("aportaya.aportes.aporte_confirmado");
    }

    @Test
    @DisplayName("Un tipo sin prefijo de modulo no se acepta")
    void unTipoSinPrefijoNoSeAcepta() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new EventoDominio("aporteConfirmado", "pago", id, Map.of(), id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<modulo>.<evento>");
        assertThatThrownBy(() -> new EventoDominio("Aportes.Aporte", "pago", id, Map.of(), id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("La carga es inmutable: el evento es un hecho, no un borrador")
    void laCargaEsInmutable() {
        UUID id = UUID.randomUUID();
        EventoDominio evento = new EventoDominio("identidad.usuario_registrado", "usuario", id, Map.of("a", 1), id);

        assertThatThrownBy(() -> evento.carga().put("b", 2)).isInstanceOf(UnsupportedOperationException.class);
    }
}
