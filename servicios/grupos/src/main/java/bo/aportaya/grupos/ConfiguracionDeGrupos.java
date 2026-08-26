package bo.aportaya.grupos;

import bo.aportaya.plataforma.dominio.Ids;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.Outbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lo que este servicio inyecta: reloj, identificadores y su outbox. */
@Configuration
public class ConfiguracionDeGrupos {

    @Bean
    public Reloj reloj() {
        return Reloj.delSistema();
    }

    @Bean
    public Ids ids() {
        return Ids.seguros();
    }

    @Bean
    public Outbox outbox(@Value("${aportaya.esquema}") String esquema) {
        return new Outbox(esquema);
    }
}
