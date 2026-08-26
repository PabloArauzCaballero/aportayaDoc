package bo.aportaya.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Los atomos chicos que sostienen la traza, la idempotencia y el reloj. */
class IdentificadoresTest {

    @Test
    @DisplayName("Un codigo de error tiene la forma AP-CU<NN>-<nn> o no existe")
    void elCodigoDeErrorTieneUnaSolaForma() {
        assertThat(CodigoError.de(1, 3)).hasToString("AP-CU01-03");
        assertThat(new CodigoError("AP-CU21-00").valor()).isEqualTo("AP-CU21-00");

        assertThatThrownBy(() -> new CodigoError("AP-CU1-3")).isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> new CodigoError("R-BIL-04")).isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> new CodigoError("")).isInstanceOf(ErrorDeDominio.class);
    }

    @Test
    @DisplayName("La clave de idempotencia se deriva del hecho, no del reintento")
    void laClaveSaleDelHecho() {
        UUID obligacion = UUID.fromString("00000000-0000-4000-8000-0000000000aa");

        assertThat(ClaveIdempotencia.deHecho("aporte", obligacion))
                .isEqualTo(ClaveIdempotencia.deHecho("aporte", obligacion));
        assertThat(ClaveIdempotencia.deHecho("aporte", obligacion)).hasToString("aporte:" + obligacion);
    }

    @Test
    @DisplayName("Una clave en blanco o desmedida no distingue nada")
    void claveInvalida() {
        assertThatThrownBy(() -> new ClaveIdempotencia(" ")).isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> new ClaveIdempotencia("x".repeat(121))).isInstanceOf(ErrorDeDominio.class);
    }

    @Test
    @DisplayName("Una traza en blanco no traza nada")
    void trazaEnBlanco() {
        assertThatThrownBy(() -> new Traza("")).isInstanceOf(ErrorDeDominio.class);
    }

    @Test
    @DisplayName("Los identificadores no se repiten y no se adivinan")
    void losIdentificadoresNoSeRepiten() {
        Ids ids = Ids.seguros();
        Set<UUID> vistos = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            assertThat(vistos.add(ids.nuevo())).isTrue();
        }
        assertThat(ids.nuevo().version()).isEqualTo(4);
        assertThat(Traza.nueva(ids).id()).isNotBlank();
    }

    @Test
    @DisplayName("El reloj se inyecta: fijo para probar, del sistema para correr")
    void elRelojSeInyecta() {
        Instant momento = Instant.parse("2026-03-06T12:00:00Z");

        assertThat(Reloj.fijo(momento).ahora()).isEqualTo(momento);
        assertThat(Reloj.fijo(momento).hoy()).isEqualTo(LocalDate.of(2026, 3, 6));
        assertThat(Reloj.delSistema().ahora()).isNotNull();
    }
}
