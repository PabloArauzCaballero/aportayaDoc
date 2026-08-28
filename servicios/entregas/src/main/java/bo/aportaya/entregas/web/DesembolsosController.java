package bo.aportaya.entregas.web;

import bo.aportaya.entregas.aplicacion.CU28EmitirDesembolso;
import bo.aportaya.entregas.web.generado.DesembolsosApi;
import bo.aportaya.entregas.web.generado.modelo.EntradaOrdenDesembolso;
import bo.aportaya.entregas.web.generado.modelo.EntradaRespuestaDesembolso;
import bo.aportaya.entregas.web.generado.modelo.SalidaIntentoDesembolso;
import bo.aportaya.entregas.web.generado.modelo.SalidaOrdenDesembolso;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /desembolsos}: la orden y la respuesta del proveedor.
 *
 * <p>La respuesta se anota como un intento mas y nunca sobreescribe al anterior: lo que
 * un proveedor contesto —y cuando— es lo unico que permite reclamarle despues.
 */
@RestController
public class DesembolsosController implements DesembolsosApi {

    private final CU28EmitirDesembolso cu28;
    private final SesionDeLaPeticion sesion;

    public DesembolsosController(CU28EmitirDesembolso cu28, SesionDeLaPeticion sesion) {
        this.cu28 = cu28;
        this.sesion = sesion;
    }

    @Override
    @Permiso("ENTREGA_EJECUTAR")
    public ResponseEntity<SalidaOrdenDesembolso> emitirOrdenDesembolso(
            UUID idempotencyKey, EntradaOrdenDesembolso cuerpo) {
        Traza.marcarCasoDeUso("CU-28", cuerpo.getEntregaId().toString());

        var salida = cu28.emitir(
                new CU28EmitirDesembolso.EntradaOrden(
                        cuerpo.getEntregaId(),
                        cuerpo.getProveedorId(),
                        cuerpo.getCuentaDestinoId(),
                        cuerpo.getGlosa(),
                        idempotencyKey.toString(),
                        Boolean.TRUE.equals(cuerpo.getSaldoRetenido())),
                sesion.actual());

        var respuesta = new SalidaOrdenDesembolso();
        respuesta.setOrdenId(salida.ordenId());
        respuesta.setEstado(SalidaOrdenDesembolso.EstadoEnum.fromValue(salida.estado()));
        respuesta.setClaveIdempotencia(salida.claveIdempotencia());
        respuesta.setIntentos(salida.intentos());
        respuesta.setEsNueva(salida.esNueva());

        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("ENTREGA_EJECUTAR")
    public ResponseEntity<SalidaIntentoDesembolso> anotarRespuestaDesembolso(
            UUID ordenId, EntradaRespuestaDesembolso cuerpo) {
        Traza.marcarCasoDeUso("CU-28", ordenId.toString());

        var salida = cu28.anotarRespuesta(
                new CU28EmitirDesembolso.EntradaRespuesta(
                        ordenId,
                        cuerpo.getIniciado(),
                        Boolean.TRUE.equals(cuerpo.getExitoso()),
                        cuerpo.getReferenciaProveedor(),
                        cuerpo.getCodigoError(),
                        cuerpo.getMensajeProveedor()),
                sesion.actual());

        var respuesta = new SalidaIntentoDesembolso();
        respuesta.setOrdenId(salida.ordenId());
        respuesta.setEstado(salida.estado());
        respuesta.setNumeroIntento(salida.numeroIntento());
        respuesta.setReintentableEn(salida.reintentableEn());
        respuesta.setEsNuevo(salida.esNuevo());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
