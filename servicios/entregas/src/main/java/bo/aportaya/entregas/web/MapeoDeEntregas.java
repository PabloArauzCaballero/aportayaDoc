package bo.aportaya.entregas.web;

import bo.aportaya.entregas.web.generado.modelo.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** El puente entre el {@code Dinero} del contrato y el del dominio. */
final class MapeoDeEntregas {

    private MapeoDeEntregas() {}

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
}
