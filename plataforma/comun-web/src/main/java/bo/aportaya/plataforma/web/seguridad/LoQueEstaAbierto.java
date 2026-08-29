package bo.aportaya.plataforma.web.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Que rutas estan abiertas — las que declaran {@link Publico}, y solo esas.
 *
 * <p><b>La anotacion es la declaracion, y tiene que ser la que manda.</b> Antes la
 * cadena de filtros abria cuatro patrones escritos a mano y {@code @Publico} solo se
 * comprobaba al arrancar: el resultado fue que {@code POST /usuarios} (CU-01) y
 * {@code POST /sesiones} (CU-04) —las dos rutas por las que se entra al sistema, las
 * dos anotadas— devolvian 401. No habia forma de conseguir un token, y por lo tanto
 * no habia forma de usar nada. Dos listas de lo mismo divergen; esta es la unica.
 *
 * <p>Sigue siendo denegar por omision: lo abierto es lo que lleva la anotacion, una
 * ruta nueva nace cerrada, y el arranque ya falla si un endpoint no declara ninguna
 * de las dos.
 *
 * <p>Las rutas se leen **una sola vez** y se guardan. Resolver el manejador en cada
 * peticion, dentro de un filtro, es pedirle al mapeo que haga su trabajo dos veces
 * por peticion. Y se registran en la bitacora al arrancar: quien opera tiene que
 * poder ver que quedo abierto sin leer el codigo.
 */
public class LoQueEstaAbierto {

    private static final Logger BITACORA = LoggerFactory.getLogger(LoQueEstaAbierto.class);
    private static final AntPathMatcher COMPARADOR = new AntPathMatcher();

    private final ObjectProvider<RequestMappingHandlerMapping> mapeo;
    private volatile Set<String> abiertas;

    public LoQueEstaAbierto(ObjectProvider<RequestMappingHandlerMapping> mapeo) {
        this.mapeo = mapeo;
    }

    /** {@code METODO patron}; {@code *} como metodo cuando la ruta no lo restringe. */
    private Set<String> resolver() {
        Set<String> conocidas = abiertas;
        if (conocidas != null) {
            return conocidas;
        }
        RequestMappingHandlerMapping rutas;
        try {
            rutas = mapeo.getIfAvailable();
        } catch (RuntimeException noSePudo) {
            // Falla del lado seguro —nada abierto— pero **lo dice**. Un fallo de
            // resolucion silencioso deja el sistema cerrado sin que nadie entienda
            // por que, que es peor que abrirse: al menos abrirse se nota.
            BITACORA.warn("No se pudo resolver el mapeo de rutas: nada queda abierto", noSePudo);
            return Set.of();
        }
        if (rutas == null) {
            // Todavia no hay mapeo. No se guarda nada: fallar del lado seguro es lo
            // unico aceptable cuando no se sabe, pero volver a preguntar despues es
            // distinto de decidir para siempre que nada esta abierto.
            return Set.of();
        }
        Set<String> halladas = new LinkedHashSet<>();
        rutas.getHandlerMethods().forEach((RequestMappingInfo info, HandlerMethod manejador) -> {
            if (!declaraPublico(manejador)) {
                return;
            }
            var patrones = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues()
                    : info.getPatternValues();
            var metodos = info.getMethodsCondition().getMethods();
            for (String patron : patrones) {
                if (metodos.isEmpty()) {
                    halladas.add("* " + patron);
                } else {
                    metodos.forEach(m -> halladas.add(m.name() + " " + patron));
                }
            }
        });
        BITACORA.info(
                "Rutas sin sesion ({}): {}",
                halladas.size(),
                halladas.isEmpty() ? "ninguna" : String.join(", ", halladas));
        abiertas = halladas;
        return halladas;
    }

    private static boolean declaraPublico(HandlerMethod manejador) {
        Method metodo = manejador.getMethod();
        if (metodo.isAnnotationPresent(Publico.class)) {
            return true;
        }
        // Un @Publico de clase abre sus rutas, salvo las que declaran su propio
        // @Permiso: lo mas especifico gana, igual que en Spring.
        return manejador.getBeanType().isAnnotationPresent(Publico.class) && !metodo.isAnnotationPresent(Permiso.class);
    }

    public boolean abierta(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();
        String contexto = peticion.getContextPath();
        if (contexto != null && !contexto.isEmpty() && ruta.startsWith(contexto)) {
            ruta = ruta.substring(contexto.length());
        }
        for (String entrada : resolver()) {
            int corte = entrada.indexOf(' ');
            String metodo = entrada.substring(0, corte);
            String patron = entrada.substring(corte + 1);
            if (("*".equals(metodo) || metodo.equals(peticion.getMethod())) && COMPARADOR.match(patron, ruta)) {
                return true;
            }
        }
        return false;
    }
}
