package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.aplicacion.CU40EvaluarLimites.EntradaLimites;
import bo.aportaya.nucleofinanciero.dominio.CostoDeOperacion;
import bo.aportaya.nucleofinanciero.infraestructura.CuentaBilleteraRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.LibroDeBilletera;
import bo.aportaya.nucleofinanciero.infraestructura.LibroDeBilletera.Pata;
import bo.aportaya.nucleofinanciero.infraestructura.OrdenRecargaRepositorio;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-10 · Recargar saldo.
 *
 * <p>Dos actos separados y a proposito: **solicitar** crea la orden y devuelve el QR;
 * **acreditar** suma el saldo cuando el proveedor confirma. El saldo no se mueve
 * cuando la persona dice que pago, sino cuando el dinero llego. Acreditar al pedir
 * seria regalar saldo a quien abandona el pago a medias.
 *
 * <p>{@code AGENTE} no existe: [[ADR-039]] retiro el efectivo del alcance el 20 de
 * agosto de 2026, y el unico ingreso de fondos es electronico.
 */
@Service
public class CU10RecargarSaldo {

    private static final String CONCEPTO = "RECARGA";

    private final Datos datos;
    private final CuentaBilleteraRepositorio cuentas;
    private final OrdenRecargaRepositorio ordenes;
    private final LibroDeBilletera libro;
    private final CU40EvaluarLimites limites;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Duration vigenciaDeLaOrden;
    private final UUID cuentaPuenteDeCustodia;

    public CU10RecargarSaldo(
            Datos datos,
            CuentaBilleteraRepositorio cuentas,
            OrdenRecargaRepositorio ordenes,
            LibroDeBilletera libro,
            CU40EvaluarLimites limites,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.recarga.vigencia-de-la-orden}") Duration vigenciaDeLaOrden,
            @Value("${aportaya.custodia.cuenta-puente}") UUID cuentaPuenteDeCustodia) {
        this.datos = datos;
        this.cuentas = cuentas;
        this.ordenes = ordenes;
        this.libro = libro;
        this.limites = limites;
        this.outbox = outbox;
        this.reloj = reloj;
        this.vigenciaDeLaOrden = vigenciaDeLaOrden;
        this.cuentaPuenteDeCustodia = cuentaPuenteDeCustodia;
    }

    @Transactional
    public SalidaSolicitud solicitar(EntradaSolicitud entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // La clave se valida ANTES de escribir (invariante 7). Repetir el pedido
            // devuelve la misma orden, no una segunda.
            var yaExiste = ordenes.porClaveIdempotencia(dsl, entrada.claveIdempotencia());
            if (yaExiste.isPresent()) {
                var orden = ordenes.ver(dsl, yaExiste.get()).orElseThrow();
                return new SalidaSolicitud(orden.id(), orden.estado(), orden.expiraEn(), null);
            }

            var cuenta = cuentas.bloquear(dsl, entrada.cuentaBilleteraId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(10, 4), "Esa billetera no existe."));

            // AP-CU10-04.
            if (!cuenta.operativa()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(10, 4), "La billetera esta " + cuenta.estado() + ": no admite recargas.");
            }

            // AP-CU10-03. Un medio de fondeo sin verificar puede no ser de quien dice.
            if (entrada.instrumentoFondeoId().isPresent()) {
                var instrumento = ordenes.instrumento(
                                dsl, entrada.instrumentoFondeoId().get())
                        .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(10, 3), "Ese medio de fondeo no existe."));
                if (!instrumento.verificado() || !instrumento.titularCoincide()) {
                    throw new ErrorDeNegocio(
                            CodigoError.de(10, 3), "Ese medio de fondeo no esta verificado a nombre del titular.");
                }
            }

            // AP-CU10-01 · R-LIM-01. Se evalua al SOLICITAR y no solo al acreditar,
            // para no mostrarle un QR a alguien cuya recarga va a rebotar despues.
            limites.exigirDentroDe(dsl, new EntradaLimites(cuenta.id(), CONCEPTO, entrada.monto()), ctx);

            Dinero acreditado = CostoDeOperacion.acreditacion(entrada.monto(), entrada.costoProveedor());
            OffsetDateTime expira = ahora.plus(vigenciaDeLaOrden);

            UUID ordenId = ordenes.crear(
                    dsl,
                    cuenta.id(),
                    entrada.instrumentoFondeoId(),
                    entrada.monto(),
                    entrada.costoProveedor(),
                    acreditado,
                    entrada.claveIdempotencia(),
                    ahora,
                    expira);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.recarga_solicitada",
                            "orden_recarga",
                            ordenId,
                            Map.of(
                                    "cuentaBilleteraId", cuenta.id().toString(),
                                    "monto", entrada.monto().toString(),
                                    "medio", entrada.medio()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaSolicitud(ordenId, "PENDIENTE", expira, acreditado);
        });
    }

    /**
     * El proveedor confirmo: ahora si se mueve el saldo.
     *
     * <p>El asiento tiene dos patas y cuadra: sale del puente de custodia —que
     * representa lo que entro desde afuera— y entra a la billetera. Sin la
     * contrapartida, R-BIL-01 rechaza la transaccion entera.
     */
    @Transactional
    public SalidaAcreditacion acreditar(UUID ordenId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var orden = ordenes.ver(dsl, ordenId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(10, 5), "Esa orden no existe."));

            // AP-CU10-05.
            if (!"PENDIENTE".equals(orden.estado())) {
                throw new ErrorDeNegocio(CodigoError.de(10, 5), "Esa orden ya esta " + orden.estado() + ".");
            }
            if (orden.expiraEn() != null && orden.expiraEn().isBefore(ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(10, 5), "Esa orden de recarga ya vencio.");
            }

            var cuenta = cuentas.bloquear(dsl, orden.cuentaId()).orElseThrow();

            UUID transaccionId = libro.registrar(
                    dsl,
                    "RECARGA",
                    "ORDEN_RECARGA",
                    orden.id(),
                    "API",
                    orden.acreditado(),
                    "recarga:" + orden.id(),
                    Optional.of(ctx.usuarioId()),
                    List.of(
                            Pata.debito(cuentaPuenteDeCustodia, orden.acreditado(), "Ingreso desde custodia"),
                            Pata.credito(cuenta.id(), orden.acreditado(), "Recarga acreditada")),
                    ahora);

            // Si otra confirmacion gano la carrera, la transaccion se revierte entera:
            // el mismo pago no puede sumar saldo dos veces.
            if (!ordenes.acreditar(dsl, orden.id(), transaccionId, ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(10, 5), "Otra confirmacion acredito esa orden primero.");
            }

            var aplicados =
                    limites.exigirDentroDe(dsl, new EntradaLimites(cuenta.id(), CONCEPTO, orden.acreditado()), ctx);
            limites.acumularDentroDe(dsl, cuenta.id(), aplicados, orden.acreditado());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.recarga_acreditada",
                            "orden_recarga",
                            orden.id(),
                            Map.of(
                                    "cuentaBilleteraId", cuenta.id().toString(),
                                    "monto", orden.acreditado().toString()),
                            UUID.fromString(ctx.traza().id())));

            var despues = cuentas.ver(dsl, cuenta.id()).orElseThrow();
            return new SalidaAcreditacion(orden.id(), transaccionId, despues.disponible());
        });
    }

    /** Trabajo programado: una orden que nadie pago no queda pendiente para siempre. */
    @Transactional
    public int expirarVencidas(ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> ordenes.expirarVencidas(dsl, ahora));
    }

    public record EntradaSolicitud(
            String claveIdempotencia,
            UUID cuentaBilleteraId,
            Dinero monto,
            Dinero costoProveedor,
            String medio,
            Optional<UUID> instrumentoFondeoId) {}

    public record SalidaSolicitud(UUID ordenRecargaId, String estado, OffsetDateTime expiraEn, Dinero acreditara) {}

    public record SalidaAcreditacion(UUID ordenRecargaId, UUID transaccionId, Dinero saldoDespues) {}
}
