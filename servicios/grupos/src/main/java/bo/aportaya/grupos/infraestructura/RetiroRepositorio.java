package bo.aportaya.grupos.infraestructura;

import static bo.aportaya.grupos.generado.Tables.CUPO;
import static bo.aportaya.grupos.generado.Tables.PARTICIPANTE;
import static bo.aportaya.grupos.generado.Tables.SOLICITUD_RETIRO;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/** Solicitudes de retiro, salida del participante y liberacion del cupo. */
@Component
public class RetiroRepositorio {

    public Optional<String> estadoDelParticipante(DSLContext dsl, UUID participanteId) {
        return dsl.select(PARTICIPANTE.ESTADO)
                .from(PARTICIPANTE)
                .where(PARTICIPANTE.ID.eq(participanteId))
                .fetchOptional(PARTICIPANTE.ESTADO);
    }

    public UUID solicitar(
            DSLContext dsl,
            UUID participanteId,
            String motivo,
            String posicion,
            boolean requiereReemplazo,
            java.math.BigDecimal liquidacionCalculada,
            OffsetDateTime ahora) {
        return dsl.insertInto(SOLICITUD_RETIRO)
                .set(SOLICITUD_RETIRO.PARTICIPANTE_ID, participanteId)
                .set(SOLICITUD_RETIRO.MOTIVO, motivo)
                .set(SOLICITUD_RETIRO.SOLICITADO_EN, ahora)
                .set(SOLICITUD_RETIRO.ESTADO, "PENDIENTE")
                .set(SOLICITUD_RETIRO.POSICION, posicion)
                .set(SOLICITUD_RETIRO.REQUIERE_REEMPLAZO, requiereReemplazo)
                // Es el MONTO liquidado, no una bandera: el nombre enganya y el tipo no.
                .set(SOLICITUD_RETIRO.LIQUIDACION_CALCULADA, liquidacionCalculada)
                .returning(SOLICITUD_RETIRO.ID)
                .fetchOne(SOLICITUD_RETIRO.ID);
    }

    public void aprobar(DSLContext dsl, UUID solicitudId, Optional<UUID> planDePago) {
        dsl.update(SOLICITUD_RETIRO)
                .set(SOLICITUD_RETIRO.ESTADO, "APROBADA")
                .set(SOLICITUD_RETIRO.PLAN_REGULARIZACION_ID, planDePago.orElse(null))
                .where(SOLICITUD_RETIRO.ID.eq(solicitudId))
                .execute();
    }

    /** El participante sale, y su cupo queda libre para quien venga. */
    public void retirar(DSLContext dsl, UUID participanteId, String motivo, OffsetDateTime ahora) {
        dsl.update(PARTICIPANTE)
                .set(PARTICIPANTE.ESTADO, "RETIRADO")
                .set(PARTICIPANTE.FECHA_SALIDA, ahora)
                .set(PARTICIPANTE.MOTIVO_SALIDA, motivo)
                .where(PARTICIPANTE.ID.eq(participanteId))
                .execute();

        dsl.update(CUPO)
                .set(CUPO.ESTADO, "LIBRE")
                .set(CUPO.PARTICIPANTE_ID, (UUID) null)
                .set(CUPO.LIBERADO_EN, ahora)
                .where(CUPO.PARTICIPANTE_ID.eq(participanteId))
                .execute();
    }

    public String estadoDelGrupoDe(DSLContext dsl, UUID participanteId) {
        return String.valueOf(dsl.fetchOne(
                        """
                        SELECT g.estado FROM grupos.grupo g
                          JOIN grupos.participante p ON p.grupo_id = g.id
                         WHERE p.id = ?
                        """,
                        participanteId)
                .get(0));
    }
}
