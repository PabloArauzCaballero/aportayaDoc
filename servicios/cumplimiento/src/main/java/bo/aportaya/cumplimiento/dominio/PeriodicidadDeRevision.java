package bo.aportaya.cumplimiento.dominio;

import java.time.LocalDate;

/**
 * CU-02 y CU-06 · Cada cuanto se vuelve a mirar a un cliente. Puro.
 *
 * <p>A mas riesgo, mas seguido. Los meses no son constantes de codigo: llegan de
 * configuracion (invariante 10), porque son politica de cumplimiento y cambian sin
 * que cambie el programa.
 */
public record PeriodicidadDeRevision(int mesesRiesgoAlto, int mesesRiesgoMedio, int mesesRiesgoBajo) {

    public int mesesPara(ClasificacionPep.NivelRiesgo nivel) {
        return switch (nivel) {
            case ALTO -> mesesRiesgoAlto;
            case MEDIO -> mesesRiesgoMedio;
            case BAJO -> mesesRiesgoBajo;
        };
    }

    /** La fecha de la proxima revision, que se **guarda** y no se recalcula (invariante 8). */
    public LocalDate proximaDesde(LocalDate ultimaCalificacion, ClasificacionPep.NivelRiesgo nivel) {
        return ultimaCalificacion.plusMonths(mesesPara(nivel));
    }
}
