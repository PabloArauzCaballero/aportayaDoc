package bo.aportaya.identidad.dominio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * La union de los permisos de los roles VIGENTES a una fecha: ni revocados, ni
 * vencidos.
 *
 * <p>Atomo puro y con la fecha por parametro, no {@code now()}. Es lo que hace que
 * «el permiso efectivo de cualquier operador es reconstruible a cualquier fecha
 * pasada» sea una propiedad comprobable y no una aspiracion.
 */
public final class PermisosEfectivos {

    private PermisosEfectivos() {}

    public static Set<String> de(List<AsignacionVigente> asignaciones, OffsetDateTime enLaFecha) {
        Set<String> permisos = new TreeSet<>();
        for (AsignacionVigente asignacion : asignaciones) {
            if (asignacion.vigenteEn(enLaFecha)) {
                permisos.addAll(asignacion.permisos());
            }
        }
        return Set.copyOf(permisos);
    }

    /** Una asignacion con su ventana de vigencia y los permisos de su rol. */
    public record AsignacionVigente(
            OffsetDateTime otorgadaEn,
            Optional<OffsetDateTime> vigenteHasta,
            Optional<OffsetDateTime> revocadaEn,
            Set<String> permisos) {

        public boolean vigenteEn(OffsetDateTime momento) {
            if (momento.isBefore(otorgadaEn)) {
                return false;
            }
            if (revocadaEn.map(momento::isAfter).orElse(false)) {
                return false;
            }
            // Vencida no es borrada: sigue en la tabla y deja de contar sola.
            return vigenteHasta.map(hasta -> !momento.isAfter(hasta)).orElse(true);
        }
    }
}
