package bo.aportaya.publicidad.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Que anuncio se muestra en un espacio, y cuanto cuesta mostrarlo.
 *
 * <p>Gana la puja mas alta entre los que todavia tienen presupuesto del dia. Es la
 * regla mas simple que cumple lo unico que importa aca: **nunca se entrega por encima
 * del presupuesto autorizado**. Un anuncio cuyo conjunto ya gasto su dia no compite,
 * aunque puje mas que todos.
 *
 * <p>El costo depende del modelo de puja. En CPM la puja es por mil impresiones, asi
 * que cada una cuesta la milesima parte; en CPC la impresion es gratis y se cobra el
 * clic. Por eso la impresion de un CPC entra con costo cero y no es un error.
 */
public final class SubastaDelEspacio {

    /** Mil impresiones: la unidad en que se puja en CPM. */
    private static final BigDecimal POR_MIL = BigDecimal.ONE.movePointRight(3);

    /** Cuatro decimales, como la columna {@code costo}. */
    private static final int DECIMALES_DEL_COSTO = 4;

    public static final String CPM = "CPM";
    public static final String CPC = "CPC";

    private SubastaDelEspacio() {}

    /**
     * Un candidato a entregarse: su puja, su modelo y lo que le queda del dia.
     *
     * @param gastadoHoy lo ya consumido del {@code presupuestoDiario}
     */
    public record Candidato(
            UUID anuncioId,
            UUID conjuntoId,
            String modeloPuja,
            BigDecimal pujaMaxima,
            BigDecimal presupuestoDiario,
            BigDecimal gastadoHoy) {

        public BigDecimal disponibleHoy() {
            return presupuestoDiario.subtract(gastadoHoy).max(BigDecimal.ZERO);
        }

        public BigDecimal costoDeLaImpresion() {
            return costoDeImpresion(modeloPuja, pujaMaxima);
        }

        /** Cierto si lo que costaria mostrarlo cabe en lo que le queda del dia. */
        public boolean puedeEntregar() {
            return costoDeLaImpresion().compareTo(disponibleHoy()) <= 0;
        }
    }

    /** Lo que cuesta una impresion segun el modelo de puja. */
    public static BigDecimal costoDeImpresion(String modeloPuja, BigDecimal pujaMaxima) {
        if (CPC.equals(modeloPuja)) {
            return BigDecimal.ZERO.setScale(DECIMALES_DEL_COSTO, RoundingMode.UNNECESSARY);
        }
        return pujaMaxima.divide(POR_MIL, DECIMALES_DEL_COSTO, RoundingMode.HALF_UP);
    }

    /** Lo que cuesta un clic: en CPC, la puja; en CPM el clic no se cobra aparte. */
    public static BigDecimal costoDeClic(String modeloPuja, BigDecimal pujaMaxima) {
        if (CPC.equals(modeloPuja)) {
            return pujaMaxima.setScale(DECIMALES_DEL_COSTO, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(DECIMALES_DEL_COSTO, RoundingMode.UNNECESSARY);
    }

    /** El ganador entre los que pueden entregar, o vacio si no hay ninguno. */
    public static Optional<Candidato> ganador(List<Candidato> candidatos) {
        return candidatos.stream()
                .filter(Candidato::puedeEntregar)
                .max(Comparator.comparing(Candidato::pujaMaxima)
                        .thenComparing(c -> c.anuncioId().toString()));
    }
}
