package bo.aportaya.transparencia;

import bo.aportaya.transparencia.dominio.SenalDeRiesgo;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Lo que CU-97 necesita repetir en cada prueba: el mapa de codigos y las metricas
 * armadas. Vive aparte para que las pruebas digan que comprueban y no como se arma el
 * escenario.
 */
final class EscenarioDeRiesgo {

    /** De metrica a codigo de alerta: es catalogo, no una constante escondida. */
    static final Map<String, String> CODIGOS = Map.of(
            "TASA_PAGO_EN_TERMINO", "GRUPO_INVIABLE",
            "MORA_CONCENTRADA", "MORA_CONCENTRADA",
            "RETIRO_MASIVO", "RETIRO_MASIVO",
            "CAIDA_ABRUPTA_SCORE", "CAIDA_ABRUPTA_SCORE");

    private EscenarioDeRiesgo() {}

    /** Tasa de pago en termino: menor es peor, y por eso `mayorEsPeor` va en false. */
    static SenalDeRiesgo.Metrica tasaDePago(String valor, String umbral) {
        return new SenalDeRiesgo.Metrica(
                "TASA_PAGO_EN_TERMINO", new BigDecimal(valor), "RATIO", new BigDecimal(umbral), false);
    }

    /** Mora concentrada en una sola persona: mayor es peor. */
    static SenalDeRiesgo.Metrica moraConcentrada(String valor, String umbral) {
        return new SenalDeRiesgo.Metrica(
                "MORA_CONCENTRADA", new BigDecimal(valor), "RATIO", new BigDecimal(umbral), true);
    }

    /** Caida del puntaje, en puntos: mayor es peor. */
    static SenalDeRiesgo.Metrica caidaDePuntaje(String valor, String umbral) {
        return new SenalDeRiesgo.Metrica(
                "CAIDA_ABRUPTA_SCORE", new BigDecimal(valor), "PUNTOS", new BigDecimal(umbral), true);
    }
}
