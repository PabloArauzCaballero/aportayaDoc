package bo.aportaya.grupos.web;

import bo.aportaya.grupos.web.generado.modelo.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** El puente entre el {@code Dinero} del contrato y el del dominio. */
final class MapeoDeGrupos {

    private MapeoDeGrupos() {}

    static bo.aportaya.plataforma.dominio.Dinero dinero(Dinero valor) {
        return bo.aportaya.plataforma.dominio.Dinero.de(
                new BigDecimal(valor.getMonto()),
                Moneda.valueOf(valor.getMoneda().getValue()));
    }

    static String importe(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
    }

    /**
     * Un telefono enmascarado: los cuatro ultimos digitos y nada mas.
     *
     * <p>Va enmascarado hasta entre servicios. Un telefono en claro viajando por la red
     * interna queda en el log de cada salto del camino, y de ahi ya no se borra.
     */
    static String enmascarar(String telefono) {
        if (telefono == null || telefono.length() <= 4) {
            return "****";
        }
        return "*".repeat(telefono.length() - 4) + telefono.substring(telefono.length() - 4);
    }

    /** El orden publicado del sorteo, que viaja como numeros de cupo. */
    static java.util.List<Integer> comoEnteros(java.util.List<String> crudos) {
        return crudos.stream()
                .map(String::trim)
                .filter(t -> t.matches("-?\\d+"))
                .map(Integer::valueOf)
                .toList();
    }

    /**
     * El retiro, con la plata que ya puso y la que debe.
     *
     * <p>Los tres importes vienen de {@code aportes}, no de este esquema: quien se
     * retira se lleva lo suyo menos lo que debe, y ese calculo se hace con la verdad
     * del servicio que lleva los pagos.
     */
    static bo.aportaya.grupos.aplicacion.CU65Retirarse.EntradaRetiro entradaDeRetiro(
            bo.aportaya.grupos.web.generado.modelo.EntradaRetiro cuerpo,
            bo.aportaya.grupos.dominio.puertos.HechosDeOtrosServicios.EstadoDePagos pagos,
            boolean yaCobroSuTurno) {
        var moneda = bo.aportaya.plataforma.dominio.Moneda.valueOf(pagos.moneda());
        return new bo.aportaya.grupos.aplicacion.CU65Retirarse.EntradaRetiro(
                cuerpo.getParticipanteId(),
                cuerpo.getMotivo(),
                yaCobroSuTurno,
                bo.aportaya.plataforma.dominio.Dinero.de(pagos.totalAportado(), moneda),
                bo.aportaya.plataforma.dominio.Dinero.de(pagos.deudaVigente(), moneda),
                bo.aportaya.plataforma.dominio.Dinero.de(pagos.porAportar(), moneda));
    }

    /** El plazo habil, con los dias que salteo y por que los salteo. */
    static bo.aportaya.grupos.web.generado.modelo.SalidaPlazoHabil plazo(
            bo.aportaya.grupos.aplicacion.CU59CalcularPlazo.SalidaPlazo salida) {
        var respuesta = new bo.aportaya.grupos.web.generado.modelo.SalidaPlazoHabil();
        respuesta.setFechaLimite(salida.fechaLimite());
        respuesta.setDiasSalteados(salida.diasSalteados().stream()
                .map(d -> {
                    var salteado = new bo.aportaya.grupos.web.generado.modelo.DiaSalteado();
                    salteado.setFecha(d.fecha());
                    salteado.setDescripcion(d.descripcion());
                    return salteado;
                })
                .toList());
        return respuesta;
    }
}
