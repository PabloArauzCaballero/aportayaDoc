package bo.aportaya.notificaciones;

import bo.aportaya.notificaciones.dominio.Canal;
import bo.aportaya.notificaciones.dominio.VentanaDeEnvio;
import java.time.LocalTime;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Por que canales sale un aviso, y a que horas. */
@Configuration
public class ConfiguracionDeNotificaciones {

    /**
     * Los canales encendidos.
     *
     * <p>Sale de {@link Canal#encendidosPorOmision()} y no de una lista escrita aca:
     * **encender un canal apagado es una de las dieciocho prohibiciones**, y duplicar la
     * lista seria la forma mas facil de que un dia queden distintas. La configuracion
     * puede apagar uno de estos; nunca encender uno que no este.
     */
    @Bean
    public Set<Canal> canalesEncendidos() {
        return Canal.encendidosPorOmision();
    }

    /**
     * La franja en la que se manda un aviso que no es obligatorio.
     *
     * <p>Fuera de ella el envio se reprograma, no se descarta: nadie recibe publicidad a
     * las tres de la manana, y nadie deja de recibir el aviso de que le vence una cuota.
     */
    @Bean
    public VentanaDeEnvio ventanaDeEnvio(
            @Value("${aportaya.ventana-de-envio.desde}") String desde,
            @Value("${aportaya.ventana-de-envio.hasta}") String hasta) {
        return new VentanaDeEnvio(LocalTime.parse(desde), LocalTime.parse(hasta));
    }
}
