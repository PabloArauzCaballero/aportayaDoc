package bo.aportaya.plataforma.dominio;

/**
 * Se intento consultar sin saber quien consulta.
 *
 * <p>Es una clase propia y no un {@code IllegalStateException} porque la capa web la
 * traduce a {@code 401} y porque tiene que ser imposible atraparla por accidente
 * junto con otra cosa: lo que protege es la politica de fila.
 */
public class SinContextoDeSesion extends ErrorDeDominio {

    private static final long serialVersionUID = 1L;

    public SinContextoDeSesion(String detalle) {
        super("Ninguna consulta corre sin contexto de sesion: " + detalle);
    }
}
