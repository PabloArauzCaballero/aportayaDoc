package bo.aportaya.organizador.web;

import bo.aportaya.organizador.aplicacion.CU92EvaluarDesempeno;
import bo.aportaya.organizador.dominio.PuntajeDeDesempeno;
import bo.aportaya.organizador.dominio.RequisitosDeHabilitacion;
import bo.aportaya.organizador.web.generado.modelo.EntradaEvaluacion;
import bo.aportaya.organizador.web.generado.modelo.Faltante;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La traduccion entre los modelos del contrato y los del dominio del organizador.
 *
 * <p>Los indicadores de desempeno viajan como CADENA por lo mismo que el dinero: una
 * morosidad que cambia al deserializarse decide mal una sancion.
 */
final class MapeoDeOrganizador {

    private MapeoDeOrganizador() {}

    static Map<String, BigDecimal> medidos(Map<String, String> crudos) {
        Map<String, BigDecimal> salida = new LinkedHashMap<>();
        crudos.forEach((clave, valor) -> salida.put(clave, new BigDecimal(valor)));
        return salida;
    }

    static List<Faltante> faltantes(List<RequisitosDeHabilitacion.Faltante> crudos) {
        return crudos.stream()
                .map(f -> {
                    var salida = new Faltante();
                    salida.setCodigo(f.codigo());
                    salida.setMotivo(f.motivo());
                    return salida;
                })
                .toList();
    }

    static CU92EvaluarDesempeno.EntradaEvaluacion evaluacion(UUID organizadorId, EntradaEvaluacion cuerpo) {
        return new CU92EvaluarDesempeno.EntradaEvaluacion(
                organizadorId,
                cuerpo.getPeriodo(),
                cuerpo.getMetricas().stream()
                        .map(m -> new PuntajeDeDesempeno.Metrica(
                                m.getCodigo(),
                                new BigDecimal(m.getValor()),
                                new BigDecimal(m.getMeta()),
                                new BigDecimal(m.getPeso()),
                                Boolean.TRUE.equals(m.getMayorEsMejor())))
                        .toList(),
                new BigDecimal(cuerpo.getMorosidad()),
                new BigDecimal(cuerpo.getTasaFinalizacion()),
                new BigDecimal(cuerpo.getSatisfaccion()),
                new BigDecimal(cuerpo.getTiempoRespuestaHoras()),
                cuerpo.getIncidenciasAbiertas(),
                cuerpo.getCoberturasConsumidas(),
                cuerpo.getLimiteDeGruposDelNivel(),
                new BigDecimal(cuerpo.getLimiteDeMontoDelNivel()));
    }
}
