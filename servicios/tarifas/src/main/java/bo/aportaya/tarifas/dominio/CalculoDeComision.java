package bo.aportaya.tarifas.dominio;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula lo que se cobra, y **por que**.
 *
 * <p>Devuelve el desglose junto con el numero. El reclamo tipico no es «me cobraron de
 * mas»: es «nadie me aviso». Un total sin desglose no se puede explicar seis meses
 * despues, y lo que no se puede explicar se termina devolviendo.
 *
 * <p>Puro: sin Spring, sin jOOQ, sin reloj. Se le pasa todo lo que necesita.
 */
public final class CalculoDeComision {

    private CalculoDeComision() {}

    /** Un concepto tal como lo aplica el calculo, ya resuelto contra su regla. */
    public record Concepto(
            String codigo,
            String nombreComercial,
            String metodoCalculo,
            BigDecimal valorPorcentual,
            BigDecimal valorFijo,
            BigDecimal montoMinimo,
            BigDecimal montoMaximo,
            boolean gravadoIva,
            boolean gravadoIt,
            boolean precioIncluyeImpuesto) {}

    /** Un impuesto vigente del catalogo. Nunca una constante (invariante 10). */
    public record Impuesto(String codigo, BigDecimal alicuota) {}

    /** Una linea de lo que se le muestra al usuario. */
    public record LineaDesglose(String concepto, String detalle, Dinero monto) {}

    public record Resultado(
            Dinero montoBase,
            Dinero montoComision,
            Dinero montoDescuento,
            Dinero montoImpuesto,
            Dinero montoTotal,
            List<LineaDesglose> desglose,
            boolean pisoAplicado,
            boolean techoAplicado) {}

    /**
     * @param descuento lo que rebajan segmento, promocion o exencion, ya resuelto
     */
    public static Resultado calcular(
            Dinero base, Concepto concepto, List<Impuesto> impuestos, Dinero descuento, PoliticaDeRedondeo redondeo) {

        Moneda moneda = base.moneda();
        PoliticaDeRedondeo politica = redondeo == null ? PoliticaDeRedondeo.alCentavo() : redondeo;
        List<LineaDesglose> desglose = new ArrayList<>();

        BigDecimal bruto = brutoSegunMetodo(base, concepto);
        boolean piso = false;
        boolean techo = false;

        // El piso y el techo se aplican ANTES del descuento: un techo que se mide
        // despues del descuento deja de ser techo y pasa a ser otro descuento.
        if (concepto.montoMinimo() != null && bruto.compareTo(concepto.montoMinimo()) < 0) {
            bruto = concepto.montoMinimo();
            piso = true;
        }
        if (concepto.montoMaximo() != null && bruto.compareTo(concepto.montoMaximo()) > 0) {
            bruto = concepto.montoMaximo();
            techo = true;
        }

        Dinero comision = Dinero.de(politica.aplicar(bruto), moneda);
        desglose.add(new LineaDesglose(concepto.codigo(), explicar(concepto, piso, techo), comision));

        Dinero rebaja = descuento == null ? Dinero.cero(moneda) : descuento;
        if (rebaja.esMayorQue(comision)) {
            // Un descuento mayor que la comision no genera un credito a favor: la deja
            // en cero. Regalar plata por un error de configuracion no es una promocion.
            rebaja = comision;
        }
        if (rebaja.monto().signum() > 0) {
            desglose.add(new LineaDesglose("DESCUENTO", "Beneficio aplicado", rebaja));
        }

        Dinero neta = comision.menos(rebaja);
        Dinero impuesto = Dinero.cero(moneda);
        for (Impuesto tributo : impuestos) {
            if (!aplica(concepto, tributo)) {
                continue;
            }
            BigDecimal montoDelTributo = concepto.precioIncluyeImpuesto()
                    // Si el precio ya lo incluye, el impuesto se EXTRAE del total, no
                    // se suma encima: sumarlo cobraria el impuesto dos veces (R-TAR-12).
                    ? neta.monto()
                            .multiply(tributo.alicuota())
                            .divide(BigDecimal.ONE.add(tributo.alicuota()), 2, RoundingMode.HALF_EVEN)
                    : neta.monto().multiply(tributo.alicuota()).setScale(2, RoundingMode.HALF_EVEN);
            Dinero linea = Dinero.de(montoDelTributo, moneda);
            impuesto = impuesto.mas(linea);
            desglose.add(new LineaDesglose(
                    tributo.codigo(),
                    concepto.precioIncluyeImpuesto() ? "Incluido en el precio" : "Se suma al precio",
                    linea));
        }

        Dinero total = concepto.precioIncluyeImpuesto() ? neta : neta.mas(impuesto);
        return new Resultado(base, comision, rebaja, impuesto, total, List.copyOf(desglose), piso, techo);
    }

    private static BigDecimal brutoSegunMetodo(Dinero base, Concepto concepto) {
        return switch (concepto.metodoCalculo()) {
            case MetodoDeCalculo.GRATUITO -> BigDecimal.ZERO;
            case MetodoDeCalculo.FIJO -> concepto.valorFijo();
            case MetodoDeCalculo.PORCENTUAL -> base.monto().multiply(concepto.valorPorcentual());
            case MetodoDeCalculo.MIXTO -> concepto.valorFijo().add(base.monto().multiply(concepto.valorPorcentual()));
            // Los escalonados llegan con su tramo YA resuelto en `regla_tarifa`: el
            // porcentaje y el fijo de esta Concepto son los del tramo que gano.
            default ->
                concepto.valorFijo() == null
                        ? base.monto().multiply(concepto.valorPorcentual())
                        : concepto.valorFijo().add(base.monto().multiply(nuloEsCero(concepto.valorPorcentual())));
        };
    }

    private static BigDecimal nuloEsCero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static boolean aplica(Concepto concepto, Impuesto tributo) {
        return switch (tributo.codigo()) {
            case "IVA" -> concepto.gravadoIva();
            case "IT" -> concepto.gravadoIt();
            // Un impuesto que el concepto no declara NO se cobra. Denegar por omision
            // (invariante 9): cobrar un tributo que nadie declaro es lo que se reclama.
            default -> false;
        };
    }

    private static String explicar(Concepto concepto, boolean piso, boolean techo) {
        String base = concepto.nombreComercial();
        if (piso) {
            return base + " (monto minimo)";
        }
        if (techo) {
            return base + " (tope aplicado)";
        }
        return base;
    }
}
