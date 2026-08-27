package bo.aportaya.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.auditoria.aplicacion.CU98PublicarTablero;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-98 · Publicar el tablero de indicadores.
 *
 * <p>Los cuatro criterios de aceptacion, contra PostgreSQL real. Los tres ultimos
 * dependen de columnas que el modelo no tenia hasta este carril —`provisorio`,
 * `casos` y la definicion versionada—: sin ellas, el caso de uso se podia escribir
 * pero no cumplir, que es la peor forma de darlo por hecho.
 */
class CU98Test extends BaseDeAuditoria {

    @Test
    @DisplayName("Dado un mes con todos los cierres cuadrados · Cuando se publica el tablero · Entonces cada indicador"
            + " tiene valor, meta y variación respecto del mes anterior")
    void criterio1() {
        UUID definicion = fixtura.definicion("TASA_DE_MOROSIDAD_81", "RIESGO", "MENOR_ES_MEJOR", 0, "v1");
        fixtura.indicador(definicion, "TASA_DE_MOROSIDAD_81", "2081-01", "5.0000", null, 400, false);
        fixtura.indicador(definicion, "TASA_DE_MOROSIDAD_81", "2081-02", "6.0000", "4.0000", 420, false);

        var salida = tablero("2081-02");

        assertThat(salida.provisorio()).isFalse();
        assertThat(salida.indicadores()).hasSize(1);

        CU98PublicarTablero.Indicador indicador = salida.indicadores().get(0);
        assertThat(indicador.valor()).contains(new BigDecimal("6.0000"));
        assertThat(indicador.meta()).contains(new BigDecimal("4.0000"));
        // Subio de 5 a 6: +20 %. Y como en morosidad MENOS es mejor, no cumple.
        assertThat(indicador.variacionPeriodoAnterior()).contains(new BigDecimal("20.00"));
        assertThat(indicador.cumpleMeta()).contains(false);
        assertThat(indicador.serie())
                .extracting(CU98PublicarTablero.PuntoDeSerie::periodo)
                .containsExactly("2081-01");
    }

    @Test
    @DisplayName("Dado un período todavía abierto · Cuando se consulta el tablero · Entonces los indicadores se marcan"
            + " como provisorios")
    void criterio2() {
        UUID definicion = fixtura.definicion("GRUPOS_ACTIVOS_82", "NEGOCIO", "MAYOR_ES_MEJOR", 0, "v1");
        fixtura.indicador(definicion, "GRUPOS_ACTIVOS_82", "2082-03", "120.0000", "100.0000", 120, true);

        var salida = tablero("2082-03");

        assertThat(salida.provisorio()).isTrue();
        assertThat(salida.indicadores().get(0).provisorio()).isTrue();
        // El valor se publica igual: provisorio no es «oculto», es «no lo cites».
        assertThat(salida.indicadores().get(0).valor()).contains(new BigDecimal("120.0000"));
    }

    @Test
    @DisplayName("Dado un indicador de dimensión GRUPO con menos casos que el mínimo · Cuando se publica · Entonces el"
            + " valor se suprime y se informa el motivo")
    void criterio3() {
        UUID definicion = fixtura.definicion("PUNTUALIDAD_MEDIA_83", "RIESGO", "MAYOR_ES_MEJOR", 5, "v1");
        fixtura.indicador(definicion, "PUNTUALIDAD_MEDIA_83", "2083-02", "92.0000", "90.0000", 3, false);

        var indicador = tablero("2083-02").indicadores().get(0);

        assertThat(indicador.suprimidoPorPrivacidad()).isTrue();
        assertThat(indicador.valor()).isEmpty();
        // El motivo se puede explicar con numeros: 3 casos contra un minimo de 5.
        assertThat(indicador.casos()).contains(3);
        assertThat(indicador.minimoCasos()).isEqualTo(5);
        // Y la serie tambien se calla: publicarla seria dar el valor por la puerta de
        // atras, y la supresion quedaria decorativa.
        assertThat(indicador.serie()).isEmpty();
    }

    @Test
    @DisplayName("Dado un cambio en la definición de un indicador · Cuando se recalcula la serie · Entonces la serie"
            + " anterior sigue disponible y el corte queda señalado")
    void criterio4() {
        UUID v1 = fixtura.definicion("TASA_DE_MOROSIDAD_84", "RIESGO", "MENOR_ES_MEJOR", 0, "v1");
        UUID v2 = fixtura.definicion("TASA_DE_MOROSIDAD_84", "RIESGO", "MENOR_ES_MEJOR", 0, "v2");

        fixtura.indicador(v1, "TASA_DE_MOROSIDAD_84", "2084-01", "5.0000", null, 400, false);
        fixtura.indicador(v2, "TASA_DE_MOROSIDAD_84", "2084-02", "3.0000", null, 420, false);

        var febrero = tablero("2084-02").indicadores().get(0);
        var enero = tablero("2084-01").indicadores().get(0);

        // La serie vieja sigue ahi: el punto de enero se publica igual.
        assertThat(febrero.serie())
                .extracting(CU98PublicarTablero.PuntoDeSerie::periodo)
                .containsExactly("2084-01");
        // Y el corte queda senalado porque cada numero dice con que version se calculo:
        // sin esto, la baja de 5 a 3 se leeria como una mejora y fue un cambio de
        // formula.
        assertThat(febrero.definicionVersion()).isEqualTo("v2");
        assertThat(enero.definicionVersion()).isEqualTo("v1");
    }
}
