package bo.aportaya.notificaciones.aplicacion;

import bo.aportaya.notificaciones.dominio.Canal;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La ruta {@code POST /notificaciones} de la Ola 0, y **no es CU-80**.
 *
 * <p>Lo dice su propio contrato: esta operacion recibe el pedido y emite el evento; el
 * despacho lo hace el consumidor, que es CU-80. La diferencia importa: disparar CU-80
 * por API seria una puerta para mandar mensajes a nombre de la entidad sin que ningun
 * hecho del dominio los respalde.
 *
 * <p>Por eso aca no se elige canal ni se renderiza plantilla: se deja el hecho escrito
 * y se responde con los canales que estan encendidos (ADR-035). Cual de ellos termina
 * usandose depende de la supresion y de la verificacion del destinatario, que CU-80
 * comprueba cuando consume el evento.
 */
@Service
public class SolicitarAviso {

    private final Datos datos;
    private final Outbox outbox;

    public SolicitarAviso(Datos datos, Outbox outbox) {
        this.datos = datos;
        this.outbox = outbox;
    }

    @Transactional
    public Salida ejecutar(Entrada entrada, ContextoSesion ctx) {
        UUID notificacionId = UUID.randomUUID();

        return datos.conContexto(ctx, dsl -> {
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "notificaciones.aviso_solicitado",
                            "notificacion",
                            notificacionId,
                            Map.of(
                                    "destinatarioId", entrada.destinatarioId().toString(),
                                    "evento", entrada.evento(),
                                    "datos", String.valueOf(entrada.datos())),
                            UUID.fromString(ctx.traza().id())));

            return new Salida(notificacionId, canalesEncendidos());
        });
    }

    /**
     * Los canales encendidos, en orden estable.
     *
     * <p>Sale de {@link Canal#encendidosPorOmision()} y no de una lista escrita aca:
     * encender un canal apagado es una de las dieciocho prohibiciones, y duplicar la
     * lista seria la forma mas facil de que un dia queden distintas.
     */
    private List<String> canalesEncendidos() {
        return Canal.encendidosPorOmision().stream()
                .map(Canal::name)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    public record Entrada(UUID destinatarioId, String evento, Object datos) {}

    public record Salida(UUID notificacionId, List<String> canales) {}
}
