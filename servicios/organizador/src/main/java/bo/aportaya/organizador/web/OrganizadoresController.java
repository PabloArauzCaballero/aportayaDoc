package bo.aportaya.organizador.web;

import bo.aportaya.organizador.aplicacion.CU90PostularOrganizador;
import bo.aportaya.organizador.aplicacion.CU91FirmarContrato;
import bo.aportaya.organizador.aplicacion.CU92EvaluarDesempeno;
import bo.aportaya.organizador.aplicacion.CU93SancionarOrganizador;
import bo.aportaya.organizador.web.generado.OrganizadoresApi;
import bo.aportaya.organizador.web.generado.modelo.EntradaApelacion;
import bo.aportaya.organizador.web.generado.modelo.EntradaAprobacion;
import bo.aportaya.organizador.web.generado.modelo.EntradaContrato;
import bo.aportaya.organizador.web.generado.modelo.EntradaEvaluacion;
import bo.aportaya.organizador.web.generado.modelo.EntradaPostulacion;
import bo.aportaya.organizador.web.generado.modelo.EntradaResolucion;
import bo.aportaya.organizador.web.generado.modelo.EntradaSancion;
import bo.aportaya.organizador.web.generado.modelo.FirmarContratoRequest;
import bo.aportaya.organizador.web.generado.modelo.RescindirContratoRequest;
import bo.aportaya.organizador.web.generado.modelo.SalidaApelacion;
import bo.aportaya.organizador.web.generado.modelo.SalidaContrato;
import bo.aportaya.organizador.web.generado.modelo.SalidaEvaluacion;
import bo.aportaya.organizador.web.generado.modelo.SalidaFirma;
import bo.aportaya.organizador.web.generado.modelo.SalidaHabilitacion;
import bo.aportaya.organizador.web.generado.modelo.SalidaPostulacion;
import bo.aportaya.organizador.web.generado.modelo.SalidaRescision;
import bo.aportaya.organizador.web.generado.modelo.SalidaResolucion;
import bo.aportaya.organizador.web.generado.modelo.SalidaSancion;
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
 * Las paginas de {@code /organizadores}: postulacion, contrato, desempeno y sancion.
 *
 * <p>Sancionar y resolver la apelacion son operaciones distintas con permisos distintos
 * a proposito: **quien decide no resuelve su propia apelacion** (R-SEG-04). Que sean dos
 * rutas es lo que permite exigirlo.
 */
@RestController
public class OrganizadoresController implements OrganizadoresApi {

    private final CU90PostularOrganizador cu90;
    private final CU91FirmarContrato cu91;
    private final CU92EvaluarDesempeno cu92;
    private final CU93SancionarOrganizador cu93;
    private final SesionDeLaPeticion sesion;

    public OrganizadoresController(
            CU90PostularOrganizador cu90,
            CU91FirmarContrato cu91,
            CU92EvaluarDesempeno cu92,
            CU93SancionarOrganizador cu93,
            SesionDeLaPeticion sesion) {
        this.cu90 = cu90;
        this.cu91 = cu91;
        this.cu92 = cu92;
        this.cu93 = cu93;
        this.sesion = sesion;
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<SalidaPostulacion> postularOrganizador(EntradaPostulacion cuerpo) {
        Traza.marcarCasoDeUso("CU-90", cuerpo.getKycReforzadoId().toString());

        var salida = cu90.postular(
                new CU90PostularOrganizador.EntradaPostulacion(
                        cuerpo.getMotivacion(),
                        cuerpo.getExperienciaDeclarada(),
                        cuerpo.getKycReforzadoId(),
                        new java.math.BigDecimal(cuerpo.getReputacion()),
                        MapeoDeOrganizador.medidos(cuerpo.getMedidos())),
                sesion.actual());

        var respuesta = new SalidaPostulacion();
        respuesta.setSolicitudId(salida.solicitudId());
        respuesta.setEstado(SalidaPostulacion.EstadoEnum.fromValue(salida.estado()));
        respuesta.setEsNueva(salida.esNueva());
        respuesta.setFaltantes(MapeoDeOrganizador.faltantes(salida.faltantes()));

        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("ADMIN_PLATAFORMA")
    public ResponseEntity<SalidaHabilitacion> aprobarPostulacion(UUID solicitudId, EntradaAprobacion cuerpo) {
        Traza.marcarCasoDeUso("CU-90", solicitudId.toString());

        var salida = cu90.aprobar(
                new CU90PostularOrganizador.EntradaAprobacion(
                        solicitudId, MapeoDeOrganizador.medidos(cuerpo.getMedidos())),
                sesion.actual());
        return ResponseEntity.ok(habilitacion(salida));
    }

    @Override
    @Permiso("ADMIN_PLATAFORMA")
    public ResponseEntity<SalidaHabilitacion> habilitarOrganizador(UUID organizadorId) {
        Traza.marcarCasoDeUso("CU-90", organizadorId.toString());
        return ResponseEntity.ok(habilitacion(cu90.habilitar(organizadorId, sesion.actual())));
    }

    private SalidaHabilitacion habilitacion(CU90PostularOrganizador.SalidaHabilitacion salida) {
        var respuesta = new SalidaHabilitacion();
        respuesta.setOrganizadorId(salida.organizadorId());
        respuesta.setEstado(SalidaHabilitacion.EstadoEnum.fromValue(salida.estado()));
        respuesta.setFaltantes(MapeoDeOrganizador.faltantes(salida.faltantes()));
        return respuesta;
    }

    @Override
    @Permiso("ADMIN_PLATAFORMA")
    public ResponseEntity<SalidaContrato> emitirContrato(UUID organizadorId, EntradaContrato cuerpo) {
        Traza.marcarCasoDeUso("CU-91", organizadorId.toString());

        var salida = cu91.emitir(
                new CU91FirmarContrato.EntradaEmision(
                        organizadorId,
                        cuerpo.getVersion(),
                        cuerpo.getContenidoHash(),
                        cuerpo.getObligaciones(),
                        cuerpo.getCausalesRescision()),
                sesion.actual());

        var respuesta = new SalidaContrato();
        respuesta.setContratoId(salida.contratoId());
        respuesta.setVersion(salida.version());
        respuesta.setYaExistia(salida.yaExistia());

        var estado = salida.yaExistia() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("ORGANIZADOR")
    public ResponseEntity<SalidaFirma> firmarContrato(UUID contratoId, FirmarContratoRequest cuerpo) {
        Traza.marcarCasoDeUso("CU-91", contratoId.toString());

        var salida = cu91.firmar(contratoId, cuerpo.getTokenFirmaId(), sesion.actual());

        var respuesta = new SalidaFirma();
        respuesta.setContratoId(salida.contratoId());
        respuesta.setFirmadoEn(salida.firmadoEn());
        respuesta.setEsNueva(salida.esNueva());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("ADMIN_PLATAFORMA")
    public ResponseEntity<SalidaRescision> rescindirContrato(UUID contratoId, RescindirContratoRequest cuerpo) {
        Traza.marcarCasoDeUso("CU-91", contratoId.toString());

        var salida = cu91.rescindir(
                new CU91FirmarContrato.EntradaRescision(contratoId, cuerpo.getMotivo()), sesion.actual());

        var respuesta = new SalidaRescision();
        respuesta.setContratoId(salida.contratoId());
        respuesta.setRescindidoEn(salida.rescindidoEn());
        respuesta.setEstadoDelOrganizador(salida.estadoDelOrganizador());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("ADMIN_PLATAFORMA")
    public ResponseEntity<SalidaEvaluacion> evaluarDesempeno(UUID organizadorId, EntradaEvaluacion cuerpo) {
        Traza.marcarCasoDeUso("CU-92", organizadorId.toString());

        var salida = cu92.evaluar(MapeoDeOrganizador.evaluacion(organizadorId, cuerpo), sesion.actual());

        var respuesta = new SalidaEvaluacion();
        respuesta.setEvaluacionId(salida.evaluacionId());
        respuesta.setNivelActual(SalidaEvaluacion.NivelActualEnum.fromValue(salida.nivelActual()));
        respuesta.setNivelSugerido(SalidaEvaluacion.NivelSugeridoEnum.fromValue(salida.nivelSugerido()));
        respuesta.setPuntaje(salida.puntaje().toPlainString());
        respuesta.setEsNueva(salida.esNueva());

        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("ADMIN_PLATAFORMA")
    public ResponseEntity<SalidaSancion> sancionarOrganizador(UUID organizadorId, EntradaSancion cuerpo) {
        Traza.marcarCasoDeUso("CU-93", organizadorId.toString());

        var salida = cu93.sancionar(
                new CU93SancionarOrganizador.EntradaSancion(
                        organizadorId,
                        Optional.ofNullable(cuerpo.getEvaluacionId()),
                        cuerpo.getTipo().getValue(),
                        cuerpo.getMotivo(),
                        Optional.ofNullable(cuerpo.getDuracionDias()).map(Duration::ofDays)),
                sesion.actual());

        var respuesta = new SalidaSancion();
        respuesta.setSancionId(salida.sancionId());
        respuesta.setTipo(salida.tipo());
        respuesta.setEstadoDelOrganizador(salida.estadoDelOrganizador());
        respuesta.setPuedeApelarHasta(salida.puedeApelarHasta());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("ORGANIZADOR")
    public ResponseEntity<SalidaApelacion> apelarSancion(UUID sancionId, EntradaApelacion cuerpo) {
        Traza.marcarCasoDeUso("CU-93", sancionId.toString());

        var salida = cu93.apelar(
                new CU93SancionarOrganizador.EntradaApelacion(
                        sancionId, cuerpo.getArgumento(), cuerpo.getEvidenciasJson()),
                sesion.actual());

        var respuesta = new SalidaApelacion();
        respuesta.setApelacionId(salida.apelacionId());
        respuesta.setEstado(salida.estado());
        respuesta.setEsNueva(salida.esNueva());

        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("ADMIN_PLATAFORMA")
    public ResponseEntity<SalidaResolucion> resolverApelacion(UUID sancionId, EntradaResolucion cuerpo) {
        Traza.marcarCasoDeUso("CU-93", sancionId.toString());

        var salida = cu93.resolver(
                new CU93SancionarOrganizador.EntradaResolucion(
                        sancionId,
                        Boolean.TRUE.equals(cuerpo.getAceptada()),
                        cuerpo.getArgumento(),
                        cuerpo.getEvidenciasJson(),
                        cuerpo.getResolucion()),
                sesion.actual());

        var respuesta = new SalidaResolucion();
        respuesta.setApelacionId(salida.apelacionId());
        respuesta.setEstadoDeLaApelacion(
                SalidaResolucion.EstadoDeLaApelacionEnum.fromValue(salida.estadoDeLaApelacion()));
        respuesta.setEstadoDeLaSancion(SalidaResolucion.EstadoDeLaSancionEnum.fromValue(salida.estadoDeLaSancion()));
        return ResponseEntity.ok(respuesta);
    }
}
