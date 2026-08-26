package bo.aportaya.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlazoHabilTest {

    /** Viernes 6 de marzo de 2026. */
    private static final LocalDate VIERNES = LocalDate.of(2026, 3, 6);

    private final CalendarioHabil finDeSemana =
            fecha -> fecha.getDayOfWeek() == DayOfWeek.SATURDAY || fecha.getDayOfWeek() == DayOfWeek.SUNDAY;

    @Test
    @DisplayName("Cinco dias habiles desde un viernes caen el viernes siguiente")
    void cincoDiasHabilesSaltanElFinDeSemana() {
        assertThat(PlazoHabil.sumar(VIERNES, 5, finDeSemana)).isEqualTo(LocalDate.of(2026, 3, 13));
    }

    @Test
    @DisplayName("Cero dias habiles es el mismo dia")
    void ceroDiasEsElMismoDia() {
        assertThat(PlazoHabil.sumar(VIERNES, 0, finDeSemana)).isEqualTo(VIERNES);
    }

    @Test
    @DisplayName("Un feriado en el medio corre el vencimiento")
    void unFeriadoCorreElVencimiento() {
        LocalDate feriado = LocalDate.of(2026, 3, 9);
        Set<LocalDate> noHabiles = Set.of(feriado);
        CalendarioHabil conFeriado = fecha -> finDeSemana.esNoHabil(fecha) || noHabiles.contains(fecha);

        assertThat(PlazoHabil.sumar(VIERNES, 1, conFeriado)).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    @DisplayName("El corrimiento va hacia adelante, a favor del cliente")
    void elCorrimientoVaHaciaAdelante() {
        LocalDate sabado = LocalDate.of(2026, 3, 7);

        assertThat(PlazoHabil.siguienteHabil(sabado, finDeSemana)).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(PlazoHabil.siguienteHabil(VIERNES, finDeSemana)).isEqualTo(VIERNES);
    }

    @Test
    @DisplayName("Un plazo no se cuenta hacia atras")
    void noSeCuentaHaciaAtras() {
        assertThatThrownBy(() -> PlazoHabil.sumar(VIERNES, -1, finDeSemana)).isInstanceOf(ErrorDeDominio.class);
    }

    @Test
    @DisplayName("Un calendario sin ningun dia habil falla en vez de colgarse")
    void unCalendarioSinDiasHabilesFalla() {
        CalendarioHabil ninguno = fecha -> true;

        assertThatThrownBy(() -> PlazoHabil.sumar(VIERNES, 1, ninguno))
                .isInstanceOf(ErrorDeDominio.class)
                .hasMessageContaining("feriados cargados");
        assertThatThrownBy(() -> PlazoHabil.siguienteHabil(VIERNES, ninguno)).isInstanceOf(ErrorDeDominio.class);
    }
}
