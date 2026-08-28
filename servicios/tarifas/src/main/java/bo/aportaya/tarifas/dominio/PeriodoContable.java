package bo.aportaya.tarifas.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

/**
 * El mes al que se imputa un ingreso, en formato {@code AAAA-MM}.
 *
 * <p>Criterio **devengado**: un devengo de marzo cobrado en mayo se imputa a marzo.
 * Imputarlo al mes de cobro mueve el resultado de un mes a otro y hace que el cierre
 * de marzo, ya firmado, deje de ser cierto.
 */
public record PeriodoContable(String valor) {

    private static final Pattern FORMA = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    public PeriodoContable {
        if (valor == null || !FORMA.matcher(valor).matches()) {
            throw new ErrorDeNegocio(CodigoError.de(35, 5), "Periodo contable mal formado: " + valor + ".");
        }
    }

    public static PeriodoContable de(OffsetDateTime momento) {
        return new PeriodoContable("%04d-%02d".formatted(momento.getYear(), momento.getMonthValue()));
    }

    public LocalDate primerDia() {
        return LocalDate.parse(valor + "-01");
    }

    public LocalDate ultimoDia() {
        LocalDate primero = primerDia();
        return primero.withDayOfMonth(primero.lengthOfMonth());
    }
}
