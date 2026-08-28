package bo.aportaya.garantia.web;

import bo.aportaya.garantia.dominio.CuadreDeDisolucion;
import bo.aportaya.garantia.dominio.DevolucionDelFondo;
import bo.aportaya.garantia.web.generado.modelo.DevolucionDeParticipante;
import bo.aportaya.garantia.web.generado.modelo.Dinero;
import bo.aportaya.garantia.web.generado.modelo.LiquidacionDeParticipante;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** El puente entre los modelos del contrato y los del dominio de la garantia. */
final class MapeoDeGarantia {

    private MapeoDeGarantia() {}

    static bo.aportaya.plataforma.dominio.Dinero dinero(Dinero valor) {
        return bo.aportaya.plataforma.dominio.Dinero.de(
                new BigDecimal(valor.getMonto()),
                Moneda.valueOf(valor.getMoneda().getValue()));
    }

    static Dinero dinero(bo.aportaya.plataforma.dominio.Dinero valor) {
        var salida = new Dinero();
        salida.setMonto(valor.monto().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        salida.setMoneda(Dinero.MonedaEnum.fromValue(valor.moneda().name()));
        return salida;
    }

    static DevolucionDeParticipante devolucion(DevolucionDelFondo.Devolucion d) {
        var salida = new DevolucionDeParticipante();
        salida.setParticipanteId(d.participanteId());
        salida.setAportado(dinero(d.aportado()));
        salida.setaDevolver(dinero(d.aDevolver()));
        return salida;
    }

    static LiquidacionDeParticipante liquidacion(CuadreDeDisolucion.Liquidacion l) {
        var salida = new LiquidacionDeParticipante();
        salida.setParticipanteId(l.participanteId());
        salida.setAportado(dinero(l.aportado()));
        salida.setRecibido(dinero(l.recibido()));
        salida.setaDevolver(dinero(l.aDevolver()));
        salida.setaCobrarle(dinero(l.aCobrarle()));
        return salida;
    }
}
