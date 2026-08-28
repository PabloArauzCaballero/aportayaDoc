package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo.EntradaRetencion;
import bo.aportaya.nucleofinanciero.infraestructura.BloqueoRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.CuentaBilleteraRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-17 · Bloquear saldo por orden de autoridad.
 *
 * <p>Es el unico caso en que una retencion **no tiene fecha de fin**: la levanta la
 * misma autoridad que la puso, y ponerle vencimiento propio seria decidir por un juez
 * cuando se acaba su orden.
 *
 * <p>El documento de respaldo es obligatorio y va con su hash. Inmovilizar la plata de
 * alguien sin poder mostrar despues el papel que lo ordeno es indefendible ante esa
 * persona y ante el supervisor.
 */
@Service
public class CU17BloquearPorAutoridad {

    private static final String MOTIVO = "ORDEN_AUTORIDAD";
    private static final int LARGO_DEL_HASH = 64;

    private final Datos datos;
    private final BloqueoRepositorio bloqueos;
    private final CuentaBilleteraRepositorio cuentas;
    private final CU13RetenerSaldo retenciones;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU17BloquearPorAutoridad(
            Datos datos,
            BloqueoRepositorio bloqueos,
            CuentaBilleteraRepositorio cuentas,
            CU13RetenerSaldo retenciones,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.bloqueos = bloqueos;
        this.cuentas = cuentas;
        this.retenciones = retenciones;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaBloqueo ejecutar(EntradaBloqueo entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        // AP-CU17-03: sin papel no se inmoviliza nada.
        if (entrada.documentoUrl() == null
                || entrada.documentoUrl().isBlank()
                || entrada.hashDocumento() == null
                || entrada.hashDocumento().length() != LARGO_DEL_HASH) {
            throw new ErrorDeNegocio(
                    CodigoError.de(17, 3),
                    "Un bloqueo por autoridad necesita el documento que lo ordena, con su hash.");
        }

        return datos.conContexto(ctx, dsl -> {
            var cuenta = cuentas.bloquear(dsl, entrada.cuentaBilleteraId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(17, 2), "Esa billetera no existe."));

            // AP-CU17-01 · R-BIL-14. Se comprueba antes para dar el codigo del
            // contrato en vez de una violacion cruda de unicidad.
            if (bloqueos.existeOficio(dsl, entrada.numeroOficio())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(17, 1), "Ya hay un bloqueo con el oficio " + entrada.numeroOficio() + ".");
            }

            // El monto: lo que el oficio diga, o TODO el disponible si es total. Un
            // alcance total sobre una cuenta que despues recibe plata no la alcanza,
            // y eso es correcto: el oficio inmoviliza lo que habia.
            Dinero aBloquear = entrada.montoBloqueado().orElse(cuenta.disponible());
            if (aBloquear.esMayorQue(cuenta.disponible())) {
                aBloquear = cuenta.disponible();
            }

            var retencion = retenciones.retenerDentroDe(
                    dsl,
                    new EntradaRetencion(
                            cuenta.id(),
                            aBloquear,
                            MOTIVO,
                            Optional.empty(),
                            Optional.of("BLOQUEO_SALDO"),
                            Optional.empty(),
                            // Sin fecha de fin, y a proposito: la levanta el juez.
                            Optional.empty()),
                    ctx);

            UUID bloqueoId = bloqueos.registrar(
                    dsl,
                    cuenta.id(),
                    retencion.retencionId(),
                    entrada.autoridad(),
                    entrada.tipoOrden(),
                    entrada.numeroOficio(),
                    Optional.of(aBloquear),
                    entrada.alcance(),
                    entrada.documentoUrl(),
                    entrada.hashDocumento(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.saldo_bloqueado_por_autoridad",
                            "bloqueo_saldo",
                            bloqueoId,
                            Map.of(
                                    "cuentaBilleteraId", cuenta.id().toString(),
                                    "numeroOficio", entrada.numeroOficio(),
                                    "autoridad", entrada.autoridad()),
                            UUID.fromString(ctx.traza().id())));

            var despues = cuentas.ver(dsl, cuenta.id()).orElseThrow();
            return new SalidaBloqueo(bloqueoId, retencion.retencionId(), aBloquear, despues.disponible());
        });
    }

    /** Levantar: la misma autoridad lo ordena, y recien ahi vuelve el saldo. */
    @Transactional
    public SalidaLevantamiento levantar(UUID bloqueoId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            if (!bloqueos.levantar(dsl, bloqueoId, ctx.usuarioId(), ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(17, 2), "Ese bloqueo no esta vigente.");
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.bloqueo_levantado",
                            "bloqueo_saldo",
                            bloqueoId,
                            Map.of("levantadoPor", ctx.usuarioId().toString()),
                            UUID.fromString(ctx.traza().id())));
            return new SalidaLevantamiento(bloqueoId, "LEVANTADO");
        });
    }

    public record EntradaBloqueo(
            UUID cuentaBilleteraId,
            String autoridad,
            String tipoOrden,
            String numeroOficio,
            Optional<Dinero> montoBloqueado,
            String alcance,
            String documentoUrl,
            String hashDocumento) {}

    public record SalidaBloqueo(UUID bloqueoId, UUID retencionId, Dinero montoBloqueado, Dinero saldoDisponible) {}

    public record SalidaLevantamiento(UUID bloqueoId, String estado) {}
}
