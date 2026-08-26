package bo.aportaya.plataforma.web.seguridad;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Si un endpoint no declara {@link Permiso} ni {@link Publico}, **el proceso no
 * levanta**.
 *
 * <p>Es deliberado que rompa el arranque y no que avise: un endpoint sin decision de
 * autenticacion queda abierto y nadie lo nota, y encontrarlo en produccion cuesta
 * infinitamente mas que encontrarlo al desplegar.
 */
@Component
public class TodoEndpointDecideSuAcceso {

    private final RequestMappingHandlerMapping rutas;

    public TodoEndpointDecideSuAcceso(RequestMappingHandlerMapping rutas) {
        this.rutas = rutas;
    }

    @PostConstruct
    public void verificar() {
        List<String> sinDecision = new ArrayList<>();
        rutas.getHandlerMethods().forEach((RequestMappingInfo info, HandlerMethod manejador) -> {
            if (esDeInfraestructura(manejador) || decideSuAcceso(manejador)) {
                return;
            }
            sinDecision.add(info + " -> " + manejador.getShortLogMessage());
        });

        if (!sinDecision.isEmpty()) {
            throw new IllegalStateException("Estos endpoints no declaran @Permiso ni @Publico, asi que no arranco:\n"
                    + String.join("\n", sinDecision.stream().map(r -> "  " + r).toList()));
        }
    }

    private boolean decideSuAcceso(HandlerMethod manejador) {
        Method metodo = manejador.getMethod();
        Class<?> clase = manejador.getBeanType();
        return metodo.isAnnotationPresent(Permiso.class)
                || metodo.isAnnotationPresent(Publico.class)
                || clase.isAnnotationPresent(Permiso.class)
                || clase.isAnnotationPresent(Publico.class);
    }

    /** Los de Spring: actuator y el manejador de errores no son endpoints del dominio. */
    private boolean esDeInfraestructura(HandlerMethod manejador) {
        String paquete = manejador.getBeanType().getPackageName();
        return paquete.startsWith("org.springframework");
    }
}
