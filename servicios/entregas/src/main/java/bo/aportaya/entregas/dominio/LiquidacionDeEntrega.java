package bo.aportaya.entregas.dominio;

import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.List;

/**
 * Cuanto se le entrega al beneficiario, y de donde sale cada descuento.
 *
 * <p>El neto **nunca es negativo** (AP-CU22-05). Que las deducciones superen la bolsa
 * significa que el beneficiario terminaria debiendo por cobrar su turno, y eso no es
 * una entrega: es un error de calculo que hay que resolver antes, no cobrarselo.
 *
 * <p>Cada deduccion viaja con su tipo y su origen. «Le descontamos 518» no se puede
 * explicar; «comision de plataforma 18, aporte propio del periodo 500» si.
 */
public final class LiquidacionDeEntrega {

    private LiquidacionDeEntrega() {}

    /**
     * @param referenciaOrigenId la fila que justifica el descuento — el cargo de
     *     comision, la obligacion de aporte. Sin ella la deduccion no se puede auditar
     */
    public record Deduccion(
            String tipo, String descripcion, Dinero monto, java.util.UUID referenciaOrigenId, boolean esObligatoria) {}

    public record Resultado(Dinero bruto, Dinero totalDeducciones, Dinero neto, List<Deduccion> deducciones) {}

    public static Resultado liquidar(Dinero bruto, List<Deduccion> deducciones) {
        Dinero total = deducciones.stream().map(Deduccion::monto).reduce(Dinero.cero(bruto.moneda()), Dinero::mas);

        if (total.esMayorQue(bruto)) {
            throw new ErrorDeNegocio(
                    CodigoError.de(22, 5),
                    "Las deducciones (" + total + ") superan la bolsa (" + bruto
                            + "): el beneficiario terminaria debiendo por cobrar su turno.");
        }
        return new Resultado(bruto, total, bruto.menos(total), List.copyOf(deducciones));
    }
}
