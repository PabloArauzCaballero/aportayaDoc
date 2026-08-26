package bo.aportaya.identidad.dominio;

/**
 * La unica respuesta a «¿le pido el segundo factor?».
 *
 * <p>Para el operador devuelve **siempre** verdadero: ni el dispositivo de confianza
 * ni nada mas lo exime ({@code R-SEG-10}). Que no exista un camino que lo omita es
 * justamente el criterio de aceptacion, y por eso la decision vive en un solo lugar y
 * no repartida en tres {@code if}.
 */
public final class ExigeSegundoFactor {

    private ExigeSegundoFactor() {}

    public static boolean decidir(PerfilDeAcceso perfil, boolean dispositivoConfiable, boolean permisoExigeMfa) {
        if (perfil.esOperador()) {
            return true;
        }
        return !dispositivoConfiable || permisoExigeMfa;
    }
}
