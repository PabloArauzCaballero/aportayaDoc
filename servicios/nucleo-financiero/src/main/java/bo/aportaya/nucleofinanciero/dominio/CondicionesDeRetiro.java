package bo.aportaya.nucleofinanciero.dominio;

import bo.aportaya.plataforma.dominio.Dinero;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * CU-11 · Las condiciones duras del retiro, todas juntas. Puro.
 *
 * <p>Estan reunidas en un lugar y devuelven **el motivo**, no un booleano, porque un
 * retiro rechazado sin decir por que es la peor experiencia posible con la plata
 * propia: la persona no sabe si esperar, corregir algo o llamar.
 *
 * <p>El orden de las comprobaciones va de lo que la persona puede resolver a lo que
 * no. Primero lo suyo —MFA, saldo, instrumento—; despues lo del sistema —bloqueo
 * judicial, encaje—. Al reves, alguien con un problema de saldo se enteraria primero
 * de que «el sistema esta restringido» y no sabria que hacer.
 */
public final class CondicionesDeRetiro {

    private CondicionesDeRetiro() {}

    /** Lo que hay que saber para decidir. Todo llega resuelto: este atomo no consulta. */
    public record Situacion(
            boolean mfaVerificado,
            Dinero disponible,
            Dinero solicitado,
            boolean instrumentoDelTitular,
            boolean instrumentoVerificado,
            Optional<OffsetDateTime> instrumentoBloqueadoHasta,
            boolean hayBloqueoDeAutoridad,
            boolean encajeCumplido) {}

    public record Veredicto(boolean permitido, String codigo, String motivo) {

        static Veredicto si() {
            return new Veredicto(true, null, null);
        }

        static Veredicto no(String codigo, String motivo) {
            return new Veredicto(false, codigo, motivo);
        }
    }

    public static Veredicto evaluar(Situacion s, OffsetDateTime ahora) {
        if (!s.mfaVerificado()) {
            return Veredicto.no("MFA_REQUERIDO", "Falta el segundo factor para autorizar el retiro.");
        }
        if (s.solicitado().esMayorQue(s.disponible())) {
            return Veredicto.no(
                    "SALDO_INSUFICIENTE", "El disponible no cubre el retiro: quedan " + s.disponible() + ".");
        }
        if (!s.instrumentoDelTitular()) {
            return Veredicto.no(
                    "TITULAR_NO_COINCIDE", "La cuenta de destino no esta a nombre del titular de la billetera.");
        }
        if (!s.instrumentoVerificado()) {
            return Veredicto.no("INSTRUMENTO_EN_ENFRIAMIENTO", "Esa cuenta de destino todavia no esta verificada.");
        }
        if (s.instrumentoBloqueadoHasta().filter(hasta -> hasta.isAfter(ahora)).isPresent()) {
            // La ventana de enfriamiento existe para que agregar una cuenta ajena y
            // vaciar la billetera no sea una sola maniobra.
            return Veredicto.no(
                    "INSTRUMENTO_EN_ENFRIAMIENTO",
                    "Esa cuenta se agrego hace poco: se habilita el "
                            + s.instrumentoBloqueadoHasta().get().toLocalDate() + ".");
        }
        if (s.hayBloqueoDeAutoridad()) {
            return Veredicto.no("BLOQUEO_DE_AUTORIDAD", "Hay saldo inmovilizado por orden de autoridad.");
        }
        if (!s.encajeCumplido()) {
            // Registrar que el encaje no se cumple y seguir pagando es el escenario
            // clasico de la corrida: cobran los primeros y no queda para los demas.
            return Veredicto.no(
                    "ENCAJE_INCUMPLIDO", "Los retiros estan suspendidos temporalmente por control de custodia.");
        }
        return Veredicto.si();
    }
}
