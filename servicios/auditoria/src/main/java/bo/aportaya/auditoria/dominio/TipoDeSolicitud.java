package bo.aportaya.auditoria.dominio;

import bo.aportaya.plataforma.dominio.ErrorDeDominio;

/**
 * Los derechos que un titular puede ejercer sobre sus datos.
 *
 * <p>Son los cinco que admite {@code ck_solicitud_datos_personales_tipo}. **Manda la
 * tabla**: CU-07 habla de «supresion» y el modelo la llama {@code CANCELACION}, y el
 * modelo tambien admite {@code PORTABILIDAD}, que el caso de uso no menciona. Donde
 * el texto y el esquema no coinciden, gana el que rechaza.
 */
public enum TipoDeSolicitud {
    ACCESO,
    RECTIFICACION,
    OPOSICION,
    PORTABILIDAD,
    /** Lo que CU-07 llama supresion: borrar o anonimizar segun la retencion legal. */
    CANCELACION;

    public static TipoDeSolicitud de(String valor) {
        String normalizado = valor == null ? "" : valor.trim().toUpperCase(java.util.Locale.ROOT);
        // «SUPRESION» se acepta como sinonimo porque es la palabra del caso de uso y
        // la que usa la gente; se traduce aca, una vez, en vez de dejar que cada
        // llamador adivine cual de las dos escribir.
        if ("SUPRESION".equals(normalizado)) {
            return CANCELACION;
        }
        try {
            return TipoDeSolicitud.valueOf(normalizado);
        } catch (IllegalArgumentException noExiste) {
            throw new ErrorDeDominio("No existe el derecho '" + valor + "'");
        }
    }

    /** Solo la cancelacion evalua retencion legal: los demas no borran nada. */
    public boolean borraDatos() {
        return this == CANCELACION;
    }
}
