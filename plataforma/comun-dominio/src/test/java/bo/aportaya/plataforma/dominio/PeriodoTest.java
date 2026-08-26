package bo.aportaya.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeriodoTest {

    private static final LocalDate UNO = LocalDate.of(2026, 3, 1);
    private static final LocalDate TREINTA_Y_UNO = LocalDate.of(2026, 3, 31);

    @Test
    @DisplayName("Un periodo no termina antes de empezar")
    void noTerminaAntesDeEmpezar() {
        assertThatThrownBy(() -> Periodo.de(TREINTA_Y_UNO, UNO))
                .isInstanceOf(ErrorDeDominio.class)
                .hasMessageContaining("antes de empezar");
    }

    @Test
    @DisplayName("Contiene los dos extremos")
    void contieneLosExtremos() {
        Periodo marzo = Periodo.de(UNO, TREINTA_Y_UNO);

        assertThat(marzo.contiene(UNO)).isTrue();
        assertThat(marzo.contiene(TREINTA_Y_UNO)).isTrue();
        assertThat(marzo.contiene(LocalDate.of(2026, 3, 15))).isTrue();
        assertThat(marzo.contiene(LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(marzo.contiene(LocalDate.of(2026, 4, 1))).isFalse();
    }

    @Test
    @DisplayName("El solape se detecta aunque toque por un solo dia")
    void detectaElSolapePorUnDia() {
        Periodo marzo = Periodo.de(UNO, TREINTA_Y_UNO);
        Periodo pegado = Periodo.de(TREINTA_Y_UNO, LocalDate.of(2026, 4, 15));
        Periodo despues = Periodo.de(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(marzo.seSolapaCon(pegado)).isTrue();
        assertThat(pegado.seSolapaCon(marzo)).isTrue();
        assertThat(marzo.seSolapaCon(despues)).isFalse();
        assertThat(despues.seSolapaCon(marzo)).isFalse();

        Periodo antes = Periodo.de(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        assertThat(marzo.seSolapaCon(antes)).isFalse();
    }

    @Test
    @DisplayName("Los dias cuentan los dos extremos")
    void cuentaLosDiasConLosExtremos() {
        assertThat(Periodo.de(UNO, TREINTA_Y_UNO).dias()).isEqualTo(31);
        assertThat(Periodo.de(UNO, UNO).dias()).isEqualTo(1);
    }
}
