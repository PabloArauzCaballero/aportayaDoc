package bo.aportaya.entregas.web;

import bo.aportaya.entregas.aplicacion.CU22LiquidarEntrega;
import bo.aportaya.entregas.dominio.LiquidacionDeEntrega;
import bo.aportaya.entregas.web.generado.EntregasApi;
import bo.aportaya.entregas.web.generado.modelo.EjecutarEntregaRequest;
import bo.aportaya.entregas.web.generado.modelo.EntradaLiquidacion;
import bo.aportaya.entregas.web.generado.modelo.SalidaAutorizacion;
import bo.aportaya.entregas.web.generado.modelo.SalidaEjecucionEntrega;
import bo.aportaya.entregas.web.generado.modelo.SalidaLiquidacion;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /entregas}: liquidar, autorizar y ejecutar.
 *
 * <p>Los tres pasos son tres operaciones y no una a proposito: **quien autoriza no
 * ejecuta** (R-SEG-04), y eso solo se puede sostener si son actos separados, cada uno
 * con su sesion y su permiso.
 */
@RestController
public class EntregasController implements EntregasApi {

    private final CU22LiquidarEntrega cu22;
    private final SesionDeLaPeticion sesion;

    public EntregasController(CU22LiquidarEntrega cu22, SesionDeLaPeticion sesion) {
        this.cu22 = cu22;
        this.sesion = sesion;
    }

    @Override
    @Permiso("ENTREGA_EJECUTAR")
    public ResponseEntity<SalidaLiquidacion> liquidarEntrega(UUID idempotencyKey, EntradaLiquidacion cuerpo) {
        Traza.marcarCasoDeUso("CU-22", cuerpo.getTurnoId().toString());

        var salida = cu22.liquidar(
                new CU22LiquidarEntrega.EntradaLiquidacion(
                        cuerpo.getGrupoId(),
                        cuerpo.getPeriodoId(),
                        cuerpo.getTurnoId(),
                        cuerpo.getCupoId(),
                        cuerpo.getBeneficiarioId(),
                        MapeoDeEntregas.dinero(cuerpo.getBruto()),
                        MapeoDeEntregas.dinero(cuerpo.getRecaudado()),
                        cuerpo.getDeducciones().stream()
                                .map(d -> new LiquidacionDeEntrega.Deduccion(
                                        d.getTipo().getValue(),
                                        d.getDescripcion(),
                                        MapeoDeEntregas.dinero(d.getMonto()),
                                        d.getReferenciaOrigenId(),
                                        Boolean.TRUE.equals(d.getEsObligatoria())))
                                .toList(),
                        cuerpo.getMetodoDesembolso().getValue(),
                        cuerpo.getFechaProgramada()),
                sesion.actual());

        var respuesta = new SalidaLiquidacion();
        respuesta.setEntregaId(salida.entregaId());
        respuesta.setBruto(MapeoDeEntregas.dinero(salida.bruto()));
        respuesta.setTotalDeducciones(MapeoDeEntregas.dinero(salida.totalDeducciones()));
        respuesta.setNeto(MapeoDeEntregas.dinero(salida.neto()));
        respuesta.setEstado(salida.estado());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("ENTREGA_AUTORIZAR")
    public ResponseEntity<SalidaAutorizacion> autorizarEntrega(UUID entregaId) {
        Traza.marcarCasoDeUso("CU-22", entregaId.toString());

        var salida = cu22.autorizar(entregaId, sesion.actual());

        var respuesta = new SalidaAutorizacion();
        respuesta.setEntregaId(salida.entregaId());
        respuesta.setEstado(SalidaAutorizacion.EstadoEnum.fromValue(salida.estado()));
        respuesta.setAutorizadaPor(salida.autorizadaPor());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("ENTREGA_EJECUTAR")
    public ResponseEntity<SalidaEjecucionEntrega> ejecutarEntrega(UUID entregaId, EjecutarEntregaRequest cuerpo) {
        Traza.marcarCasoDeUso("CU-22", entregaId.toString());

        var salida = cu22.ejecutar(entregaId, MapeoDeEntregas.dinero(cuerpo.getMontoEntregado()), sesion.actual());

        var respuesta = new SalidaEjecucionEntrega();
        respuesta.setEntregaId(salida.entregaId());
        respuesta.setEstado(SalidaEjecucionEntrega.EstadoEnum.fromValue(salida.estado()));
        respuesta.setMontoEntregado(MapeoDeEntregas.dinero(salida.montoEntregado()));
        return ResponseEntity.ok(respuesta);
    }
}
