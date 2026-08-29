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

    /** La orden de la autoridad, tal como la firma quien la emite. */
    static bo.aportaya.nucleofinanciero.aplicacion.CU17BloquearPorAutoridad.EntradaBloqueo entradaDeBloqueo(
            bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaBloqueo cuerpo) {
        return new bo.aportaya.nucleofinanciero.aplicacion.CU17BloquearPorAutoridad.EntradaBloqueo(
                cuerpo.getCuentaBilleteraId(),
                cuerpo.getAutoridad().getValue(),
                cuerpo.getTipoOrden().getValue(),
                cuerpo.getNumeroOficio(),
                dineroOpcional(cuerpo.getMontoBloqueado()),
                cuerpo.getAlcance().getValue(),
                cuerpo.getDocumentoUrl().toString(),
                cuerpo.getHashDocumento());
    }

    /** El extracto, con el motivo del bloqueo si lo hubo: se dice, no se omite. */
    static bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaExtracto extracto(
            bo.aportaya.nucleofinanciero.aplicacion.CU15EmitirExtracto.SalidaExtracto salida) {
        var respuesta = new bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaExtracto();
        respuesta.setCuentaBilleteraId(salida.cuentaBilleteraId());
        respuesta.setDesde(salida.desde());
        respuesta.setHasta(salida.hasta());
        respuesta.setSaldoFinal(dinero(salida.saldoFinal()));
        respuesta.setCantidadMovimientos(salida.cantidadMovimientos());
        respuesta.setHashArchivo(salida.hashArchivo());
        respuesta.setEmitido(salida.emitido());
        respuesta.setMotivoDelBloqueo(salida.motivoDelBloqueo());
        return respuesta;
    }

    /** La retencion: apartar saldo no es moverlo, y por eso lleva su propio motivo. */
    static bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo.EntradaRetencion entradaDeRetencion(
            bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaRetencion cuerpo) {
        return new bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo.EntradaRetencion(
                cuerpo.getCuentaBilleteraId(),
                dinero(cuerpo.getMonto()),
                cuerpo.getMotivo(),
                java.util.Optional.ofNullable(cuerpo.getTransaccionOrigenId()),
                java.util.Optional.ofNullable(cuerpo.getReferenciaTipo()),
                java.util.Optional.ofNullable(cuerpo.getReferenciaId()),
                java.util.Optional.ofNullable(cuerpo.getExpiraEn()));
    }

    /** El reverso, con quien lo autorizo: un asiento inverso sin firma no existe. */
    static bo.aportaya.nucleofinanciero.aplicacion.CU14ReversarTransaccion.EntradaReverso entradaDeReverso(
            String claveIdempotencia, bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaReverso cuerpo) {
        return new bo.aportaya.nucleofinanciero.aplicacion.CU14ReversarTransaccion.EntradaReverso(
                claveIdempotencia,
                cuerpo.getTransaccionOriginalId(),
                cuerpo.getTipo().getValue(),
                cuerpo.getMotivo(),
                cuerpo.getAutorizadaPor());
    }
}
