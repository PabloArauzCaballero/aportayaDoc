package bo.aportaya.grupos.dominio;

import java.util.List;

/**
 * Lo que hace falta para rehacer un sorteo desde afuera.
 *
 * <p>El hash comprometido, la semilla revelada, las entropias que aportaron los
 * participantes, el metodo y el orden que salio. Con eso cualquiera recomputa el
 * resultado y comprueba que nadie lo eligio.
 *
 * <p>Antes del revelado la semilla es nula, y ese es exactamente el punto: comprometer
 * y revelar existen para que nadie —nosotros incluidos— pueda elegir el resultado
 * despues de conocerlo.
 */
public record PaqueteDeSorteo(
        String hashComprometido,
        String semillaRevelada,
        String metodo,
        List<String> entropias,
        List<String> ordenPublicado) {}
