package bo.aportaya.transparencia.web;

import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import bo.aportaya.transparencia.aplicacion.CU70RegistrarEventoReputacion;
import bo.aportaya.transparencia.aplicacion.CU71RecalcularPuntaje;
import bo.aportaya.transparencia.aplicacion.CU72SellarBloque;
import bo.aportaya.transparencia.aplicacion.CU74EvaluarInsignias;
import bo.aportaya.transparencia.aplicacion.CU75EmitirCertificado;
import bo.aportaya.transparencia.aplicacion.CU76PublicarResena;
import bo.aportaya.transparencia.aplicacion.CU97EvaluarRiesgo;
import bo.aportaya.transparencia.web.generado.ReputacionApi;
import bo.aportaya.transparencia.web.generado.modelo.EntradaCertificado;
import bo.aportaya.transparencia.web.generado.modelo.EntradaCierreAlerta;
import bo.aportaya.transparencia.web.generado.modelo.EntradaCompensacion;
import bo.aportaya.transparencia.web.generado.modelo.EntradaEvaluacionRiesgo;
import bo.aportaya.transparencia.web.generado.modelo.EntradaEventoReputacion;
import bo.aportaya.transparencia.web.generado.modelo.EntradaModeracion;
import bo.aportaya.transparencia.web.generado.modelo.EntradaRecalculo;
import bo.aportaya.transparencia.web.generado.modelo.EntradaResena;
import bo.aportaya.transparencia.web.generado.modelo.EntradaRevocacionCertificado;
import bo.aportaya.transparencia.web.generado.modelo.EntradaRevocacionInsignia;
import bo.aportaya.transparencia.web.generado.modelo.EntradaSellado;
import bo.aportaya.transparencia.web.generado.modelo.EntradaSnapshot;
import bo.aportaya.transparencia.web.generado.modelo.SalidaCertificado;
import bo.aportaya.transparencia.web.generado.modelo.SalidaEvaluacionRiesgo;
import bo.aportaya.transparencia.web.generado.modelo.SalidaEventoReputacion;
import bo.aportaya.transparencia.web.generado.modelo.SalidaInsignia;
import bo.aportaya.transparencia.web.generado.modelo.SalidaPuntaje;
import bo.aportaya.transparencia.web.generado.modelo.SalidaResena;
import bo.aportaya.transparencia.web.generado.modelo.SalidaSellado;
import bo.aportaya.transparencia.web.generado.modelo.SalidaSnapshot;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /reputacion}.
 *
 * <p>Una sola clase porque el generador agrupa por el primer tramo de la ruta, y las
 * doce operaciones caen bajo {@code /reputacion}. La traduccion vive en
 * {@link MapeoDeTransparencia} y {@link MapeoDeReputacion}.
 */
@RestController
public class ReputacionController implements ReputacionApi {

    private final CU70RegistrarEventoReputacion cu70;
    private final CU71RecalcularPuntaje cu71;
    private final CU72SellarBloque cu72;
    private final CU74EvaluarInsignias cu74;
    private final CU75EmitirCertificado cu75;
    private final CU76PublicarResena cu76;
    private final CU97EvaluarRiesgo cu97;
    private final SesionDeLaPeticion sesion;

    public ReputacionController(
            CU70RegistrarEventoReputacion cu70,
            CU71RecalcularPuntaje cu71,
            CU72SellarBloque cu72,
            CU74EvaluarInsignias cu74,
            CU75EmitirCertificado cu75,
            CU76PublicarResena cu76,
            CU97EvaluarRiesgo cu97,
            SesionDeLaPeticion sesion) {
        this.cu70 = cu70;
        this.cu71 = cu71;
        this.cu72 = cu72;
        this.cu74 = cu74;
        this.cu75 = cu75;
        this.cu76 = cu76;
        this.cu97 = cu97;
        this.sesion = sesion;
    }

    @Override
    @Permiso("SOPORTE")
    public ResponseEntity<SalidaEventoReputacion> registrarEventoReputacion(
            UUID idempotencyKey, EntradaEventoReputacion cuerpo) {
        Traza.marcarCasoDeUso("CU-70", cuerpo.getUsuarioId().toString());

        var salida = cu70.registrar(MapeoDeReputacion.evento(cuerpo), sesion.actual());
        var estado = salida.esNuevo() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(MapeoDeReputacion.evento(salida));
    }

    @Override
    @Permiso("SOPORTE")
    public ResponseEntity<SalidaEventoReputacion> compensarEventoReputacion(UUID eventoId, EntradaCompensacion cuerpo) {
        Traza.marcarCasoDeUso("CU-70", eventoId.toString());

        var salida = cu70.compensar(eventoId, cuerpo.getDescripcion(), sesion.actual());
        return ResponseEntity.status(HttpStatus.CREATED).body(MapeoDeReputacion.evento(salida));
    }

    @Override
    @Permiso("SOPORTE")
    public ResponseEntity<SalidaPuntaje> recalcularPuntaje(UUID idempotencyKey, EntradaRecalculo cuerpo) {
        Traza.marcarCasoDeUso("CU-71", cuerpo.getUsuarioId().toString());

        var salida = cu71.recalcular(MapeoDeReputacion.recalculo(cuerpo), sesion.actual());

        var respuesta = new SalidaPuntaje();
        respuesta.setPuntajeId(salida.puntajeId());
        respuesta.setPuntaje(salida.puntaje().toPlainString());
        respuesta.setNivelDeConfianza(SalidaPuntaje.NivelDeConfianzaEnum.fromValue(salida.nivelDeConfianza()));
        respuesta.setComponentes(salida.componentes().stream()
                .map(MapeoDeTransparencia::componente)
                .toList());
        respuesta.setPuntajeAnterior(
                salida.puntajeAnterior() == null
                        ? null
                        : salida.puntajeAnterior().toPlainString());
        respuesta.setEventosConsiderados(salida.eventosConsiderados());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("SOPORTE")
    public ResponseEntity<SalidaSnapshot> tomarSnapshotReputacion(EntradaSnapshot cuerpo) {
        Traza.marcarCasoDeUso("CU-71", cuerpo.getUsuarioId().toString());

        UUID id = cu71.tomarSnapshot(cuerpo.getUsuarioId(), cuerpo.getMotivo().getValue(), sesion.actual());

        var respuesta = new SalidaSnapshot();
        respuesta.setSnapshotId(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("AUDITORIA_LEER")
    public ResponseEntity<SalidaSellado> sellarBloque(UUID idempotencyKey, EntradaSellado cuerpo) {
        Traza.marcarCasoDeUso("CU-72", cuerpo.getGrupoId().toString());

        var salida = cu72.sellar(MapeoDeReputacion.sellado(cuerpo), sesion.actual());

        var respuesta = new SalidaSellado();
        respuesta.setBloqueId(salida.bloqueId());
        respuesta.setNumeroBloque(salida.numeroBloque());
        respuesta.setHashAnterior(salida.hashAnterior());
        respuesta.setRaizMerkle(salida.raizMerkle());
        respuesta.setHashBloque(salida.hashBloque());
        respuesta.setEntidadesSelladas(salida.entidadesSelladas());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("SOPORTE")
    public ResponseEntity<java.util.List<SalidaInsignia>> evaluarInsignias(
            UUID idempotencyKey, bo.aportaya.transparencia.web.generado.modelo.EntradaEvaluacionInsignias cuerpo) {
        Traza.marcarCasoDeUso("CU-74", cuerpo.getUsuarioId().toString());

        var otorgamientos = cu74.evaluar(MapeoDeReputacion.evaluacion(cuerpo), sesion.actual());
        return ResponseEntity.ok(
                otorgamientos.stream().map(MapeoDeReputacion::insignia).toList());
    }

    @Override
    @Permiso("SOPORTE")
    public ResponseEntity<SalidaInsignia> revocarInsignia(UUID otorgadaId, EntradaRevocacionInsignia cuerpo) {
        Traza.marcarCasoDeUso("CU-74", otorgadaId.toString());

        var salida = cu74.revocar(otorgadaId, cuerpo.getMotivoRevocacion(), sesion.actual());

        return ResponseEntity.ok(MapeoDeReputacion.insignia(salida));
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<SalidaCertificado> emitirCertificado(UUID idempotencyKey, EntradaCertificado cuerpo) {
        Traza.marcarCasoDeUso("CU-75", cuerpo.getUsuarioId().toString());

        var salida = cu75.emitir(MapeoDeReputacion.certificado(cuerpo), sesion.actual());

        var respuesta = new SalidaCertificado();
        respuesta.setCertificadoId(salida.certificadoId());
        respuesta.setCodigoVerificacion(salida.codigoVerificacion());
        respuesta.setUrlPublica(salida.urlPublica());
        respuesta.setHashContenido(salida.hashContenido());
        respuesta.setEmitidoEn(salida.emitidoEn());
        respuesta.setExpiraEn(salida.expiraEn());
        respuesta.setContenido(salida.contenido());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<Void> revocarCertificado(String codigo, EntradaRevocacionCertificado cuerpo) {
        Traza.marcarCasoDeUso("CU-75", codigo);

        cu75.revocar(codigo, cuerpo.getMotivo(), sesion.actual());
        return ResponseEntity.noContent().build();
    }

    @Override
    @Permiso("PARTICIPANTE")
    public ResponseEntity<SalidaResena> publicarResena(UUID idempotencyKey, EntradaResena cuerpo) {
        Traza.marcarCasoDeUso("CU-76", cuerpo.getEvaluadoUsuarioId().toString());

        var salida = cu76.publicar(MapeoDeReputacion.resena(cuerpo), sesion.actual());
        return ResponseEntity.status(HttpStatus.CREATED).body(MapeoDeReputacion.resena(salida));
    }

    @Override
    @Permiso("MODERADOR_CONTENIDO")
    public ResponseEntity<SalidaResena> moderarResena(UUID resenaId, EntradaModeracion cuerpo) {
        Traza.marcarCasoDeUso("CU-76", resenaId.toString());

        var salida = cu76.moderar(
                new CU76PublicarResena.EntradaModeracion(
                        resenaId, cuerpo.getDecision().getValue(), cuerpo.getMotivo()),
                sesion.actual());
        return ResponseEntity.ok(MapeoDeReputacion.resena(salida));
    }

    @Override
    @Permiso("ANALISTA_CUMPLIMIENTO")
    public ResponseEntity<SalidaEvaluacionRiesgo> evaluarRiesgo(UUID idempotencyKey, EntradaEvaluacionRiesgo cuerpo) {
        Traza.marcarCasoDeUso("CU-97", cuerpo.getAmbito().getValue());

        var salida = cu97.evaluar(MapeoDeReputacion.riesgo(cuerpo), sesion.actual());

        var respuesta = new SalidaEvaluacionRiesgo();
        respuesta.setAlertas(
                salida.alertas().stream().map(MapeoDeReputacion::alerta).toList());
        respuesta.setNivelRiesgo(SalidaEvaluacionRiesgo.NivelRiesgoEnum.fromValue(salida.nivelRiesgo()));
        respuesta.setVersionModelo(salida.versionModelo());
        respuesta.setMetricasEnAlerta(salida.metricasEnAlerta().stream()
                .map(MapeoDeTransparencia::metrica)
                .toList());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("ANALISTA_CUMPLIMIENTO")
    public ResponseEntity<Void> cerrarAlertaRiesgo(UUID alertaId, EntradaCierreAlerta cuerpo) {
        Traza.marcarCasoDeUso("CU-97", alertaId.toString());

        cu97.cerrar(alertaId, cuerpo.getDesenlace().getValue(), sesion.actual());
        return ResponseEntity.noContent().build();
    }
}
