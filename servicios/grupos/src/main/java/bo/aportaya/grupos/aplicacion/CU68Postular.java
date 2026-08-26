package bo.aportaya.grupos.aplicacion;

import bo.aportaya.grupos.dominio.CriterioDeEmparejamiento;
import bo.aportaya.grupos.dominio.MotivoDelPuntaje;
import bo.aportaya.grupos.infraestructura.EmparejamientoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-68 · Postular a un grupo y ser emparejado.
 *
 * <p>El postulante ve **por que** se le propuso ese grupo —monto compatible, gente de
 * su zona, riesgo parecido— y nunca los datos de los demas. Un puntaje sin explicacion
 * es un numero que nadie puede discutir, y un emparejamiento que nadie puede discutir
 * es uno que se acepta por resignacion.
 *
 * <p>Lo que este servicio no sabe llega resuelto: la restriccion vigente vive en
 * `garantia`, el nivel de conocimiento del cliente en `cumplimiento` y la reputacion
 * en `transparencia`. `grupos` no lee esos esquemas (invariante 11); pone el criterio
 * y la decision.
 */
@Service
public class CU68Postular {

    private final Datos datos;
    private final EmparejamientoRepositorio emparejamientos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU68Postular(Datos datos, EmparejamientoRepositorio emparejamientos, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.emparejamientos = emparejamientos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaPostulacion postular(EntradaPostulacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // La restriccion se responde diciendo QUE la levanta: rechazar sin decir
            // como salir deja a alguien fuera sin camino de vuelta.
            if (entrada.tieneRestriccionVigente()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(68, 1),
                        "Tenes una restriccion vigente.",
                        Map.of(
                                "montoQueLaLevanta",
                                entrada.montoQueLevantaLaRestriccion().toString()));
            }
            if (!entrada.kycSuficiente()) {
                throw new ErrorDeNegocio(CodigoError.de(68, 2), "Necesitas elevar tu nivel de verificacion.");
            }
            if (!emparejamientos.hayCuposLibres(dsl, entrada.grupoId())) {
                throw new ErrorDeNegocio(CodigoError.de(68, 4), "Este grupo ya no tiene cupos libres.");
            }
            if (emparejamientos.yaPostulo(dsl, entrada.grupoId(), ctx.usuarioId())) {
                throw new ErrorDeNegocio(CodigoError.de(68, 6), "Ya tenes una postulacion pendiente en este grupo.");
            }

            CriterioDeEmparejamiento criterio = emparejamientos
                    .criterioVigente(dsl, ahora)
                    .orElseThrow(() ->
                            new ErrorDeNegocio(CodigoError.de(68, 5), "No hay criterio de emparejamiento vigente."));

            if (!criterio.alcanzaLaReputacion(entrada.reputacion())) {
                throw new ErrorDeNegocio(CodigoError.de(68, 3), "Tu reputacion todavia no alcanza para este grupo.");
            }
            if (!criterio.admiteOtroMoroso(entrada.morososDelGrupo())) {
                throw new ErrorDeNegocio(CodigoError.de(68, 5), "Este grupo esta cerrado a nuevos ingresos por ahora.");
            }

            BigDecimal puntaje = criterio.puntuar(
                    entrada.afinidadReputacion(),
                    entrada.afinidadMonto(),
                    entrada.afinidadGeografia(),
                    entrada.afinidadHistorial());

            UUID solicitud = emparejamientos.postular(
                    dsl,
                    entrada.grupoId(),
                    ctx.usuarioId(),
                    entrada.cuposSolicitados(),
                    entrada.mensaje(),
                    puntaje,
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "grupos.ingreso_solicitado",
                            "solicitud_ingreso",
                            solicitud,
                            Map.of("grupoId", entrada.grupoId().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaPostulacion(
                    solicitud,
                    puntaje,
                    MotivoDelPuntaje.de(
                            criterio,
                            entrada.afinidadReputacion(),
                            entrada.afinidadMonto(),
                            entrada.afinidadGeografia(),
                            entrada.afinidadHistorial()));
        });
    }

    /**
     * Responder a una propuesta de grupo. Alcanzadas las aceptaciones antes de
     * expirar, la propuesta se materializa en un grupo real.
     */
    @Transactional
    public EstadoPropuesta responder(
            UUID propuestaId,
            UUID postulacionId,
            boolean acepto,
            int aceptacionesNecesarias,
            java.util.Optional<UUID> grupoMaterializado,
            ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var propuesta = emparejamientos
                    .propuesta(dsl, propuestaId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(68, 7), "Esa propuesta no existe."));

            // Expirada vuelve a la bolsa: nadie queda atado a un grupo que no se armo.
            if (ahora.isAfter(propuesta.expiraEn())) {
                throw new ErrorDeNegocio(CodigoError.de(68, 7), "Esa propuesta ya vencio.");
            }
            if (emparejamientos.responder(dsl, propuestaId, postulacionId, acepto, ahora) == 0) {
                throw new ErrorDeNegocio(CodigoError.de(68, 6), "Ya respondiste a esta propuesta.");
            }

            int aceptaciones = emparejamientos.contarAceptaciones(dsl, propuestaId);
            if (aceptaciones >= aceptacionesNecesarias && grupoMaterializado.isPresent()) {
                emparejamientos.materializar(dsl, propuestaId, grupoMaterializado.get(), aceptaciones);
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "grupos.propuesta_materializada",
                                "propuesta_grupo",
                                propuestaId,
                                Map.of("grupoId", grupoMaterializado.get().toString()),
                                UUID.fromString(ctx.traza().id())));
                return new EstadoPropuesta(aceptaciones, true);
            }

            emparejamientos.anotarAceptacion(dsl, propuestaId, aceptaciones);
            return new EstadoPropuesta(aceptaciones, false);
        });
    }

    public record EntradaPostulacion(
            UUID grupoId,
            short cuposSolicitados,
            String mensaje,
            boolean tieneRestriccionVigente,
            BigDecimal montoQueLevantaLaRestriccion,
            boolean kycSuficiente,
            int reputacion,
            int morososDelGrupo,
            BigDecimal afinidadReputacion,
            BigDecimal afinidadMonto,
            BigDecimal afinidadGeografia,
            BigDecimal afinidadHistorial) {}

    public record SalidaPostulacion(UUID solicitudId, BigDecimal puntaje, List<String> motivos) {}

    public record EstadoPropuesta(int aceptaciones, boolean materializada) {}
}
