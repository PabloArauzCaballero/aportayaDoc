package bo.aportaya.tarifas.web;

import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.tarifas.web.generado.modelo.Dinero;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/** El puente entre el {@code Dinero} del contrato y el del dominio. */
final class MapeoDeTarifas {

    private MapeoDeTarifas() {}

    static bo.aportaya.plataforma.dominio.Dinero dinero(Dinero valor) {
        return bo.aportaya.plataforma.dominio.Dinero.de(
                new BigDecimal(valor.getMonto()),
                Moneda.valueOf(valor.getMoneda().getValue()));
    }

    static Optional<bo.aportaya.plataforma.dominio.Dinero> dineroOpcional(Dinero valor) {
        return Optional.ofNullable(valor).map(MapeoDeTarifas::dinero);
    }

    /** Un descuento ausente es cero en la moneda de la base, no un importe sin moneda. */
    static bo.aportaya.plataforma.dominio.Dinero ceroSiFalta(Dinero valor, Dinero referencia) {
        return valor == null
                ? bo.aportaya.plataforma.dominio.Dinero.cero(dinero(referencia).moneda())
                : dinero(valor);
    }

    static Dinero dinero(bo.aportaya.plataforma.dominio.Dinero valor) {
        var salida = new Dinero();
        salida.setMonto(valor.monto().setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        salida.setMoneda(Dinero.MonedaEnum.fromValue(valor.moneda().name()));
        return salida;
    }

    static String importe(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }
}
