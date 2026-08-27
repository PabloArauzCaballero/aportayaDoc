package bo.aportaya.auditoria.dominio;

/** Las tres que admite {@code ck_proceso_anonimizacion_estrategia}. */
public enum EstrategiaDeAnonimizacion {
    /** No queda nada bajo retencion: se borra. */
    BORRADO_TOTAL,
    /** Parte vence y parte no: se borra lo vencido y se seudonimiza el resto. */
    BORRADO_PARCIAL,
    /** Todo sigue bajo retencion legal: el dato queda, la identidad no. */
    SEUDONIMIZACION
}
