package bo.aportaya.plataforma.web.seguridad;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca explicita de ruta sin sesion.
 *
 * <p>La guardia deniega por omision. Que lo publico sea lo que lleva una anotacion
 * —y no lo que se olvido de proteger— es toda la diferencia: un endpoint nuevo nace
 * cerrado, y abrirlo exige escribirlo.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Publico {

    /** Por que esta abierto. Sin motivo escrito, no se aprueba en revision. */
    String value();
}
