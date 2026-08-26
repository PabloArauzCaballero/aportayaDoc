package bo.aportaya.plataforma.dominio;

import static bo.aportaya.plataforma.dominio.Moneda.BOB;
import static bo.aportaya.plataforma.dominio.Moneda.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DineroTest {

    @Test
    @DisplayName("Suma y resta con la misma moneda")
    void sumaYResta() {
        Dinero cuota = Dinero.de("150.00", BOB);
        Dinero recargo = Dinero.de("7.50", BOB);

        assertThat(cuota.mas(recargo)).isEqualTo(Dinero.de("157.50", BOB));
        assertThat(cuota.menos(recargo)).isEqualTo(Dinero.de("142.50", BOB));
    }

    @Test
    @DisplayName("Operar monedas distintas lanza error del dominio, no convierte en silencio")
    void monedasDistintasLanzan() {
        Dinero enBolivianos = Dinero.de("150.00", BOB);
        Dinero enDolares = Dinero.de("10.00", USD);

        assertThatThrownBy(() -> enBolivianos.mas(enDolares))
                .isInstanceOf(ErrorDeDominio.class)
                .hasMessageContaining("BOB")
                .hasMessageContaining("USD");
    }

    @Test
    @DisplayName("Construir con mas de dos decimales exige redondear a proposito")
    void masDeDosDecimalesNoSeRedondeaSolo() {
        assertThatThrownBy(() -> Dinero.de("10.005", BOB))
                .isInstanceOf(ErrorDeDominio.class)
                .hasMessageContaining("decimales");
    }

    @Test
    @DisplayName("Dividir sin regla de redondeo no se permite")
    void dividirExigeRegla() {
        Dinero total = Dinero.de("100.00", BOB);

        assertThatThrownBy(() -> total.dividir(new BigDecimal("3"), RoundingMode.UNNECESSARY))
                .isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> total.dividir(BigDecimal.ZERO, RoundingMode.HALF_UP))
                .isInstanceOf(ErrorDeDominio.class);
        assertThat(total.dividir(new BigDecimal("3"), RoundingMode.HALF_UP)).isEqualTo(Dinero.de("33.33", BOB));
    }

    @Test
    @DisplayName("Compara por valor: 1.10 y 1.1 son el mismo importe")
    void comparaPorValorYNoPorEscala() {
        assertThat(Dinero.de("1.10", BOB)).isEqualTo(Dinero.de(new BigDecimal("1.1"), BOB));
        assertThat(Dinero.de("1.10", BOB)).hasSameHashCodeAs(Dinero.de(new BigDecimal("1.1"), BOB));
        assertThat(Dinero.de("1.10", BOB)).isNotEqualTo(Dinero.de("1.10", USD));
    }

    @Test
    @DisplayName("La frontera de salida es una cadena de dos decimales")
    void seSerializaComoCadenaDeDosDecimales() {
        assertThat(Dinero.de("150.5", BOB)).hasToString("150.50");
        assertThat(Dinero.cero(BOB)).hasToString("0.00");
        assertThat(Dinero.de("-45.00", BOB)).hasToString("-45.00");
    }

    @Test
    @DisplayName("Signo y comparaciones")
    void signoYComparaciones() {
        Dinero cero = Dinero.cero(BOB);
        Dinero deuda = Dinero.de("-45.00", BOB);
        Dinero saldo = Dinero.de("45.00", BOB);

        assertThat(cero.esCero()).isTrue();
        assertThat(deuda.esNegativo()).isTrue();
        assertThat(saldo.esMayorQue(cero)).isTrue();
        assertThat(deuda.esMenorQue(cero)).isTrue();
        assertThat(deuda.negado()).isEqualTo(saldo);
        assertThat(saldo).isGreaterThan(deuda);
    }

    @Test
    @DisplayName("Las comparaciones contestan que no cuando corresponde")
    void lasComparacionesContestanQueNo() {
        Dinero saldo = Dinero.de("45.00", BOB);
        Dinero cero = Dinero.cero(BOB);

        assertThat(saldo.esCero()).isFalse();
        assertThat(saldo.esNegativo()).isFalse();
        assertThat(cero.esMayorQue(saldo)).isFalse();
        assertThat(saldo.esMenorQue(cero)).isFalse();
    }

    @Test
    @DisplayName("Igualdad: contra null, contra otro tipo y contra otro importe")
    void igualdadEnLosBordes() {
        Dinero saldo = Dinero.de("45.00", BOB);

        assertThat(saldo).isNotEqualTo(null);
        assertThat(saldo).isNotEqualTo("45.00");
        assertThat(saldo).isNotEqualTo(Dinero.de("45.01", BOB));
        assertThat(saldo).isEqualTo(saldo);
    }

    @Test
    @DisplayName("Multiplicar redondea una sola vez y con regla explicita")
    void multiplicaConReglaExplicita() {
        Dinero base = Dinero.de("100.00", BOB);

        assertThat(base.por(new BigDecimal("0.135"), RoundingMode.HALF_UP)).isEqualTo(Dinero.de("13.50", BOB));
        assertThat(base.por(new BigDecimal("0.1234"), RoundingMode.DOWN)).isEqualTo(Dinero.de("12.34", BOB));
    }
}
