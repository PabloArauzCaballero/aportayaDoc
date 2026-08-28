package bo.aportaya.plataforma.web.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Guardia global: **denegar por omision**.
 *
 * <p>Cada servicio valida la firma del JWT el mismo contra el JWKS de identidad
 * (ADR-024). No confia en ninguna cabecera del gateway: si un servicio cree lo que
 * le dice una cabecera, cualquiera que alcance la red interna suplanta a cualquiera.
 * **La red interna no es perimetro de confianza.**
 */
@Configuration
@EnableWebSecurity
public class ConfiguracionDeSeguridad {

    @Bean
    public SecurityFilterChain cadena(HttpSecurity http) throws Exception {
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
                        .requestMatchers("/api/v1/publico/**", "/api/v1/verificar/**")
                        // Son las rutas de verificacion publica
                        // (CU-61, CU-72, CU-73, CU-75). Existen para que un tercero SIN cuenta
                        // compruebe el sorteo, la cadena de bloques o un certificado: exigirles
                        // sesion las vaciaria de sentido. Van noindex y no exponen nada mas que
                        // lo que el titular eligio publicar.
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}
