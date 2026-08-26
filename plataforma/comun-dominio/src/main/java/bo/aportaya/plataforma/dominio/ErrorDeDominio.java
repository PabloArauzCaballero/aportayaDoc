package bo.aportaya.plataforma.dominio;

/**
 * Lo que el dominio rechaza por si mismo, sin preguntarle a la base ni a la red.
 *
 * <p>Que sea una sola clase raiz es lo que permite que la capa web la traduzca a un
 * {@code 422} —regla de negocio— y no la confunda con un {@code 400} de esquema.
 */
public class ErrorDeDominio extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ErrorDeDominio(String mensaje) {
        super(mensaje);
    }
}
