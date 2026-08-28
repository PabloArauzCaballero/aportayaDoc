package bo.aportaya.nucleofinanciero.infraestructura.clientes;

import bo.aportaya.nucleofinanciero.dominio.puertos.HechosDeOtrosServicios;
import bo.aportaya.plataforma.web.clientes.ClienteDeServicio;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Le pregunta a {@code grupos} y a {@code aportes}, por sus contratos.
 *
 * <p>Las dos preguntas que frenan un cierre —¿debe algo?, ¿sigue en un pasanaku?— se
 * responden **que si** cuando el servicio no contesta. Cerrar una billetera porque
 * {@code aportes} estaba caido dejaria el aporte impago del lado de los otros del
 * grupo, y esa perdida no es de quien se va: es de los que se quedan (invariante 9).
 *
 * <p>El alias, en cambio, se responde vacio: un destino que no se pudo resolver no es
 * un destino, y mandar la plata «por las dudas» seria peor que rechazar.
 */
@Component
public class HechosPorHttp implements HechosDeOtrosServicios {

    private final ClienteDeServicio grupos;
    private final ClienteDeServicio aportes;

    public HechosPorHttp(
            RestClient.Builder constructor,
            @Value("${aportaya.servicios.grupos}") String urlGrupos,
            @Value("${aportaya.servicios.aportes}") String urlAportes) {
        this.grupos = new ClienteDeServicio(constructor, urlGrupos, "grupos");
        this.aportes = new ClienteDeServicio(constructor, urlAportes, "aportes");
    }

    @Override
    public Optional<UUID> usuarioDelAlias(String alias) {
        return grupos.consultar("/grupos/participantes/alias/" + alias, Alias.class)
                .filter(Alias::existe)
                .map(Alias::usuarioId);
    }

    @Override
    public boolean tieneObligacionesAbiertas(UUID usuarioId) {
        return aportes.consultar("/aportes/participantes/" + usuarioId + "/estado", Estado.class)
                .map(e -> e.obligacionesAbiertas() > 0)
                .orElse(true);
    }

    @Override
    public boolean participaEnGrupoActivo(UUID usuarioId) {
        return grupos.consultar("/grupos/participantes/" + usuarioId + "/actividad", Actividad.class)
                .map(a -> a.gruposActivos() > 0)
                .orElse(true);
    }

    private record Alias(boolean existe, UUID usuarioId) {}

    private record Estado(int obligacionesAbiertas) {}

    private record Actividad(int gruposActivos) {}
}
