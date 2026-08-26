package bo.aportaya.identidad.dominio.puertos;

/**
 * Verificar una credencial toca algo de afuera: la **pimienta**, que es un secreto de
 * configuracion y no un dato de la fila.
 *
 * <p>Por eso es un puerto y no una funcion suelta. El adaptador por omision es
 * Argon2id local; ningun nombre de proveedor sale de el.
 */
public interface HasheadorDeCredencial {

    String hashear(char[] credencial);

    /**
     * Comparacion en tiempo constante. Nunca {@code equals} sobre el hash: la
     * diferencia de milisegundos entre un fallo temprano y uno tardio alcanza para
     * adivinar de a un caracter.
     */
    boolean coincide(char[] credencial, String hashGuardado);
}
