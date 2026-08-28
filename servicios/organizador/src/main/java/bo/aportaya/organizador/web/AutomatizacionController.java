package bo.aportaya.organizador.web;

import bo.aportaya.organizador.aplicacion.CU95DefinirAutomatizacion;
import bo.aportaya.organizador.aplicacion.CU96EjecutarTarea;
import bo.aportaya.organizador.web.generado.AutomatizacionApi;
import bo.aportaya.organizador.web.generado.modelo.EntradaEjecucion;
import bo.aportaya.organizador.web.generado.modelo.EntradaProgramacion;
import bo.aportaya.organizador.web.generado.modelo.EntradaRegla;
import bo.aportaya.organizador.web.generado.modelo.SalidaEjecucion;
import bo.aportaya.organizador.web.generado.modelo.SalidaProgramacion;
import bo.aportaya.organizador.web.generado.modelo.SalidaRegla;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /automatizacion}: reglas y tareas del organizador.
 *
 * <p>Definir una regla y activarla son dos actos: una regla que se activa al definirse
 * empieza a operar sobre grupos reales antes de que nadie la haya leido entera.
 */
@RestController
public class AutomatizacionController implements AutomatizacionApi {

    private final CU95DefinirAutomatizacion cu95;
    private final CU96EjecutarTarea cu96;
    private final SesionDeLaPeticion sesion;

    public AutomatizacionController(CU95DefinirAutomatizacion cu95, CU96EjecutarTarea cu96, SesionDeLaPeticion sesion) {
        this.cu95 = cu95;
        this.cu96 = cu96;
        this.sesion = sesion;
    }

    @Override
    @Permiso("ADMIN_PLATAFORMA")
    public ResponseEntity<SalidaRegla> definirRegla(EntradaRegla cuerpo) {
        Traza.marcarCasoDeUso("CU-95", cuerpo.getCodigo());

        var salida = cu95.definir(
                new CU95DefinirAutomatizacion.EntradaRegla(
                        cuerpo.getCodigo(),
                        cuerpo.getDescripcion(),
                        cuerpo.getDisparador().getValue(),
                        cuerpo.getExpresionDisparo(),
                        cuerpo.getCondicion(),
                        cuerpo.getAccion().getValue(),
                        Boolean.TRUE.equals(cuerpo.getRequiereConfirmacionHumana()),
                        cuerpo.getPrioridad()),
                sesion.actual());
        return ResponseEntity.status(HttpStatus.CREATED).body(regla(salida));
    }

    @Override
    @Permiso("ADMIN_PLATAFORMA")
    public ResponseEntity<SalidaRegla> activarRegla(UUID reglaId) {
        Traza.marcarCasoDeUso("CU-95", reglaId.toString());
        return ResponseEntity.ok(regla(cu95.activar(reglaId, sesion.actual())));
    }

    private SalidaRegla regla(CU95DefinirAutomatizacion.SalidaRegla salida) {
        var respuesta = new SalidaRegla();
        respuesta.setReglaId(salida.reglaId());
        respuesta.setCodigo(salida.codigo());
        respuesta.setActiva(salida.activa());
        respuesta.setRequiereConfirmacionHumana(salida.requiereConfirmacionHumana());
        return respuesta;
    }

    @Override
    @Permiso("ORGANIZADOR")
    public ResponseEntity<SalidaProgramacion> programarTarea(EntradaProgramacion cuerpo) {
        Traza.marcarCasoDeUso("CU-96", cuerpo.getReglaId().toString());

        var salida = cu96.programar(
                new CU96EjecutarTarea.EntradaProgramacion(
                        cuerpo.getReglaId(), cuerpo.getGrupoId(), cuerpo.getProgramadaPara()),
                sesion.actual());

        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(programacion(salida));
    }

    @Override
    @Permiso("ORGANIZADOR")
    public ResponseEntity<SalidaProgramacion> aprobarTarea(UUID tareaId) {
        Traza.marcarCasoDeUso("CU-96", tareaId.toString());
        return ResponseEntity.ok(programacion(cu96.aprobar(tareaId, sesion.actual())));
    }

    private SalidaProgramacion programacion(CU96EjecutarTarea.SalidaProgramacion salida) {
        var respuesta = new SalidaProgramacion();
        respuesta.setTareaId(salida.tareaId());
        respuesta.setEstado(SalidaProgramacion.EstadoEnum.fromValue(salida.estado()));
        respuesta.setClaveIdempotencia(salida.claveIdempotencia());
        respuesta.setEsNueva(salida.esNueva());
        return respuesta;
    }

    @Override
    @Permiso("ORGANIZADOR")
    public ResponseEntity<SalidaEjecucion> anotarEjecucion(UUID tareaId, EntradaEjecucion cuerpo) {
        Traza.marcarCasoDeUso("CU-96", tareaId.toString());

        var salida = cu96.anotarEjecucion(
                new CU96EjecutarTarea.EntradaEjecucion(
                        tareaId,
                        cuerpo.getIniciada(),
                        cuerpo.getResultado().getValue(),
                        cuerpo.getRegistrosAfectados(),
                        cuerpo.getDetalleJson(),
                        cuerpo.getMensajeError()),
                sesion.actual());

        var respuesta = new SalidaEjecucion();
        respuesta.setEjecucionId(salida.ejecucionId());
        respuesta.setEstadoDeLaTarea(salida.estadoDeLaTarea());
        respuesta.setIntentos(salida.intentos());
        respuesta.setEsNueva(salida.esNueva());

        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }
}
