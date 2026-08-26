package bo.aportaya.grupos.dominio;

import java.util.Optional;

/**
 * Si dos turnos se pueden permutar, y por que no.
 *
 * <p>Atomo puro: recibe el estado de los dos turnos y si cada parte esta al dia, y
 * decide. Que la decision este junta y no repartida en cuatro {@code if} es lo que
 * permite leer de un vistazo las cuatro razones por las que una permuta no procede.
 */
public final class PermutaPosible {

    private PermutaPosible() {}

    public static Optional<Motivo> impedimento(
            String estadoDelOrigen,
            String estadoDelDestino,
            boolean solicitanteAlDia,
            boolean contraparteAlDia,
            boolean elReglamentoLoPermite) {
        // El pasado no se reordena: un turno ya cobrado o en curso no se permuta.
        if (!"PROGRAMADO".equals(estadoDelOrigen) || !"PROGRAMADO".equals(estadoDelDestino)) {
            return Optional.of(Motivo.TURNO_NO_PERMUTABLE);
        }
        // Primero se pone al dia: permutar con deuda seria adelantar el cobro de un
        // moroso, y el grupo entero paga esa cuenta.
        if (!solicitanteAlDia) {
            return Optional.of(Motivo.SOLICITANTE_EN_MORA);
        }
        if (!contraparteAlDia) {
            return Optional.of(Motivo.CONTRAPARTE_EN_MORA);
        }
        if (!elReglamentoLoPermite) {
            return Optional.of(Motivo.REGLAMENTO_NO_PERMITE);
        }
        return Optional.empty();
    }

    public enum Motivo {
        TURNO_NO_PERMUTABLE(1, "Ese turno ya no se puede permutar."),
        SOLICITANTE_EN_MORA(2, "Ponete al dia antes de permutar tu turno."),
        CONTRAPARTE_EN_MORA(3, "La otra persona tiene aportes pendientes."),
        REGLAMENTO_NO_PERMITE(4, "El reglamento de este grupo no permite permutas.");

        private final int numero;
        private final String mensaje;

        Motivo(int numero, String mensaje) {
            this.numero = numero;
            this.mensaje = mensaje;
        }

        public int numero() {
            return numero;
        }

        public String mensaje() {
            return mensaje;
        }
    }
}
