package bo.aportaya.tarifas.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.Set;

/**
 * Como se calcula una comision.
 *
 * <p>Los seis que admite {@code ck_concepto_tarifa_metodo_calculo}. El metodo y los
 * valores tienen que ser coherentes (R-TAR-03, {@code ck_concepto_metodo}): un
 * concepto {@code PORCENTUAL} sin porcentaje cobra cero y nadie se entera hasta el
 * cierre del mes.
 */
public final class MetodoDeCalculo {

    public static final String GRATUITO = "GRATUITO";
    public static final String FIJO = "FIJO";
    public static final String PORCENTUAL = "PORCENTUAL";
    public static final String MIXTO = "MIXTO";
    public static final String ESCALONADO_POR_TRAMO = "ESCALONADO_POR_TRAMO";
    public static final String ESCALONADO_ACUMULATIVO = "ESCALONADO_ACUMULATIVO";

    public static final Set<String> ADMITIDOS =
            Set.of(GRATUITO, FIJO, PORCENTUAL, MIXTO, ESCALONADO_POR_TRAMO, ESCALONADO_ACUMULATIVO);

    private MetodoDeCalculo() {}

    /**
     * Comprueba metodo y valores juntos, antes de escribir.
     *
     * <p>La base tiene el mismo CHECK. Se valida acá igual para que sea una regla de
     * negocio con su mensaje y no un error 500 con el nombre de una restriccion.
     */
    public static void exigirCoherencia(String metodo, BigDecimal valorFijo, BigDecimal valorPorcentual) {
        if (metodo == null || !ADMITIDOS.contains(metodo)) {
            throw new ErrorDeNegocio(CodigoError.de(30, 4), "Metodo de calculo no admitido: " + metodo + ".");
        }
        boolean coherente =
                switch (metodo) {
                    case GRATUITO -> true;
                    case FIJO -> valorFijo != null;
                    case PORCENTUAL -> valorPorcentual != null;
                    case MIXTO -> valorFijo != null && valorPorcentual != null;
                    default -> true; // los escalonados llevan los valores en cada regla_tarifa
                };
        if (!coherente) {
            throw new ErrorDeNegocio(
                    CodigoError.de(30, 4),
                    "El concepto es " + metodo + " y le faltan los valores que ese metodo exige.");
        }
    }
}
