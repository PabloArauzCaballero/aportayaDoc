package bo.aportaya.aportes.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.Set;

/**
 * Como llego la disputa del proveedor.
 *
 * <p><b>Hueco declarado:</b> el CU-19 nombra {@code FRAUDE_DECLARADO} y
 * {@code ERROR_TECNICO}; {@code ck_disputa_pago_tipo} no los admite. Manda la DDL.
 */
public final class TipoDeDisputa {

    public static final Set<String> ADMITIDOS = Set.of("CONTRACARGO", "DESCONOCIMIENTO", "MONTO_INCORRECTO");

    private TipoDeDisputa() {}

    public static String exigir(String tipo) {
        if (tipo == null || !ADMITIDOS.contains(tipo)) {
            throw new ErrorDeNegocio(
                    CodigoError.de(19, 6),
                    "Tipo de disputa no admitido: " + tipo + ".",
                    java.util.Map.of(
                            "admitidos", ADMITIDOS.stream().sorted().toList().toString()));
        }
        return tipo;
    }
}
