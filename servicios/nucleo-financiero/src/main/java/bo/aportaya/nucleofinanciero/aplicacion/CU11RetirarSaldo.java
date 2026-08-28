package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo.EntradaRetencion;
import bo.aportaya.nucleofinanciero.aplicacion.CU40EvaluarLimites.EntradaLimites;
import bo.aportaya.nucleofinanciero.dominio.CondicionesDeRetiro;
import bo.aportaya.nucleofinanciero.dominio.CondicionesDeRetiro.Situacion;
import bo.aportaya.nucleofinanciero.dominio.CondicionesDeRetiro.Veredicto;
import bo.aportaya.nucleofinanciero.dominio.CostoDeOperacion;
import bo.aportaya.nucleofinanciero.infraestructura.CuentaBilleteraRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.LibroDeBilletera;
import bo.aportaya.nucleofinanciero.infraestructura.LibroDeBilletera.Pata;
import bo.aportaya.nucleofinanciero.infraestructura.OrdenRetiroRepositorio;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-11 · Retirar saldo.
 *
 * <p>**Retencion primero, pago despues. Nunca al reves.** Si se instruyera el pago
 * antes de retener, entre las dos cosas la persona podria gastar el mismo saldo en
 * otra operacion y el retiro saldria contra un disponible que ya no existe. Retener
 * primero cuesta un paso mas y cierra ese hueco entero.
 *
 * <p>El proveedor se instruye **fuera de la transaccion** (invariante 6): una llamada
 * de red adentro mantiene abierta la transaccion tanto como tarde el proveedor, y si
 * el timeout llega, se revierte una retencion que el proveedor quiza ya acepto.
 */
@Service
public class CU11RetirarSaldo {

    private static final String CONCEPTO = "RETIRO";
    private static final String MOTIVO_RETENCION = "COMISION_PENDIENTE";

    private final Datos datos;
    private final CuentaBilleteraRepositorio cuentas;
    private final OrdenRetiroRepositorio ordenes;
    private final CU13RetenerSaldo retenciones;
    private final CU40EvaluarLimites limites;
    private final LibroDeBilletera libro;
    private final Outbox outbox;
    private final Reloj reloj;
    private final UUID cuentaPuenteDeCustodia;

    public CU11RetirarSaldo(
            Datos datos,
            CuentaBilleteraRepositorio cuentas,
            OrdenRetiroRepositorio ordenes,
            CU13RetenerSaldo retenciones,
            CU40EvaluarLimites limites,
            LibroDeBilletera libro,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.custodia.cuenta-puente}") UUID cuentaPuenteDeCustodia) {
        this.datos = datos;
        this.cuentas = cuentas;
        this.ordenes = ordenes;
        this.retenciones = retenciones;
        this.limites = limites;
        this.libro = libro;
        this.outbox = outbox;
        this.reloj = reloj;
        this.cuentaPuenteDeCustodia = cuentaPuenteDeCustodia;
    }

    @Transactional
    public SalidaRetiro solicitar(EntradaRetiro entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var yaExiste = ordenes.porClaveIdempotencia(dsl, entrada.claveIdempotencia());
            if (yaExiste.isPresent()) {
                var orden = ordenes.ver(dsl, yaExiste.get()).orElseThrow();
                return new SalidaRetiro(
                        orden.id(),
                        orden.estado(),
                        entrada.costo(),
                        orden.neto(),
                        orden.retencionId().orElse(null));
            }

            var cuenta = cuentas.bloquear(dsl, entrada.cuentaBilleteraId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(11, 1), "Esa billetera no existe."));
            var instrumento = ordenes.instrumento(dsl, entrada.instrumentoDestinoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(11, 4), "Esa cuenta de destino no existe."));

            // Las condiciones duras, todas juntas y en orden: primero lo que la
            // persona puede resolver, despues lo del sistema.
            Veredicto veredicto = CondicionesDeRetiro.evaluar(
                    new Situacion(
                            entrada.mfaVerificado(),
                            cuenta.disponible(),
                            entrada.monto(),
                            instrumento.usuarioId().equals(cuenta.usuarioId()) && instrumento.titularCoincide(),
                            instrumento.verificado(),
                            instrumento.bloqueadoHasta(),
                            ordenes.hayBloqueoDeAutoridad(dsl, cuenta.id()),
                            ordenes.encajeCumplido(dsl, cuenta.moneda().name())),
                    ahora);
            if (!veredicto.permitido()) {
                throw new ErrorDeNegocio(codigoDe(veredicto.codigo()), veredicto.motivo());
            }

            limites.exigirDentroDe(dsl, new EntradaLimites(cuenta.id(), CONCEPTO, entrada.monto()), ctx);

            Dinero neto = CostoDeOperacion.netoDeRetiro(entrada.monto(), entrada.costo());

            // El orden importa: la retencion ANTES de la orden. Al reves, entre una y
            // otra la persona podria gastar el mismo saldo en otra operacion.
            var retencion = retenciones.retenerDentroDe(
                    dsl,
                    new EntradaRetencion(
                            cuenta.id(),
                            entrada.monto(),
                            MOTIVO_RETENCION,
                            Optional.empty(),
                            Optional.of("ORDEN_RETIRO"),
                            Optional.empty(),
                            Optional.empty()),
                    ctx);

            UUID ordenId = ordenes.crear(
                    dsl,
                    cuenta.id(),
                    entrada.instrumentoDestinoId(),
                    retencion.retencionId(),
                    ctx.usuarioId(),
                    entrada.monto(),
                    entrada.costo(),
                    neto,
                    entrada.mfaVerificado(),
                    entrada.requiereDobleAprobacion(),
                    instrumento.bloqueadoHasta(),
                    entrada.claveIdempotencia(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.retiro_solicitado",
                            "orden_retiro",
                            ordenId,
                            Map.of(
                                    "cuentaBilleteraId", cuenta.id().toString(),
                                    "monto", entrada.monto().toString(),
                                    "neto", neto.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaRetiro(ordenId, "PENDIENTE", entrada.costo(), neto, retencion.retencionId());
        });
    }

    /**
     * El proveedor pago: la retencion se **ejecuta** y el libro registra la salida.
     *
     * <p>Ejecutar y no liberar: el importe se consumio de verdad. Liberarlo devolveria
     * al disponible una plata que ya salio del sistema.
     */
    @Transactional
    public SalidaPago confirmarPago(UUID ordenId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var orden = ordenes.ver(dsl, ordenId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(11, 1), "Esa orden no existe."));

            if (!ordenes.pasarA(dsl, ordenId, "PENDIENTE", "PAGADA", ahora)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(11, 1), "Esa orden ya no esta pendiente: no se puede pagar dos veces.");
            }

            orden.retencionId().ifPresent(id -> retenciones.ejecutarDentroDe(dsl, id, ctx));

            UUID transaccionId = libro.registrar(
                    dsl,
                    "RETIRO",
                    "ORDEN_RETIRO",
                    orden.id(),
                    "API",
                    orden.solicitado(),
                    "retiro:" + orden.id(),
                    Optional.of(ctx.usuarioId()),
                    List.of(
                            Pata.debito(orden.cuentaId(), orden.solicitado(), "Retiro pagado"),
                            Pata.credito(cuentaPuenteDeCustodia, orden.solicitado(), "Salida hacia custodia")),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.retiro_pagado",
                            "orden_retiro",
                            orden.id(),
                            Map.of(
                                    "cuentaBilleteraId",
                                    orden.cuentaId().toString(),
                                    "monto",
                                    orden.solicitado().toString()),
                            UUID.fromString(ctx.traza().id())));

            var despues = cuentas.ver(dsl, orden.cuentaId()).orElseThrow();
            return new SalidaPago(orden.id(), transaccionId, despues.disponible());
        });
    }

    /**
     * El proveedor rechazo en firme: la retencion se **libera** y el saldo vuelve.
     *
     * <p>No se escribe ningun movimiento: la plata nunca salio. Registrar un debito y
     * su reverso por algo que no ocurrio ensuciaria el extracto de la persona con dos
     * lineas que no explican nada.
     */
    @Transactional
    public SalidaRechazo rechazar(UUID ordenId, String motivo, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var orden = ordenes.ver(dsl, ordenId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(11, 1), "Esa orden no existe."));

            if (!ordenes.pasarA(dsl, ordenId, "PENDIENTE", "RECHAZADA", ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(11, 1), "Esa orden ya no esta pendiente.");
            }
            orden.retencionId().ifPresent(id -> retenciones.liberarDentroDe(dsl, id, ctx));

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.retiro_rechazado",
                            "orden_retiro",
                            orden.id(),
                            Map.of("cuentaBilleteraId", orden.cuentaId().toString(), "motivo", motivo),
                            UUID.fromString(ctx.traza().id())));

            var despues = cuentas.ver(dsl, orden.cuentaId()).orElseThrow();
            return new SalidaRechazo(orden.id(), motivo, despues.disponible());
        });
    }

    private CodigoError codigoDe(String codigo) {
        return switch (codigo) {
            case "SALDO_INSUFICIENTE" -> CodigoError.de(11, 1);
            case "MFA_REQUERIDO" -> CodigoError.de(11, 2);
            case "INSTRUMENTO_EN_ENFRIAMIENTO" -> CodigoError.de(11, 3);
            case "TITULAR_NO_COINCIDE" -> CodigoError.de(11, 4);
            case "BLOQUEO_DE_AUTORIDAD" -> CodigoError.de(11, 5);
            default -> CodigoError.de(11, 6); // ENCAJE_INCUMPLIDO
        };
    }

    public record EntradaRetiro(
            String claveIdempotencia,
            UUID cuentaBilleteraId,
            Dinero monto,
            Dinero costo,
            UUID instrumentoDestinoId,
            boolean mfaVerificado,
            boolean requiereDobleAprobacion) {}

    public record SalidaRetiro(
            UUID ordenRetiroId, String estado, Dinero costoRetiro, Dinero montoNeto, UUID retencionId) {}

    public record SalidaPago(UUID ordenRetiroId, UUID transaccionId, Dinero saldoDespues) {}

    public record SalidaRechazo(UUID ordenRetiroId, String motivo, Dinero saldoDespues) {}
}
