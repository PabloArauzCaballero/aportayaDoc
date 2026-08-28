package bo.aportaya.erp.aplicacion;

import bo.aportaya.erp.infraestructura.ComprasRepositorio;
import bo.aportaya.erp.infraestructura.PeriodoRepositorio;
import bo.aportaya.erp.infraestructura.PresupuestoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-103 · Registrar y pagar una factura de proveedor.
 *
 * <p>**Quien aprueba no paga** (R-CTB-05). Es el control mas viejo de la contabilidad y
 * sigue siendo el que mas fraude interno evita: una sola persona que aprueba y paga
 * puede inventarse un proveedor, aprobarle una factura y transferirse la plata sin que
 * nadie mire dos veces. Lo verifica {@code fn_ctb_segregacion_pago} en la base, y lo
 * comprueba antes este caso de uso para poder explicarlo.
 *
 * <p>**El saldo nunca queda negativo** (R-CTB-04). La factura se lee con {@code FOR
 * UPDATE}: sin bloquear, dos pagos simultaneos leen el mismo monto pagado y los dos
 * pasan. La base lo atraparia despues, pero para entonces el error ya salio del sistema
 * como dos transferencias.
 */
@Service
public class CU103FacturaDeProveedor {

    private final Datos datos;
    private final ComprasRepositorio compras;
    private final PeriodoRepositorio periodos;
    private final PresupuestoRepositorio presupuestos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU103FacturaDeProveedor(
            Datos datos,
            ComprasRepositorio compras,
            PeriodoRepositorio periodos,
            PresupuestoRepositorio presupuestos,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.compras = compras;
        this.periodos = periodos;
        this.presupuestos = presupuestos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaFactura registrar(EntradaFactura entrada, ContextoSesion ctx) {
        if (entrada.vencimiento().isBefore(entrada.emision())) {
            throw new ErrorDeNegocio(CodigoError.de(103, 3), "Una factura no puede vencer antes de emitirse.");
        }

        return datos.conContexto(ctx, dsl -> {
            var tercero = compras.terceroPorId(dsl, entrada.terceroId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(103, 3), "Ese proveedor no existe."));
            if (!"ACTIVO".equals(tercero.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(103, 3),
                        "El proveedor esta " + tercero.estado() + ": no se le registran facturas.");
            }

            // Si la factura cita una orden, el monto no puede excederla: aprobar una
            // orden por X y recibir una factura por 3X vacia el sentido de aprobarla.
            if (entrada.ordenCompraId() != null) {
                var orden = compras.orden(dsl, entrada.ordenCompraId())
                        .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(103, 3), "Esa orden no existe."));
                if (orden.aprobadaPor() == null) {
                    throw new ErrorDeNegocio(CodigoError.de(103, 3), "La orden de compra citada no esta aprobada.");
                }
                if (entrada.monto().compareTo(orden.montoTotal()) > 0) {
                    throw new ErrorDeNegocio(
                            CodigoError.de(103, 4),
                            "La factura (%s) excede la orden aprobada (%s)."
                                    .formatted(
                                            entrada.monto().toPlainString(),
                                            orden.montoTotal().toPlainString()));
                }
            }

            // AP-CU103-01 · R-CTB-01. Una factura con fecha dentro de un periodo cerrado
            // cambiaria un resultado ya publicado. Se rechaza antes de escribir nada.
            var periodo = periodos.periodoDeLaFecha(dsl, entrada.emision());
            if (periodo.isPresent() && !"ABIERTO".equals(periodo.get().estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(103, 1),
                        "El periodo %d de la fecha de emision esta cerrado."
                                .formatted(periodo.get().mes()));
            }

            UUID id;
            try {
                id = compras.registrarFactura(
                        dsl,
                        entrada.terceroId(),
                        entrada.ordenCompraId(),
                        entrada.centroCostoId(),
                        entrada.numeroFactura(),
                        entrada.emision(),
                        entrada.vencimiento(),
                        entrada.monto(),
                        entrada.moneda(),
                        entrada.aprobadaPor());
            } catch (org.jooq.exception.IntegrityConstraintViolationException
                    | org.springframework.dao.DataIntegrityViolationException e) {
                // uq_factura_proveedor_numero: la misma factura cargada dos veces se
                // paga dos veces.
                throw new ErrorDeNegocio(
                        CodigoError.de(103, 3),
                        "Ese proveedor ya tiene registrada la factura " + entrada.numeroFactura() + ".");
            }

            // La ejecucion presupuestaria sube con la factura: si no hay partida para
            // esa cuenta y ese periodo, no se bloquea nada — un olvido de planificacion
            // no puede parar una compra ya hecha.
            boolean imputada = periodo.isPresent()
                    && entrada.cuentaGastoId() != null
                    && presupuestos.ejecutar(
                            dsl, entrada.cuentaGastoId(), periodo.get().id(), entrada.monto());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.factura_proveedor_registrada",
                            "factura_proveedor",
                            id,
                            Map.of(
                                    "numeroFactura", entrada.numeroFactura(),
                                    "monto", entrada.monto().toPlainString(),
                                    "vencimiento", entrada.vencimiento().toString(),
                                    "imputadaAPresupuesto", Boolean.toString(imputada)),
                            UUID.fromString(ctx.traza().id())));
            return new SalidaFactura(
                    id,
                    entrada.aprobadaPor() == null ? "REGISTRADA" : "APROBADA",
                    entrada.monto(),
                    BigDecimal.ZERO,
                    entrada.monto());
        });
    }

    /**
     * **No hay aprobacion posterior, y no es un olvido.**
     *
     * <p>{@code factura_proveedor} es append-only (R-AUD-01), asi que
     * {@code aprobada_por} no se puede completar despues del alta, y
     * {@code ck_factura_proveedor_aprobacion} tampoco admite una fila APROBADA sin
     * aprobador. La aprobacion es un dato del alta: quien registra la factura la trae ya
     * aprobada, o la factura queda REGISTRADA y no se paga.
     *
     * <p>Lo que **no** se relaja es la segregacion: quien figura como aprobador no puede
     * ser quien autoriza el pago. Queda declarado como hueco en
     * {@code planes/informes/carril-3D-erp.md}.
     */
    @Transactional
    public SalidaFactura pagar(EntradaPago entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var factura = compras.facturaBloqueada(dsl, entrada.facturaId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(103, 3), "Esa factura no existe."));

            // AP-CU103-02 · R-CTB-05. Se comprueba aca para poder explicarlo; la base lo
            // frena igual con fn_ctb_segregacion_pago.
            if (factura.aprobadaPor() == null) {
                throw new ErrorDeNegocio(CodigoError.de(103, 2), "La factura no esta aprobada: no se puede pagar.");
            }
            if (factura.aprobadaPor().equals(entrada.autorizadoPor())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(103, 2),
                        "Quien aprobo la factura no puede ademas autorizar su pago (R-CTB-05).");
            }

            BigDecimal pagadoNuevo = factura.montoPagado().add(entrada.monto());
            // AP-CU103-08 · R-CTB-04: el saldo nunca queda negativo.
            if (pagadoNuevo.compareTo(factura.monto()) > 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(103, 4),
                        "El pago (%s) excede el saldo pendiente (%s)."
                                .formatted(
                                        entrada.monto().toPlainString(),
                                        factura.monto()
                                                .subtract(factura.montoPagado())
                                                .toPlainString()));
            }

            UUID pagoId = compras.pagar(
                    dsl,
                    entrada.facturaId(),
                    entrada.monto(),
                    entrada.moneda(),
                    entrada.formaPago(),
                    entrada.autorizadoPor(),
                    pagadoNuevo,
                    factura.monto(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.pago_a_proveedor_registrado",
                            "pago_a_proveedor",
                            pagoId,
                            Map.of(
                                    "facturaId", entrada.facturaId().toString(),
                                    "monto", entrada.monto().toPlainString(),
                                    "saldoPendiente",
                                            factura.monto()
                                                    .subtract(pagadoNuevo)
                                                    .toPlainString(),
                                    "autorizadoPor", entrada.autorizadoPor().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaFactura(
                    entrada.facturaId(),
                    pagadoNuevo.compareTo(factura.monto()) >= 0 ? "PAGADA" : "PAGADA_PARCIAL",
                    factura.monto(),
                    pagadoNuevo,
                    factura.monto().subtract(pagadoNuevo));
        });
    }

    public record EntradaFactura(
            UUID terceroId,
            UUID ordenCompraId,
            UUID centroCostoId,
            String numeroFactura,
            LocalDate emision,
            LocalDate vencimiento,
            BigDecimal monto,
            String moneda,
            UUID cuentaGastoId,
            UUID aprobadaPor) {}

    public record EntradaPago(UUID facturaId, BigDecimal monto, String moneda, String formaPago, UUID autorizadoPor) {}

    public record SalidaFactura(
            UUID facturaId, String estado, BigDecimal monto, BigDecimal montoPagado, BigDecimal saldoPendiente) {}
}
