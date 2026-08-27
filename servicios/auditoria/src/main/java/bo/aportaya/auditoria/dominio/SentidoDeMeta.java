package bo.aportaya.auditoria.dominio;

import java.math.BigDecimal;

/**
 * Para que lado se cumple una meta.
 *
 * <p>No es un detalle: sin esto, «morosidad 7 % con meta 5 %» se leeria como que
 * cumple, porque 7 es mayor que 5. La mitad de los indicadores de riesgo y
 * cumplimiento son de los que **cuanto menos, mejor**, y el semaforo se pinta al
 * reves de lo que sugiere la aritmetica ingenua.
 */
public enum SentidoDeMeta {
    /** Cumple cuando el valor alcanza o supera la meta: volumen, grupos activos. */
    MAYOR_ES_MEJOR,
    /** Cumple cuando el valor no supera la meta: morosidad, incidencias, costo. */
    MENOR_ES_MEJOR;

    public boolean cumple(BigDecimal valor, BigDecimal meta) {
        // `compareTo` y nunca `equals`: dos BigDecimal con distinta escala —5 y 5.00—
        // no son `equals` aunque valgan lo mismo (invariante 4).
        return this == MAYOR_ES_MEJOR ? valor.compareTo(meta) >= 0 : valor.compareTo(meta) <= 0;
    }
}
