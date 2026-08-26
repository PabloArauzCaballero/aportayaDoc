package bo.aportaya.plataforma.web.dinero;

import bo.aportaya.plataforma.dominio.Dinero;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra la frontera del dinero para TODO el servicio, de una vez.
 *
 * <p>Global y no anotacion por campo: un campo que alguien olvide anotar sale como
 * numero, y ese olvido no lo ve ninguna revision.
 */
@Configuration
public class ConfiguracionDeDinero {

    @Bean
    public SimpleModule moduloDeDinero() {
        SimpleModule modulo = new SimpleModule("aportaya-dinero");
        modulo.addSerializer(Dinero.class, new SerializadorDeDinero());
        modulo.addDeserializer(Dinero.class, new DeserializadorDeDinero());
        return modulo;
    }
}
