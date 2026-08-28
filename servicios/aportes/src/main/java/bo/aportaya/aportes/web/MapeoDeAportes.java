package bo.aportaya.aportes.web;

import bo.aportaya.aportes.web.generado.modelo.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * El puente entre el {@code Dinero} del contrato y el del dominio.
 *
 * <p>Son dos tipos distintos y tienen que serlo: el del contrato lo genera OpenAPI y
 * lleva el importe como cadena; el del dominio es el que sabe sumar sin perder
 * centavos. Convertir aca —y no dejar que el generado entre al caso de uso— es lo que
 * mantiene el dominio libre del contrato.
 */
final class MapeoDeAportes {

    private MapeoDeAportes() {}

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
