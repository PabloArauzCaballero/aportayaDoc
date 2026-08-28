package bo.aportaya.tarifas.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.tarifas.dominio.SegmentoAplicable;
import bo.aportaya.tarifas.infraestructura.SegmentoRepositorio;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-36 · Segmentar comercialmente y aplicar precio diferenciado.
 *
 * <p>Todo precio cobrado tiene que ser reconstruible: tarifario, segmento, promocion y
 * exencion, cada uno con su identificador. Un precio mas bajo sin explicacion es
 * indistinguible de un error, y cuando alguien pregunte por que pago eso, no hay
 * respuesta.
 */
@Service
public class CU36ResolverPrecio {

    /**
     * Hechos que **no** pueden entrar en un criterio de precio.
     *
     * <p>Un criterio de precio no se apoya en datos sensibles ni en categorias
     * protegidas. No es una opinion: cobrarle distinto a alguien por su genero, su
     * origen o su salud es discriminacion, y no deja de serlo porque lo decida un JSON.
     */
    private static final Set<String> HECHOS_PROHIBIDOS = Set.of(
            "genero", "sexo", "nacionalidad", "origen", "religion", "estado_civil", "discapacidad", "salud", "edad");

    private final Datos datos;
    private final SegmentoRepositorio segmentos;
    private final Outbox outbox;

    public CU36ResolverPrecio(Datos datos, SegmentoRepositorio segmentos, Outbox outbox) {
        this.datos = datos;
        this.segmentos = segmentos;
        this.outbox = outbox;
    }

    @Transactional
    public SalidaSegmento crear(EntradaSegmento entrada, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            // AP-CU36-05: se comprueba antes de escribir. Un criterio discriminatorio
            // que llega a la tabla ya se aplico a alguien.
            var prohibido = entrada.criterio().keySet().stream()
                    .filter(hecho -> HECHOS_PROHIBIDOS.contains(hecho.toLowerCase(java.util.Locale.ROOT)))
                    .findFirst();
            if (prohibido.isPresent()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(36, 5),
                        "El criterio usa «" + prohibido.get()
                                + "»: un precio no se puede apoyar en datos sensibles ni categorias protegidas.");
            }
            // AP-CU36-01: el criterio tiene que ser evaluable. Uno vacio califica a
            // todo el mundo, que es lo mismo que no tener segmento.
            if (entrada.criterio().isEmpty()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(36, 1), "Un segmento sin criterio califica a todos: no es un segmento.");
            }
            // AP-CU36-02: la ambiguedad se resuelve al DEFINIR, no al cobrar. Dos
            // segmentos con la misma prioridad hacen que dos usuarios iguales paguen
            // distinto segun el orden en que la base devuelva las filas.
            if (segmentos.hayPrioridadOcupada(dsl, entrada.prioridad())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(36, 2), "Ya hay un segmento activo con prioridad " + entrada.prioridad() + ".");
            }

            UUID id = segmentos.crear(
                    dsl, entrada.codigo(), entrada.descripcion(), aJson(entrada.criterio()), entrada.prioridad());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "tarifas.segmento_publicado",
                            "segmento_comercial",
                            id,
                            Map.of("codigo", entrada.codigo(), "prioridad", Integer.toString(entrada.prioridad())),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaSegmento(id, entrada.codigo(), entrada.prioridad());
        });
    }

    /**
     * Que segmento rige para un usuario, y por que.
     *
     * <p>No escribe: es una consulta. El segmento se guarda cuando se cotiza, en la
     * {@code cotizacion_comision} — seis meses despues hay que poder decir por que pago
     * eso, y la respuesta no puede depender de que los criterios no hayan cambiado.
     */
    @Transactional(readOnly = true)
    public SegmentoAplicable.Eleccion resolver(Map<String, Integer> hechosDelUsuario, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            List<SegmentoAplicable.Candidato> candidatos = segmentos.activos(dsl);
            return SegmentoAplicable.elegir(candidatos, hechosDelUsuario);
        });
    }

    private String aJson(Map<String, Integer> criterio) {
        StringBuilder json = new StringBuilder("{");
        boolean primero = true;
        for (var entrada : criterio.entrySet()) {
            if (!primero) {
                json.append(',');
            }
            json.append('"')
                    .append(entrada.getKey().replace("\"", "\\\""))
                    .append("\":")
                    .append(entrada.getValue());
            primero = false;
        }
        return json.append('}').toString();
    }

    public record EntradaSegmento(String codigo, String descripcion, Map<String, Integer> criterio, int prioridad) {}

    public record SalidaSegmento(UUID segmentoId, String codigo, int prioridad) {}
}
