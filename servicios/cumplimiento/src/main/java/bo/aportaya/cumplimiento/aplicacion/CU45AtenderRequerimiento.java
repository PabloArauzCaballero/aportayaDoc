package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.infraestructura.GobiernoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.RequerimientoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-45 · Atender un requerimiento de autoridad.
 *
 * <p>Un oficio abre la puerta a los datos de una persona que no se entera y no puede
 * oponerse. Por eso las cuatro condiciones:
 *
 * <ul>
 *   <li>**No se actua sin el documento y su hash.** Entregar informacion porque alguien
 *       dijo por telefono que habia un oficio es exactamente lo que la reserva existe
 *       para impedir.
 *   <li>**El alcance ambiguo se aclara antes**, no se interpreta con generosidad. «Toda
 *       la informacion del cliente» no es un alcance: es un cheque en blanco.
 *   <li>**Un oficio, un bloqueo** (R-BIL-14). El bloqueo lo escribe el nucleo
 *       financiero; aca se ata al oficio que lo ordeno.
 *   <li>**Cada acceso queda registrado con el numero de oficio como justificacion**
 *       (R-SEG-02). Sin eso, no hay forma de distinguir una consulta legitima de una
 *       curiosidad.
 * </ul>
 */
@Service
public class CU45AtenderRequerimiento {

    private final Datos datos;
    private final RequerimientoRepositorio requerimientos;
    private final GobiernoRepositorio gobierno;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int minimoDeCaracteresDeAlcance;

    public CU45AtenderRequerimiento(
            Datos datos,
            RequerimientoRepositorio requerimientos,
            GobiernoRepositorio gobierno,
            Outbox outbox,
            Reloj reloj,
            int minimoDeCaracteresDeAlcance) {
        this.datos = datos;
        this.requerimientos = requerimientos;
        this.gobierno = gobierno;
        this.outbox = outbox;
        this.reloj = reloj;
        this.minimoDeCaracteresDeAlcance = minimoDeCaracteresDeAlcance;
    }

    @Transactional
    public SalidaRequerimiento registrar(EntradaRequerimiento entrada, ContextoSesion ctx) {
        // AP-CU45-02. Antes de tocar la base: sin oficio no hay nada que registrar.
        if (entrada.documentoUrl() == null
                || entrada.documentoUrl().isBlank()
                || entrada.hashDocumento() == null
                || entrada.hashDocumento().length() != 64) {
            throw new ErrorDeNegocio(CodigoError.de(45, 2), "No se actua sobre un oficio sin el documento y su hash.");
        }
        // AP-CU45-03. Un alcance de tres palabras entrega mas de lo que la autoridad
        // pidio, y lo entregado no se puede devolver.
        if (entrada.alcance() == null || entrada.alcance().trim().length() < minimoDeCaracteresDeAlcance) {
            throw new ErrorDeNegocio(
                    CodigoError.de(45, 3), "El alcance es ambiguo: se pide aclaracion antes de entregar informacion.");
        }

        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // AP-CU45-01 · invariante 7: se valida antes de escribir.
            var duplicado = requerimientos.porNumeroDeOficio(dsl, entrada.numeroOficio());
            if (duplicado.isPresent()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(45, 1),
                        "El oficio " + entrada.numeroOficio() + " ya esta registrado.",
                        Map.of("requerimientoId", duplicado.get().toString()));
            }

            UUID id = requerimientos.registrar(
                    dsl,
                    entrada.usuarioAfectadoId(),
                    entrada.autoridad(),
                    entrada.numeroOficio(),
                    ahora,
                    // R-CON-01 en espiritu: el plazo llega de la autoridad y se GUARDA.
                    // Recalcularlo despues seria regalarse dias que nadie concedio.
                    entrada.plazoRespuesta(),
                    entrada.alcance(),
                    entrada.documentoUrl(),
                    entrada.hashDocumento());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.requerimiento_recibido",
                            "requerimiento_autoridad",
                            id,
                            Map.of(
                                    "autoridad", entrada.autoridad(),
                                    "numeroOficio", entrada.numeroOficio(),
                                    "plazoRespuesta", entrada.plazoRespuesta().toString(),
                                    "exigeBloqueo", Boolean.toString(entrada.exigeBloqueoDeSaldo()),
                                    // El acceso a los datos del afectado se registra con
                                    // el oficio como justificacion (R-SEG-02). Lo escribe
                                    // auditoria, que es quien posee esa bitacora.
                                    "justificacionDeAcceso", "Oficio " + entrada.numeroOficio()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaRequerimiento(id, null, 0, null);
        });
    }

    /** El bloqueo lo ejecuta el nucleo financiero; aca se ata al oficio (R-BIL-14). */
    @Transactional
    public void anotarBloqueo(UUID requerimientoId, UUID bloqueoSaldoId, ContextoSesion ctx) {
        datos.conContexto(ctx, dsl -> {
            if (!requerimientos.anotarBloqueo(dsl, requerimientoId, bloqueoSaldoId)) {
                throw new ErrorDeNegocio(CodigoError.de(45, 1), "Ese requerimiento no existe.");
            }
            return null;
        });
    }

    /** Se responde SIEMPRE, dentro o fuera de plazo. Fuera de plazo, con hallazgo. */
    @Transactional
    public SalidaRequerimiento responder(EntradaRespuesta entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            if (!requerimientos.responder(
                    dsl, entrada.requerimientoId(), ctx.usuarioId(), entrada.respuestaUrl(), ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(45, 1), "Ese requerimiento ya fue respondido o archivado.");
            }
            boolean fueraDePlazo = ahora.isAfter(entrada.plazoRespuesta());
            if (fueraDePlazo) {
                gobierno.abrirHallazgo(
                        dsl,
                        "OFI-" + entrada.numeroOficio(),
                        "AUTOEVALUACION",
                        "El oficio " + entrada.numeroOficio() + " se respondio despues de su plazo ("
                                + entrada.plazoRespuesta() + ").",
                        "ALTA",
                        "REQUERIMIENTOS_DE_AUTORIDAD",
                        ahora.toLocalDate(),
                        ahora.toLocalDate().plusDays(15));
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.requerimiento_respondido",
                            "requerimiento_autoridad",
                            entrada.requerimientoId(),
                            Map.of(
                                    "numeroOficio", entrada.numeroOficio(),
                                    "fueraDePlazo", Boolean.toString(fueraDePlazo)),
                            UUID.fromString(ctx.traza().id())));
            return new SalidaRequerimiento(entrada.requerimientoId(), null, 0, entrada.respuestaUrl());
        });
    }

    public record EntradaRequerimiento(
            String autoridad,
            String numeroOficio,
            OffsetDateTime plazoRespuesta,
            UUID usuarioAfectadoId,
            String alcance,
            String documentoUrl,
            String hashDocumento,
            boolean exigeBloqueoDeSaldo) {}

    public record EntradaRespuesta(
            UUID requerimientoId, String numeroOficio, OffsetDateTime plazoRespuesta, String respuestaUrl) {}

    public record SalidaRequerimiento(
            UUID requerimientoId, UUID bloqueoSaldoId, int accesosRegistrados, String respuestaUrl) {}
}
