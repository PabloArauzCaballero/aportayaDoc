package bo.aportaya.grupos.dominio;

import java.util.Optional;

/**
 * Si se puede invitar a alguien, y por que no.
 *
 * <p>Atomo puro. La regla que importa esta en el ultimo caso: **insistir tres veces
 * es recordar, insistir diez es acoso.** El tope de reenvios no es una cortesia con
 * el destinatario; es lo que impide que la plataforma se convierta en el canal por el
 * que alguien hostiga a otro.
 */
public final class InvitacionAdmisible {

    /** Del catalogo cuando exista; por ahora, configuracion — nunca una constante suelta. */
    public static final int REENVIOS_POR_OMISION = 3;

    private InvitacionAdmisible() {}

    public static Optional<Motivo> impedimento(
            boolean hayCuposLibres,
            boolean estaSuprimido,
            boolean yaEsParticipante,
            int enviosRealizados,
            int topeDeReenvios,
            boolean emisorHabilitado) {

        if (!emisorHabilitado) {
            return Optional.of(Motivo.EMISOR_NO_HABILITADO);
        }
        if (!hayCuposLibres) {
            return Optional.of(Motivo.SIN_CUPOS_LIBRES);
        }
        // La supresion se responde SIN revelar el motivo: decir «esa persona pidió no
        // recibir mensajes» ya cuenta algo de ella a quien no tiene por que saberlo.
        if (estaSuprimido) {
            return Optional.of(Motivo.DESTINATARIO_SUPRIMIDO);
        }
        if (yaEsParticipante) {
            return Optional.of(Motivo.YA_ES_PARTICIPANTE);
        }
        if (enviosRealizados >= topeDeReenvios) {
            return Optional.of(Motivo.TOPE_REENVIOS);
        }
        return Optional.empty();
    }

    public enum Motivo {
        SIN_CUPOS_LIBRES(1, "Este grupo ya no tiene cupos libres."),
        // El mismo texto que un envio exitoso: no se revela que la persona esta
        // suprimida, porque eso es un dato suyo y no de quien invita.
        DESTINATARIO_SUPRIMIDO(2, "Listo, si corresponde le va a llegar la invitacion."),
        YA_ES_PARTICIPANTE(3, "Esa persona ya esta en el grupo."),
        TOPE_REENVIOS(4, "Ya reenviaste esta invitacion demasiadas veces."),
        EMISOR_NO_HABILITADO(7, "No podes invitar a este grupo.");

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

        /** La supresion se responde como si hubiera salido bien. */
        public boolean seRespondeComoExito() {
            return this == DESTINATARIO_SUPRIMIDO;
        }
    }
}
