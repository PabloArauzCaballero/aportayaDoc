package bo.aportaya.garantia.aplicacion;

import bo.aportaya.garantia.dominio.EstadoDelExpediente;
import bo.aportaya.garantia.dominio.PlazoDeDescargo;
import bo.aportaya.garantia.infraestructura.ExpedienteRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-25 · Declarar el incumplimiento con descargo y evidencia.
 *
 * <p>Declarar que alguien incumplio tiene consecuencias reales: le bloquea grupos, lo
 * pone en una lista, le genera una deuda. Por eso el expediente **nace notificado y con
 * su plazo de descargo guardado** (R-GAR-01): enterarse despues de que el plazo empezo
 * a correr es no tener plazo.
 *
 * <p>La evidencia es **inmutable** ({@code tg_evidencia_inmutable}, R-GAR-02). Una
 * prueba que se puede editar despues de presentada no es una prueba, y el descargo
 * pasaria a ser contra un blanco movil.
 */
@Service
public class CU25DeclararIncumplimiento {

    private final Datos datos;
    private final ExpedienteRepositorio expedientes;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Duration plazoDeDescargo;

    public CU25DeclararIncumplimiento(
            Datos datos, ExpedienteRepositorio expedientes, Outbox outbox, Reloj reloj, Duration plazoDeDescargo) {
        this.datos = datos;
        this.expedientes = expedientes;
        this.outbox = outbox;
        this.reloj = reloj;
        this.plazoDeDescargo = plazoDeDescargo;
    }

    @Transactional
    public SalidaDeclaracion declarar(EntradaDeclaracion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // Un expediente por obligacion: dos por el mismo impago le cobran dos veces
            // la misma falta a la misma persona.
            var previo = expedientes.porObligacion(dsl, entrada.obligacionId());
            if (previo.isPresent()) {
                var expediente = previo.get();
                return new SalidaDeclaracion(
                        expediente.id(),
                        expediente.codigoExpediente(),
                        expedientes.estadoCorriente(dsl, expediente.id()),
                        expediente.fechaLimiteSubsanacion(),
                        false);
            }

            // AP-CU25-01: sin evidencia no se declara. Declarar un incumplimiento sin
            // con que probarlo deja al participante sin nada contra que defenderse.
            if (entrada.descripcionDeLaEvidencia() == null
                    || entrada.descripcionDeLaEvidencia().isBlank()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(25, 1), "No se declara un incumplimiento sin evidencia que lo respalde.");
            }

            int reincidencia = expedientes.expedientesPreviosDe(dsl, entrada.usuarioId()) + 1;
            var plazo = new PlazoDeDescargo(ahora, plazoDeDescargo);

            UUID expedienteId = expedientes.abrir(
                    dsl,
                    entrada.codigoExpediente(),
                    entrada.usuarioId(),
                    entrada.participanteId(),
                    entrada.grupoId(),
                    entrada.periodoId(),
                    entrada.cupoId(),
                    entrada.obligacionId(),
                    entrada.tipo(),
                    entrada.severidad(),
                    entrada.origenDeteccion(),
                    entrada.montoInvolucrado(),
                    entrada.diasMora(),
                    reincidencia,
                    entrada.afectoALaEntrega(),
                    ctx.usuarioId(),
                    ahora,
                    plazo.limite(),
                    ahora);

            // La evidencia queda inmutable desde el momento en que entra.
            expedientes.agregarEvidencia(
                    dsl,
                    expedienteId,
                    entrada.tipoDeEvidencia(),
                    entrada.descripcionDeLaEvidencia(),
                    entrada.urlDeLaEvidencia(),
                    entrada.hashDeLaEvidencia(),
                    null,
                    ctx.usuarioId(),
                    ahora);

            expedientes.registrarTransicion(
                    dsl,
                    expedienteId,
                    null,
                    EstadoDelExpediente.NOTIFICADO,
                    "Incumplimiento declarado y notificado al participante",
                    entrada.montoInvolucrado(),
                    ctx.usuarioId(),
                    false,
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "garantia.incumplimiento_declarado",
                            "registro_incumplimiento",
                            expedienteId,
                            Map.of(
                                    "usuarioId", entrada.usuarioId().toString(),
                                    "grupoId", entrada.grupoId().toString(),
                                    "tipo", entrada.tipo(),
                                    "severidad", entrada.severidad(),
                                    "monto", entrada.montoInvolucrado().toString(),
                                    "puedeDescargarHasta", plazo.limite().toString(),
                                    "reincidencia", Integer.toString(reincidencia)),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaDeclaracion(
                    expedienteId, entrada.codigoExpediente(), EstadoDelExpediente.NOTIFICADO, plazo.limite(), true);
        });
    }

    /** El descargo del participante, dentro de su plazo. */
    @Transactional
    public SalidaDescargo presentarDescargo(EntradaDescargo entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var expediente = expedientes
                    .ver(dsl, entrada.expedienteId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(25, 2), "Ese expediente no existe."));

            // AP-CU25-03: lo presenta el afectado, no un tercero.
            if (!expediente.usuarioId().equals(ctx.usuarioId())) {
                throw new ErrorDeNegocio(CodigoError.de(25, 3), "El descargo lo presenta quien recibio la imputacion.");
            }
            // AP-CU25-02 · R-GAR-01: el plazo se calculo al notificar y se guardo. No
            // se recalcula ni se estira.
            var plazo = new PlazoDeDescargo(expediente.notificadoEn(), plazoDeDescargo);
            if (!plazo.admiteDescargoEn(ahora)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(25, 2),
                        "El plazo para presentar descargo vencio el " + expediente.fechaLimiteSubsanacion() + ".");
            }
            var previo = expedientes.descargoDe(dsl, expediente.id());
            if (previo.isPresent()) {
                return new SalidaDescargo(previo.get(), "PRESENTADO", false);
            }

            UUID descargoId = expedientes.presentarDescargo(
                    dsl,
                    expediente.id(),
                    expediente.participanteId(),
                    entrada.argumento(),
                    entrada.evidenciasJson(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "garantia.descargo_presentado",
                            "descargo_participante",
                            descargoId,
                            Map.of("expedienteId", expediente.id().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaDescargo(descargoId, "PRESENTADO", true);
        });
    }

    /** Resuelve el descargo. Aceptarlo cierra el expediente como SUBSANADO. */
    @Transactional
    public SalidaResolucion resolverDescargo(EntradaResolucion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var expediente = expedientes
                    .bloquear(dsl, entrada.expedienteId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(25, 2), "Ese expediente no existe."));
            var descargoId = expedientes
                    .descargoDe(dsl, expediente.id())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(25, 2), "Ese expediente no tiene descargo que resolver."));

            // AP-CU25-05: quien resuelve no es el imputado.
            if (expediente.usuarioId().equals(ctx.usuarioId())) {
                throw new ErrorDeNegocio(CodigoError.de(25, 5), "Quien resuelve el descargo no puede ser el imputado.");
            }
            if (!expedientes.resolverDescargo(
                    dsl,
                    descargoId,
                    entrada.aceptado() ? "ACEPTADO" : "RECHAZADO",
                    ctx.usuarioId(),
                    entrada.resolucion(),
                    ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(25, 2), "Ese descargo ya fue resuelto.");
            }

            String actual = expedientes.estadoCorriente(dsl, expediente.id());
            String siguiente =
                    entrada.aceptado() ? EstadoDelExpediente.SUBSANADO : EstadoDelExpediente.EN_GESTION_COBRANZA;
            expedientes.registrarTransicion(
                    dsl,
                    expediente.id(),
                    actual,
                    siguiente,
                    entrada.resolucion(),
                    expediente.montoInvolucrado(),
                    ctx.usuarioId(),
                    false,
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "garantia.descargo_resuelto",
                            "descargo_participante",
                            descargoId,
                            Map.of(
                                    "expedienteId", expediente.id().toString(),
                                    "aceptado", Boolean.toString(entrada.aceptado()),
                                    "estadoDelExpediente", siguiente),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaResolucion(descargoId, entrada.aceptado() ? "ACEPTADO" : "RECHAZADO", siguiente);
        });
    }

    public record EntradaDeclaracion(
            String codigoExpediente,
            UUID usuarioId,
            UUID participanteId,
            UUID grupoId,
            UUID periodoId,
            UUID cupoId,
            UUID obligacionId,
            String tipo,
            String severidad,
            String origenDeteccion,
            Dinero montoInvolucrado,
            int diasMora,
            boolean afectoALaEntrega,
            String tipoDeEvidencia,
            String descripcionDeLaEvidencia,
            String urlDeLaEvidencia,
            String hashDeLaEvidencia) {}

    public record SalidaDeclaracion(
            UUID expedienteId,
            String codigoExpediente,
            String estado,
            OffsetDateTime puedeDescargarHasta,
            boolean esNuevo) {}

    public record EntradaDescargo(UUID expedienteId, String argumento, String evidenciasJson) {}

    public record SalidaDescargo(UUID descargoId, String estado, boolean esNuevo) {}

    public record EntradaResolucion(UUID expedienteId, boolean aceptado, String resolucion) {}

    public record SalidaResolucion(UUID descargoId, String estadoDelDescargo, String estadoDelExpediente) {}
}
