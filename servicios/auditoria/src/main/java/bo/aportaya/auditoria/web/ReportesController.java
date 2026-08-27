package bo.aportaya.auditoria.web;

import bo.aportaya.auditoria.aplicacion.CU58DescargarExportacion;
import bo.aportaya.auditoria.aplicacion.CU58EjecutarReporte;
import bo.aportaya.auditoria.web.generado.ReportesApi;
import bo.aportaya.auditoria.web.generado.modelo.EntradaReporte;
import bo.aportaya.auditoria.web.generado.modelo.SalidaReporte;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * La pagina de CU-58: traduce y delega, sin logica.
 *
 * <p>El {@code @Permiso} de la clase es el <b>piso</b>: hay que poder ejecutar reportes
 * para llegar hasta aca. El permiso que decide de verdad es el que declara <b>cada
 * definicion</b>, y ese lo verifica el caso de uso porque depende del dato y no de la
 * ruta. Un guardia de ruta no puede saber que exige un reporte que se dio de alta
 * ayer.
 *
 * <p>Por eso los permisos del token entran como parametro: el controlador los lee de la
 * sesion y el organismo decide. La decision no vive en la pagina.
 */
@RestController
public class ReportesController implements ReportesApi {

    private final CU58EjecutarReporte cu58;
    private final CU58DescargarExportacion descargas;
    private final SesionDeLaPeticion sesion;

    public ReportesController(CU58EjecutarReporte cu58, CU58DescargarExportacion descargas, SesionDeLaPeticion sesion) {
        this.cu58 = cu58;
        this.descargas = descargas;
        this.sesion = sesion;
    }

    /**
     * {@code AUDITORIA_LEER} sale del catalogo sembrado. La primera version decia
     * {@code REPORTES_EJECUTAR}, que suena mejor y <b>no existe</b>: un permiso que solo
     * vive en una anotacion no se le puede asignar a nadie, asi que el endpoint queda
     * cerrado para todos o —peor— abierto, segun como resuelva el guardia un codigo
     * desconocido. Los codigos no se inventan.
     */
    @Override
    @Permiso("AUDITORIA_LEER")
    public ResponseEntity<SalidaReporte> ejecutarReporte(
            UUID definicionId, UUID idempotencyKey, EntradaReporte cuerpo) {

        Traza.marcarCasoDeUso("CU-58", String.valueOf(definicionId));

        var salida = cu58.ejecutar(
                new CU58EjecutarReporte.EntradaReporte(
                        definicionId,
                        cuerpo.getParametros(),
                        Optional.ofNullable(cuerpo.getFormato()).map(f -> f.getValue()),
                        Optional.ofNullable(cuerpo.getJustificacion()),
                        sesion.permisos()),
                sesion.actual());

        SalidaReporte respuesta = new SalidaReporte(
                salida.ejecucionId(),
                SalidaReporte.EstadoEnum.fromValue(salida.estado()),
                salida.filasGeneradas(),
                salida.hashResultado());
        salida.exportacionId().ifPresent(respuesta::setExportacionId);

        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * Devuelve {@code 204} y no el archivo: esta operacion <b>autoriza</b> la descarga y
     * la cuenta. El archivo lo entrega el almacenamiento, con su propia URL firmada
     * (ADR-034); hacerlo pasar por el servicio pondria un reporte de cien megabytes en
     * el camino de todas las demas peticiones.
     */
    @Override
    @Permiso("AUDITORIA_LEER")
    public ResponseEntity<Void> descargarExportacion(UUID exportacionId, UUID idempotencyKey) {
        Traza.marcarCasoDeUso("CU-58", String.valueOf(exportacionId));
        descargas.ejecutar(exportacionId, sesion.actual());
        return ResponseEntity.noContent().build();
    }
}
