package bo.aportaya.plataforma.web.seguridad;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.SinContextoDeSesion;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.web.traza.TrazaDeLaPeticion;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * El JWT verificado, convertido en {@link ContextoSesion}.
 *
 * <p>Es la pieza que faltaba para que exista un controlador autenticado. Hasta ahora
 * los unicos endpoints escritos eran publicos —el alta y el inicio de sesion—, asi
 * que nadie habia necesitado esto; el primero que lo necesita no puede resolverlo en
 * su servicio, porque entonces los catorce lo resolverian cada uno a su manera y la
 * unica pregunta que importa —quien esta operando— tendria catorce respuestas.
 *
 * <p><b>Los datos salen del token, no de una cabecera.</b> Cada servicio valida la
 * firma contra el JWKS de identidad; lo que llega en una cabecera del gateway no se
 * cree, porque quien alcance la red interna puede escribir cualquier cabecera
 * (ADR-024, y el punto 5 de los cinco errores de planes/02 §2.1).
 *
 * <p>La ausencia de sesion es un <b>defecto</b> y no un caso: no se cae al usuario del
 * sistema. Un trabajo programado usa {@code ContextoSesion.deSistema}, que es un rol
 * con sus propias politicas de fila y no una excepcion a las politicas.
 */
@Component
public class SesionDeLaPeticion {

    /** El reclamo con el rol. Uno solo: el rol es del token, no una lista a interpretar. */
    static final String RECLAMO_ROL = "rol";

    static final String RECLAMO_DISPOSITIVO = "dispositivo";

    /**
     * El contexto de quien esta operando.
     *
     * @throws SinContextoDeSesion si no hay token, si no es un JWT o si le falta el
     *     sujeto o el rol. Preferimos fallar acá y no dentro de la transaccion: un
     *     {@code SET LOCAL} con contexto incompleto deja la consulta corriendo sin
     *     politica de fila, y eso no falla — devuelve filas de todos.
     */
    public ContextoSesion actual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof Jwt token)) {
            throw new SinContextoDeSesion("la peticion llego sin token verificado");
        }

        String sujeto = token.getSubject();
        if (sujeto == null || sujeto.isBlank()) {
            throw new SinContextoDeSesion("el token no trae sujeto");
        }

        String rol = token.getClaimAsString(RECLAMO_ROL);
        if (rol == null || rol.isBlank()) {
            // Sin rol no hay contexto (lo dice `ContextoSesion`), y sin contexto no
            // hay politica de fila. Un token valido pero sin rol es un token que no
            // sirve para operar, y decirlo acá es mejor que averiguarlo por una
            // consulta que devolvio de mas.
            throw new SinContextoDeSesion("el token no trae rol");
        }

        return new ContextoSesion(
                usuarioDe(sujeto),
                rol,
                new Traza(TrazaDeLaPeticion.actual()),
                token.getClaimAsString(RECLAMO_DISPOSITIVO));
    }

    private static UUID usuarioDe(String sujeto) {
        try {
            return UUID.fromString(sujeto);
        } catch (IllegalArgumentException noEsUuid) {
            throw new SinContextoDeSesion("el sujeto del token no es un identificador de usuario");
        }
    }
}
