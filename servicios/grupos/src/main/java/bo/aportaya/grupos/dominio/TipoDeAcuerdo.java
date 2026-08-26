package bo.aportaya.grupos.dominio;

/**
 * Los tipos que el modelo admite, y solo esos.
 *
 * <p><b>Divergencia declarada con el caso de uso.</b> CU-63 nombra
 * {@code CONDONACION}, {@code EXPULSION}, {@code PERMUTA}, {@code TRASPASO_CUPO},
 * {@code DISOLUCION} y {@code REPETIR_SORTEO}. El {@code .puml} —que manda sobre el
 * flujo, contrato §1— dice otra cosa: los cuatro primeros llevan sufijo y los dos
 * ultimos no existen. Se usan los del modelo; renombrarlos seria inventar un enum
 * que la base rechaza.
 */
public enum TipoDeAcuerdo {
    ADMISION_REEMPLAZO,
    CAMBIO_FECHA_COBRO,
    CAMBIO_MONTO,
    CAMBIO_REGLAMENTO,
    CONDONACION_MORA,
    DISOLUCION_ANTICIPADA,
    EXPULSION_PARTICIPANTE,
    PERMUTA_TURNOS;

    /**
     * Si el afectado es parte interesada, su voto no pondera.
     *
     * <p>No es desconfianza: es que nadie decide sobre su propia expulsion ni sobre
     * la condonacion de su propia mora, y dejarlo votar convertiria el quorum en una
     * formalidad en los dos casos donde mas importa.
     */
    public boolean tieneParteInteresada() {
        return this == EXPULSION_PARTICIPANTE || this == CONDONACION_MORA;
    }
}
