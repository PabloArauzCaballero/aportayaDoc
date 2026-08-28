package bo.aportaya.transparencia.web;

import bo.aportaya.transparencia.aplicacion.CU70RegistrarEventoReputacion;
import bo.aportaya.transparencia.aplicacion.CU71RecalcularPuntaje;
import bo.aportaya.transparencia.aplicacion.CU72SellarBloque;
import bo.aportaya.transparencia.aplicacion.CU74EvaluarInsignias;
import bo.aportaya.transparencia.aplicacion.CU75EmitirCertificado;
import bo.aportaya.transparencia.aplicacion.CU76PublicarResena;
import bo.aportaya.transparencia.aplicacion.CU97EvaluarRiesgo;
import bo.aportaya.transparencia.dominio.CriterioDeInsignia;
import bo.aportaya.transparencia.dominio.IndicadoresDeReputacion;
import bo.aportaya.transparencia.web.generado.modelo.AlertaDeRiesgo;
import bo.aportaya.transparencia.web.generado.modelo.EntradaCertificado;
import bo.aportaya.transparencia.web.generado.modelo.EntradaEvaluacionInsignias;
import bo.aportaya.transparencia.web.generado.modelo.EntradaEvaluacionRiesgo;
import bo.aportaya.transparencia.web.generado.modelo.EntradaEventoReputacion;
import bo.aportaya.transparencia.web.generado.modelo.EntradaRecalculo;
import bo.aportaya.transparencia.web.generado.modelo.EntradaResena;
import bo.aportaya.transparencia.web.generado.modelo.EntradaSellado;
import bo.aportaya.transparencia.web.generado.modelo.SalidaEventoReputacion;
import bo.aportaya.transparencia.web.generado.modelo.SalidaInsignia;
import bo.aportaya.transparencia.web.generado.modelo.SalidaResena;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** La traduccion de las doce operaciones de {@code /reputacion}. */
final class MapeoDeReputacion {

    private MapeoDeReputacion() {}

    static CU70RegistrarEventoReputacion.EntradaEvento evento(EntradaEventoReputacion cuerpo) {
        return new CU70RegistrarEventoReputacion.EntradaEvento(
                cuerpo.getUsuarioId(),
                cuerpo.getGrupoId(),
                cuerpo.getParticipanteId(),
                cuerpo.getTipo().getValue(),
                cuerpo.getReferenciaTipo().getValue(),
                cuerpo.getReferenciaId(),
                cuerpo.getDescripcion(),
                Boolean.TRUE.equals(cuerpo.getEsReversible()),
                cuerpo.getOcurridoEn());
    }

    static SalidaEventoReputacion evento(CU70RegistrarEventoReputacion.SalidaEvento salida) {
        var respuesta = new SalidaEventoReputacion();
        respuesta.setEventoId(salida.eventoId());
        respuesta.setImpacto(salida.impacto().toPlainString());
        respuesta.setFactorAfectado(salida.factorAfectado());
        respuesta.setEsNuevo(salida.esNuevo());
        return respuesta;
    }

    static CU71RecalcularPuntaje.EntradaPuntaje recalculo(EntradaRecalculo cuerpo) {
        var i = cuerpo.getIndicadores();
        return new CU71RecalcularPuntaje.EntradaPuntaje(
                cuerpo.getUsuarioId(),
                MapeoDeTransparencia.mediciones(cuerpo.getMediciones()),
                MapeoDeTransparencia.mediciones(cuerpo.getMedicionesAnteriores()),
                new IndicadoresDeReputacion(
                        new BigDecimal(i.getPuntualidad()),
                        new BigDecimal(i.getIncumplimiento()),
                        new BigDecimal(i.getMontoAportado()),
                        i.getGruposCompletados(),
                        i.getGruposAbandonados(),
                        i.getIncumplimientosAbiertos(),
                        i.getAntiguedadMeses()));
    }

    static CU72SellarBloque.EntradaBloque sellado(EntradaSellado cuerpo) {
        return new CU72SellarBloque.EntradaBloque(
                cuerpo.getGrupoId(),
                cuerpo.getMotivo().getValue(),
                cuerpo.getHechos().stream()
                        .map(h -> new CU72SellarBloque.Hecho(
                                h.getTipoEntidad().getValue(),
                                h.getEntidadId(),
                                new LinkedHashMap<>(h.getCampos()),
                                h.getOcurridoEn()))
                        .toList(),
                cuerpo.getDesde(),
                cuerpo.getHasta(),
                cuerpo.getExcepcionesDeConciliacionAbiertas());
    }

    static CU75EmitirCertificado.EntradaCertificado certificado(EntradaCertificado cuerpo) {
        return new CU75EmitirCertificado.EntradaCertificado(
                cuerpo.getUsuarioId(),
                cuerpo.getSnapshotId(),
                new LinkedHashMap<>(cuerpo.getDisponibles()),
                new LinkedHashSet<>(cuerpo.getIncluir()),
                cuerpo.getVigenciaDias(),
                Boolean.TRUE.equals(cuerpo.getIdentidadVerificada()));
    }

    static CU76PublicarResena.EntradaResena resena(EntradaResena cuerpo) {
        return new CU76PublicarResena.EntradaResena(
                cuerpo.getGrupoId(),
                cuerpo.getAutorParticipanteId(),
                cuerpo.getEvaluadoUsuarioId(),
                cuerpo.getCalificacion(),
                cuerpo.getDimension().getValue(),
                cuerpo.getComentario(),
                Boolean.TRUE.equals(cuerpo.getCicloCerrado()),
                cuerpo.getCerradoEn(),
                cuerpo.getDiasDeVentana(),
                Boolean.TRUE.equals(cuerpo.getAutorFueExpulsado()),
                cuerpo.getResenasPreviasDelAutor());
    }

    static SalidaResena resena(CU76PublicarResena.SalidaResena salida) {
        var respuesta = new SalidaResena();
        respuesta.setResenaId(salida.resenaId());
        respuesta.setEstadoModeracion(SalidaResena.EstadoModeracionEnum.fromValue(salida.estadoModeracion()));
        respuesta.setPesoEnReputacion(salida.pesoEnReputacion().toPlainString());
        respuesta.setMotivoModeracion(salida.motivoModeracion());
        respuesta.setMarcas(
                salida.marcas().stream().map(SalidaResena.MarcasEnum::fromValue).toList());
        return respuesta;
    }

    /**
     * La evaluacion de insignias.
     *
     * <p>Cuando viene {@code tipoDeEvento}, CU-74 decide solas cuales insignias mirar:
     * la lista de codigos se ignora a proposito, porque quien dispara un evento no tiene
     * por que saber que insignias dependen de el.
     */
    static CU74EvaluarInsignias.EntradaEvaluacion evaluacion(EntradaEvaluacionInsignias cuerpo) {
        var h = cuerpo.getHechos();
        return new CU74EvaluarInsignias.EntradaEvaluacion(
                cuerpo.getUsuarioId(),
                cuerpo.getTipoDeEvento(),
                java.util.List.of(),
                new CriterioDeInsignia.Hechos(
                        h.getCiclosCompletados(),
                        h.getCiclosCompletadosSinCobertura(),
                        h.getAportesPuntualesConsecutivos(),
                        h.getDiasCorridosSinMora(),
                        h.getRegularizacionesCumplidasEnPlazo(),
                        h.getGruposCerradosComoOrganizador(),
                        h.getSancionesFirmesComoOrganizador(),
                        h.getEntregasFueraDePlazoComoOrganizador(),
                        h.getDesempenoComoOrganizador() == null
                                ? BigDecimal.ZERO
                                : new BigDecimal(h.getDesempenoComoOrganizador()),
                        h.getInvitadosQueCompletaronUnCiclo(),
                        h.getMesesDeAntiguedad(),
                        Boolean.TRUE.equals(h.getIdentidadVigente()),
                        Boolean.TRUE.equals(h.getTieneDeudaCastigada())));
    }

    static SalidaInsignia insignia(CU74EvaluarInsignias.Otorgamiento o) {
        var salida = new SalidaInsignia();
        salida.setOtorgadaId(o.otorgadaId());
        salida.setInsigniaCodigo(o.insigniaCodigo());
        salida.setOtorgadaEn(o.otorgadaEn());
        salida.setMotivoLegible(o.motivoLegible());
        salida.setRevocada(o.revocada());
        salida.setEsNueva(o.esNueva());
        return salida;
    }

    static CU97EvaluarRiesgo.EntradaRiesgo riesgo(EntradaEvaluacionRiesgo cuerpo) {
        Map<String, String> codigos = new LinkedHashMap<>();
        cuerpo.getCodigoDeAlerta().forEach((clave, valor) -> codigos.put(clave, valor.getValue()));

        return new CU97EvaluarRiesgo.EntradaRiesgo(
                cuerpo.getAmbito().getValue(),
                cuerpo.getAmbitoId(),
                cuerpo.getPeriodoId(),
                Boolean.TRUE.equals(cuerpo.getPeriodoCerrado()),
                MapeoDeTransparencia.metricas(cuerpo.getMetricas()),
                codigos,
                cuerpo.getObservaciones(),
                cuerpo.getPuntajeDeRiesgo() == null ? null : new BigDecimal(cuerpo.getPuntajeDeRiesgo()));
    }

    static AlertaDeRiesgo alerta(CU97EvaluarRiesgo.Alerta a) {
        var salida = new AlertaDeRiesgo();
        salida.setAlertaId(a.alertaId());
        salida.setCodigo(AlertaDeRiesgo.CodigoEnum.fromValue(a.codigo()));
        salida.setSeveridad(AlertaDeRiesgo.SeveridadEnum.fromValue(a.severidad()));
        salida.setDescripcion(a.descripcion());
        salida.setAccionSugerida(a.accionSugerida());
        salida.setMensajeAlUsuario(a.mensajeAlUsuario());
        return salida;
    }
}
