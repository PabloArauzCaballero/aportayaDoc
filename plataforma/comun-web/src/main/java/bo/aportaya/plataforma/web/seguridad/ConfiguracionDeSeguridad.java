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
                        .permitAll()
                        // Las unicas rutas publicas del sistema, y tienen un solo dueno.
                        .requestMatchers("/api/v1/publico/**", "/api/v1/verificar/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}
