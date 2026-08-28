package bo.aportaya.erp.web;

import bo.aportaya.erp.aplicacion.CU101Presupuestar;
import bo.aportaya.erp.aplicacion.CU102AltaDeTercero;
import bo.aportaya.erp.aplicacion.CU103FacturaDeProveedor;
import bo.aportaya.erp.aplicacion.CU104CobrarCuenta;
import bo.aportaya.erp.aplicacion.CU105DepreciarActivo;
import bo.aportaya.erp.web.generado.modelo.EntradaCuentaPorCobrar;
import bo.aportaya.erp.web.generado.modelo.EntradaFactura;
import bo.aportaya.erp.web.generado.modelo.EntradaOrden;
import bo.aportaya.erp.web.generado.modelo.EntradaPago;
import bo.aportaya.erp.web.generado.modelo.EntradaPresupuesto;
import bo.aportaya.erp.web.generado.modelo.SalidaDepreciacion;
import bo.aportaya.erp.web.generado.modelo.SalidaFactura;
import bo.aportaya.erp.web.generado.modelo.SalidaOrden;
import bo.aportaya.erp.web.generado.modelo.SalidaPresupuesto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * La traduccion entre los modelos del contrato y los del caso de uso.
 *
 * <p>Vive aparte del controlador por una razon concreta: el generador agrupa las quince
 * operaciones de {@code /erp} en una sola interfaz, asi que el controlador no se puede
 * partir en varios {@code @RestController} —Spring registraria dos veces cada mapeo—.
 * Lo que si se puede separar es esto, que ademas es lo unico del paquete {@code web}
 * que tiene sentido leer solo.
 *
 * <p>Sin logica: si aparece un {@code if} sobre una regla del pasanaku, esta mal
 * ubicado. Lo unico que decide aca es la forma.
 */
final class MapeoDeErp {

    private MapeoDeErp() {}

    /**
     * Un importe siempre como cadena al centavo.
     *
     * <p>{@code UNNECESSARY} a proposito: si un importe llega con mas de dos decimales,
     * es que el caso de uso lo dejo sin cerrar y redondearlo aca lo taparia. Mejor que
     * falle donde se ve.
     */
    static String monto(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    static List<CU101Presupuestar.Partida> partidas(EntradaPresupuesto cuerpo) {
        return cuerpo.getPartidas().stream()
                .map(p -> new CU101Presupuestar.Partida(
                        p.getCuentaContableId(), p.getPeriodoContableId(), new BigDecimal(p.getMontoPresupuestado())))
                .toList();
    }

    static CU101Presupuestar.EntradaPresupuesto presupuesto(EntradaPresupuesto cuerpo) {
        return new CU101Presupuestar.EntradaPresupuesto(
                cuerpo.getCentroCostoId(),
                cuerpo.getEjercicioFiscalId(),
                cuerpo.getNombre(),
                cuerpo.getMoneda().getValue(),
                partidas(cuerpo));
    }

    static CU102AltaDeTercero.EntradaOrden orden(EntradaOrden cuerpo) {
        return new CU102AltaDeTercero.EntradaOrden(
                cuerpo.getTerceroComercialId(),
                cuerpo.getCentroCostoId(),
                cuerpo.getNumero(),
                cuerpo.getDescripcion(),
                new BigDecimal(cuerpo.getMonto()),
                cuerpo.getMoneda().getValue());
    }

    static CU103FacturaDeProveedor.EntradaFactura factura(EntradaFactura cuerpo) {
        return new CU103FacturaDeProveedor.EntradaFactura(
                cuerpo.getTerceroComercialId(),
                cuerpo.getOrdenCompraId(),
                cuerpo.getCentroCostoId(),
                cuerpo.getNumeroFactura(),
                cuerpo.getFechaEmision(),
                cuerpo.getFechaVencimiento(),
                new BigDecimal(cuerpo.getMonto()),
                cuerpo.getMoneda().getValue(),
                cuerpo.getCuentaGastoId(),
                cuerpo.getAprobadaPor());
    }

    static CU103FacturaDeProveedor.EntradaPago pago(java.util.UUID facturaId, EntradaPago cuerpo) {
        return new CU103FacturaDeProveedor.EntradaPago(
                facturaId,
                new BigDecimal(cuerpo.getMonto()),
                cuerpo.getMoneda().getValue(),
                cuerpo.getFormaPago().getValue(),
                cuerpo.getAutorizadoPor());
    }

    static CU104CobrarCuenta.EntradaCuenta cuentaPorCobrar(EntradaCuentaPorCobrar cuerpo) {
        return new CU104CobrarCuenta.EntradaCuenta(
                cuerpo.getOrigenTipo(),
                cuerpo.getOrigenId(),
                cuerpo.getTerceroComercialId(),
                new BigDecimal(cuerpo.getMonto()),
                cuerpo.getMoneda().getValue(),
                cuerpo.getFechaVencimiento());
    }

    static SalidaPresupuesto presupuesto(CU101Presupuestar.SalidaPresupuesto salida) {
        var respuesta = new SalidaPresupuesto();
        respuesta.setPresupuestoId(salida.presupuestoId());
        respuesta.setEstado(SalidaPresupuesto.EstadoEnum.fromValue(salida.estado()));
        respuesta.setTotal(monto(salida.total()));
        return respuesta;
    }

    static SalidaOrden orden(CU102AltaDeTercero.SalidaOrden salida) {
        var respuesta = new SalidaOrden();
        respuesta.setOrdenCompraId(salida.ordenId());
        respuesta.setEstado(SalidaOrden.EstadoEnum.fromValue(salida.estado()));
        respuesta.setMonto(monto(salida.monto()));
        return respuesta;
    }

    /**
     * La factura, con {@code asientoContableId} nulo.
     *
     * <p>No es un olvido: el asiento lo escribe {@code nucleo-financiero} al consumir el
     * evento (invariante 12), y {@code factura_proveedor} es append-only, asi que la
     * fila no lo va a tener nunca. El contrato lo declara nulable por eso.
     */
    static SalidaFactura factura(CU103FacturaDeProveedor.SalidaFactura salida) {
        var respuesta = new SalidaFactura();
        respuesta.setFacturaProveedorId(salida.facturaId());
        respuesta.setEstado(SalidaFactura.EstadoEnum.fromValue(salida.estado()));
        respuesta.setMonto(monto(salida.monto()));
        respuesta.setMontoPagado(monto(salida.montoPagado()));
        respuesta.setSaldoPendiente(monto(salida.saldoPendiente()));
        return respuesta;
    }

    static SalidaDepreciacion depreciacion(CU105DepreciarActivo.Corrida corrida) {
        var respuesta = new SalidaDepreciacion();
        respuesta.setActivoFijoId(corrida.activoId());
        respuesta.setDepreciacionId(corrida.depreciacionId());
        respuesta.setMonto(monto(corrida.monto()));
        respuesta.setValorEnLibros(monto(corrida.valorEnLibros()));
        respuesta.setTotalmenteDepreciado(corrida.totalmenteDepreciado());
        return respuesta;
    }
}
