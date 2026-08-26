package bo.aportaya.identidad.dominio;

/**
 * Que se corta cuando cambia la credencial.
 *
 * <p>Para el participante, la sesion que hizo el cambio sobrevive: es lo razonable, y
 * lo que arriesga es lo suyo. Para el operador **no sobrevive ninguna** — porque el
 * caso que importa es justamente que quien hizo el cambio sea el atacante
 * ({@code R-SEG-11}).
 */
public enum CorteDeCredencial {
    /** Se cierran todas menos la que hizo el cambio. */
    SALVO_LA_ACTUAL,
    /** R-SEG-11: sesiones, confianza de dispositivos y refrescos, todo. */
    TOTAL;

    public static CorteDeCredencial para(PerfilDeAcceso perfil) {
        return perfil.esOperador() ? TOTAL : SALVO_LA_ACTUAL;
    }

    public boolean alcanzaALaSesionActual() {
        return this == TOTAL;
    }

    public boolean quitaLaConfianzaDeLosDispositivos() {
        return this == TOTAL;
    }

    public String motivo() {
        return this == TOTAL
                ? "R-SEG-11: restablecimiento de credencial de operador"
                : "cambio de credencial del titular";
    }
}
