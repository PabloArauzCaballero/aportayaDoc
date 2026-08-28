package bo.aportaya.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El codigo de error, validado al construirlo. */
class CodigoErrorTest {

    @Test
    @DisplayName("un caso de uso de dos digitos da AP-CUnn-nn")
    void dosDigitos() {
        assertThat(CodigoError.de(41, 2).valor()).isEqualTo("AP-CU41-02");
        assertThat(CodigoError.de(5, 1).valor()).isEqualTo("AP-CU05-01");
    }

    @Test
    @DisplayName("un caso de uso de tres digitos tambien: la boveda tiene del 100 al 114")
    void tresDigitos() {
        // Sin esto, quince casos de uso —ERP y publicidad— tendrian que inventarse otra
        // numeracion, y el codigo que devuelve la API dejaria de ser el que el caso de
        // uso declara en docs/CasosDeUso/.
        assertThat(CodigoError.de(100, 1).valor()).isEqualTo("AP-CU100-01");
        assertThat(CodigoError.de(114, 12).valor()).isEqualTo("AP-CU114-12");
    }

    @Test
    @DisplayName("lo que no tiene la forma se rechaza al construirlo, no al usarlo")
    void formaInvalida() {
        assertThatThrownBy(() -> new CodigoError("AP-CU1-01")).isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> new CodigoError("AP-CU1000-01")).isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> new CodigoError("CU41-02")).isInstanceOf(ErrorDeDominio.class);
        assertThatThrownBy(() -> new CodigoError("AP-CU41-2")).isInstanceOf(ErrorDeDominio.class);
    }
}
