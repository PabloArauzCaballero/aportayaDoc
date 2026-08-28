package bo.aportaya.entregas.infraestructura;

import bo.aportaya.entregas.dominio.puertos.TitularDeLaBilletera;
import bo.aportaya.plataforma.web.clientes.ClienteDeServicio;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Le pregunta a {@code identidad} si el titular declarado es el de la billetera.
 *
 * <p>Cuando {@code identidad} no responde, la respuesta es <b>no</b>. No es pesimismo:
 * es denegar por omision (invariante 9). Dejar registrar una cuenta porque el servicio
 * que verifica estaba caido es exactamente el hueco por el que entra una cuenta ajena.
 */
@Component
public class TitularPorHttp implements TitularDeLaBilletera {

    private final RestClient rest;

    public TitularPorHttp(
            RestClient.Builder constructor, @Value("${aportaya.servicios.identidad}") String urlDeIdentidad) {
        this.rest = constructor.baseUrl(urlDeIdentidad).build();
    }

    @Override
    public boolean esElMismo(UUID usuarioId, String nombreDeclarado, String documentoDeclarado) {
        try {
            var respuesta = rest.post()
                    .uri("/usuarios/{id}/titularidad/verificacion", usuarioId)
                    .headers(ClienteDeServicio::propagarElToken)
                    .body(Map.of("nombreCompleto", nombreDeclarado, "documento", documentoDeclarado))
                    .retrieve()
                    .body(Veredicto.class);
            return respuesta != null && respuesta.coincide();
        } catch (RuntimeException noSePudo) {
            return false;
        }
    }

    private record Veredicto(boolean coincide) {}
}
