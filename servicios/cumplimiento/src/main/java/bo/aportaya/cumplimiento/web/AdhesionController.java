package bo.aportaya.cumplimiento.web;

import bo.aportaya.cumplimiento.aplicacion.CU02ElevarDiligencia;
import bo.aportaya.cumplimiento.aplicacion.CU03DeclararPep;
import bo.aportaya.cumplimiento.aplicacion.CU05AceptarContrato;
import bo.aportaya.cumplimiento.dominio.ClasificacionPep;
import bo.aportaya.cumplimiento.web.generado.modelo.EntradaAceptacion;
import bo.aportaya.cumplimiento.web.generado.modelo.EntradaDiligencia;
import bo.aportaya.cumplimiento.web.generado.modelo.EntradaPep;
import bo.aportaya.cumplimiento.web.generado.modelo.SalidaAceptacion;
import bo.aportaya.cumplimiento.web.generado.modelo.SalidaDiligencia;
import bo.aportaya.cumplimiento.web.generado.modelo.SalidaDiligenciaLimitesNuevosInner;
import bo.aportaya.cumplimiento.web.generado.modelo.SalidaPep;
import bo.aportaya.plataforma.dominio.ClaveIdempotencia;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * La parte de {@code /cumplimiento} que atiende la adhesion y la diligencia.
 *
 * <p>No es un {@code @RestController} propio: {@link CumplimientoController} implementa
 * la interfaz generada —el generador agrupa las siete operaciones bajo
 * {@code /cumplimiento}, y dos controladores registrarian dos veces cada mapeo—. Esta
 * clase es a donde delega, para que ningun archivo cargue con las siete.
 */
final class AdhesionController {

    private final CU02ElevarDiligencia cu02;
    private final CU03DeclararPep cu03;
    private final CU05AceptarContrato cu05;
    private final SesionDeLaPeticion sesion;
    private final HttpServletRequest peticion;

    AdhesionController(
            CU02ElevarDiligencia cu02,
            CU03DeclararPep cu03,
            CU05AceptarContrato cu05,
            SesionDeLaPeticion sesion,
            HttpServletRequest peticion) {
        this.cu02 = cu02;
        this.cu03 = cu03;
        this.cu05 = cu05;
        this.sesion = sesion;
        this.peticion = peticion;
    }

    ResponseEntity<SalidaAceptacion> aceptarContrato(UUID contratoId, EntradaAceptacion cuerpo) {
        Traza.marcarCasoDeUso("CU-05", cuerpo.getUsuarioId().toString());

        // La IP y el dispositivo salen de la peticion, no del cuerpo: son la evidencia
        // del acto, y una evidencia que declara quien la firma no prueba nada.
        var salida = cu05.ejecutar(
                new CU05AceptarContrato.EntradaAceptacion(
                        cuerpo.getUsuarioId(),
                        contratoId,
                        cuerpo.getVersion(),
                        Optional.ofNullable(cuerpo.getTokenFirma()),
                        Optional.ofNullable(peticion.getRemoteAddr()),
                        Optional.empty(),
                        cuerpo.getConsentimientos()),
                sesion.actual());

        var respuesta = new SalidaAceptacion();
        respuesta.setAceptacionId(salida.aceptacionId());
        respuesta.setHashEvidencia(salida.hashEvidencia());
        respuesta.setAceptadoEn(salida.aceptadoEn());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    ResponseEntity<SalidaPep> declararPep(UUID usuarioId, EntradaPep cuerpo) {
        Traza.marcarCasoDeUso("CU-03", usuarioId.toString());

        var salida = cu03.ejecutar(
                new CU03DeclararPep.EntradaDeclaracion(
                        usuarioId,
                        Boolean.TRUE.equals(cuerpo.getEsPep()),
                        Optional.ofNullable(cuerpo.getTipoPep()).map(t -> t.getValue()),
                        Optional.ofNullable(cuerpo.getCargo()),
                        Optional.ofNullable(cuerpo.getInstitucion()),
                        cuerpo.getBeneficiariosFinales().stream()
                                .map(b -> new ClasificacionPep.BeneficiarioFinal(
                                        b.getNombre(),
                                        b.getDocumento(),
                                        Boolean.TRUE.equals(b.getEsPep()),
                                        Optional.ofNullable(b.getTipoPep())
                                                .map(t -> ClasificacionPep.TipoPep.valueOf(t.getValue()))))
                                .toList()),
                sesion.actual());

        var respuesta = new SalidaPep();
        respuesta.setDeclaracionId(salida.declaracionId());
        respuesta.setExigeDiligenciaReforzada(salida.exigeDiligenciaReforzada());
        respuesta.setNivelRiesgo(SalidaPep.NivelRiesgoEnum.fromValue(salida.nivelRiesgo()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    ResponseEntity<SalidaDiligencia> elevarDiligencia(UUID idempotencyKey, UUID usuarioId, EntradaDiligencia cuerpo) {
        Traza.marcarCasoDeUso("CU-02", usuarioId.toString());

        var salida = cu02.ejecutar(
                new CU02ElevarDiligencia.EntradaDiligencia(
                        new ClaveIdempotencia(idempotencyKey.toString()),
                        usuarioId,
                        cuerpo.getNivelDestino().getValue(),
                        cuerpo.getDocumentos().stream().map(d -> d.getTipo()).toList(),
                        cuerpo.getAprobadaPor(),
                        Optional.ofNullable(cuerpo.getSegundaRevisionPor())),
                sesion.actual());

        var respuesta = new SalidaDiligencia();
        respuesta.setDiligenciaId(salida.diligenciaId());
        respuesta.setEstado(SalidaDiligencia.EstadoEnum.fromValue(salida.estado()));
        respuesta.setFaltantes(salida.faltantes());
        respuesta.setLimitesNuevos(salida.limitesNuevos().stream()
                .map(t -> {
                    var limite = new SalidaDiligenciaLimitesNuevosInner();
                    limite.setConcepto(t.concepto());
                    limite.setMonto(t.monto());
                    limite.setVentana(t.ventana());
                    return limite;
                })
                .toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
