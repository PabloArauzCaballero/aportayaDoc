package bo.aportaya.plataforma.dominio;

import java.time.LocalDate;

/**
 * Que dias no cuentan. Se inyecta porque los feriados son un dato sembrado con
 * alcance —nacional, departamental, del sector financiero—, no una constante.
 */
@FunctionalInterface
public interface CalendarioHabil {

    boolean esNoHabil(LocalDate fecha);

    default boolean esHabil(LocalDate fecha) {
        return !esNoHabil(fecha);
    }
}
