package bo.aportaya.identidad.dominio;

/**
 * Cuando N intentos fallidos consecutivos se convierten en un bloqueo.
 *
 * <p>El numero NO es una constante del codigo: llega del catalogo
 * ({@code politica_token}, {@code umbral_operativo}), porque cambiarlo es una
 * decision de seguridad y no un despliegue.
 */
public record PoliticaDeIntentos(int intentosMaximos, java.time.Duration duracionDelBloqueo) {

    public PoliticaDeIntentos {
        if (intentosMaximos < 1) {
            throw new IllegalArgumentException("Una politica que bloquea al primer intento no deja entrar a nadie");
        }
    }

    public boolean debeBloquear(int fallidosConsecutivos) {
        return fallidosConsecutivos >= intentosMaximos;
    }
}
