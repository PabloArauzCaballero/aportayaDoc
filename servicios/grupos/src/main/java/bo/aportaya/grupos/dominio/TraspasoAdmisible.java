package bo.aportaya.grupos.dominio;

import java.util.Optional;

/**
 * Si un cupo se puede traspasar, y por que no.
 *
 * <p>Atomo puro, y con una afirmacion que vale mas que el resto: **la deuda no se
 * traspasa con el cupo**. Las obligaciones vencidas se quedan con quien las genero;
 * solo las futuras pasan al entrante. Sin eso, un cupo se convertiria en una forma de
 * venderle a alguien la deuda de otro.
 */
public final class TraspasoAdmisible {

    private TraspasoAdmisible() {}

    public static Optional<Motivo> impedimento(
            String estadoDelCupo,
            boolean elTurnoYaSeCobro,
            boolean salienteAlDia,
            String kycDelEntrante,
            String kycMinimoDelGrupo,
            int reputacionDelEntrante,
            int reputacionMinimaDelGrupo,
            boolean hayAcuerdoSiSeExige) {

        if (!"OCUPADO".equals(estadoDelCupo) || elTurnoYaSeCobro) {
            return Optional.of(Motivo.CUPO_NO_TRASPASABLE);
        }
        if (!salienteAlDia) {
            return Optional.of(Motivo.SALIENTE_CON_DEUDA);
        }
        if (NivelDeKyc.de(kycDelEntrante).alcanza(NivelDeKyc.de(kycMinimoDelGrupo))) {
            if (reputacionDelEntrante < reputacionMinimaDelGrupo) {
                return Optional.of(Motivo.ENTRANTE_SIN_REPUTACION);
            }
        } else {
            return Optional.of(Motivo.ENTRANTE_SIN_KYC);
        }
        if (!hayAcuerdoSiSeExige) {
            return Optional.of(Motivo.ACUERDO_REQUERIDO);
        }
        return Optional.empty();
    }

    /** Los niveles del modelo, ordenados: cada uno alcanza a los de abajo. */
    public enum NivelDeKyc {
        NINGUNO,
        BASICO,
        INTERMEDIO,
        COMPLETO;

        public static NivelDeKyc de(String valor) {
            return valueOf(valor);
        }

        public boolean alcanza(NivelDeKyc minimo) {
            return ordinal() >= minimo.ordinal();
        }
    }

    public enum Motivo {
        CUPO_NO_TRASPASABLE(1, "Ese cupo ya no se puede traspasar."),
        SALIENTE_CON_DEUDA(2, "Salda tus aportes pendientes antes de traspasar el cupo."),
        ENTRANTE_SIN_KYC(3, "Quien entra necesita elevar su nivel de verificacion."),
        ENTRANTE_SIN_REPUTACION(4, "Quien entra no llega a la reputacion que pide el grupo."),
        ACUERDO_REQUERIDO(5, "Este grupo exige que el traspaso se vote.");

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
