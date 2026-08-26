package bo.aportaya.nucleofinanciero.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ErrorDeDominio;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-24 · el átomo del cuadre, sin base y sin Spring. */
class CuadrarPartidasTest {

    @Test
    @DisplayName("CU-24 · dos partidas que cuadran devuelven el mismo total en debe y en haber")
    void cuadra() {
        var totales = CuadrarPartidas.verificar(List.of(
                new Partida("1.1.01", new BigDecimal("150.00"), BigDecimal.ZERO),
                new Partida("2.1.01", BigDecimal.ZERO, new BigDecimal("150.00"))));

        assertThat(totales.debe().toString()).isEqualTo("150.00");
        assertThat(totales.haber().toString()).isEqualTo("150.00");
    }

    @Test
    @DisplayName("CU-24 · CA: un asiento con debe distinto de haber se rechaza con AP-CU24-01")
    void descuadrado() {
        assertThatThrownBy(() -> CuadrarPartidas.verificar(List.of(
                        new Partida("1.1.01", new BigDecimal("150.00"), BigDecimal.ZERO),
                        new Partida("2.1.01", BigDecimal.ZERO, new BigDecimal("100.00")))))
                .isInstanceOf(ErrorDeNegocio.class)
                .satisfies(
                        e -> assertThat(((ErrorDeNegocio) e).codigo().valor()).isEqualTo("AP-CU24-01"));
    }

    @Test
    @DisplayName("CU-24 · un asiento con una sola partida no puede cuadrar consigo mismo")
    void unaSolaPartida() {
        assertThatThrownBy(() -> CuadrarPartidas.verificar(
                        List.of(new Partida("1.1.01", new BigDecimal("150.00"), BigDecimal.ZERO))))
                .isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("CU-24 · tres partidas que cuadran entre todas también son un asiento válido")
    void variasPartidas() {
        var totales = CuadrarPartidas.verificar(List.of(
                new Partida("1.1.01", new BigDecimal("100.00"), BigDecimal.ZERO),
                new Partida("1.1.02", new BigDecimal("50.00"), BigDecimal.ZERO),
                new Partida("2.1.01", BigDecimal.ZERO, new BigDecimal("150.00"))));

        assertThat(totales.debe()).isEqualTo(totales.haber());
    }

    @Test
    @DisplayName("Una partida no admite debe y haber a la vez")
    void debeYHaberALaVez() {
        assertThatThrownBy(() -> new Partida("1.1.01", new BigDecimal("10.00"), new BigDecimal("5.00")))
                .isInstanceOf(ErrorDeDominio.class);
    }

    @Test
    @DisplayName("Una partida no admite importes negativos")
    void importeNegativo() {
        assertThatThrownBy(() -> new Partida("1.1.01", new BigDecimal("-10.00"), BigDecimal.ZERO))
                .isInstanceOf(ErrorDeDominio.class);
    }
}
