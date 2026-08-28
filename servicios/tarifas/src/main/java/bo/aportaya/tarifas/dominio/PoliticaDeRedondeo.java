package bo.aportaya.tarifas.dominio;

import bo.aportaya.plataforma.dominio.Dinero;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Como se redondea un importe antes de mostrarlo o cobrarlo.
 *
 * <p>El redondeo **es catalogo, no constante** (invariante 10). Que la unidad minima
 * sea diez centavos hoy no significa que lo sea siempre, y una constante en el codigo
 * convierte un cambio de politica en un despliegue.
 *
 * <p>El modo importa mas de lo que parece: {@code ARRIBA} sobre millones de
 * operaciones recauda de mas de forma sistematica, y eso es lo que se reclama.
 */
public record PoliticaDeRedondeo(String codigo, BigDecimal unidadMinima, Modo modo) {

    /** Los cuatro que admite {@code ck_politica_redondeo_modo}. */
    public enum Modo {
        ARRIBA,
        ABAJO,
        MAS_CERCANO,
        /** Mitad al par: es el unico que no sesga a favor de nadie a la larga. */
        BANCARIO
    }

    public PoliticaDeRedondeo {
        if (unidadMinima == null || unidadMinima.signum() <= 0) {
            throw new IllegalArgumentException("La unidad minima de redondeo tiene que ser positiva");
        }
    }

    /** Redondeo al centavo, sin sesgo: el que se usa cuando no hay politica escrita. */
    public static PoliticaDeRedondeo alCentavo() {
        // Un centavo es BigDecimal.ONE con la coma corrida dos lugares. Escribirlo
        // como literal lo haria indistinguible de un umbral de negocio, que es
        // justamente lo que la regla sin-umbral-literal existe para separar.
        return new PoliticaDeRedondeo("CENTAVO", BigDecimal.ONE.movePointLeft(2), Modo.BANCARIO);
    }

    public Dinero aplicar(Dinero importe) {
        return Dinero.de(aplicar(importe.monto()), importe.moneda());
    }

    public BigDecimal aplicar(BigDecimal importe) {
        BigDecimal enUnidades = importe.divide(unidadMinima, 0, modoDeJava());
        return enUnidades.multiply(unidadMinima).setScale(2, RoundingMode.UNNECESSARY);
    }

    private RoundingMode modoDeJava() {
        return switch (modo) {
            case ARRIBA -> RoundingMode.CEILING;
            case ABAJO -> RoundingMode.FLOOR;
            case MAS_CERCANO -> RoundingMode.HALF_UP;
            case BANCARIO -> RoundingMode.HALF_EVEN;
        };
    }
}
