package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.dominio.VigenciaDeRetencion;
import bo.aportaya.nucleofinanciero.dominio.VigenciaDeRetencion.Motivo;
import bo.aportaya.nucleofinanciero.infraestructura.CuentaBilleteraRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.RetencionRepositorio;
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
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-13 · Retener y liberar saldo.
 *
 * <p>Retener es apartar plata que sigue siendo de la persona pero que todavia no puede
 * usar. Por eso el caso de uso no toca los saldos: inserta la retencion y el trigger
 * {@code tg_retencion_sincroniza_saldo} recalcula disponible y retenido desde el
 * libro. Escribirlos a mano seria mantener dos verdades sobre la misma plata.
 *
 * <p>La diferencia entre **liberar** y **ejecutar** importa y no es de nombre: liberar
 * devuelve el importe al disponible —la razon de la retencion desaparecio—, ejecutar
 * lo consume porque la operacion que la motivo si ocurrio. Confundirlas duplica o
 * evapora dinero.
 */
@Service
public class CU13RetenerSaldo {

    private final Datos datos;
    private final CuentaBilleteraRepositorio cuentas;
    private final RetencionRepositorio retenciones;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU13RetenerSaldo(
            Datos datos,
            CuentaBilleteraRepositorio cuentas,
            RetencionRepositorio retenciones,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.cuentas = cuentas;
        this.retenciones = retenciones;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaRetencion retener(EntradaRetencion entrada, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> retenerDentroDe(dsl, entrada, ctx));
    }

    /** La version para usar dentro de la transaccion de otra operacion. */
    public SalidaRetencion retenerDentroDe(DSLContext dsl, EntradaRetencion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        Motivo motivo = Motivo.valueOf(entrada.motivo());

        var cuenta = cuentas.bloquear(dsl, entrada.cuentaBilleteraId())
                .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(13, 1), "Esa billetera no existe."));

        // AP-CU13-01. Se compara contra el DISPONIBLE, no contra el total: lo ya
        // retenido esta comprometido con otra cosa.
        if (entrada.monto().esMayorQue(cuenta.disponible())) {
            throw new ErrorDeNegocio(
                    CodigoError.de(13, 1),
                    "No hay disponible para retener: quedan " + cuenta.disponible() + ".",
                    Map.of(
                            "disponible",
                            cuenta.disponible().toString(),
                            "pedido",
                            entrada.monto().toString()));
        }

        Optional<OffsetDateTime> expira;
        try {
            expira = VigenciaDeRetencion.resolver(
                    motivo, entrada.expiraEn(), retenciones.diasDeVigencia(dsl, cuenta.id()), ahora);
        } catch (IllegalArgumentException fechaMala) {
            throw new ErrorDeNegocio(CodigoError.de(13, 2), fechaMala.getMessage());
        }

        UUID retencionId = retenciones.retener(
                dsl,
                cuenta.id(),
                entrada.monto(),
                motivo.name(),
                entrada.transaccionOrigenId(),
                entrada.referenciaTipo(),
                entrada.referenciaId(),
                expira,
                ahora);

        outbox.emitir(
                dsl,
                new EventoDominio(
                        "nucleo_financiero.saldo_retenido",
                        "retencion_saldo",
                        retencionId,
                        Map.of(
                                "cuentaBilleteraId", cuenta.id().toString(),
                                "monto", entrada.monto().toString(),
                                "motivo", motivo.name()),
                        UUID.fromString(ctx.traza().id())));

        // Se relee la cuenta: el trigger ya corrio y los saldos de `cuenta` son los
        // de antes. Devolver los viejos le mostraria a la app un disponible que ya
        // no existe.
        var despues = cuentas.ver(dsl, cuenta.id()).orElseThrow();
        return new SalidaRetencion(retencionId, despues.disponible(), despues.retenido(), expira.orElse(null));
    }

    /** La razon desaparecio: el importe vuelve al disponible. */
    @Transactional
    public SalidaCierre liberar(UUID retencionId, ContextoSesion ctx) {
        return cerrar(retencionId, "LIBERADA", "nucleo_financiero.retencion_liberada", ctx);
    }

    /** La operacion ocurrio: el importe se consume y no vuelve. */
    @Transactional
    public SalidaCierre ejecutar(UUID retencionId, ContextoSesion ctx) {
        return cerrar(retencionId, "EJECUTADA", "nucleo_financiero.retencion_ejecutada", ctx);
    }

    private SalidaCierre cerrar(UUID retencionId, String estado, String evento, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var retencion = retenciones
                    .ver(dsl, retencionId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(13, 3), "Esa retencion no existe."));

            // AP-CU13-03. El UPDATE condicional decide, no este `if`: el `if` da el
            // mensaje bueno, la condicion del WHERE evita la carrera.
            if (!VigenciaDeRetencion.estaAbierta(retencion.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(13, 3),
                        "Esa retencion ya esta " + retencion.estado() + ": no admite mas operaciones.");
            }
            if (!retenciones.cerrar(dsl, retencionId, estado, Optional.of(ctx.usuarioId()), ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(13, 3), "Otra operacion cerro esa retencion primero.");
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            evento,
                            "retencion_saldo",
                            retencionId,
                            Map.of(
                                    "cuentaBilleteraId", retencion.cuentaId().toString(),
                                    "monto", retencion.monto().toString()),
                            UUID.fromString(ctx.traza().id())));

            var despues = cuentas.ver(dsl, retencion.cuentaId()).orElseThrow();
            return new SalidaCierre(retencionId, estado, despues.disponible(), despues.retenido());
        });
    }

    /** Trabajo programado: lo que vencio deja de retener plata ajena. */
    @Transactional
    public int vencerCaducadas(ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> retenciones.vencerLasCaducadas(dsl, ahora));
    }

    public record EntradaRetencion(
            UUID cuentaBilleteraId,
            Dinero monto,
            String motivo,
            Optional<UUID> transaccionOrigenId,
            Optional<String> referenciaTipo,
            Optional<UUID> referenciaId,
            Optional<OffsetDateTime> expiraEn) {

        public static EntradaRetencion simple(UUID cuentaId, Dinero monto, String motivo) {
            return new EntradaRetencion(
                    cuentaId, monto, motivo, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    public record SalidaRetencion(
            UUID retencionId, Dinero saldoDisponible, Dinero saldoRetenido, OffsetDateTime expiraEn) {}

    public record SalidaCierre(UUID retencionId, String estado, Dinero saldoDisponible, Dinero saldoRetenido) {}
}
