package bo.aportaya.identidad.dominio;

import java.util.List;
import java.util.Objects;

/**
 * Quien esta entrando, a los efectos de decidir cuantos factores se le exigen.
 *
 * <p>Atomo puro: recibe los ambitos de sus roles vigentes y no consulta nada. Que la
 * decision sea pura es lo que permite probar las siete combinaciones en milisegundos
 * en vez de montar siete usuarios en la base.
 */
public record PerfilDeAcceso(List<String> ambitosDeRolesVigentes) {

    /** Un rol de este ambito convierte al usuario en operador. */
    public static final String AMBITO_OPERADOR = "GLOBAL";

    public PerfilDeAcceso {
        ambitosDeRolesVigentes = List.copyOf(Objects.requireNonNull(ambitosDeRolesVigentes, "ambitos"));
    }

    public static PerfilDeAcceso participante() {
        return new PerfilDeAcceso(List.of());
    }

    /**
     * Verdadero si alguno de sus roles vigentes es de ambito {@code GLOBAL}.
     *
     * <p>Lo que para el participante es una comodidad razonable —arriesga lo suyo—
     * para el operador convierte el robo del equipo en el robo del rol.
     */
    public boolean esOperador() {
        return ambitosDeRolesVigentes.contains(AMBITO_OPERADOR);
    }
}
