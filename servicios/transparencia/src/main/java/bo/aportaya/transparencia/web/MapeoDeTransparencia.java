package bo.aportaya.transparencia.web;

import bo.aportaya.transparencia.dominio.PuntajeDeReputacion;
import bo.aportaya.transparencia.dominio.SenalDeRiesgo;
import bo.aportaya.transparencia.web.generado.modelo.Componente;
import bo.aportaya.transparencia.web.generado.modelo.Medicion;
import bo.aportaya.transparencia.web.generado.modelo.MetricaDeRiesgo;
import java.math.BigDecimal;
import java.util.List;

/**
 * La traduccion entre los modelos del contrato y los del dominio de reputacion.
 *
 * <p>Los numeros de reputacion viajan como CADENA por la misma razon que el dinero: un
 * puntaje que cambia al deserializarse no es un puntaje, y aca la cadena de bloques
 * sella exactamente lo que se publico.
 */
final class MapeoDeTransparencia {

    private MapeoDeTransparencia() {}

    static List<PuntajeDeReputacion.Medicion> mediciones(List<Medicion> crudas) {
        return crudas == null
                ? List.of()
                : crudas.stream()
                        .map(m -> new PuntajeDeReputacion.Medicion(
                                m.getCodigo(),
                                new BigDecimal(m.getValorCrudo()),
                                new BigDecimal(m.getValorNormalizado())))
                        .toList();
    }

    static Componente componente(PuntajeDeReputacion.Componente c) {
        var salida = new Componente();
        salida.setCodigo(c.codigo());
        salida.setValorCrudo(c.valorCrudo().toPlainString());
        salida.setValorNormalizado(c.valorNormalizado().toPlainString());
        salida.setContribucion(c.contribucion().toPlainString());
        salida.setTendencia(Componente.TendenciaEnum.fromValue(c.tendencia()));
        return salida;
    }

    static List<SenalDeRiesgo.Metrica> metricas(List<MetricaDeRiesgo> crudas) {
        return crudas == null
                ? List.of()
                : crudas.stream()
                        .map(m -> new SenalDeRiesgo.Metrica(
                                m.getCodigo(),
                                new BigDecimal(m.getValor()),
                                m.getUnidad(),
                                m.getUmbral() == null ? BigDecimal.ZERO : new BigDecimal(m.getUmbral()),
                                Boolean.TRUE.equals(m.getMayorEsPeor())))
                        .toList();
    }

    static MetricaDeRiesgo metrica(SenalDeRiesgo.Metrica m) {
        var salida = new MetricaDeRiesgo();
        salida.setCodigo(m.codigo());
        salida.setValor(m.valor().toPlainString());
        salida.setUnidad(m.unidad());
        salida.setUmbral(m.umbral().toPlainString());
        salida.setMayorEsPeor(m.mayorEsPeor());
        return salida;
    }
}
