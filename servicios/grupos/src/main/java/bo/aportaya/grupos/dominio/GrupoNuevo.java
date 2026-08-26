package bo.aportaya.grupos.dominio;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeDominio;
import java.time.LocalDate;

/**
 * Los datos con los que nace un grupo, validados antes de tocar la base.
 *
 * <p>Las tres reglas que estan aca y no en la base son las que el usuario tiene que
 * ver ANTES de confirmar: un grupo con dos periodos, un aporte de cero o una fecha de
 * inicio en el pasado son errores que conviene explicar, no rechazar con el nombre de
 * una restriccion.
 */
public record GrupoNuevo(
        String nombre, Dinero montoDelAporte, String periodicidad, int diaDeCobro, int cupos, LocalDate fechaDeInicio) {

    /** Menos de tres periodos no es un pasanaku: es un prestamo con otro nombre. */
    public static final int PERIODOS_MINIMOS = 3;

    public GrupoNuevo {
        if (cupos < PERIODOS_MINIMOS) {
            throw new ErrorDeDominio(
                    "Un pasanaku necesita al menos %d participantes: con menos es un prestamo con otro nombre"
                            .formatted(PERIODOS_MINIMOS));
        }
        if (montoDelAporte == null || !montoDelAporte.esMayorQue(Dinero.cero(montoDelAporte.moneda()))) {
            throw new ErrorDeDominio("El aporte tiene que ser mayor a cero");
        }
        if (diaDeCobro < 1 || diaDeCobro > 28) {
            // Hasta 28 y no 31: en febrero el 30 no existe, y un cobro que se corre
            // solo algunos meses es un cobro que nadie entiende.
            throw new ErrorDeDominio("El dia de cobro va del 1 al 28, para que exista en todos los meses");
        }
    }

    /** El fondo de cada periodo: lo que va a recibir quien tenga el turno. */
    public Dinero fondoPorPeriodo() {
        return montoDelAporte.por(java.math.BigDecimal.valueOf(cupos), java.math.RoundingMode.HALF_UP);
    }
}
