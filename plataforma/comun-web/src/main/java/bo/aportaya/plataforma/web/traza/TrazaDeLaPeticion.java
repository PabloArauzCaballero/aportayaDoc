package bo.aportaya.plataforma.web.traza;

/**
 * El identificador de la peticion en curso, para quien vive fuera de este paquete.
 *
 * <p>Existe porque {@code Traza} se llama igual en el dominio y en web, y un archivo
 * que importa las dos tiene que calificar una entera. Este alias deja el codigo de
 * los servicios legible: {@code TrazaDeLaPeticion.actual()} dice de cual habla.
 */
public final class TrazaDeLaPeticion {

    private TrazaDeLaPeticion() {}

    public static String actual() {
        return Traza.actual();
    }
}
