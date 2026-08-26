package bo.aportaya.grupos.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Los pesos con los que se puntúa la compatibilidad de un postulante.
 *
 * <p><b>Son datos con vigencia, no constantes en el codigo.</b> Cambiar cuanto pesa
 * la reputacion frente a la geografia es una decision de producto que se toma un
 * martes; si viviera en un {@code static final}, tomarla costaria un despliegue, y
 * nadie podria explicar despues con que pesos se armo un grupo de hace seis meses.
 */
public record CriterioDeEmparejamiento(
        BigDecimal pesoReputacion,
        BigDecimal pesoMonto,
        BigDecimal pesoGeografia,
        BigDecimal pesoHistorialComun,
        int reputacionMinima,
        int maxMorososPorGrupo) {

    private static final int ESCALA = 4;

    /**
     * El puntaje, entre 0 y 1, con cada dimension ya normalizada por quien la mide.
     *
     * <p>Se divide por la suma de los pesos y no por cuatro: asi, poner un peso en
     * cero desactiva esa dimension en vez de castigar a todo el mundo por igual.
     */
    public BigDecimal puntuar(
            BigDecimal reputacion, BigDecimal monto, BigDecimal geografia, BigDecimal historialComun) {
        BigDecimal suma = pesoReputacion.add(pesoMonto).add(pesoGeografia).add(pesoHistorialComun);
        if (suma.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return pesoReputacion
                .multiply(reputacion)
                .add(pesoMonto.multiply(monto))
                .add(pesoGeografia.multiply(geografia))
                .add(pesoHistorialComun.multiply(historialComun))
                .divide(suma, ESCALA, RoundingMode.HALF_UP);
    }

    /**
     * Proteger a los que ya estan es parte del servicio.
     *
     * <p>Un grupo que llego a su tope de morosos no recibe a nadie mas: el que entra
     * heredaria un riesgo que no eligio, y los que estan verian crecer el suyo.
     */
    public boolean admiteOtroMoroso(int morososActuales) {
        return morososActuales < maxMorososPorGrupo;
    }

    public boolean alcanzaLaReputacion(int reputacionDelPostulante) {
        return reputacionDelPostulante >= reputacionMinima;
    }
}
