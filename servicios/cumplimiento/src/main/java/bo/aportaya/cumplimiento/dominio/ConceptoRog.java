package bo.aportaya.cumplimiento.dominio;

/**
 * Traduce el tipo de transaccion al concepto de operacion del articulo 53.
 *
 * <p>El mapeo esta aca y no en la base porque es una lectura de la norma, y una lectura
 * de la norma tiene que poder discutirse leyendo codigo, no un {@code CASE} enterrado
 * en una funcion PL/pgSQL. La bóveda tiene su propia copia en {@code fn_uif_concepto};
 * **esta la reproduce exactamente**, y la prueba lo verifica contra la base.
 */
public final class ConceptoRog {

    private ConceptoRog() {}

    public static String de(String tipoDeTransaccion) {
        return switch (tipoDeTransaccion) {
            case "RECARGA" -> "CARGA_BILLETERA";
            case "RETIRO" -> "RETIRO_BILLETERA";
            case "TRANSFERENCIA_P2P", "APORTE_A_GRUPO" -> "TRANSFERENCIA_BILLETERA";
            default -> "ELECTRONICA";
        };
    }

    /**
     * Si el formulario corresponde a este caso de uso.
     *
     * <p>Los ROG son cuatro y se distinguen por inciso, no por monto: ROG-01 y ROG-02
     * son retiros en efectivo y por cambio de moneda extranjera, **sin umbral**. Que se
     * reporten «sin importar el monto» no es un descuido del catalogo: es la norma.
     */
    public static boolean esRog(String formulario) {
        return formulario != null && formulario.startsWith("ROG-");
    }
}
