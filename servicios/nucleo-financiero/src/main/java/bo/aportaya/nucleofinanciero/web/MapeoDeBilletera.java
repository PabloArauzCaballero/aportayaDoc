package bo.aportaya.nucleofinanciero.web;

import bo.aportaya.nucleofinanciero.web.generado.modelo.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/** El puente entre el {@code Dinero} del contrato y el del dominio. */
final class MapeoDeBilletera {

    private MapeoDeBilletera() {}

    static bo.aportaya.plataforma.dominio.Dinero dinero(Dinero valor) {
        return bo.aportaya.plataforma.dominio.Dinero.de(
                new BigDecimal(valor.getMonto()),
                Moneda.valueOf(valor.getMoneda().getValue()));
    }

    static Optional<bo.aportaya.plataforma.dominio.Dinero> dineroOpcional(Dinero valor) {
        return Optional.ofNullable(valor).map(MapeoDeBilletera::dinero);
    }

    /** Cero en la misma moneda: un importe ausente no es un importe sin moneda. */
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
}
