package bo.aportaya.auditoria.dominio;

/**
 * Las cinco familias del tablero, cada una con un dueno con nombre.
 *
 * <p>El dueno importa mas que la clasificacion: es quien escribe la explicacion
 * cuando su indicador esta en rojo. Un indicador sin dueno es un numero que nadie
 * defiende y que, la tercera vez que aparece en rojo, deja de mirarse.
 */
public enum FamiliaDeIndicador {
    NEGOCIO,
    RIESGO,
    CUMPLIMIENTO,
    OPERACION,
    FINANZAS
}
