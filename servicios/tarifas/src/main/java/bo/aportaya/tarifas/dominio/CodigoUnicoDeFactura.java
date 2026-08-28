package bo.aportaya.tarifas.dominio;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * El CUF: el codigo unico del documento fiscal.
 *
 * <p>Se compone de datos que **identifican al documento sin ambiguedad**: emisor,
 * sucursal, punto de venta, correlativo y momento. Dos facturas con el mismo CUF es lo
 * que R-TAR-09 impide, y la base lo sostiene con {@code uq_factura_cuf}.
 *
 * <p>Puro y determinista: el mismo documento da el mismo codigo. Si dependiera de un
 * azar, un reintento generaria un codigo distinto para el mismo documento y quedarian
 * dos facturas donde hay una.
 */
public record CodigoUnicoDeFactura(String valor) {

    private static final DateTimeFormatter MOMENTO = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    public static CodigoUnicoDeFactura componer(
            String nitEmisor, int sucursal, int puntoVenta, long numeroFactura, OffsetDateTime momento) {

        String material =
                "%s|%d|%d|%d|%s".formatted(nitEmisor, sucursal, puntoVenta, numeroFactura, momento.format(MOMENTO));
        // El digito verificador cierra el codigo: un CUF con un caracter cambiado deja
        // de validar, que es de lo que sirve tenerlo.
        return new CodigoUnicoDeFactura(material.replace("|", "") + verificadorDe(material));
    }

    private static String verificadorDe(String material) {
        int suma = 0;
        for (int i = 0; i < material.length(); i++) {
            suma += material.charAt(i) * (i % 9 + 1);
        }
        return Integer.toString(suma % 10);
    }
}
