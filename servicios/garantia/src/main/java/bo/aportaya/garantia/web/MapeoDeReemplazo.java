package bo.aportaya.garantia.web;

import bo.aportaya.garantia.aplicacion.CU66ReemplazarParticipante;
import bo.aportaya.garantia.web.generado.modelo.SalidaReemplazo;

/**
 * La salida del reemplazo, que aparece en dos rutas distintas.
 *
 * <p>Vive aparte porque {@code /incumplimientos} propone el reemplazo y
 * {@code /garantia} lo ejecuta: son dos controladores —el generador los separa por el
 * primer tramo de la ruta— y la traduccion es la misma.
 */
final class MapeoDeReemplazo {

    private MapeoDeReemplazo() {}

    static SalidaReemplazo salida(CU66ReemplazarParticipante.SalidaReemplazo salida) {
        var respuesta = new SalidaReemplazo();
        respuesta.setReemplazoId(salida.reemplazoId());
        respuesta.setEstado(SalidaReemplazo.EstadoEnum.fromValue(salida.estado()));
        respuesta.setDeudaAsumidaPorElEntrante(MapeoDeGarantia.dinero(salida.deudaAsumidaPorElEntrante()));
        respuesta.setDeudaRetenidaPorElSaliente(MapeoDeGarantia.dinero(salida.deudaRetenidaPorElSaliente()));
        respuesta.setEsNuevo(salida.esNuevo());
        return respuesta;
    }
}
