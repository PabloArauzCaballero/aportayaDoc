package bo.aportaya.erp.web;

import bo.aportaya.erp.aplicacion.CU100AbrirCerrarPeriodo;
import bo.aportaya.erp.aplicacion.CU101Presupuestar;
import bo.aportaya.erp.aplicacion.CU102AltaDeTercero;
import bo.aportaya.erp.aplicacion.CU103FacturaDeProveedor;
import bo.aportaya.erp.aplicacion.CU104CobrarCuenta;
import bo.aportaya.erp.aplicacion.CU105DepreciarActivo;
import bo.aportaya.erp.aplicacion.CU106GenerarEstadoFinanciero;
import bo.aportaya.erp.web.generado.ErpApi;
import bo.aportaya.erp.web.generado.modelo.EntradaCierre;
import bo.aportaya.erp.web.generado.modelo.EntradaCobro;
import bo.aportaya.erp.web.generado.modelo.EntradaCuentaPorCobrar;
import bo.aportaya.erp.web.generado.modelo.EntradaDepreciacion;
import bo.aportaya.erp.web.generado.modelo.EntradaEjercicio;
import bo.aportaya.erp.web.generado.modelo.EntradaEstadoFinanciero;
import bo.aportaya.erp.web.generado.modelo.EntradaFactura;
import bo.aportaya.erp.web.generado.modelo.EntradaOrden;
import bo.aportaya.erp.web.generado.modelo.EntradaPago;
import bo.aportaya.erp.web.generado.modelo.EntradaPresupuesto;
import bo.aportaya.erp.web.generado.modelo.EntradaTercero;
import bo.aportaya.erp.web.generado.modelo.SalidaCierre;
import bo.aportaya.erp.web.generado.modelo.SalidaCobro;
import bo.aportaya.erp.web.generado.modelo.SalidaCorrida;
import bo.aportaya.erp.web.generado.modelo.SalidaCuentaPorCobrar;
import bo.aportaya.erp.web.generado.modelo.SalidaDepreciacion;
import bo.aportaya.erp.web.generado.modelo.SalidaEjercicio;
import bo.aportaya.erp.web.generado.modelo.SalidaEstadoFinanciero;
import bo.aportaya.erp.web.generado.modelo.SalidaFactura;
import bo.aportaya.erp.web.generado.modelo.SalidaOrden;
import bo.aportaya.erp.web.generado.modelo.SalidaPlantilla;
import bo.aportaya.erp.web.generado.modelo.SalidaPresupuesto;
import bo.aportaya.erp.web.generado.modelo.SalidaTercero;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /erp}: traducen y delegan, sin logica.
 *
 * <p><b>Una sola clase y no siete.</b> El generador agrupa las operaciones por el primer
 * tramo de la ruta, y las quince de este servicio caen bajo {@code /erp}: dos
 * {@code @RestController} implementando la misma interfaz registrarian dos veces cada
 * mapeo y Spring no levanta. Partirla exigiria partir las rutas, y las rutas las manda
 * el contrato.
 *
 * <p>Los importes entran y salen como CADENA decimal (ADR-019): un {@code number} de
 * JSON es un doble, y un doble no cuenta plata.
 */
@RestController
public class ErpController implements ErpApi {

    private final CU100AbrirCerrarPeriodo cu100;
    private final CU101Presupuestar cu101;
    private final CU102AltaDeTercero cu102;
    private final CU103FacturaDeProveedor cu103;
    private final CU104CobrarCuenta cu104;
    private final CU105DepreciarActivo cu105;
    private final CU106GenerarEstadoFinanciero cu106;
    private final SesionDeLaPeticion sesion;

    public ErpController(
            CU100AbrirCerrarPeriodo cu100,
            CU101Presupuestar cu101,
            CU102AltaDeTercero cu102,
            CU103FacturaDeProveedor cu103,
            CU104CobrarCuenta cu104,
            CU105DepreciarActivo cu105,
            CU106GenerarEstadoFinanciero cu106,
            SesionDeLaPeticion sesion) {
        this.cu100 = cu100;
        this.cu101 = cu101;
        this.cu102 = cu102;
        this.cu103 = cu103;
        this.cu104 = cu104;
        this.cu105 = cu105;
        this.cu106 = cu106;
        this.sesion = sesion;
    }

    // ------------------------------------------------------------------ CU-100 --

    @Override
    @Permiso("CONTABILIDAD_ERP_CERRAR")
    public ResponseEntity<SalidaEjercicio> abrirEjercicioFiscal(UUID idempotencyKey, EntradaEjercicio cuerpo) {
        Traza.marcarCasoDeUso("CU-100", String.valueOf(cuerpo.getAnio()));

        var salida = cu100.abrirEjercicio(cuerpo.getAnio(), sesion.actual());

        var respuesta = new SalidaEjercicio();
        respuesta.setEjercicioId(salida.ejercicioId());
        respuesta.setAnio(salida.anio());
        respuesta.setPeriodos(salida.periodosAbiertos());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("CONTABILIDAD_ERP_CERRAR")
    public ResponseEntity<SalidaCierre> cerrarPeriodoContable(
            UUID periodoId, UUID idempotencyKey, EntradaCierre cuerpo) {
        Traza.marcarCasoDeUso("CU-100", periodoId.toString());

        var salida = cu100.cerrarPeriodo(
                new CU100AbrirCerrarPeriodo.EntradaCierre(periodoId, cuerpo.getGlosa()), sesion.actual());

        var respuesta = new SalidaCierre();
        respuesta.setCierreId(salida.cierreId());
        respuesta.setPeriodoId(salida.periodoId());
        respuesta.setTotalDebe(MapeoDeErp.monto(salida.totalDebe()));
        respuesta.setTotalHaber(MapeoDeErp.monto(salida.totalHaber()));
        respuesta.setCerradoEn(salida.cerradoEn());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    // ------------------------------------------------------------------ CU-101 --

    @Override
    @Permiso("CONTABILIDAD_ERP_PRESUPUESTO")
    public ResponseEntity<SalidaPresupuesto> crearPresupuesto(UUID idempotencyKey, EntradaPresupuesto cuerpo) {
        Traza.marcarCasoDeUso("CU-101", cuerpo.getCentroCostoId().toString());

        var salida = cu101.crear(MapeoDeErp.presupuesto(cuerpo), sesion.actual());

        return ResponseEntity.status(HttpStatus.CREATED).body(MapeoDeErp.presupuesto(salida));
    }

    @Override
    @Permiso("CONTABILIDAD_ERP_PRESUPUESTO")
    public ResponseEntity<SalidaPresupuesto> aprobarPresupuesto(UUID presupuestoId, UUID idempotencyKey) {
        Traza.marcarCasoDeUso("CU-101", presupuestoId.toString());
        return ResponseEntity.ok(MapeoDeErp.presupuesto(cu101.aprobar(presupuestoId, sesion.actual())));
    }

    // ------------------------------------------------------------------ CU-102 --

    @Override
    @Permiso("CONTABILIDAD_ERP_COMPRAS")
    public ResponseEntity<SalidaTercero> darDeAltaTercero(UUID idempotencyKey, EntradaTercero cuerpo) {
        Traza.marcarCasoDeUso("CU-102", cuerpo.getNumeroDocumento());

        UUID id = cu102.darDeAlta(
                new CU102AltaDeTercero.EntradaTercero(
                        cuerpo.getTipo().getValue(),
                        cuerpo.getRazonSocial(),
                        cuerpo.getNumeroDocumento(),
                        cuerpo.getEmail(),
                        cuerpo.getCuentaContableId()),
                sesion.actual());

        var respuesta = new SalidaTercero();
        respuesta.setTerceroComercialId(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("CONTABILIDAD_ERP_COMPRAS")
    public ResponseEntity<SalidaOrden> crearOrdenDeCompra(UUID idempotencyKey, EntradaOrden cuerpo) {
        Traza.marcarCasoDeUso("CU-102", cuerpo.getNumero());

        var salida = cu102.crearOrden(MapeoDeErp.orden(cuerpo), sesion.actual());

        return ResponseEntity.status(HttpStatus.CREATED).body(MapeoDeErp.orden(salida));
    }

    @Override
    @Permiso("CONTABILIDAD_ERP_COMPRAS")
    public ResponseEntity<SalidaOrden> aprobarOrdenDeCompra(UUID ordenId, UUID idempotencyKey) {
        Traza.marcarCasoDeUso("CU-102", ordenId.toString());
        return ResponseEntity.ok(MapeoDeErp.orden(cu102.aprobarOrden(ordenId, sesion.actual())));
    }

    // ------------------------------------------------------------------ CU-103 --

    @Override
    @Permiso("CONTABILIDAD_ERP_CUENTAS_POR_PAGAR")
    public ResponseEntity<SalidaFactura> registrarFacturaDeProveedor(UUID idempotencyKey, EntradaFactura cuerpo) {
        Traza.marcarCasoDeUso("CU-103", cuerpo.getNumeroFactura());

        var salida = cu103.registrar(MapeoDeErp.factura(cuerpo), sesion.actual());

        return ResponseEntity.status(HttpStatus.CREATED).body(MapeoDeErp.factura(salida));
    }

    @Override
    @Permiso("CONTABILIDAD_ERP_PAGAR")
    public ResponseEntity<SalidaFactura> pagarFacturaDeProveedor(
            UUID facturaId, UUID idempotencyKey, EntradaPago cuerpo) {
        Traza.marcarCasoDeUso("CU-103", facturaId.toString());

        var salida = cu103.pagar(MapeoDeErp.pago(facturaId, cuerpo), sesion.actual());

        return ResponseEntity.status(HttpStatus.CREATED).body(MapeoDeErp.factura(salida));
    }

    // ------------------------------------------------------------------ CU-104 --

    @Override
    @Permiso("CONTABILIDAD_ERP_COBRAR")
    public ResponseEntity<SalidaCuentaPorCobrar> abrirCuentaPorCobrar(
            UUID idempotencyKey, EntradaCuentaPorCobrar cuerpo) {
        Traza.marcarCasoDeUso("CU-104", cuerpo.getOrigenTipo());

        UUID id = cu104.abrir(MapeoDeErp.cuentaPorCobrar(cuerpo), sesion.actual());

        var respuesta = new SalidaCuentaPorCobrar();
        respuesta.setCuentaPorCobrarId(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("CONTABILIDAD_ERP_COBRAR")
    public ResponseEntity<SalidaCobro> registrarCobro(UUID cuentaId, UUID idempotencyKey, EntradaCobro cuerpo) {
        Traza.marcarCasoDeUso("CU-104", cuentaId.toString());

        var salida = cu104.cobrar(
                new CU104CobrarCuenta.EntradaCobro(
                        cuentaId,
                        new BigDecimal(cuerpo.getMonto()),
                        cuerpo.getFormaCobro().getValue()),
                sesion.actual());

        var respuesta = new SalidaCobro();
        respuesta.setCobroId(salida.cobroId());
        respuesta.setCuentaPorCobrarId(salida.cuentaPorCobrarId());
        respuesta.setEstado(SalidaCobro.EstadoEnum.fromValue(salida.estado()));
        respuesta.setCobrado(MapeoDeErp.monto(salida.cobrado()));
        respuesta.setSaldoPendiente(MapeoDeErp.monto(salida.saldoPendiente()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    // ------------------------------------------------------------------ CU-105 --

    @Override
    @Permiso("CONTABILIDAD_ERP_ACTIVOS_FIJOS")
    public ResponseEntity<SalidaDepreciacion> depreciarActivo(
            UUID activoId, UUID idempotencyKey, EntradaDepreciacion cuerpo) {
        Traza.marcarCasoDeUso("CU-105", activoId.toString());

        var corrida = cu105.depreciar(activoId, cuerpo.getPeriodoContableId(), sesion.actual());
        return ResponseEntity.status(HttpStatus.CREATED).body(MapeoDeErp.depreciacion(corrida));
    }

    @Override
    @Permiso("CONTABILIDAD_ERP_ACTIVOS_FIJOS")
    public ResponseEntity<SalidaCorrida> correrDepreciacionDelPeriodo(UUID periodoId, UUID idempotencyKey) {
        Traza.marcarCasoDeUso("CU-105", periodoId.toString());

        var salida = cu105.correr(periodoId, sesion.actual());

        var respuesta = new SalidaCorrida();
        respuesta.setPeriodoId(salida.periodoId());
        respuesta.setDepreciaciones(
                salida.depreciaciones().stream().map(MapeoDeErp::depreciacion).toList());
        respuesta.setTotalDepreciado(MapeoDeErp.monto(salida.totalDepreciado()));
        respuesta.setYaCorridos(salida.yaCorridos());
        respuesta.setTotalmenteDepreciados(salida.totalmenteDepreciados());
        return ResponseEntity.ok(respuesta);
    }

    // ------------------------------------------------------------------ CU-106 --

    @Override
    @Permiso("CONTABILIDAD_ERP_REPORTES")
    public ResponseEntity<SalidaEstadoFinanciero> generarEstadoFinanciero(
            UUID periodoId, UUID idempotencyKey, EntradaEstadoFinanciero cuerpo) {
        Traza.marcarCasoDeUso("CU-106", cuerpo.getTipo().getValue());

        var salida = cu106.generar(periodoId, cuerpo.getTipo().getValue(), sesion.actual());

        var respuesta = new SalidaEstadoFinanciero();
        respuesta.setEstadoFinancieroId(salida.estadoId());
        respuesta.setTipo(SalidaEstadoFinanciero.TipoEnum.fromValue(salida.tipo()));
        respuesta.setCuadra(salida.estado().ecuacionCierra());
        respuesta.setHashContenido(salida.hashContenido());
        respuesta.setGeneradoEn(salida.generadoEn());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("CONTABILIDAD_ERP_REPORTES")
    public ResponseEntity<SalidaPlantilla> validarPlantillaDeAsiento(UUID plantillaId) {
        Traza.marcarCasoDeUso("CU-106", plantillaId.toString());

        var plantilla = cu106.validarPlantilla(plantillaId, sesion.actual());

        var respuesta = new SalidaPlantilla();
        respuesta.setPlantillaId(plantilla.plantillaId());
        respuesta.setLineas(plantilla.lineas());
        respuesta.setDebes(plantilla.debes());
        respuesta.setHaberes(plantilla.haberes());
        return ResponseEntity.ok(respuesta);
    }
}
