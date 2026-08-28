package bo.aportaya.aportes.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.Set;

/**
 * Por que se devuelve la plata.
 *
 * <p>El motivo no es texto libre: es la unica columna por la que despues se puede
 * contar cuantos reembolsos salieron por un cobro doble del proveedor y cuantos por
 * un error nuestro. Si admite cualquier frase, esa cuenta no se puede hacer y el
 * proveedor no responde por nada.
 *
 * <p><b>Hueco declarado:</b> {@code docs/CasosDeUso/CU-19} nombra {@code MONTO_ERRONEO},
 * {@code NO_RECONOCIDO} y {@code SERVICIO_NO_PRESTADO}, y un campo {@code observacion}.
 * La DDL de {@code aportes.reembolso} no admite ninguno de los tres ni tiene esa
 * columna. Por precedencia manda la DDL; el desvio esta en {@code planes/informes/carril-3A.md}.
 */
public final class MotivoDeReembolso {

    /** Los cuatro que acepta {@code ck_reembolso_motivo}. */
    public static final Set<String> ADMITIDOS = Set.of("DISPUTA", "DUPLICADO", "ERROR_MONTO", "GRUPO_CANCELADO");

    private MotivoDeReembolso() {}

    /** Devuelve el motivo si la base lo va a aceptar; si no, rechaza antes de escribir. */
    public static String exigir(String motivo) {
        if (motivo == null || !ADMITIDOS.contains(motivo)) {
            throw new ErrorDeNegocio(
                    CodigoError.de(19, 5),
                    "Motivo de reembolso no admitido: " + motivo + ".",
                    java.util.Map.of(
                            "admitidos", ADMITIDOS.stream().sorted().toList().toString()));
        }
        return motivo;
    }
}
