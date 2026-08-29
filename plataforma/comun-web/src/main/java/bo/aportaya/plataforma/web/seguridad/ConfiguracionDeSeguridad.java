package bo.aportaya.plataforma.web.seguridad;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Guardia global: **denegar por omision**.
 *
 * <p>Cada servicio valida la firma del JWT el mismo contra el JWKS de identidad
 * (ADR-024). No confia en ninguna cabecera del gateway: si un servicio cree lo que
 * le dice una cabecera, cualquiera que alcance la red interna suplanta a cualquiera.
 * **La red interna no es perimetro de confianza.**
 *
 * <p>Lo que esta abierto sale de {@link Publico}, no de una lista escrita aca. Las
 * unicas rutas que siguen por patron son las que **no pasan por un controlador**: las
 * sondas del orquestador y el JWKS. Todo lo demas lo decide su anotacion, que es la
 * misma que el arranque exige — ver {@link LoQueEstaAbierto}.
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionDeSeguridad {

    private static final AuthorizationManager<RequestAuthorizationContext> CON_SESION =
            AuthenticatedAuthorizationManager.authenticated();

    @Bean
    public LoQueEstaAbierto loQueEstaAbierto(
            // Con el nombre puesto: hay mas de un `RequestMappingHandlerMapping` en el
            // contexto y pedirlo solo por tipo falla por ambiguo. El de las rutas del
            // producto es este.
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mapeo) {
        return new LoQueEstaAbierto(mapeo);
    }

    @Bean
    public SecurityFilterChain cadena(HttpSecurity http, LoQueEstaAbierto abierto) throws Exception {
        return http.csrf(csrf -> csrf.disable()) // API sin sesion de navegador: no hay cookie que falsificar
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas.requestMatchers("/actuator/health/**", "/actuator/info")
                        // Las sondas de vida y de preparacion no llevan
                        // dato de nadie, y el orquestador las consulta sin sesion. Pedirles
                        // token seria pedirle credenciales a Kubernetes para saber si el
                        // proceso respira (ADR-037).
                        .permitAll()
                        .requestMatchers("/.well-known/jwks.json")
                        // La clave PUBLICA con la que los trece verifican la firma
                        // (ADR-024). Pedirle sesion seria pedir sesion para poder
                        // comprobar la sesion. Solo la publica: la privada no sale de
                        // identidad.
                        .permitAll()
                        .anyRequest()
                        // Lo que declara @Publico entra sin sesion; el resto no entra.
                        // Ahi viven las rutas de verificacion publica (CU-61, CU-72,
                        // CU-73, CU-75), que existen para que un tercero SIN cuenta
                        // compruebe el sorteo o un certificado, y las dos por las que
                        // se entra al sistema: el registro (CU-01) y el ingreso
                        // (CU-04), donde por definicion todavia no hay sesion.
                        .access((autenticacion, contexto) -> new AuthorizationDecision(abierto.abierta(
                                        contexto.getRequest())
                                || (autenticacion.get() != null
                                        && autenticacion.get().isAuthenticated()
                                        && !"anonymousUser"
                                                .equals(autenticacion.get().getName())))))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}
