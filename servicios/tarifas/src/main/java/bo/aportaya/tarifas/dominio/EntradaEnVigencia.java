package bo.aportaya.tarifas.dominio;

import java.time.OffsetDateTime;

/**
 * Cuando empieza a regir un tarifario nuevo.
 *
 * <p>Un incremento no entra hasta cumplir su preaviso (R-TAR-08). Es la unica
 * diferencia real entre subir un precio y cobrarle de sorpresa a alguien que ya no
 * puede irse a otro lado sin perder lo que puso.
 */
public record EntradaEnVigencia(OffsetDateTime fechaAviso, int diasPreaviso, boolean requierePreaviso) {

    /** Los cuatro que admite {@code ck_cambio_tarifario_tipo_cambio}. */
    public static boolean exigePreaviso(String tipoCambio) {
        return "INCREMENTO".equals(tipoCambio) || "NUEVO_CONCEPTO".equals(tipoCambio);
    }

    public OffsetDateTime momento() {
        return requierePreaviso ? fechaAviso.plusDays(diasPreaviso) : fechaAviso;
    }

    public boolean cumplidoEn(OffsetDateTime ahora) {
        return !ahora.isBefore(momento());
    }
}
