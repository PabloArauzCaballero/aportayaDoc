package bo.aportaya.notificaciones.web;

import bo.aportaya.notificaciones.aplicacion.CU82ProcesarRespuesta;
import bo.aportaya.notificaciones.aplicacion.ConsultarSupresion;
import bo.aportaya.notificaciones.aplicacion.SolicitarAviso;
import bo.aportaya.notificaciones.web.generado.NotificacionesApi;
import bo.aportaya.notificaciones.web.generado.modelo.EntradaNotificacion;
import bo.aportaya.notificaciones.web.generado.modelo.EntradaRespuesta;
import bo.aportaya.notificaciones.web.generado.modelo.SalidaNotificacion;
import bo.aportaya.notificaciones.web.generado.modelo.SalidaRespuesta;
import bo.aportaya.notificaciones.web.generado.modelo.Supresion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.Publico;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /notificaciones}.
 *
 * <p>El webhook es **la unica puerta que abre alguien de afuera** de todo el sistema
 * que no es una ruta publica de consulta. Va sin sesion porque el proveedor no la
 * tiene, y lo que lo protege es la firma, que CU-82 verifica antes de tocar la base.
 */
@RestController
public class NotificacionesController implements NotificacionesApi {

    /**
     * El identificador del proceso que atiende los webhooks.
     *
     * <p>Fijo y no aleatorio por peticion: es lo que permite mirar la bitacora y ver
     * todo lo que entro por esta puerta, que es la unica abierta desde afuera.
     */
    private static final UUID PROCESO_DEL_WEBHOOK = UUID.fromString("00000000-0000-0000-0000-0000000082cb");

    private final SolicitarAviso avisos;
    private final ConsultarSupresion supresiones;
    private final CU82ProcesarRespuesta cu82;
    private final SesionDeLaPeticion sesion;

    public NotificacionesController(
            SolicitarAviso avisos,
            ConsultarSupresion supresiones,
            CU82ProcesarRespuesta cu82,
            SesionDeLaPeticion sesion) {
        this.avisos = avisos;
        this.supresiones = supresiones;
        this.cu82 = cu82;
        this.sesion = sesion;
    }

    /**
     * Si un destino pidio no recibir mas.
     *
     * <p>La pregunta {@code grupos} antes de invitar: a quien se dio de baja no se le
     * escribe, aunque quien invita no lo sepa.
     */
    @Override
    @Permiso("SOPORTE")
    public ResponseEntity<Supresion> consultarSupresion(String identificador, String categoria) {
        Traza.marcarCasoDeUso("CU-80", categoria);

        var respuesta = new Supresion();
        respuesta.setSuprimido(supresiones.estaSuprimido(identificador, categoria, sesion.actual()));
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("SOPORTE")
    public ResponseEntity<SalidaNotificacion> emitirNotificacion(UUID idempotencyKey, EntradaNotificacion cuerpo) {
        Traza.marcarCasoDeUso("CU-80", cuerpo.getEvento());

        var salida = avisos.ejecutar(
                new SolicitarAviso.Entrada(cuerpo.getDestinatarioId(), cuerpo.getEvento(), cuerpo.getDatos()),
                sesion.actual());

        var respuesta = new SalidaNotificacion();
        respuesta.setNotificacionId(salida.notificacionId());
        respuesta.setCanales(salida.canales().stream()
                .map(SalidaNotificacion.CanalesEnum::fromValue)
                .toList());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(respuesta);
    }

    @Override
    @Publico("CU-82: el proveedor que entrega el mensaje no tiene sesion; lo autentica su firma")
    public ResponseEntity<SalidaRespuesta> procesarRespuestaEntrante(String proveedor, EntradaRespuesta cuerpo) {
        Traza.marcarCasoDeUso("CU-82", proveedor);

        var salida = cu82.ejecutar(
                new CU82ProcesarRespuesta.EntradaRespuesta(
                        cuerpo.getCanal() == null
                                ? proveedor
                                : cuerpo.getCanal().getValue(),
                        cuerpo.getRemitente(),
                        cuerpo.getFirma(),
                        cuerpo.getCargaUtil(),
                        cuerpo.getContenido(),
                        cuerpo.getClaveIdempotencia(),
                        Optional.ofNullable(cuerpo.getNotificacionRelacionadaId())),
                // Sin sesion de usuario: el proveedor no la tiene. `deSistema` no es
                // una excepcion a las politicas de fila, es un rol con las suyas.
                ContextoSesion.deSistema(
                        PROCESO_DEL_WEBHOOK, new bo.aportaya.plataforma.dominio.Traza(Traza.actual())));

        var respuesta = new SalidaRespuesta();
        respuesta.setRespuestaId(salida.respuestaId());
        respuesta.setIntencion(SalidaRespuesta.IntencionEnum.fromValue(salida.intencion()));
        respuesta.setAccion(SalidaRespuesta.AccionEnum.fromValue(salida.accion()));
        respuesta.setReferenciaId(salida.referenciaId());
        return ResponseEntity.ok(respuesta);
    }
}
