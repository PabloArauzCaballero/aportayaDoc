package bo.aportaya.plataforma.web.clientes;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

/**
 * Los tiempos maximos de toda llamada saliente, puestos una sola vez.
 *
 * <p>Se configura el {@code RestClient.Builder} global en vez de pedirle a cada
 * adaptador que ponga los suyos: catorce servicios con varios clientes cada uno son
 * decenas de lugares donde olvidarse, y el olvido no se ve hasta que un destino se
 * cuelga y arrastra al que pregunta.
 */
@Configuration
public class ConfiguracionDeClientes {

    /** Conectar es rapido o no es: si el destino no acepta en un segundo, esta caido. */
    private static final Duration CONEXION = Duration.ofSeconds(1);

    @Bean
    @ConditionalOnMissingBean
    public RestClientCustomizer tiemposMaximosDeSalida() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(CONEXION);
        fabrica.setReadTimeout(ClienteDeServicio.tiempoMaximo());
        return constructor -> constructor.requestFactory(fabrica);
    }
}
