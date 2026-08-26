package bo.aportaya.plataforma.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Un importe con su moneda, sobre {@link BigDecimal} con escala 2.
 *
 * <p>Es el atomo que justifica el cambio de lenguaje: con {@code BigDecimal}, dos de
 * las tres reglas de disciplina del stack anterior las resuelve el tipo. La tercera
 * —serializar como cadena— vive en la frontera de salida, porque el cliente es
 * JavaScript y un {@code number} de JSON llega como doble del otro lado.
 *
 * <p>No hay aritmetica sobre el {@code BigDecimal} desnudo: la moneda viaja pegada al
 * importe y {@link #dividir} exige la regla de redondeo, siempre.
 */
public final class Dinero implements Comparable<Dinero> {

    /** La escala del modelo: {@code numeric(14,2)}, o {@code (16,2)} para acumulados. */
    public static final int ESCALA = 2;

    private final BigDecimal monto;
    private final Moneda moneda;

    private Dinero(BigDecimal monto, Moneda moneda) {
        this.monto = monto;
        this.moneda = moneda;
    }

    public static Dinero de(String monto, Moneda moneda) {
        Objects.requireNonNull(monto, "monto");
        return de(new BigDecimal(monto), moneda);
    }

    public static Dinero de(BigDecimal monto, Moneda moneda) {
        Objects.requireNonNull(monto, "monto");
        Objects.requireNonNull(moneda, "moneda");
        if (monto.scale() > ESCALA) {
            throw new ErrorDeDominio(
                    "Un importe no se redondea al construirlo: %s tiene %d decimales. Redondea con una regla explicita."
                            .formatted(monto, monto.scale()));
        }
        return new Dinero(monto.setScale(ESCALA, RoundingMode.UNNECESSARY), moneda);
    }

    public static Dinero cero(Moneda moneda) {
        return new Dinero(BigDecimal.ZERO.setScale(ESCALA, RoundingMode.UNNECESSARY), moneda);
    }

    public BigDecimal monto() {
        return monto;
    }

    public Moneda moneda() {
        return moneda;
    }

    public Dinero mas(Dinero otro) {
        return new Dinero(monto.add(mismaMoneda(otro).monto), moneda);
    }

    public Dinero menos(Dinero otro) {
        return new Dinero(monto.subtract(mismaMoneda(otro).monto), moneda);
    }

    public Dinero negado() {
        return new Dinero(monto.negate(), moneda);
    }

    /** Multiplica por un factor sin moneda —una tasa, una cantidad— y redondea una sola vez. */
    public Dinero por(BigDecimal factor, RoundingMode regla) {
        Objects.requireNonNull(factor, "factor");
        return new Dinero(monto.multiply(factor).setScale(ESCALA, exigirRegla(regla)), moneda);
    }

    /** Nunca sin regla: dividir sin decir como redondear es perder centavos en silencio. */
    public Dinero dividir(BigDecimal divisor, RoundingMode regla) {
        Objects.requireNonNull(divisor, "divisor");
        if (divisor.signum() == 0) {
            throw new ErrorDeDominio("No se divide un importe entre cero");
        }
        return new Dinero(monto.divide(divisor, ESCALA, exigirRegla(regla)), moneda);
    }

    public boolean esCero() {
        return monto.signum() == 0;
    }

    public boolean esNegativo() {
        return monto.signum() < 0;
    }

    public boolean esMayorQue(Dinero otro) {
        return compareTo(mismaMoneda(otro)) > 0;
    }

    public boolean esMenorQue(Dinero otro) {
        return compareTo(mismaMoneda(otro)) < 0;
    }

    private Dinero mismaMoneda(Dinero otro) {
        Objects.requireNonNull(otro, "otro");
        if (otro.moneda != moneda) {
            throw new ErrorDeDominio("No se operan %s con %s: el tipo de cambio es un dato con fecha, no una constante"
                    .formatted(moneda, otro.moneda));
        }
        return otro;
    }

    private static RoundingMode exigirRegla(RoundingMode regla) {
        Objects.requireNonNull(regla, "regla de redondeo");
        if (regla == RoundingMode.UNNECESSARY) {
            throw new ErrorDeDominio("La regla de redondeo la fija el tarifario, y tiene que ser explicita");
        }
        return regla;
    }

    @Override
    public int compareTo(Dinero otro) {
        return monto.compareTo(mismaMoneda(otro).monto);
    }

    /**
     * Compara por VALOR: la escala esta normalizada a 2 en construccion, asi que
     * {@code 1.10} y {@code 1.1} son el mismo importe y tambien el mismo objeto.
     */
    @Override
    public boolean equals(Object otro) {
        return otro instanceof Dinero d && moneda == d.moneda && monto.compareTo(d.monto) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(monto.stripTrailingZeros(), moneda);
    }

    /** La forma de la frontera: cadena decimal de dos decimales. */
    @Override
    public String toString() {
        return monto.toPlainString();
    }
}
