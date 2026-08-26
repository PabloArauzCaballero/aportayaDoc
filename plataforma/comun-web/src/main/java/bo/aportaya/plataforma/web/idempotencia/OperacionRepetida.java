package bo.aportaya.plataforma.web.idempotencia;

import bo.aportaya.plataforma.dominio.ErrorDeDominio;

/**
 * La clave de idempotencia ya se uso, y la respuesta original esta guardada.
 *
 * <p>No es un error del usuario: es la red haciendo lo que hace. El manejador la
 * convierte en {@code 200} con **la respuesta original, integra** — nunca en un
 * {@code 409}, porque el cliente hizo lo correcto al reintentar.
 */
public class OperacionRepetida extends ErrorDeDominio {

    private static final long serialVersionUID = 1L;

    private final transient int codigoHttp;
    private final transient String cuerpo;

    public OperacionRepetida(int codigoHttp, String cuerpo) {
        super("Operacion ya registrada");
        this.codigoHttp = codigoHttp;
        this.cuerpo = cuerpo;
    }

    public int codigoHttp() {
        return codigoHttp;
    }

    public String cuerpo() {
        return cuerpo;
    }
}
