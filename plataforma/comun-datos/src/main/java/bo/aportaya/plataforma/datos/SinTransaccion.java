package bo.aportaya.plataforma.datos;

import bo.aportaya.plataforma.dominio.ErrorDeDominio;

/**
 * Se intento fijar el contexto de fila sin una transaccion abierta.
 *
 * <p>Importa porque {@code SET LOCAL} fuera de una transaccion **no hace nada** y
 * PostgreSQL solo avisa con un WARNING: la consulta correria sin politica de fila y
 * devolveria filas de todos los usuarios, sin error y sin rastro.
 */
public class SinTransaccion extends ErrorDeDominio {

    private static final long serialVersionUID = 1L;

    public SinTransaccion() {
        super("conContexto exige una transaccion abierta: SET LOCAL fuera de una transaccion no fija nada");
    }
}
