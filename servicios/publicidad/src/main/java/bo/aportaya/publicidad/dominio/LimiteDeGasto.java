package bo.aportaya.publicidad.dominio;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Cuanto le queda a una cuenta publicitaria antes de tocar su techo del mes.
 *
 * <p>El limite es opcional: una cuenta sin limite no tiene techo, y eso es una
 * decision comercial legitima, no un dato faltante. Cuando lo hay, lo que decide es
 * cuanto queda —limite menos consumido—, no el limite a secas: un presupuesto de 500
 * cabe en un limite de 1.000 solo si todavia no se gastaron 600.
 */
public final class LimiteDeGasto {

    private LimiteDeGasto() {}

    /** Lo que queda del mes, o vacio si la cuenta no tiene techo. */
    public static Optional<BigDecimal> disponible(BigDecimal limite, BigDecimal consumido) {
        if (limite == null) {
            return Optional.empty();
        }
        BigDecimal gastado = consumido == null ? BigDecimal.ZERO : consumido;
        return Optional.of(limite.subtract(gastado).max(BigDecimal.ZERO));
    }

    /** Cierto si el presupuesto entra en lo que queda. Sin limite, entra siempre. */
    public static boolean cabe(BigDecimal presupuesto, BigDecimal limite, BigDecimal consumido) {
        return disponible(limite, consumido)
                .map(queda -> presupuesto.compareTo(queda) <= 0)
                .orElse(true);
    }
}
