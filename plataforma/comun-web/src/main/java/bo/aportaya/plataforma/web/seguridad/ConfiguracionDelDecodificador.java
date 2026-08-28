package bo.aportaya.plataforma.web.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Como cada servicio valida la firma del token, sin preguntarle a nadie.
 *
 * <p>ADR-024: {@code identidad} es el unico emisor y los otros trece son servidores de
 * recurso que validan localmente contra el JWKS publicado. La clave publica se descarga
 * una vez y se cachea; a partir de ahi autorizar no cuesta un salto de red, que es lo
 * que permite que trece servicios decidan sin depender de la disponibilidad de uno.
 *
 * <p>El bean vive aca y no en cada servicio por la razon de siempre: catorce copias
 * divergen. La direccion del JWKS es lo unico que cambia, y viene por configuracion
 * ({@code aportaya.jwt.jwks-uri}) — si falta, el proceso no levanta, que es lo correcto:
 * un servicio que arranca sin saber contra que validar es un servicio que acepta
 * cualquier token.
 */
@Configuration
public class ConfiguracionDelDecodificador {

    @Bean
    public JwtDecoder decodificador(@Value("${aportaya.jwt.jwks-uri}") String jwksUri) {
        return NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
    }
}
