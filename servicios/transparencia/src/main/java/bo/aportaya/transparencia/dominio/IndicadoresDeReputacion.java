package bo.aportaya.transparencia.dominio;

import java.math.BigDecimal;

/**
 * Los siete indicadores con los que se decide el nivel de confianza de un puntaje.
 *
 * <p>Vive en el dominio y no junto al repositorio que los lee, aunque de ahi salgan:
 * son parte de la entrada de CU-71, y una entrada de caso de uso escrita con un tipo
 * de {@code infraestructura} obliga a la capa web a importar infraestructura para
 * armarla — que es justo lo que {@code ArquitecturaTest} prohibe, y con razon: la
 * frontera deja de poder cambiar sin tocar el almacenamiento.
 */
public record IndicadoresDeReputacion(
        BigDecimal puntualidad,
        BigDecimal incumplimiento,
        BigDecimal montoAportado,
        int gruposCompletados,
        int gruposAbandonados,
        int incumplimientosAbiertos,
        int antiguedadMeses) {}
