package bo.aportaya.grupos.aplicacion;

import bo.aportaya.grupos.dominio.InvitacionAdmisible;
import bo.aportaya.grupos.infraestructura.InvitacionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Ids;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-69 · Invitar a un contacto.
 *
 * <p>Si el destinatario esta suprimido **no se envia nada y se responde como si
 * hubiera salido bien**. Decir «esa persona pidio no recibir mensajes» ya cuenta algo
 * de ella a quien no tiene por que saberlo, y quien invita no necesita esa
 * informacion para nada.
 *
 * <p>El token es de un solo uso, y lo garantiza el {@code WHERE estado = 'ENVIADA'}
 * del {@code UPDATE}: la segunda aceptacion actualiza cero filas. Comprobarlo con un
 * {@code SELECT} previo dejaria pasar dos aceptaciones simultaneas.
 */
@Service
public class CU69Invitar {

    private static final Duration VIGENCIA = Duration.ofDays(7);

    private final Datos datos;
    private final InvitacionRepositorio invitaciones;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Ids ids;

    public CU69Invitar(Datos datos, InvitacionRepositorio invitaciones, Outbox outbox, Reloj reloj, Ids ids) {
        this.datos = datos;
        this.invitaciones = invitaciones;
        this.outbox = outbox;
        this.reloj = reloj;
        this.ids = ids;
    }

    @Transactional
    public Resultado invitar(EntradaInvitacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var impedimento = InvitacionAdmisible.impedimento(
                    invitaciones.hayCuposLibres(dsl, entrada.grupoId()),
                    entrada.destinatarioSuprimido(),
                    entrada.yaEsParticipante(),
                    0,
                    entrada.topeDeReenvios(),
                    invitaciones.emisorHabilitado(dsl, entrada.grupoId(), ctx.usuarioId()));

            if (impedimento.isPresent()) {
                var motivo = impedimento.get();
                if (motivo.seRespondeComoExito()) {
                    // Nada se envia y nada se escribe, pero la respuesta no lo delata.
                    return new Resultado(Optional.empty(), motivo.mensaje());
                }
                throw new ErrorDeNegocio(CodigoError.de(69, motivo.numero()), motivo.mensaje());
            }

            UUID invitacion = invitaciones.crear(
                    dsl,
                    entrada.grupoId(),
                    entrada.telefonoInvitado(),
                    entrada.nombreSugerido(),
                    ctx.usuarioId(),
                    // El token lo emite `identidad`, que es quien posee
                    // token_verificacion; aca solo se recibe su identificador. La
                    // clave foranea cruza esquemas y la verifica el motor.
                    entrada.tokenId(),
                    entrada.canal(),
                    ahora,
                    ahora.plus(VIGENCIA));

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "grupos.invitacion_enviada",
                            "invitacion",
                            invitacion,
                            Map.of("grupoId", entrada.grupoId().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new Resultado(Optional.of(invitacion), "Invitacion enviada.");
        });
    }

    /** Aceptar consume el token: la segunda vez no queda nada que consumir. */
    @Transactional
    public void aceptar(UUID invitacionId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        datos.conContexto(ctx, dsl -> {
            if (invitaciones.aceptar(dsl, invitacionId, ahora) == 0) {
                throw new ErrorDeNegocio(CodigoError.de(69, 5), "Esa invitacion ya no es valida.");
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "grupos.invitacion_aceptada",
                            "invitacion",
                            invitacionId,
                            Map.of("invitacionId", invitacionId.toString()),
                            UUID.fromString(ctx.traza().id())));
            return null;
        });
    }

    /** Insistir tres veces es recordar; insistir diez es acoso. */
    @Transactional
    public void reenviar(UUID invitacionId, int topeDeReenvios, ContextoSesion ctx) {
        datos.conContexto(ctx, dsl -> {
            var invitacion = invitaciones
                    .porId(dsl, invitacionId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(69, 5), "Esa invitacion no existe."));
            if (invitacion.envios() >= topeDeReenvios) {
                throw new ErrorDeNegocio(CodigoError.de(69, 4), InvitacionAdmisible.Motivo.TOPE_REENVIOS.mensaje());
            }
            invitaciones.reenviar(dsl, invitacionId);
            return null;
        });
    }

    public record EntradaInvitacion(
            UUID grupoId,
            String telefonoInvitado,
            String nombreSugerido,
            String canal,
            boolean destinatarioSuprimido,
            boolean yaEsParticipante,
            int topeDeReenvios,
            UUID tokenId) {}

    public record Resultado(Optional<UUID> invitacionId, String mensaje) {}
}
