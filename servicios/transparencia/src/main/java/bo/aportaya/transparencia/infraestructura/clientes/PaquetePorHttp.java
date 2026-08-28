package bo.aportaya.transparencia.infraestructura.clientes;

import bo.aportaya.plataforma.web.clientes.ClienteDeServicio;
import bo.aportaya.transparencia.aplicacion.CU61VerificarSorteo.PaqueteDeSorteo;
import bo.aportaya.transparencia.dominio.puertos.PaquetesDeSorteo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Le pide el paquete a {@code grupos}, por su contrato.
 *
 * <p>Sin respuesta se devuelve vacio, y quien pregunta responde «no verificable». Es lo
 * unico honesto: decir que un sorteo verifica sin haber podido recomputarlo seria
 * justamente la afirmacion que CU-61 existe para reemplazar.
 */
@Component
public class PaquetePorHttp implements PaquetesDeSorteo {

    private final ClienteDeServicio grupos;

    public PaquetePorHttp(RestClient.Builder constructor, @Value("${aportaya.servicios.grupos}") String urlGrupos) {
        this.grupos = new ClienteDeServicio(constructor, urlGrupos, "grupos");
    }

    @Override
    public Optional<PaqueteDeSorteo> de(UUID sorteoId) {
        return grupos.consultar("/grupos/sorteos/" + sorteoId + "/paquete", Paquete.class)
                .map(p -> new PaqueteDeSorteo(
                        sorteoId,
                        p.hashComprometido(),
                        p.semillaRevelada(),
                        p.entropias() == null ? List.of() : p.entropias(),
                        p.metodo(),
                        p.cuposEnOrdenOriginal() == null ? List.of() : p.cuposEnOrdenOriginal(),
                        p.ordenPublicado() == null ? List.of() : p.ordenPublicado()));
    }

    private record Paquete(
            String hashComprometido,
            String semillaRevelada,
            List<String> entropias,
            String metodo,
            List<Integer> cuposEnOrdenOriginal,
            List<Integer> ordenPublicado) {}
}
