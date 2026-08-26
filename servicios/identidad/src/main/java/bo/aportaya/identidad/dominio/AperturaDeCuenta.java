package bo.aportaya.identidad.dominio;

/**
 * En que estado nace un usuario, y por que.
 *
 * <p>Nace {@code PENDIENTE_VERIFICACION} y **no activo**. La diferencia no es
 * cosmetica: `identidad` puede crear al usuario, pero la debida diligencia vive en
 * `cumplimiento` y la billetera en `nucleo-financiero`. Hasta que esos dos
 * respondan, el usuario existe y no opera — que es exactamente lo que el caso de uso
 * pide cuando dice «queda habilitado a operar solo dentro de los limites que le
 * corresponden por ese nivel de conocimiento».
 *
 * <p>Marcarlo activo desde el principio seria prometer una habilitacion que todavia
 * nadie evaluo.
 */
public enum AperturaDeCuenta {
    /** Creado, sin diligencia ni billetera todavia. */
    PENDIENTE_VERIFICACION,
    /** Diligencia completa y billetera abierta: recien aca opera. */
    ACTIVO;

    public boolean puedeOperar() {
        return this == ACTIVO;
    }
}
