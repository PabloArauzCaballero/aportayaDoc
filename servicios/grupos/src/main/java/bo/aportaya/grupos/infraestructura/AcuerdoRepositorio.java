package bo.aportaya.grupos.infraestructura;

import static bo.aportaya.grupos.generado.Tables.ACUERDO;
import static bo.aportaya.grupos.generado.Tables.CUPO;
import static bo.aportaya.grupos.generado.Tables.VOTO_PARTICIPANTE;

import bo.aportaya.grupos.dominio.ComputoDeVotacion.Sentido;
import bo.aportaya.grupos.dominio.ComputoDeVotacion.VotoPonderado;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

/** Acuerdos, votos y el peso de cada participante. */
@Component
public class AcuerdoRepositorio {

    public boolean hayAcuerdoAbierto(DSLContext dsl, UUID grupoId, String tipo, Optional<UUID> afectado) {
        var condicion = ACUERDO.GRUPO_ID.eq(grupoId).and(ACUERDO.TIPO.eq(tipo)).and(ACUERDO.ESTADO.eq("ABIERTO"));
        if (afectado.isPresent()) {
            condicion = condicion.and(ACUERDO.REFERENCIA_AFECTADA_ID.eq(afectado.get()));
        }
        return dsl.fetchExists(dsl.selectFrom(ACUERDO).where(condicion));
    }

    public UUID proponer(
            DSLContext dsl,
            UUID grupoId,
            String tipo,
            String descripcion,
            UUID propuestoPor,
            BigDecimal quorum,
            Optional<UUID> afectado,
            OffsetDateTime abiertoEn,
            OffsetDateTime cierraEn) {
        return dsl.insertInto(ACUERDO)
                .set(ACUERDO.GRUPO_ID, grupoId)
                .set(ACUERDO.TIPO, tipo)
                .set(ACUERDO.DESCRIPCION, descripcion)
                .set(ACUERDO.PROPUESTO_POR, propuestoPor)
                .set(ACUERDO.QUORUM_REQUERIDO, quorum)
                .set(ACUERDO.VOTOS_A_FAVOR, (short) 0)
                .set(ACUERDO.VOTOS_EN_CONTRA, (short) 0)
                .set(ACUERDO.ABSTENCIONES, (short) 0)
                .set(ACUERDO.ESTADO, "ABIERTO")
                .set(ACUERDO.REFERENCIA_AFECTADA_ID, afectado.orElse(null))
                .set(ACUERDO.ABIERTO_EN, abiertoEn)
                .set(ACUERDO.CIERRA_EN, cierraEn)
                .returning(ACUERDO.ID)
                .fetchOne(ACUERDO.ID);
    }

    public Optional<Acuerdo> porId(DSLContext dsl, UUID acuerdoId) {
        Record fila = dsl.select(
                        ACUERDO.GRUPO_ID,
                        ACUERDO.TIPO,
                        ACUERDO.ESTADO,
                        ACUERDO.QUORUM_REQUERIDO,
                        ACUERDO.REFERENCIA_AFECTADA_ID,
                        ACUERDO.CIERRA_EN)
                .from(ACUERDO)
                .where(ACUERDO.ID.eq(acuerdoId))
                .fetchOne();
        return fila == null
                ? Optional.empty()
                : Optional.of(new Acuerdo(
                        fila.get(ACUERDO.GRUPO_ID),
                        fila.get(ACUERDO.TIPO),
                        fila.get(ACUERDO.ESTADO),
                        fila.get(ACUERDO.QUORUM_REQUERIDO),
                        Optional.ofNullable(fila.get(ACUERDO.REFERENCIA_AFECTADA_ID)),
                        fila.get(ACUERDO.CIERRA_EN)));
    }

    /**
     * El peso del participante es la suma de las fracciones de sus cupos. La
     * ponderacion se guarda en el voto y no se recalcula despues.
     */
    public BigDecimal pesoDe(DSLContext dsl, UUID participanteId) {
        BigDecimal peso = dsl.select(org.jooq.impl.DSL.sum(CUPO.FRACCION))
                .from(CUPO)
                .where(CUPO.PARTICIPANTE_ID.eq(participanteId))
                .and(CUPO.ESTADO.eq("OCUPADO"))
                .fetchOne(0, BigDecimal.class);
        return peso == null ? BigDecimal.ZERO : peso;
    }

    public BigDecimal pesoTotalDelGrupo(DSLContext dsl, UUID grupoId) {
        BigDecimal peso = dsl.select(org.jooq.impl.DSL.sum(CUPO.FRACCION))
                .from(CUPO)
                .where(CUPO.GRUPO_ID.eq(grupoId))
                .and(CUPO.ESTADO.eq("OCUPADO"))
                .fetchOne(0, BigDecimal.class);
        return peso == null ? BigDecimal.ZERO : peso;
    }

    /** La unicidad {@code (acuerdo, participante)} es lo que impide votar dos veces. */
    public void votar(
            DSLContext dsl,
            UUID acuerdoId,
            UUID participanteId,
            String sentido,
            BigDecimal peso,
            OffsetDateTime ahora) {
        dsl.insertInto(VOTO_PARTICIPANTE)
                .set(VOTO_PARTICIPANTE.ACUERDO_ID, acuerdoId)
                .set(VOTO_PARTICIPANTE.PARTICIPANTE_ID, participanteId)
                .set(VOTO_PARTICIPANTE.SENTIDO, sentido)
                .set(VOTO_PARTICIPANTE.PESO, peso)
                .set(VOTO_PARTICIPANTE.EMITIDO_EN, ahora)
                .execute();
    }

    public List<VotoPonderado> votosDe(DSLContext dsl, UUID acuerdoId) {
        return dsl.select(VOTO_PARTICIPANTE.SENTIDO, VOTO_PARTICIPANTE.PESO)
                .from(VOTO_PARTICIPANTE)
                .where(VOTO_PARTICIPANTE.ACUERDO_ID.eq(acuerdoId))
                .fetch()
                .map(fila -> new VotoPonderado(
                        Sentido.valueOf(fila.get(VOTO_PARTICIPANTE.SENTIDO)), fila.get(VOTO_PARTICIPANTE.PESO)));
    }

    public void resolver(DSLContext dsl, UUID acuerdoId, String estado, ComputoResumen resumen, OffsetDateTime ahora) {
        dsl.update(ACUERDO)
                .set(ACUERDO.ESTADO, estado)
                .set(ACUERDO.VOTOS_A_FAVOR, resumen.aFavor())
                .set(ACUERDO.VOTOS_EN_CONTRA, resumen.enContra())
                .set(ACUERDO.ABSTENCIONES, resumen.abstenciones())
                .set(ACUERDO.RESUELTO_EN, ahora)
                .where(ACUERDO.ID.eq(acuerdoId))
                .execute();
    }

    public record Acuerdo(
            UUID grupoId,
            String tipo,
            String estado,
            BigDecimal quorum,
            Optional<UUID> afectado,
            OffsetDateTime cierraEn) {}

    public record ComputoResumen(short aFavor, short enContra, short abstenciones) {}
}
