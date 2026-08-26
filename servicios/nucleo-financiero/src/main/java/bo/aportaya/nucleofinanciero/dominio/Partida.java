package bo.aportaya.nucleofinanciero.dominio;

import bo.aportaya.plataforma.dominio.ErrorDeDominio;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Una línea del asiento: lo que se debita o se acredita de una cuenta, nunca las dos
 * cosas a la vez.
 *
 * <p>{@code debe}/{@code haber} viajan como {@link BigDecimal} y no como {@code Dinero}
 * porque {@code movimiento_contable} no tiene columna de moneda —el libro contable de
 * este servicio es en bolivianos, sin excepción— pero el cuadre se calcula igual sobre
 * {@code Dinero} en {@link CuadrarPartidas}.
 */
public record Partida(String cuentaCodigo, BigDecimal debe, BigDecimal haber) {

    public Partida {
        Objects.requireNonNull(cuentaCodigo, "cuentaCodigo");
        Objects.requireNonNull(debe, "debe");
        Objects.requireNonNull(haber, "haber");
        if (cuentaCodigo.isBlank()) {
            throw new ErrorDeDominio("Una partida sin código de cuenta no se puede resolver");
        }
        if (debe.signum() < 0 || haber.signum() < 0) {
            throw new ErrorDeDominio("Una partida no lleva importes negativos: %s debe=%s haber=%s"
                    .formatted(cuentaCodigo, debe, haber));
        }
        if (debe.signum() > 0 && haber.signum() > 0) {
            throw new ErrorDeDominio("Una partida es debe O haber, nunca las dos: %s debe=%s haber=%s"
                    .formatted(cuentaCodigo, debe, haber));
        }
        if (debe.signum() == 0 && haber.signum() == 0) {
            throw new ErrorDeDominio("Una partida con debe=0 y haber=0 no mueve nada: %s".formatted(cuentaCodigo));
        }
    }
}
