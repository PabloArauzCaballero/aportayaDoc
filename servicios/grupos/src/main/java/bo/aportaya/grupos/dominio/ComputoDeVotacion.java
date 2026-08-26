package bo.aportaya.grupos.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * El resultado de una votacion, calculado sobre los votos emitidos y sus pesos.
 *
 * <p>Atomo puro. El peso es el de los cupos: quien tiene dos manos pesa doble, quien
 * tiene media pesa la mitad — y la ponderacion viene **guardada en el voto**, no se
 * recalcula. Si se recalculara, traspasar un cupo despues de votar cambiaria una
 * votacion ya cerrada.
 */
public record ComputoDeVotacion(BigDecimal aFavor, BigDecimal enContra, BigDecimal abstenciones, BigDecimal pesoTotal) {

    private static final int ESCALA = 4;

    public static ComputoDeVotacion de(List<VotoPonderado> votos, BigDecimal pesoTotalDelGrupo) {
        BigDecimal aFavor = sumar(votos, Sentido.A_FAVOR);
        BigDecimal enContra = sumar(votos, Sentido.EN_CONTRA);
        BigDecimal abstenciones = sumar(votos, Sentido.ABSTENCION);
        return new ComputoDeVotacion(aFavor, enContra, abstenciones, pesoTotalDelGrupo);
    }

    /** La fraccion a favor sobre el peso total del grupo, no sobre lo votado. */
    public BigDecimal fraccionAFavor() {
        if (pesoTotal.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return aFavor.divide(pesoTotal, ESCALA, RoundingMode.HALF_UP);
    }

    /** Aprobado solo si la fraccion a favor alcanza el quorum exigido. */
    public boolean alcanza(BigDecimal quorumRequerido) {
        return fraccionAFavor().compareTo(quorumRequerido) >= 0;
    }

    private static BigDecimal sumar(List<VotoPonderado> votos, Sentido sentido) {
        return votos.stream()
                .filter(voto -> voto.sentido() == sentido)
                .map(VotoPonderado::peso)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public enum Sentido {
        A_FAVOR,
        EN_CONTRA,
        ABSTENCION
    }

    public record VotoPonderado(Sentido sentido, BigDecimal peso) {}
}
