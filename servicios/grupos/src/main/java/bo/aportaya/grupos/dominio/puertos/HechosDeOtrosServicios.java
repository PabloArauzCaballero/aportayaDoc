package bo.aportaya.grupos.dominio.puertos;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Los hechos que este servicio necesita y no le pertenecen.
 *
 * <p>Un pasanaku se decide con datos de media plataforma: si el organizador esta
 * habilitado, si hay tarifario vigente, si quien pide esta al dia, cuanta reputacion
 * tiene, si esta restringido. **Nada de eso vive en el esquema de grupos**, y leerlo
 * donde vive seria el invariante 11.
 *
 * <p>Van todos juntos en un puerto y no en seis porque se preguntan juntos: el caso de
 * uso que crea un grupo necesita tres a la vez, y el que acepta a alguien, cuatro. Un
 * puerto por servicio obligaria a cada caso de uso a coordinar seis dependencias para
 * armar una sola decision.
 *
 * <p><b>Todo se resuelve ANTES de abrir la transaccion</b> (invariante 6). Y cuando un
 * servicio no contesta, la respuesta es la que no deja pasar: denegar por omision
 * (invariante 9).
 */
public interface HechosDeOtrosServicios {

    /** ¿El organizador puede abrir grupos hoy? Lo dice {@code organizador}. */
    boolean organizadorHabilitado(UUID organizadorId);

    /** El tarifario vigente de un codigo, o vacio si no hay. Lo dice {@code tarifas}. */
    java.util.Optional<UUID> tarifarioVigente(String codigo);

    /** ¿La licencia cubre este servicio? Lo dice {@code cumplimiento}. */
    boolean licenciaHabilita(String servicio);

    /** Como viene de pagos un participante. Lo dice {@code aportes}. */
    EstadoDePagos estadoDePagos(UUID participanteId);

    /** Cuantos del grupo estan en mora. Lo dice {@code aportes}. */
    int morososDelGrupo(UUID grupoId);

    /** El puntaje de reputacion. Lo dice {@code transparencia}. */
    Reputacion reputacion(UUID usuarioId);

    /** Si esta en la lista de restriccion, y con cuanto sale. Lo dice {@code garantia}. */
    Restriccion restriccion(UUID usuarioId);

    /** Si el destino pidio no recibir mas. Lo dice {@code notificaciones}. */
    boolean contactoSuprimido(String identificador, String categoria);

    /** El enlace de un solo uso de una invitacion. Lo emite {@code identidad}. */
    UUID tokenDeInvitacion(String canal, String destinoEnmascarado);

    /** Si ese telefono ya tiene cuenta. Lo dice {@code identidad}. */
    java.util.Optional<UUID> usuarioDelTelefono(String telefonoE164);

    /**
     * Como viene de pagos alguien.
     *
     * <p>Quien no tiene obligaciones esta al dia: no deber nada porque todavia no se le
     * pidio nada no es estar en mora.
     */
    record EstadoDePagos(
            boolean alDia,
            BigDecimal totalAportado,
            BigDecimal deudaVigente,
            BigDecimal porAportar,
            int obligacionesAbiertas,
            String moneda) {}

    /** El puntaje, y si hay historial detras. Sin historial NO es puntaje cero. */
    record Reputacion(boolean tieneHistorial, BigDecimal puntaje) {}

    record Restriccion(boolean vigente, BigDecimal montoQueLaLevanta) {}
}
