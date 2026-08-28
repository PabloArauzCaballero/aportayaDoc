package bo.aportaya.tarifas.web;

import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import bo.aportaya.tarifas.aplicacion.CU30CotizarComision;
import bo.aportaya.tarifas.aplicacion.CU31DevengarComision;
import bo.aportaya.tarifas.aplicacion.CU33DevolverComision;
import bo.aportaya.tarifas.dominio.SegmentoAplicable;
import bo.aportaya.tarifas.web.generado.ComisionesApi;
import bo.aportaya.tarifas.web.generado.modelo.AceptarCotizacion200Response;
import bo.aportaya.tarifas.web.generado.modelo.EntradaCobro;
import bo.aportaya.tarifas.web.generado.modelo.EntradaCotizacion;
import bo.aportaya.tarifas.web.generado.modelo.EntradaDevengo;
import bo.aportaya.tarifas.web.generado.modelo.EntradaDevolucion;
import bo.aportaya.tarifas.web.generado.modelo.LineaDesglose;
import bo.aportaya.tarifas.web.generado.modelo.SalidaCobro;
import bo.aportaya.tarifas.web.generado.modelo.SalidaCotizacion;
import bo.aportaya.tarifas.web.generado.modelo.SalidaDevengo;
import bo.aportaya.tarifas.web.generado.modelo.SalidaDevolucion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /comisiones}: cotizar, devengar, cobrar y devolver.
 *
 * <p>Cotizar y devengar son dos actos y no uno: **lo que se cotiza es lo que se cobra**
 * (R-TAR-02), y separarlos es lo que permite mostrarle el precio a alguien antes de que
 * se comprometa, sin haberle creado todavia ninguna deuda.
 */
@RestController
public class ComisionesController implements ComisionesApi {

    private final CU30CotizarComision cu30;
    private final CU31DevengarComision cu31;
    private final CU33DevolverComision cu33;
    private final SesionDeLaPeticion sesion;

    public ComisionesController(
            CU30CotizarComision cu30, CU31DevengarComision cu31, CU33DevolverComision cu33, SesionDeLaPeticion sesion) {
        this.cu30 = cu30;
        this.cu31 = cu31;
        this.cu33 = cu33;
        this.sesion = sesion;
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaCotizacion> cotizarComision(UUID idempotencyKey, EntradaCotizacion cuerpo) {
        Traza.marcarCasoDeUso("CU-30", cuerpo.getHechoGenerador());

        var salida = cu30.cotizar(
                new CU30CotizarComision.EntradaCotizacion(
                        idempotencyKey.toString(),
                        cuerpo.getCodigoTarifario(),
                        cuerpo.getHechoGenerador(),
                        cuerpo.getReferenciaTipo().getValue(),
                        cuerpo.getReferenciaId(),
                        MapeoDeTarifas.dinero(cuerpo.getMontoBase()),
                        Optional.ofNullable(cuerpo.getGrupoId()),
                        MapeoDeTarifas.dineroOpcional(cuerpo.getDescuento()),
                        segmento(cuerpo.getSegmentoCodigo())),
                sesion.actual());

        var respuesta = new SalidaCotizacion();
        respuesta.setCotizacionId(salida.cotizacionId());
        respuesta.setMontoComision(MapeoDeTarifas.dinero(salida.montoComision()));
        respuesta.setMontoImpuesto(MapeoDeTarifas.dinero(salida.montoImpuesto()));
        respuesta.setMontoTotal(MapeoDeTarifas.dinero(salida.montoTotal()));
        respuesta.setDesglose(salida.desglose().stream()
                .map(l -> {
                    var linea = new LineaDesglose();
                    linea.setConcepto(l.concepto());
                    linea.setDetalle(l.detalle());
                    linea.setMonto(MapeoDeTarifas.dinero(l.monto()));
                    return linea;
                })
                .toList());
        respuesta.setValidaHasta(salida.validaHasta());
        respuesta.setEsNueva(salida.esNueva());

        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    /**
     * El segmento que rige, tal como lo resolvio CU-36.
     *
     * <p>El contrato manda el codigo y no la eleccion entera. Se rearma con el motivo
     * escrito para que la cotizacion diga POR QUE aplico ese precio: una comision sin
     * motivo es la que despues nadie puede explicarle al cliente.
     */
    private Optional<SegmentoAplicable.Eleccion> segmento(String codigo) {
        return Optional.ofNullable(codigo)
                .map(c -> new SegmentoAplicable.Eleccion(c, "Segmento resuelto por CU-36 antes de cotizar", true));
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<AceptarCotizacion200Response> aceptarCotizacion(UUID cotizacionId) {
        Traza.marcarCasoDeUso("CU-30", cotizacionId.toString());

        var respuesta = new AceptarCotizacion200Response();
        respuesta.setAceptada(cu30.aceptar(cotizacionId, sesion.actual()));
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaDevengo> devengarComision(UUID idempotencyKey, EntradaDevengo cuerpo) {
        Traza.marcarCasoDeUso("CU-31", cuerpo.getCotizacionId().toString());

        var salida = cu31.devengar(
                new CU31DevengarComision.EntradaDevengo(
                        idempotencyKey.toString(),
                        cuerpo.getCotizacionId(),
                        cuerpo.getTarifarioId(),
                        cuerpo.getReferenciaTipo().getValue(),
                        cuerpo.getReferenciaId(),
                        cuerpo.getUsuarioObligadoId(),
                        Optional.ofNullable(cuerpo.getGrupoId()),
                        MapeoDeTarifas.ceroSiFalta(cuerpo.getDescuento(), cuerpo.getDescuento()),
                        Boolean.TRUE.equals(cuerpo.getExentoTotal())),
                sesion.actual());

        var respuesta = new SalidaDevengo();
        respuesta.setDevengoId(salida.devengoId());
        respuesta.setCargoId(salida.cargoId());
        respuesta.setEstado(SalidaDevengo.EstadoEnum.fromValue(salida.estado()));
        respuesta.setMontoTotal(MapeoDeTarifas.dinero(salida.montoTotal()));
        respuesta.setEsNuevo(salida.esNuevo());

        var estado = salida.esNuevo() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaCobro> anotarCobroDeComision(UUID devengoId, EntradaCobro cuerpo) {
        Traza.marcarCasoDeUso("CU-31", devengoId.toString());

        var salida = cu31.anotarCobro(
                new CU31DevengarComision.EntradaCobro(
                        devengoId,
                        cuerpo.getFormaCobro().getValue(),
                        Boolean.TRUE.equals(cuerpo.getExitoso()),
                        cuerpo.getError()),
                sesion.actual());

        var respuesta = new SalidaCobro();
        respuesta.setCargoId(salida.cargoId());
        respuesta.setEstadoDelCargo(SalidaCobro.EstadoDelCargoEnum.fromValue(salida.estadoDelCargo()));
        respuesta.setEstadoDelDevengo(salida.estadoDelDevengo());
        respuesta.setCuentaPorCobrarId(salida.cuentaPorCobrarId());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("REVERSO_AUTORIZAR")
    public ResponseEntity<SalidaDevolucion> devolverComision(
            UUID idempotencyKey, UUID devengoId, EntradaDevolucion cuerpo) {
        Traza.marcarCasoDeUso("CU-33", devengoId.toString());

        var salida = cu33.devolver(
                new CU33DevolverComision.EntradaDevolucion(
                        devengoId,
                        cuerpo.getMotivo().getValue(),
                        cuerpo.getDetalle(),
                        MapeoDeTarifas.dinero(cuerpo.getMonto()),
                        cuerpo.getForma().getValue(),
                        Optional.ofNullable(cuerpo.getReclamoId()),
                        cuerpo.getAutorizadaPor()),
                sesion.actual());

        var respuesta = new SalidaDevolucion();
        respuesta.setDevolucionId(salida.devolucionId());
        respuesta.setNotaCreditoId(salida.notaCreditoId());
        respuesta.setCuf(salida.cuf());
        respuesta.setDevolucionTotal(salida.devolucionTotal());
        respuesta.setDisponibleRestante(MapeoDeTarifas.dinero(salida.disponibleRestante()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
