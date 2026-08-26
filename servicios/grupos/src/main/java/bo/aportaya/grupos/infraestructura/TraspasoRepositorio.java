package bo.aportaya.grupos.infraestructura;

import static bo.aportaya.grupos.generado.Tables.CUPO;
import static bo.aportaya.grupos.generado.Tables.PARTICIPANTE;
import static bo.aportaya.grupos.generado.Tables.TRASPASO_CUPO;
import static bo.aportaya.grupos.generado.Tables.TURNO;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

/** Traspaso del cupo entre participantes. */
@Component
public class TraspasoRepositorio {

    public Optional<EstadoDelCupo> estadoDelCupo(DSLContext dsl, UUID cupoId) {
        Record fila = dsl.select(CUPO.ESTADO, CUPO.PARTICIPANTE_ID, CUPO.GRUPO_ID)
                .from(CUPO)
                .where(CUPO.ID.eq(cupoId))
                .fetchOne();
        if (fila == null) {
            return Optional.empty();
        }
        boolean cobrado = dsl.fetchExists(
                dsl.selectFrom(TURNO).where(TURNO.CUPO_ID.eq(cupoId)).and(TURNO.ESTADO.eq("COBRADO")));
        return Optional.of(new EstadoDelCupo(
                fila.get(CUPO.ESTADO),
                Optional.ofNullable(fila.get(CUPO.PARTICIPANTE_ID)),
                fila.get(CUPO.GRUPO_ID),
                cobrado));
    }

    public UUID registrar(
            DSLContext dsl,
            UUID cupoId,
            UUID origen,
            UUID destino,
            String motivo,
            boolean derechoDeCobro,
            Optional<UUID> acuerdoId,
            OffsetDateTime ahora) {
        return dsl.insertInto(TRASPASO_CUPO)
                .set(TRASPASO_CUPO.CUPO_ID, cupoId)
                .set(TRASPASO_CUPO.PARTICIPANTE_ORIGEN_ID, origen)
                .set(TRASPASO_CUPO.PARTICIPANTE_DESTINO_ID, destino)
                .set(TRASPASO_CUPO.MOTIVO, motivo)
                // Cero, y es una afirmacion: la deuda NO viaja con el cupo. Las
                // obligaciones vencidas se quedan con quien las genero (R-GRP-11).
                // Sin esto, un cupo seria una forma de venderle a alguien la deuda
                // de otro. La columna es numeric —el MONTO traspasado—, y por eso
                // se puede decir «cero» en vez de solo «no».
                .set(TRASPASO_CUPO.DEUDA_TRANSFERIDA, java.math.BigDecimal.ZERO)
                .set(TRASPASO_CUPO.DERECHO_COBRO_TRANSFERIDO, derechoDeCobro)
                .set(TRASPASO_CUPO.APROBADO_POR_ACUERDO_ID, acuerdoId.orElse(null))
                .set(TRASPASO_CUPO.FECHA, ahora)
                .returning(TRASPASO_CUPO.ID)
                .fetchOne(TRASPASO_CUPO.ID);
    }

    /**
     * El cupo cambia de manos; el TURNO no se toca.
     *
     * <p>La posicion en el calendario es del cupo, no de la persona: si se moviera,
     * traspasar seria una forma de adelantar el propio turno.
     */
    public void traspasar(
            DSLContext dsl, UUID cupoId, UUID entrante, UUID saliente, String motivo, OffsetDateTime ahora) {
        dsl.update(CUPO)
                .set(CUPO.PARTICIPANTE_ID, entrante)
                .where(CUPO.ID.eq(cupoId))
                .execute();

        dsl.update(PARTICIPANTE)
                .set(PARTICIPANTE.ESTADO, "RETIRADO")
                .set(PARTICIPANTE.FECHA_SALIDA, ahora)
                .set(PARTICIPANTE.MOTIVO_SALIDA, motivo)
                .where(PARTICIPANTE.ID.eq(saliente))
                .execute();

        dsl.update(PARTICIPANTE)
                .set(PARTICIPANTE.ESTADO, "ACTIVO")
                .where(PARTICIPANTE.ID.eq(entrante))
                .execute();
    }

    public record EstadoDelCupo(String estado, Optional<UUID> participanteId, UUID grupoId, boolean turnoCobrado) {}
}
