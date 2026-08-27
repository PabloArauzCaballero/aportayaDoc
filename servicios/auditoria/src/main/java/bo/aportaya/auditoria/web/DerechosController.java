package bo.aportaya.auditoria.web;

import bo.aportaya.auditoria.aplicacion.CU07EjercerDerechos;
import bo.aportaya.auditoria.web.generado.AuditoriaApi;
import bo.aportaya.auditoria.web.generado.modelo.EntradaDerechos;
import bo.aportaya.auditoria.web.generado.modelo.SalidaDerechos;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * La pagina de CU-07: traduce y delega, sin logica.
 *
 * <p>Devuelve {@code 201} incluso cuando el desenlace es {@code PARCIAL}: la solicitud
 * SE CREO, y ese es el recurso. Que no se haya podido cumplir entera es parte del
 * cuerpo, no un error — un {@code 422} le diria al titular que su pedido no se
 * registro, y se registro.
 */
@RestController
public class DerechosController implements AuditoriaApi {

    private final CU07EjercerDerechos cu07;
    private final SesionDeLaPeticion sesion;

    public DerechosController(CU07EjercerDerechos cu07, SesionDeLaPeticion sesion) {
        this.cu07 = cu07;
        this.sesion = sesion;
    }

    @Override
    @Permiso("DATOS_SENSIBLES_LEER")
    public ResponseEntity<SalidaDerechos> ejercerDerechos(UUID idempotencyKey, EntradaDerechos cuerpo) {
        Traza.marcarCasoDeUso("CU-07", String.valueOf(cuerpo.getUsuarioId()));

        var salida = cu07.ejecutar(
                new CU07EjercerDerechos.EntradaDerechos(
                        cuerpo.getUsuarioId(),
                        cuerpo.getTipo().getValue(),
                        cuerpo.getDescripcion(),
                        cuerpo.getUltimaActividad()),
                sesion.actual());

        SalidaDerechos respuesta = new SalidaDerechos(
                salida.solicitudId(),
                salida.fechaLimiteLegal(),
                SalidaDerechos.EstadoEnum.fromValue(salida.estado()),
                salida.datosRetenidosPorLey());
        salida.procesoAnonimizacionId().ifPresent(respuesta::setProcesoAnonimizacionId);
        if (salida.estrategia() != null) {
            respuesta.setEstrategia(SalidaDerechos.EstrategiaEnum.fromValue(salida.estrategia()));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
