package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.aplicacion.CU40EvaluarLimites.EntradaLimites;
import bo.aportaya.nucleofinanciero.infraestructura.CuentaBilleteraRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.LibroDeBilletera;
import bo.aportaya.nucleofinanciero.infraestructura.LibroDeBilletera.Pata;
import bo.aportaya.nucleofinanciero.infraestructura.TransferenciaRepositorio;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-12 · Transferir saldo entre billeteras.
 *
 * <p>Es la unica operacion donde el dinero **no entra ni sale del sistema**: cambia de
 * bolsillo. Por eso el saldo total permanece constante, y esa es exactamente la
 * propiedad que la prueba de cuadre verifica.
 *
 * <p>Las dos cuentas se bloquean **en orden de identificador**, no en el orden en que
 * llegaron. Dos transferencias cruzadas —A→B y B→A a la vez— que bloqueen cada una
 * primero su propia cuenta se quedan esperando la otra para siempre. Ordenar el
 * bloqueo convierte un abrazo mortal en una espera.
 */
@Service
public class CU12TransferirSaldo {

    private static final String CONCEPTO = "TRANSFERENCIA";

    private final Datos datos;
    private final CuentaBilleteraRepositorio cuentas;
    private final TransferenciaRepositorio transferencias;
    private final LibroDeBilletera libro;
    private final CU40EvaluarLimites limites;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU12TransferirSaldo(
            Datos datos,
            CuentaBilleteraRepositorio cuentas,
            TransferenciaRepositorio transferencias,
            LibroDeBilletera libro,
            CU40EvaluarLimites limites,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.cuentas = cuentas;
        this.transferencias = transferencias;
        this.libro = libro;
        this.limites = limites;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaTransferencia ejecutar(EntradaTransferencia entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var yaExiste = libro.porClaveIdempotencia(dsl, entrada.claveIdempotencia());
            if (yaExiste.isPresent()) {
                var saldo = cuentas.ver(dsl, entrada.cuentaOrigenId()).orElseThrow();
                return new SalidaTransferencia(yaExiste.get(), saldo.disponible(), entrada.destinoId(), false);
            }

            // Orden de bloqueo por identificador: evita el abrazo mortal entre dos
            // transferencias cruzadas.
            UUID primera = entrada.cuentaOrigenId().compareTo(entrada.destinoId()) <= 0
                    ? entrada.cuentaOrigenId()
                    : entrada.destinoId();
            UUID segunda = primera.equals(entrada.cuentaOrigenId()) ? entrada.destinoId() : entrada.cuentaOrigenId();
            cuentas.bloquear(dsl, primera);
            cuentas.bloquear(dsl, segunda);

            var origen = cuentas.ver(dsl, entrada.cuentaOrigenId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(12, 2), "La billetera de origen no existe."));
            var destino = cuentas.ver(dsl, entrada.destinoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(12, 2), "Ese destino no existe."));

            // AP-CU12-03: se comprueba el DESTINO tambien. Acreditar en una cuenta
            // cerrada deja plata que nadie puede sacar.
            if (!destino.operativa()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(12, 3), "La billetera de destino esta " + destino.estado() + ".");
            }
            if (!origen.operativa()) {
                throw new ErrorDeNegocio(CodigoError.de(12, 3), "La billetera de origen esta " + origen.estado() + ".");
            }

            // AP-CU12-05: la politica de la cuenta manda.
            if (!transferencias.permiteP2P(dsl, origen.id())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(12, 5), "La politica de esa billetera no permite transferencias.");
            }

            // AP-CU12-01.
            if (entrada.monto().esMayorQue(origen.disponible())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(12, 1),
                        "El disponible no cubre la transferencia: quedan " + origen.disponible() + ".");
            }

            var aplicados =
                    limites.exigirDentroDe(dsl, new EntradaLimites(origen.id(), CONCEPTO, entrada.monto()), ctx);

            UUID transaccionId = libro.registrar(
                    dsl,
                    "TRANSFERENCIA_P2P",
                    "TRANSFERENCIA_P2P",
                    UUID.randomUUID(),
                    "APP",
                    entrada.monto(),
                    entrada.claveIdempotencia(),
                    Optional.of(ctx.usuarioId()),
                    List.of(
                            Pata.debito(origen.id(), entrada.monto(), entrada.concepto()),
                            Pata.credito(destino.id(), entrada.monto(), entrada.concepto())),
                    ahora);

            transferencias.registrar(
                    dsl,
                    transaccionId,
                    origen.id(),
                    destino.id(),
                    entrada.grupoId(),
                    entrada.obligacionId(),
                    entrada.monto(),
                    entrada.concepto(),
                    ahora);

            limites.acumularDentroDe(dsl, origen.id(), aplicados, entrada.monto());

            // La obligacion vive en el esquema de aportes: se pide saldarla por
            // evento, no se escribe desde aca (invariante 11).
            entrada.obligacionId()
                    .ifPresent(obligacion -> outbox.emitir(
                            dsl,
                            new EventoDominio(
                                    "nucleo_financiero.obligacion_pagada",
                                    "transferencia_p2p",
                                    obligacion,
                                    Map.of(
                                            "obligacionId", obligacion.toString(),
                                            "monto", entrada.monto().toString(),
                                            "transaccionId", transaccionId.toString()),
                                    UUID.fromString(ctx.traza().id()))));

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.transferencia_ejecutada",
                            "transferencia_p2p",
                            transaccionId,
                            Map.of(
                                    "origen", origen.id().toString(),
                                    "destino", destino.id().toString(),
                                    "monto", entrada.monto().toString()),
                            UUID.fromString(ctx.traza().id())));

            var despues = cuentas.ver(dsl, origen.id()).orElseThrow();
            return new SalidaTransferencia(
                    transaccionId,
                    despues.disponible(),
                    destino.id(),
                    entrada.obligacionId().isPresent());
        });
    }

    public record EntradaTransferencia(
            String claveIdempotencia,
            UUID cuentaOrigenId,
            UUID destinoId,
            Dinero monto,
            String concepto,
            Optional<UUID> grupoId,
            Optional<UUID> obligacionId) {}

    public record SalidaTransferencia(
            UUID transaccionId, Dinero saldoDespues, UUID destinatarioId, boolean obligacionSaldada) {}
}
