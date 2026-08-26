package bo.aportaya.grupos.infraestructura;

import static bo.aportaya.grupos.generado.Tables.SOLICITUD_PERMUTA;
import static bo.aportaya.grupos.generado.Tables.TURNO;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

/** Solicitudes de permuta y el intercambio de los dos turnos. */
@Component
public class PermutaRepositorio {

    public Optional<Turno> turno(DSLContext dsl, UUID turnoId) {
        Record fila = dsl.select(TURNO.ID, TURNO.ESTADO, TURNO.ORDEN_ASIGNADO, TURNO.PERIODO_ID, TURNO.GRUPO_ID)
                .from(TURNO)
                .where(TURNO.ID.eq(turnoId))
                .fetchOne();
        return fila == null
                ? Optional.empty()
                : Optional.of(new Turno(
                        fila.get(TURNO.ID),
                        fila.get(TURNO.ESTADO),
                        fila.get(TURNO.ORDEN_ASIGNADO),
                        fila.get(TURNO.PERIODO_ID),
                        fila.get(TURNO.GRUPO_ID)));
    }

    public UUID solicitar(
            DSLContext dsl,
            UUID origen,
            UUID destino,
            UUID solicitante,
            UUID contraparte,
            String motivo,
            OffsetDateTime ahora) {
        return dsl.insertInto(SOLICITUD_PERMUTA)
                .set(SOLICITUD_PERMUTA.TURNO_ORIGEN_ID, origen)
                .set(SOLICITUD_PERMUTA.TURNO_DESTINO_ID, destino)
                .set(SOLICITUD_PERMUTA.SOLICITANTE_ID, solicitante)
                .set(SOLICITUD_PERMUTA.CONTRAPARTE_ID, contraparte)
                .set(SOLICITUD_PERMUTA.MOTIVO, motivo)
                .set(SOLICITUD_PERMUTA.ESTADO, "PENDIENTE")
                .set(SOLICITUD_PERMUTA.APROBADA_POR_ORGANIZADOR, false)
                .set(SOLICITUD_PERMUTA.FECHA_SOLICITUD, ahora)
                .returning(SOLICITUD_PERMUTA.ID)
                .fetchOne(SOLICITUD_PERMUTA.ID);
    }

    public void marcar(DSLContext dsl, UUID solicitudId, String estado, OffsetDateTime ahora) {
        dsl.update(SOLICITUD_PERMUTA)
                .set(SOLICITUD_PERMUTA.ESTADO, estado)
                .set(SOLICITUD_PERMUTA.FECHA_EJECUCION, "EJECUTADA".equals(estado) ? ahora : null)
                .where(SOLICITUD_PERMUTA.ID.eq(solicitudId))
                .execute();
    }

    /**
     * Intercambia el CUPO entre los dos turnos, y deja cada uno apuntando al otro.
     *
     * <p><b>Desviacion declarada del caso de uso.</b> CU-62 dice «se intercambian
     * {@code orden_asignado} y {@code periodo_id}». El modelo no lo permite tal cual:
     * {@code periodo_id} es {@code NOT NULL} y {@code uq_turno_periodo} es unico
     * sobre el, asi que no hay forma de aparcar uno de los dos mientras se mueve el
     * otro — y la unicidad de {@code (grupo, orden)} choca a mitad de camino.
     *
     * <p>Intercambiar el cupo produce EL MISMO resultado observable —quien iba a
     * recibir en el periodo 1 ahora recibe en el 3— sin pelear contra dos indices
     * unicos. El turno queda atado a su periodo, que es lo que el modelo dice al
     * hacer {@code periodo_id} unico: un turno ES la asignacion de un cupo a un
     * periodo.
     */
    public void intercambiar(DSLContext dsl, Turno origen, Turno destino) {
        UUID cupoDelOrigen = cupoDe(dsl, origen.id());
        UUID cupoDelDestino = cupoDe(dsl, destino.id());

        aplicar(dsl, origen.id(), cupoDelDestino, destino.id());
        aplicar(dsl, destino.id(), cupoDelOrigen, origen.id());
    }

    private UUID cupoDe(DSLContext dsl, UUID turnoId) {
        return dsl.select(TURNO.CUPO_ID).from(TURNO).where(TURNO.ID.eq(turnoId)).fetchOne(TURNO.CUPO_ID);
    }

    private void aplicar(DSLContext dsl, UUID turnoId, UUID cupoId, UUID permutadoCon) {
        dsl.update(TURNO)
                .set(TURNO.CUPO_ID, cupoId)
                .set(TURNO.PERMUTADO_CON_TURNO_ID, permutadoCon)
                .set(TURNO.CRITERIO_ASIGNACION, "PERMUTA")
                .where(TURNO.ID.eq(turnoId))
                .execute();
    }

    public record Turno(UUID id, String estado, short orden, UUID periodoId, UUID grupoId) {}
}
