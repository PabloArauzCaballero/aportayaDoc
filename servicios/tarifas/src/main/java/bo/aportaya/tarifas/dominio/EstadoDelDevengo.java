package bo.aportaya.tarifas.dominio;

import bo.aportaya.plataforma.dominio.Dinero;

/**
 * En que quedo un devengo, **derivado** de lo que efectivamente paso.
 *
 * <p>{@code devengo_comision} es append-only ({@code tg_devengo_comision_append_only},
 * R-AUD-01): la fila se escribe una vez y su columna {@code estado} guarda el estado
 * <b>al devengar</b>, no el de hoy. El estado corriente sale de los {@code cargo_comision}
 * y las {@code devolucion_comision}, que son las filas que registran lo que realmente
 * ocurrio.
 *
 * <p>Es mas trabajo que un UPDATE, y es la unica forma honesta: un ingreso reconocido
 * que despues se puede reescribir no prueba nada. Si el estado se pudiera editar,
 * «este mes ganamos X» seria una afirmacion sin respaldo.
 *
 * <p><b>Hueco declarado:</b> el CU-31 dice «{@code devengo_comision.estado='COBRADO'}»
 * y el CU-33 «{@code estado='DEVUELTO'}». La DDL no lo permite. Manda la DDL; el
 * desvio esta en {@code planes/informes/carril-2B.md}.
 */
public final class EstadoDelDevengo {

    public static final String DEVENGADO = "DEVENGADO";
    public static final String COBRADO = "COBRADO";
    public static final String COBRADO_PARCIAL = "COBRADO_PARCIAL";
    public static final String EXONERADO = "EXONERADO";
    public static final String DEVUELTO = "DEVUELTO";
    public static final String INCOBRABLE = "INCOBRABLE";

    private EstadoDelDevengo() {}

    /**
     * @param intentosFallidos cuantos cargos fallaron
     * @param topeDeIntentos a partir de cuantos se declara incobrable
     */
    public static String derivar(
            String estadoAlDevengar,
            Dinero total,
            Dinero cobrado,
            Dinero devuelto,
            int intentosFallidos,
            int topeDeIntentos) {

        // Lo exonerado no se cobra ni se devuelve: nacio en cero y ahi se queda.
        if (EXONERADO.equals(estadoAlDevengar)) {
            return EXONERADO;
        }
        // Se mira lo devuelto ANTES que lo cobrado: para devolver hubo que cobrar, y
        // lo que interesa saber es donde termino la plata.
        if (devuelto.monto().signum() > 0 && !total.esMayorQue(devuelto)) {
            return DEVUELTO;
        }
        if (cobrado.monto().signum() > 0) {
            return total.esMayorQue(cobrado) ? COBRADO_PARCIAL : COBRADO;
        }
        if (intentosFallidos >= topeDeIntentos) {
            return INCOBRABLE;
        }
        return DEVENGADO;
    }
}
