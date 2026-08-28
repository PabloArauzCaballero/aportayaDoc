package bo.aportaya.erp.dominio;

import java.math.BigDecimal;
import java.util.List;

/**
 * El cuadre de un periodo: debe contra haber.
 *
 * <p>**Si no cuadra, no es un cierre.** {@code ck_cierre_periodo_cuadrado} exige que el
 * total debe iguale al total haber, y esta clase existe para decir por cuanto y de que
 * lado antes de que la base lo rechace — un mensaje que solo diga «no cuadra» obliga a
 * buscar la diferencia a mano en miles de asientos.
 *
 * <p>Y el estado financiero **sale de los saldos, no de un total guardado**: un balance
 * que arrastra un numero de otra tabla puede quedar desactualizado sin que nadie lo note.
 */
public final class CuadreContable {

    private CuadreContable() {}

    public static Cuadre verificar(BigDecimal totalDebe, BigDecimal totalHaber) {
        BigDecimal diferencia = totalDebe.subtract(totalHaber);
        return new Cuadre(diferencia.signum() == 0, totalDebe, totalHaber, diferencia);
    }

    /**
     * Arma el estado financiero desde los saldos por cuenta.
     *
     * <p>La ecuacion contable tiene que cerrar: activo = pasivo + patrimonio. Publicar un
     * balance que no cierra es publicar un numero que nadie puede usar.
     */
    public static Estado balance(List<SaldoDeCuenta> saldos) {
        BigDecimal activo = sumar(saldos, "ACTIVO");
        BigDecimal pasivo = sumar(saldos, "PASIVO");
        BigDecimal patrimonio = sumar(saldos, "PATRIMONIO");
        BigDecimal diferencia = activo.subtract(pasivo.add(patrimonio));
        return new Estado(
                "BALANCE_GENERAL",
                List.of(
                        new Renglon("ACTIVO", activo),
                        new Renglon("PASIVO", pasivo),
                        new Renglon("PATRIMONIO", patrimonio)),
                diferencia.signum() == 0,
                diferencia);
    }

    public static Estado resultados(List<SaldoDeCuenta> saldos) {
        BigDecimal ingresos = sumar(saldos, "INGRESO");
        BigDecimal egresos = sumar(saldos, "EGRESO");
        BigDecimal resultado = ingresos.subtract(egresos);
        return new Estado(
                "ESTADO_RESULTADOS",
                List.of(
                        new Renglon("INGRESO", ingresos),
                        new Renglon("EGRESO", egresos),
                        new Renglon("RESULTADO", resultado)),
                true,
                BigDecimal.ZERO.setScale(2));
    }

    private static BigDecimal sumar(List<SaldoDeCuenta> saldos, String tipo) {
        return saldos.stream()
                .filter(s -> tipo.equals(s.tipo()))
                .map(SaldoDeCuenta::saldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_EVEN);
    }

    /**
     * @param tipo el del plan de cuentas: ACTIVO, PASIVO, PATRIMONIO, INGRESO o EGRESO.
     *     Son los cinco que admite {@code ck_cuenta_contable_tipo}; no se inventa un
     *     sexto para acomodar un renglon del informe
     */
    public record SaldoDeCuenta(String codigo, String tipo, BigDecimal saldo) {}

    public record Renglon(String concepto, BigDecimal monto) {}

    public record Cuadre(boolean cuadra, BigDecimal totalDebe, BigDecimal totalHaber, BigDecimal diferencia) {}

    public record Estado(String tipo, List<Renglon> renglones, boolean ecuacionCierra, BigDecimal diferencia) {}
}
