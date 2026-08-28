package bo.aportaya.cumplimiento.web;

import bo.aportaya.cumplimiento.aplicacion.CU54RegistrarRiesgoOperativo;
import bo.aportaya.cumplimiento.aplicacion.CU55GestionarIncidente;
import bo.aportaya.cumplimiento.web.generado.CumplimientoApi;
import bo.aportaya.cumplimiento.web.generado.modelo.EntradaIncidente;
import bo.aportaya.cumplimiento.web.generado.modelo.EntradaRiesgoOperativo;
import bo.aportaya.cumplimiento.web.generado.modelo.SalidaIncidente;
import bo.aportaya.cumplimiento.web.generado.modelo.SalidaRiesgoOperativo;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de las rutas {@code /cumplimiento}: traducen y delegan, sin logica.
 *
 * <p><b>Es una sola clase y no una por caso de uso a proposito.</b> El generador agrupa
 * las operaciones por el primer tramo de la ruta, asi que CU-54 y CU-55 caen en la misma
 * interfaz; dos {@code @RestController} implementandola registrarian dos veces cada
 * mapeo —incluidos los que ninguno de los dos escribe— y Spring no levanta. Partirla por
 * caso de uso exigiria partir las rutas, y las rutas las manda el contrato.
 *
 * <p>La logica esta entera en {@code aplicacion/}: esta clase no decide nada. Si aparece
 * un {@code if} sobre una regla del pasanaku aca, esta mal ubicado.
 */
@RestController
public class CumplimientoController implements CumplimientoApi {

    private final CU54RegistrarRiesgoOperativo cu54;
    private final CU55GestionarIncidente cu55;
    private final SesionDeLaPeticion sesion;
    private final AdhesionController adhesion;

    public CumplimientoController(
            CU54RegistrarRiesgoOperativo cu54,
            CU55GestionarIncidente cu55,
            bo.aportaya.cumplimiento.aplicacion.CU02ElevarDiligencia cu02,
            bo.aportaya.cumplimiento.aplicacion.CU03DeclararPep cu03,
            bo.aportaya.cumplimiento.aplicacion.CU05AceptarContrato cu05,
            SesionDeLaPeticion sesion,
            jakarta.servlet.http.HttpServletRequest peticion) {
        this.cu54 = cu54;
        this.cu55 = cu55;
        this.sesion = sesion;
        this.adhesion = new AdhesionController(cu02, cu03, cu05, sesion, peticion);
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<bo.aportaya.cumplimiento.web.generado.modelo.SalidaAceptacion> aceptarContrato(
            UUID idempotencyKey,
            UUID contratoId,
            bo.aportaya.cumplimiento.web.generado.modelo.EntradaAceptacion cuerpo) {
        return adhesion.aceptarContrato(contratoId, cuerpo);
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<bo.aportaya.cumplimiento.web.generado.modelo.SalidaPep> declararPep(
            UUID usuarioId, bo.aportaya.cumplimiento.web.generado.modelo.EntradaPep cuerpo) {
        return adhesion.declararPep(usuarioId, cuerpo);
    }

    @Override
    @Permiso("ANALISTA_CUMPLIMIENTO")
    public ResponseEntity<bo.aportaya.cumplimiento.web.generado.modelo.SalidaDiligencia> elevarDiligencia(
            UUID idempotencyKey,
            UUID usuarioId,
            bo.aportaya.cumplimiento.web.generado.modelo.EntradaDiligencia cuerpo) {
        return adhesion.elevarDiligencia(idempotencyKey, usuarioId, cuerpo);
    }

    /**
     * Devuelve {@code 201} incluso sin plan de accion: el recurso que se creo es el
     * evento, y perder el registro porque todavia no hay responsable asignado seria
     * perder justo lo que la norma pide conservar.
     */
    @Override
    @Permiso("RESPONSABLE_RIESGOS")
    public ResponseEntity<SalidaRiesgoOperativo> registrarRiesgoOperativo(
            UUID idempotencyKey, EntradaRiesgoOperativo cuerpo) {

        Traza.marcarCasoDeUso("CU-54", cuerpo.getLineaNegocio());

        var salida = cu54.ejecutar(
                new CU54RegistrarRiesgoOperativo.EntradaEvento(
                        cuerpo.getCategoriaEvento().getValue(),
                        cuerpo.getFactorRiesgo().getValue(),
                        cuerpo.getLineaNegocio(),
                        cuerpo.getDescripcion(),
                        cuerpo.getFechaOcurrencia(),
                        cuerpo.getFechaDeteccion(),
                        cuerpo.getPerdidaBruta(),
                        Optional.ofNullable(cuerpo.getRecuperacion()),
                        cuerpo.getMoneda().getValue(),
                        Optional.ofNullable(cuerpo.getResponsableId()),
                        Optional.ofNullable(cuerpo.getAccionComprometida()),
                        Optional.ofNullable(cuerpo.getFechaCompromiso())),
                sesion.actual());

        SalidaRiesgoOperativo respuesta = new SalidaRiesgoOperativo(
                salida.eventoId(),
                salida.codigo(),
                salida.perdidaNeta(),
                SalidaRiesgoOperativo.MonedaEnum.fromValue(salida.moneda()));
        salida.planAccionId().ifPresent(respuesta::setPlanAccionId);

        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("RESPONSABLE_SEGURIDAD")
    public ResponseEntity<SalidaIncidente> registrarIncidente(UUID idempotencyKey, EntradaIncidente cuerpo) {
        Traza.marcarCasoDeUso("CU-55", cuerpo.getTipo().getValue());

        var salida = cu55.registrar(
                new CU55GestionarIncidente.EntradaIncidente(
                        cuerpo.getTipo().getValue(),
                        cuerpo.getSeveridad().getValue(),
                        Optional.ofNullable(cuerpo.getActivoInformacionId()),
                        Boolean.TRUE.equals(cuerpo.getDatosPersonalesAfectados()),
                        cuerpo.getUsuariosAfectados(),
                        cuerpo.getDetectadoEn(),
                        Optional.ofNullable(cuerpo.getEventoRiesgoId())),
                sesion.actual());

        SalidaIncidente respuesta = new SalidaIncidente(
                salida.incidenteId(),
                salida.codigo(),
                salida.plazoReporte(),
                salida.requiereNotificarTitulares(),
                salida.registradoEn());
        salida.plazoNotificacion().ifPresent(respuesta::setPlazoNotificacion);
        salida.contratoTerceroId().ifPresent(respuesta::setContratoTerceroId);

        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * Reportar y notificar son <b>dos llamadas y no dos banderas de una</b>: son dos
     * momentos distintos del expediente, cada uno con su propia hora. Es exactamente lo
     * que una inspeccion pide ver, y lo que una sola operacion con parametros perderia.
     */
    @Override
    @Permiso("RESPONSABLE_SEGURIDAD")
    public ResponseEntity<Void> reportarIncidente(UUID incidenteId, UUID idempotencyKey) {
        Traza.marcarCasoDeUso("CU-55", String.valueOf(incidenteId));
        cu55.reportarAlOrganismo(incidenteId, sesion.actual());
        return ResponseEntity.noContent().build();
    }

    @Override
    @Permiso("RESPONSABLE_SEGURIDAD")
    public ResponseEntity<Void> notificarTitulares(UUID incidenteId, UUID idempotencyKey) {
        Traza.marcarCasoDeUso("CU-55", String.valueOf(incidenteId));
        cu55.notificarTitulares(incidenteId, sesion.actual());
        return ResponseEntity.noContent().build();
    }
}
