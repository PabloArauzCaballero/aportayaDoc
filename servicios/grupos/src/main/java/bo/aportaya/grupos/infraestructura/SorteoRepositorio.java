package bo.aportaya.grupos.infraestructura;

import static bo.aportaya.grupos.generado.Tables.CUPO;
import static bo.aportaya.grupos.generado.Tables.SORTEO_TURNOS;
import static bo.aportaya.grupos.generado.Tables.TURNO;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

/** El sorteo y sus turnos. */
@Component
public class SorteoRepositorio {

    /** Los cupos ocupados, ordenados por numero: el punto de partida del sorteo. */
    public List<UUID> cuposPorNumero(DSLContext dsl, UUID grupoId) {
        return dsl.select(CUPO.ID)
                .from(CUPO)
                .where(CUPO.GRUPO_ID.eq(grupoId))
                .and(CUPO.ESTADO.eq("OCUPADO"))
                .orderBy(CUPO.NUMERO.asc())
                .fetch(CUPO.ID);
    }

    /** Un grupo a medio armar no se sortea: quedan cupos libres. */
    public boolean estaConformado(DSLContext dsl, UUID grupoId) {
        return dsl.fetchCount(CUPO, CUPO.GRUPO_ID.eq(grupoId).and(CUPO.ESTADO.ne("OCUPADO"))) == 0
                && dsl.fetchCount(CUPO, CUPO.GRUPO_ID.eq(grupoId)) > 0;
    }

    /** Cada participante con su reglamento aceptado, o no hay sorteo. */
    public boolean todosAceptaronElReglamento(DSLContext dsl, UUID grupoId) {
        Integer sinAceptar = (Integer) dsl.fetchOne(
                        """
                        SELECT count(*)::int FROM grupos.cupo c
                         WHERE c.grupo_id = ? AND c.participante_id IS NOT NULL
                           AND NOT EXISTS (
                             SELECT 1 FROM grupos.aceptacion_reglamento a
                              WHERE a.participante_id = c.participante_id)
                        """,
                        grupoId)
                .get(0);
        return sinAceptar != null && sinAceptar == 0;
    }

    public boolean yaHuboSorteo(DSLContext dsl, UUID grupoId) {
        return dsl.fetchExists(dsl.selectFrom(SORTEO_TURNOS)
                .where(SORTEO_TURNOS.GRUPO_ID.eq(grupoId))
                .and(SORTEO_TURNOS.ANULADO_EN.isNull()));
    }

    /** La fila es append-only: el hash publicado no admite {@code UPDATE}. */
    public UUID comprometer(
            DSLContext dsl,
            UUID grupoId,
            String hash,
            String algoritmo,
            UUID ejecutadoPor,
            OffsetDateTime ahora,
            Optional<OffsetDateTime> fechaPrevista) {
        return dsl.insertInto(SORTEO_TURNOS)
                .set(SORTEO_TURNOS.GRUPO_ID, grupoId)
                .set(SORTEO_TURNOS.ALGORITMO, algoritmo)
                .set(SORTEO_TURNOS.ESTADO, "COMPROMETIDO")
                .set(SORTEO_TURNOS.HASH_SEMILLA_PREVIO, hash)
                .set(SORTEO_TURNOS.FECHA_COMPROMISO, ahora)
                .set(SORTEO_TURNOS.FECHA_REVELADO_PREVISTA, fechaPrevista.orElse(null))
                .set(SORTEO_TURNOS.SEMILLA_PUBLICA, "")
                .set(SORTEO_TURNOS.RESULTADO, org.jooq.JSONB.valueOf("[]"))
                .set(SORTEO_TURNOS.EJECUTADO_POR, ejecutadoPor)
                .set(SORTEO_TURNOS.FECHA_EJECUCION, ahora)
                .returning(SORTEO_TURNOS.ID)
                .fetchOne(SORTEO_TURNOS.ID);
    }

    public Optional<Compromiso> compromisoDe(DSLContext dsl, UUID sorteoId) {
        Record fila = dsl.select(SORTEO_TURNOS.HASH_SEMILLA_PREVIO, SORTEO_TURNOS.ESTADO, SORTEO_TURNOS.GRUPO_ID)
                .from(SORTEO_TURNOS)
                .where(SORTEO_TURNOS.ID.eq(sorteoId))
                .fetchOne();
        return fila == null
                ? Optional.empty()
                : Optional.of(new Compromiso(
                        fila.get(SORTEO_TURNOS.HASH_SEMILLA_PREVIO),
                        fila.get(SORTEO_TURNOS.ESTADO),
                        fila.get(SORTEO_TURNOS.GRUPO_ID)));
    }

    public void revelar(DSLContext dsl, UUID sorteoId, String semilla, String resultadoJson, OffsetDateTime ahora) {
        dsl.update(SORTEO_TURNOS)
                .set(SORTEO_TURNOS.ESTADO, "REVELADO")
                .set(SORTEO_TURNOS.SEMILLA_SERVIDOR, semilla)
                .set(SORTEO_TURNOS.SEMILLA_PUBLICA, semilla)
                .set(SORTEO_TURNOS.RESULTADO, org.jooq.JSONB.valueOf(resultadoJson))
                .set(SORTEO_TURNOS.FECHA_EJECUCION, ahora)
                .where(SORTEO_TURNOS.ID.eq(sorteoId))
                .execute();
    }

    public void anular(DSLContext dsl, UUID sorteoId, OffsetDateTime ahora) {
        dsl.update(SORTEO_TURNOS)
                .set(SORTEO_TURNOS.ESTADO, "ANULADO")
                .set(SORTEO_TURNOS.ANULADO_EN, ahora)
                .where(SORTEO_TURNOS.ID.eq(sorteoId))
                .execute();
    }

    /**
     * Un turno por periodo, y en el orden del sorteo.
     *
     * <p>{@code uq_turno_periodo} es unico sobre {@code periodo_id} a secas: cada
     * periodo tiene UN beneficiario, que es lo que hace que un pasanaku sea un
     * pasanaku. Por eso recibe tantos periodos como cupos.
     */
    public void crearTurnos(
            DSLContext dsl,
            UUID grupoId,
            List<UUID> periodosEnOrden,
            List<UUID> cuposEnOrden,
            BigDecimal montoEstimado) {
        if (periodosEnOrden.size() != cuposEnOrden.size()) {
            throw new IllegalArgumentException("Cada cupo necesita su periodo: %d periodos para %d cupos"
                    .formatted(periodosEnOrden.size(), cuposEnOrden.size()));
        }
        for (int i = 0; i < cuposEnOrden.size(); i++) {
            dsl.insertInto(TURNO)
                    .set(TURNO.GRUPO_ID, grupoId)
                    .set(TURNO.PERIODO_ID, periodosEnOrden.get(i))
                    .set(TURNO.CUPO_ID, cuposEnOrden.get(i))
                    .set(TURNO.ORDEN_ASIGNADO, (short) (i + 1))
                    .set(TURNO.ESTADO, "PROGRAMADO")
                    .set(TURNO.CRITERIO_ASIGNACION, "SORTEO")
                    .set(TURNO.MONTO_ESTIMADO_COBRO, montoEstimado)
                    .execute();
        }
    }

    public int turnosDe(DSLContext dsl, UUID grupoId) {
        return dsl.fetchCount(TURNO, TURNO.GRUPO_ID.eq(grupoId));
    }

    public record Compromiso(String hash, String estado, UUID grupoId) {}
}
