package bo.aportaya.plataforma.web;

import bo.aportaya.plataforma.web.errores.TraduccionDeRestricciones;
import bo.aportaya.plataforma.web.idempotencia.Idempotencia;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Lo que un servicio da por sentado, cableado de una vez.
 *
 * <p>No hay ningun archivo donde registrar el servicio: cada uno es un proceso y
 * nadie lo anota en una lista. Es el conflicto numero uno del plan de carriles,
 * eliminado por construccion.
 */
@Configuration
@ComponentScan("bo.aportaya.plataforma.web")
public class ConfiguracionComunWeb {

    @Bean
    @ConditionalOnMissingBean
    public TraduccionDeRestricciones traduccionDeRestricciones() {
        return TraduccionDeRestricciones.cargar();
    }

    @Bean
    @ConditionalOnMissingBean
    public Idempotencia idempotencia(@Value("${aportaya.esquema}") String esquema) {
        return new Idempotencia(esquema);
    }
}
