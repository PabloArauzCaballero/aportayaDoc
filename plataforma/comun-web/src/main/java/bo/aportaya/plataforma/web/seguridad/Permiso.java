package bo.aportaya.plataforma.web.seguridad;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * El permiso que exige este endpoint, del catalogo sembrado.
 *
 * <p>No se inventa un codigo que el catalogo no tenga: es una de las dieciocho
 * prohibiciones. Y el permiso se verifica **contra el recurso**, no contra el rol —
 * si no, cualquier participante opera sobre cualquier grupo.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Permiso {

    String value();
}
