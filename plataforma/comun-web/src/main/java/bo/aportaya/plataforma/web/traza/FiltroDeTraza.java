package bo.aportaya.plataforma.web.traza;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Toma el {@code X-Request-Id} que viene de NGINX, o crea uno si no viene, y lo
 * devuelve en la respuesta.
 *
 * <p>Va primero de todo: si un error ocurre antes de este filtro, el cuerpo del
 * {@code 500} sale sin traza y el incidente queda sin hilo del que tirar.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroDeTraza extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        try {
            Traza.fijar(peticion.getHeader(Traza.CABECERA));
            respuesta.setHeader(Traza.CABECERA, Traza.actual());
            cadena.doFilter(peticion, respuesta);
        } finally {
            // El hilo vuelve al pool: dejar el MDC puesto le presta la traza de esta
            // peticion a la siguiente, y las dos quedan mal atribuidas.
            Traza.limpiar();
        }
    }
}
