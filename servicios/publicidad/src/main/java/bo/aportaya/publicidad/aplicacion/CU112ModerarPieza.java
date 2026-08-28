package bo.aportaya.publicidad.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.publicidad.dominio.DecisionDeModeracion;
import bo.aportaya.publicidad.infraestructura.CreativaRepositorio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-112 · Moderar una pieza creativa.
 *
 * <p>**Moderacion previa, nunca posterior.** Ninguna imagen ni texto de un anunciante
 * llega a un usuario sin que una persona lo haya mirado antes. Lo sostiene el trigger
 * {@code fn_pub_creativa_aprobada}, que rechaza el alta de un anuncio cuya pieza no
 * este APROBADA: aunque este caso de uso tuviera un defecto, no hay camino por el que
 * una pieza sin revisar termine en pantalla.
 *
 * <p>La revision y el estado de la pieza se escriben en la misma transaccion. Separarlos
 * dejaria una ventana en la que la pieza figura aprobada y no hay constancia de quien
 * la aprobo — que es exactamente lo que este caso de uso existe para evitar.
 */
@Service
public class CU112ModerarPieza {

    private final Datos datos;
    private final CreativaRepositorio creativas;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU112ModerarPieza(Datos datos, CreativaRepositorio creativas, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.creativas = creativas;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public UUID subir(EntradaPieza entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> {
            UUID id = creativas.subirPieza(
                    dsl,
                    entrada.anuncianteId(),
                    entrada.titulo(),
                    entrada.texto(),
                    entrada.urlRecurso(),
                    entrada.tipoRecurso(),
                    ahora);
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "publicidad.pieza_creativa_subida",
                            "pieza_creativa",
                            id,
                            Map.of("anuncianteId", entrada.anuncianteId().toString()),
                            UUID.fromString(ctx.traza().id())));
            return id;
        });
    }

    @Transactional
    public Salida moderar(EntradaRevision entrada, ContextoSesion ctx) {
        var decision = new DecisionDeModeracion(entrada.decision(), entrada.motivo());
        if (!decision.esConocida()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(112, 1), "Una revision se aprueba o se rechaza: no hay tercera opcion.");
        }
        // AP-CU112-02 · un rechazo sin motivo deja al anunciante sin nada que corregir.
        if (decision.leFaltaMotivo()) {
            throw new ErrorDeNegocio(CodigoError.de(112, 2), "Un rechazo sin motivo no le dice nada al anunciante.");
        }

        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> {
            var pieza = creativas
                    .bloqueada(dsl, entrada.piezaCreativaId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(112, 1), "Esa pieza creativa no existe."));
            // AP-CU112-01 · una pieza se revisa una vez; la version corregida es otra
            // pieza, con su propia revision.
            if (!"PENDIENTE".equals(pieza.estadoModeracion()) || creativas.tieneRevision(dsl, pieza.id())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(112, 1),
                        "Esa pieza ya esta " + pieza.estadoModeracion()
                                + ": se sube una corregida, no se revisa dos veces.");
            }
            // AP-CU112-03 · R-PUB-05: quien sube no se autoaprueba. El trigger
            // fn_pub_moderador_distinto lo sostiene cuando el anunciante es un
            // organizador; para un socio comercial no puede, y queda declarado.
            creativas.usuarioDelAnunciante(dsl, pieza.anuncianteId()).ifPresent(dueno -> {
                if (dueno.equals(ctx.usuarioId())) {
                    throw new ErrorDeNegocio(CodigoError.de(112, 3), "Quien subio la pieza no la modera (R-PUB-05).");
                }
            });

            UUID revisionId =
                    creativas.revisar(dsl, pieza.id(), ctx.usuarioId(), decision.decision(), entrada.motivo(), ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            decision.rechaza()
                                    ? "publicidad.pieza_creativa_rechazada"
                                    : "publicidad.pieza_creativa_aprobada",
                            "pieza_creativa",
                            pieza.id(),
                            Map.of(
                                    "revisionId", revisionId.toString(),
                                    "decision", decision.decision(),
                                    "motivo", entrada.motivo() == null ? "" : entrada.motivo()),
                            UUID.fromString(ctx.traza().id())));

            return new Salida(revisionId, decision.estadoResultante());
        });
    }

    public record EntradaPieza(UUID anuncianteId, String titulo, String texto, String urlRecurso, String tipoRecurso) {}

    public record EntradaRevision(UUID piezaCreativaId, String decision, String motivo) {}

    public record Salida(UUID revisionId, String estadoModeracion) {}
}
