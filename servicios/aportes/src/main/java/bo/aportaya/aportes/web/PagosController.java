package bo.aportaya.aportes.web;

import bo.aportaya.aportes.aplicacion.CU19ReembolsarPago;
import bo.aportaya.aportes.aplicacion.CU99EnrutarProveedor;
import bo.aportaya.aportes.web.generado.PagosApi;
import bo.aportaya.aportes.web.generado.modelo.EntradaDisputa;
import bo.aportaya.aportes.web.generado.modelo.EntradaEnrutamiento;
import bo.aportaya.aportes.web.generado.modelo.EntradaProveedor;
import bo.aportaya.aportes.web.generado.modelo.EntradaReembolso;
import bo.aportaya.aportes.web.generado.modelo.SalidaDisputa;
import bo.aportaya.aportes.web.generado.modelo.SalidaEjecucionReembolso;
import bo.aportaya.aportes.web.generado.modelo.SalidaEnrutamiento;
import bo.aportaya.aportes.web.generado.modelo.SalidaProveedor;
import bo.aportaya.aportes.web.generado.modelo.SalidaReembolso;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Las paginas de {@code /pagos}: reembolsos, disputas y enrutado de proveedores. */
@RestController
public class PagosController implements PagosApi {

    private final CU19ReembolsarPago cu19;
    private final CU99EnrutarProveedor cu99;
    private final SesionDeLaPeticion sesion;

    public PagosController(CU19ReembolsarPago cu19, CU99EnrutarProveedor cu99, SesionDeLaPeticion sesion) {
        this.cu19 = cu19;
        this.cu99 = cu99;
        this.sesion = sesion;
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaReembolso> solicitarReembolso(
            UUID idempotencyKey, UUID pagoId, EntradaReembolso cuerpo) {
        Traza.marcarCasoDeUso("CU-19", pagoId.toString());

        var salida = cu19.solicitar(
                new CU19ReembolsarPago.EntradaReembolso(
                        pagoId,
                        MapeoDeAportes.dinero(cuerpo.getMonto()),
                        cuerpo.getMotivo().getValue()),
                sesion.actual());

        var respuesta = new SalidaReembolso();
        respuesta.setReembolsoId(salida.reembolsoId());
        respuesta.setEstado(SalidaReembolso.EstadoEnum.fromValue(salida.estado()));
        respuesta.setDisponibleRestante(MapeoDeAportes.dinero(salida.disponibleRestante()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("REVERSO_AUTORIZAR")
    public ResponseEntity<SalidaEjecucionReembolso> aprobarReembolso(UUID idempotencyKey, UUID reembolsoId) {
        Traza.marcarCasoDeUso("CU-19", reembolsoId.toString());

        var salida = cu19.aprobar(reembolsoId, sesion.actual());

        var respuesta = new SalidaEjecucionReembolso();
        respuesta.setReembolsoId(salida.reembolsoId());
        respuesta.setEstado(SalidaEjecucionReembolso.EstadoEnum.fromValue(salida.estado()));
        respuesta.setObligacionId(salida.obligacionId());
        respuesta.setEstadoObligacion(salida.estadoObligacion());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("SOPORTE")
    public ResponseEntity<SalidaDisputa> registrarDisputa(UUID pagoId, EntradaDisputa cuerpo) {
        Traza.marcarCasoDeUso("CU-19", pagoId.toString());

        var salida = cu19.registrarDisputa(
                new CU19ReembolsarPago.EntradaDisputa(
                        pagoId,
                        cuerpo.getTipo().getValue(),
                        cuerpo.getDescripcion(),
                        MapeoDeAportes.dinero(cuerpo.getMontoDisputado()),
                        cuerpo.getReferenciaDelProveedor()),
                sesion.actual());

        var respuesta = new SalidaDisputa();
        respuesta.setDisputaId(salida.disputaId());
        respuesta.setFechaLimiteRespuesta(salida.fechaLimiteRespuesta());
        respuesta.setEsNueva(salida.esNueva());
        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("ADMIN_PLATAFORMA")
    public ResponseEntity<SalidaProveedor> darDeAltaProveedor(UUID idempotencyKey, EntradaProveedor cuerpo) {
        Traza.marcarCasoDeUso("CU-99", cuerpo.getCodigo());

        var salida = cu99.darDeAlta(
                new CU99EnrutarProveedor.EntradaAlta(
                        cuerpo.getCodigo(),
                        cuerpo.getNombre(),
                        cuerpo.getTipo().getValue(),
                        cuerpo.getUrlBase().toString(),
                        cuerpo.getReferenciaCredenciales(),
                        new BigDecimal(cuerpo.getComisionFija()),
                        new BigDecimal(cuerpo.getComisionPorcentual()),
                        Boolean.TRUE.equals(cuerpo.getSoportaWebhook()),
                        Boolean.TRUE.equals(cuerpo.getSoportaConsultaEstado()),
                        cuerpo.getPrioridad(),
                        Boolean.TRUE.equals(cuerpo.getTieneContratoVigente()),
                        Boolean.TRUE.equals(cuerpo.getPruebasCompletas())),
                sesion.actual());

        var respuesta = new SalidaProveedor();
        respuesta.setProveedorId(salida.proveedorId());
        respuesta.setCodigo(salida.codigo());
        respuesta.setActivo(salida.activo());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaEnrutamiento> enrutarCobro(EntradaEnrutamiento cuerpo) {
        Traza.marcarCasoDeUso("CU-99", cuerpo.getClaveIdempotencia());

        var salida = cu99.enrutar(
                new CU99EnrutarProveedor.EntradaEnrutamiento(
                        cuerpo.getClaveIdempotencia(), cuerpo.getYaIntentados(), cuerpo.getSaludObservada()),
                sesion.actual());

        var respuesta = new SalidaEnrutamiento();
        respuesta.setProveedorId(salida.proveedorId());
        respuesta.setProveedorCodigo(salida.proveedorCodigo());
        respuesta.setClaveIdempotencia(salida.claveIdempotencia());
        respuesta.setEstadoAnteTimeout(SalidaEnrutamiento.EstadoAnteTimeoutEnum.fromValue(salida.estadoAnteTimeout()));
        respuesta.setPuedeConsultarEstado(salida.puedeConsultarEstado());
        return ResponseEntity.ok(respuesta);
    }
}
