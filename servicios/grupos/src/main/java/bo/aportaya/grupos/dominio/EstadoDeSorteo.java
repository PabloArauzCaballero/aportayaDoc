package bo.aportaya.grupos.dominio;

/**
 * Los tres estados del sorteo. **Jamas se publica un resultado cuyo hash no cierra**:
 * si la verificacion falla, el sorteo queda {@code ANULADO} y se recomienza con
 * semilla nueva.
 */
public enum EstadoDeSorteo {
    COMPROMETIDO,
    REVELADO,
    ANULADO
}
