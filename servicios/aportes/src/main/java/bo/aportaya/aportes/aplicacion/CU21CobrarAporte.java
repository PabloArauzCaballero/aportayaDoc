package bo.aportaya.aportes.aplicacion;

import bo.aportaya.aportes.dominio.RecargoDeMora;
import bo.aportaya.aportes.dominio.SaldoDeLaObligacion;
import bo.aportaya.aportes.infraestructura.ObligacionRepositorio;
import bo.aportaya.aportes.infraestructura.PagoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-21 · Cobrar el aporte del periodo.
 *
 * <p>Este servicio **no mueve el saldo**: pide a nucleo-financiero que lo mueva y
 * registra el pago cuando le confirman. El invariante 12 dice que solo el nucleo
 * escribe el libro, y por eso aca no hay ni una linea que toque una billetera.
 *
 * <p>La obligacion se bloquea antes de acreditar. Dos pagos simultaneos sobre la misma
 * cuota sin bloqueo leen el mismo {@code monto_pagado} y los dos escriben el suyo: el
 * segundo pisa al primero, y la persona pago dos veces por una cuota registrada una.
 */
@Service
public class CU21CobrarAporte {

    private final Datos datos;
    private final ObligacionRepositorio obligaciones;
    private final PagoRepositorio pagos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU21CobrarAporte(
            Datos datos, ObligacionRepositorio obligaciones, PagoRepositorio pagos, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.obligaciones = obligaciones;
        this.pagos = pagos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaCobro acreditar(EntradaCobro entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // La clave se valida ANTES de escribir (invariante 7): el webhook del
            // proveedor llega dos veces mas seguido de lo que uno cree.
            var yaPagado = pagos.porClaveIdempotencia(dsl, entrada.claveIdempotencia());
            if (yaPagado.isPresent()) {
                var pago = pagos.ver(dsl, yaPagado.get()).orElseThrow();
                var obligacion = obligaciones.ver(dsl, pago.obligacionId()).orElseThrow();
                return new SalidaCobro(
                        pago.id(),
                        obligacion.id(),
                        obligacion.estado(),
                        obligacion.saldo().pendiente(),
                        false);
            }

            var obligacion = obligaciones
                    .bloquear(dsl, entrada.obligacionId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(21, 1), "Esa obligacion no existe."));

            // AP-CU21-01.
            if ("PAGADO".equals(obligacion.estado()) || "ANULADO".equals(obligacion.estado())) {
                throw new ErrorDeNegocio(CodigoError.de(21, 1), "Esa obligacion esta " + obligacion.estado() + ".");
            }
            // AP-CU21-04: el periodo lo resuelve grupos y llega ya decidido — este
            // servicio no lee el esquema de grupos (invariante 11).
            if (!entrada.periodoAbierto()) {
                throw new ErrorDeNegocio(CodigoError.de(21, 4), "El periodo ya se cerro.");
            }
            // AP-CU21-02: pagar de mas no es un favor, es un descuadre. El excedente
            // se devuelve por su propio camino, no se mete en la cuota.
            if (entrada.monto().esMayorQue(obligacion.saldo().pendiente())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(21, 2),
                        "El monto no coincide: falta " + obligacion.saldo().pendiente() + ".");
            }

            UUID pagoId = pagos.registrar(
                    dsl,
                    obligacion.id(),
                    entrada.proveedorId(),
                    entrada.monto(),
                    entrada.comision(),
                    entrada.monto().menos(entrada.comision()),
                    entrada.canal(),
                    entrada.referenciaProveedor(),
                    entrada.claveIdempotencia(),
                    entrada.esManual(),
                    Optional.of(ctx.usuarioId()),
                    ahora);

            var saldoDespues = new SaldoDeLaObligacion.Estado(
                    obligacion.saldo().esperado(),
                    obligacion.saldo().pagado().mas(entrada.monto()),
                    obligacion.saldo().condonado(),
                    obligacion.saldo().cubiertoPorGarantia());
            boolean vencida = obligacion.finDeGracia().isBefore(ahora.toLocalDate());
            String estadoNuevo = SaldoDeLaObligacion.estadoSegunSaldo(saldoDespues, vencida);

            if (!obligaciones.acreditar(
                    dsl, obligacion.id(), entrada.monto(), estadoNuevo, obligacion.version(), ahora)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(21, 1), "Otro pago modifico esa obligacion primero: reintenta.");
            }

            // El movimiento de dinero lo hace nucleo-financiero, no este servicio.
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "aportes.aporte_cobrado",
                            "obligacion_aporte",
                            obligacion.id(),
                            Map.of(
                                    "pagoId", pagoId.toString(),
                                    "grupoId", obligacion.grupoId().toString(),
                                    // Sin el participante, cumplimiento no puede
                                    // registrar a nombre de quien se supero el umbral
                                    // (R-UIF-02): el control queda apagado sin que se note.
                                    "participanteId",
                                            obligacion.participanteId().toString(),
                                    "monto", entrada.monto().toString(),
                                    "estado", estadoNuevo),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCobro(pagoId, obligacion.id(), estadoNuevo, saldoDespues.pendiente(), true);
        });
    }

    /**
     * Trabajo diario: genera el recargo de las vencidas.
     *
     * <p>**Uno por obligacion, no uno por corrida.** Si el trabajo corre dos veces el
     * mismo dia —o si se reintenta tras un fallo— la persona no puede terminar con dos
     * recargos por el mismo atraso.
     */
    @Transactional
    public SalidaRecargos generarRecargos(ContextoSesion ctx) {
        LocalDate hoy = reloj.ahora().atOffset(ZoneOffset.UTC).toLocalDate();

        return datos.conContexto(ctx, dsl -> {
            List<UUID> generados = new ArrayList<>();
            for (UUID obligacionId : obligaciones.vencidasSinRecargo(dsl, hoy)) {
                var obligacion = obligaciones.ver(dsl, obligacionId).orElseThrow();
                var politica = obligaciones.politicaDe(dsl, Optional.of(obligacion.grupoId()));
                if (politica.isEmpty()) {
                    // Sin politica no se inventa un recargo: cobrar sin regla escrita
                    // es exactamente lo que la persona va a reclamar, con razon.
                    continue;
                }
                var p = politica.get();
                int atraso = (int) ChronoUnit.DAYS.between(obligacion.finDeGracia(), hoy);
                var calculo = RecargoDeMora.calcular(
                        obligacion.saldo().pendiente(),
                        atraso,
                        new RecargoDeMora.Politica(
                                0,
                                RecargoDeMora.Tipo.valueOf(p.tipoRecargo()),
                                p.valorRecargo(),
                                Dinero.de(
                                        p.topeRecargo(),
                                        obligacion.saldo().esperado().moneda()),
                                p.diasParaMoraGrave(),
                                p.diasParaIncumplimiento()));

                if (calculo.recargo().esCero()) {
                    continue;
                }
                UUID recargoId = obligaciones.crearRecargo(
                        dsl, obligacion, calculo.recargo(), calculo.diasDeMora(), hoy.plusDays(1));
                generados.add(recargoId);

                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "aportes.recargo_generado",
                                "obligacion_aporte",
                                recargoId,
                                Map.of(
                                        "obligacionOrigenId", obligacion.id().toString(),
                                        "monto", calculo.recargo().toString(),
                                        "severidad", calculo.severidad()),
                                UUID.fromString(ctx.traza().id())));
            }
            return new SalidaRecargos(generados.size(), generados);
        });
    }

    public record EntradaCobro(
            String claveIdempotencia,
            UUID obligacionId,
            Dinero monto,
            Dinero comision,
            String canal,
            String referenciaProveedor,
            Optional<UUID> proveedorId,
            boolean esManual,
            boolean periodoAbierto) {}

    public record SalidaCobro(
            UUID pagoId, UUID obligacionId, String estadoObligacion, Dinero pendiente, boolean esNuevo) {}

    public record SalidaRecargos(int generados, List<UUID> obligacionesDeRecargo) {}
}
