package bo.aportaya.garantia.web;

import bo.aportaya.garantia.aplicacion.CU23CubrirIncumplimiento;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento;
import bo.aportaya.garantia.aplicacion.CU26EjecutarAval;
import bo.aportaya.garantia.aplicacion.CU27RestringirDeudor;
import bo.aportaya.garantia.aplicacion.CU66ReemplazarParticipante;
import bo.aportaya.garantia.web.generado.IncumplimientosApi;
import bo.aportaya.garantia.web.generado.modelo.EntradaCobertura;
import bo.aportaya.garantia.web.generado.modelo.EntradaDeclaracion;
import bo.aportaya.garantia.web.generado.modelo.EntradaDescargo;
import bo.aportaya.garantia.web.generado.modelo.EntradaReemplazo;
import bo.aportaya.garantia.web.generado.modelo.EntradaResolucionDescargo;
import bo.aportaya.garantia.web.generado.modelo.EntradaRestriccion;
import bo.aportaya.garantia.web.generado.modelo.SalidaCobertura;
import bo.aportaya.garantia.web.generado.modelo.SalidaDeclaracion;
import bo.aportaya.garantia.web.generado.modelo.SalidaDescargo;
import bo.aportaya.garantia.web.generado.modelo.SalidaEjecucionAval;
import bo.aportaya.garantia.web.generado.modelo.SalidaReemplazo;
import bo.aportaya.garantia.web.generado.modelo.SalidaResolucionDescargo;
import bo.aportaya.garantia.web.generado.modelo.SalidaRestriccion;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /incumplimientos}: el debido proceso completo.
 *
 * <p>Declarar, descargar, resolver y recien despues cubrir o ejecutar el aval son pasos
 * separados **porque el orden es el debido proceso**: cubrir antes de vencer el plazo de
 * descargo seria sancionar a alguien que todavia no fue oido.
 */
@RestController
public class IncumplimientosController implements IncumplimientosApi {

    private final CU23CubrirIncumplimiento cu23;
    private final CU25DeclararIncumplimiento cu25;
    private final CU26EjecutarAval cu26;
    private final CU27RestringirDeudor cu27;
    private final CU66ReemplazarParticipante cu66;
    private final SesionDeLaPeticion sesion;

    public IncumplimientosController(
            CU23CubrirIncumplimiento cu23,
            CU25DeclararIncumplimiento cu25,
            CU26EjecutarAval cu26,
            CU27RestringirDeudor cu27,
            CU66ReemplazarParticipante cu66,
            SesionDeLaPeticion sesion) {
        this.cu23 = cu23;
        this.cu25 = cu25;
        this.cu26 = cu26;
        this.cu27 = cu27;
        this.cu66 = cu66;
        this.sesion = sesion;
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<SalidaDeclaracion> declararIncumplimiento(UUID idempotencyKey, EntradaDeclaracion cuerpo) {
        Traza.marcarCasoDeUso("CU-25", cuerpo.getCodigoExpediente());

        var salida = cu25.declarar(
                new CU25DeclararIncumplimiento.EntradaDeclaracion(
                        cuerpo.getCodigoExpediente(),
                        cuerpo.getUsuarioId(),
                        cuerpo.getParticipanteId(),
                        cuerpo.getGrupoId(),
                        cuerpo.getPeriodoId(),
                        cuerpo.getCupoId(),
                        cuerpo.getObligacionId(),
                        cuerpo.getTipo().getValue(),
                        cuerpo.getSeveridad().getValue(),
                        cuerpo.getOrigenDeteccion().getValue(),
                        MapeoDeGarantia.dinero(cuerpo.getMontoInvolucrado()),
                        cuerpo.getDiasMora(),
                        Boolean.TRUE.equals(cuerpo.getAfectoALaEntrega()),
                        cuerpo.getTipoDeEvidencia().getValue(),
                        cuerpo.getDescripcionDeLaEvidencia(),
                        cuerpo.getUrlDeLaEvidencia(),
                        cuerpo.getHashDeLaEvidencia()),
                sesion.actual());

        var respuesta = new SalidaDeclaracion();
        respuesta.setExpedienteId(salida.expedienteId());
        respuesta.setCodigoExpediente(salida.codigoExpediente());
        respuesta.setEstado(salida.estado());
        respuesta.setPuedeDescargarHasta(salida.puedeDescargarHasta());
        respuesta.setEsNuevo(salida.esNuevo());

        var estado = salida.esNuevo() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<SalidaDescargo> presentarDescargo(UUID expedienteId, EntradaDescargo cuerpo) {
        Traza.marcarCasoDeUso("CU-25", expedienteId.toString());

        var salida = cu25.presentarDescargo(
                new CU25DeclararIncumplimiento.EntradaDescargo(
                        expedienteId, cuerpo.getArgumento(), cuerpo.getEvidenciasJson()),
                sesion.actual());

        var respuesta = new SalidaDescargo();
        respuesta.setDescargoId(salida.descargoId());
        respuesta.setEstado(salida.estado());
        respuesta.setEsNuevo(salida.esNuevo());

        var estado = salida.esNuevo() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<SalidaResolucionDescargo> resolverDescargo(
            UUID expedienteId, EntradaResolucionDescargo cuerpo) {
        Traza.marcarCasoDeUso("CU-25", expedienteId.toString());

        var salida = cu25.resolverDescargo(
                new CU25DeclararIncumplimiento.EntradaResolucion(
                        expedienteId, Boolean.TRUE.equals(cuerpo.getAceptado()), cuerpo.getResolucion()),
                sesion.actual());

        var respuesta = new SalidaResolucionDescargo();
        respuesta.setDescargoId(salida.descargoId());
        respuesta.setEstadoDelDescargo(
                SalidaResolucionDescargo.EstadoDelDescargoEnum.fromValue(salida.estadoDelDescargo()));
        respuesta.setEstadoDelExpediente(salida.estadoDelExpediente());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<SalidaCobertura> cubrirIncumplimiento(UUID expedienteId, EntradaCobertura cuerpo) {
        Traza.marcarCasoDeUso("CU-23", expedienteId.toString());

        var salida = cu23.cubrir(
                new CU23CubrirIncumplimiento.EntradaCobertura(
                        expedienteId,
                        MapeoDeGarantia.dinero(cuerpo.getMontoSolicitado()),
                        cuerpo.getDiasMora(),
                        cuerpo.getAprobadaPor()),
                sesion.actual());

        var respuesta = new SalidaCobertura();
        respuesta.setCoberturaId(salida.coberturaId());
        respuesta.setDeudaId(salida.deudaId());
        respuesta.setMontoCubierto(MapeoDeGarantia.dinero(salida.montoCubierto()));
        respuesta.setLimiteQueMando(SalidaCobertura.LimiteQueMandoEnum.fromValue(salida.limiteQueMando()));
        respuesta.setRequiereAprobacion(salida.requiereAprobacion());
        respuesta.setEsNueva(salida.esNueva());

        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<SalidaEjecucionAval> ejecutarAval(UUID expedienteId) {
        Traza.marcarCasoDeUso("CU-26", expedienteId.toString());

        var salida = cu26.ejecutar(expedienteId, sesion.actual());

        var respuesta = new SalidaEjecucionAval();
        respuesta.setEjecucionId(salida.ejecucionId());
        respuesta.setSubrogacionId(salida.subrogacionId());
        respuesta.setMontoEjecutado(MapeoDeGarantia.dinero(salida.montoEjecutado()));
        respuesta.setAvalistaUsuarioId(salida.avalistaUsuarioId());
        respuesta.setEsNueva(salida.esNueva());

        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<SalidaRestriccion> restringirDeudor(UUID expedienteId, EntradaRestriccion cuerpo) {
        Traza.marcarCasoDeUso("CU-27", expedienteId.toString());

        var salida = cu27.restringir(
                new CU27RestringirDeudor.EntradaRestriccion(
                        expedienteId,
                        cuerpo.getNivel().getValue(),
                        cuerpo.getMotivo(),
                        Optional.ofNullable(cuerpo.getDuracionDias()).map(Duration::ofDays)),
                sesion.actual());

        var respuesta = new SalidaRestriccion();
        respuesta.setRestriccionId(salida.restriccionId());
        respuesta.setNivel(salida.nivel());
        respuesta.setVigenteHasta(salida.vigenteHasta());
        respuesta.setEsNueva(salida.esNueva());

        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<SalidaReemplazo> proponerReemplazo(UUID expedienteId, EntradaReemplazo cuerpo) {
        Traza.marcarCasoDeUso("CU-66", expedienteId.toString());

        var salida = cu66.proponer(
                new CU66ReemplazarParticipante.EntradaReemplazo(
                        expedienteId,
                        cuerpo.getCupoId(),
                        cuerpo.getEntranteId(),
                        MapeoDeGarantia.dinero(cuerpo.getDeudaQueAsumeElEntrante()),
                        Boolean.TRUE.equals(cuerpo.getConservaOrdenDeTurno())),
                sesion.actual());

        var estado = salida.esNuevo() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(MapeoDeReemplazo.salida(salida));
    }
}
