package bo.aportaya.aportes.web;

import bo.aportaya.aportes.aplicacion.CU21CobrarAporte;
import bo.aportaya.aportes.web.generado.AportesApi;
import bo.aportaya.aportes.web.generado.modelo.EntradaCobro;
import bo.aportaya.aportes.web.generado.modelo.SalidaCobro;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * La pagina de {@code /aportes}: traduce y delega, sin logica.
 *
 * <p>La clave de idempotencia entra por cabecera y **se le pasa al caso de uso**, que
 * es quien la valida antes de escribir (invariante 7). Aca no se decide nada sobre
 * ella: comprobarla en la pagina dejaria la ventana entre la comprobacion y el
 * {@code INSERT} abierta para el segundo intento.
 */
@RestController
public class AportesController implements AportesApi {

    private final CU21CobrarAporte cu21;
    private final SesionDeLaPeticion sesion;

    public AportesController(CU21CobrarAporte cu21, SesionDeLaPeticion sesion) {
        this.cu21 = cu21;
        this.sesion = sesion;
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaCobro> cobrarAporte(UUID idempotencyKey, UUID obligacionId, EntradaCobro cuerpo) {
        Traza.marcarCasoDeUso("CU-21", obligacionId.toString());

        var salida = cu21.acreditar(
                new CU21CobrarAporte.EntradaCobro(
                        idempotencyKey.toString(),
                        obligacionId,
                        MapeoDeAportes.dinero(cuerpo.getMonto()),
                        cuerpo.getRecargo() == null
                                ? bo.aportaya.plataforma.dominio.Dinero.cero(
                                        MapeoDeAportes.dinero(cuerpo.getMonto()).moneda())
                                : MapeoDeAportes.dinero(cuerpo.getRecargo()),
                        cuerpo.getCanal().getValue(),
                        cuerpo.getReferenciaProveedor(),
                        Optional.ofNullable(cuerpo.getProveedorId()),
                        Boolean.TRUE.equals(cuerpo.getEsManual()),
                        true),
                sesion.actual());

        var respuesta = new SalidaCobro();
        respuesta.setPagoId(salida.pagoId());
        respuesta.setObligacionId(salida.obligacionId());
        respuesta.setEstadoObligacion(SalidaCobro.EstadoObligacionEnum.fromValue(salida.estadoObligacion()));
        respuesta.setPendiente(MapeoDeAportes.dinero(salida.pendiente()));
        respuesta.setEsNuevo(salida.esNuevo());

        // 201 cuando el cobro se acredito ahora; 200 cuando el reintento devolvio el
        // que ya existia. Que el cliente pueda distinguirlos es lo que hace que
        // reintentar sea seguro sin adivinar.
        var estado = salida.esNuevo() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }
}
