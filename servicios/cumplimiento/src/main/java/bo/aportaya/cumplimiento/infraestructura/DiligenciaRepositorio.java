package bo.aportaya.cumplimiento.infraestructura;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code cumplimiento.debida_diligencia}.
 *
 * <p>El trigger {@code tg_ddd_pep} vigila esta tabla en cada INSERT y UPDATE: si la
 * persona es PEP exige {@code tipo = 'REFORZADA'} y, para completarla, dos revisores
 * distintos. No se replica esa regla aca: se deja que la base la aplique y se
 * traduce su rechazo. Duplicarla en Java crearia dos verdades que pueden divergir.
 */
@Component
public class DiligenciaRepositorio {

    public Optional<Diligencia> vigenteDe(DSLContext dsl, UUID usuarioId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("tipo", String.class),
                        DSL.field("estado", String.class),
                        DSL.field("vence_en", OffsetDateTime.class))
                .from(DSL.table(DSL.name("cumplimiento", "debida_diligencia")))
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .orderBy(DSL.field("iniciada_en").desc())
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Diligencia(
                        f.get("id", UUID.class),
                        f.get("tipo", String.class),
                        f.get("estado", String.class),
                        f.get("vence_en", OffsetDateTime.class)));
    }

    public UUID abrir(
            DSLContext dsl,
            UUID usuarioId,
            String tipo,
            String estado,
            String documentosRequeridos,
            String documentosRecibidos,
            OffsetDateTime iniciadaEn) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "debida_diligencia")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("documentos_requeridos", JSONB.class), JSONB.valueOf(documentosRequeridos))
                .set(DSL.field("documentos_recibidos", JSONB.class), JSONB.valueOf(documentosRecibidos))
                .set(DSL.field("iniciada_en", OffsetDateTime.class), iniciadaEn)
                .execute();
        return id;
    }

    /** Eleva el tipo de una diligencia existente. El trigger valida el resultado. */
    public void elevarTipo(DSLContext dsl, UUID usuarioId, String tipo) {
        dsl.update(DSL.table(DSL.name("cumplimiento", "debida_diligencia")))
                .set(DSL.field("tipo", String.class), tipo)
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .execute();
    }

    /**
     * Completa la diligencia con sus dos firmas.
     *
     * <p>Las dos van juntas en el mismo UPDATE a proposito: el trigger mira la fila
     * resultante, y escribirlas en dos pasos dejaria un instante con {@code
     * estado='COMPLETA'} y una sola firma, que es justo lo que R-UIF-10 prohibe.
     */
    public void completar(
            DSLContext dsl,
            UUID diligenciaId,
            UUID aprobadaPor,
            Optional<UUID> segundaRevision,
            OffsetDateTime momento) {
        dsl.update(DSL.table(DSL.name("cumplimiento", "debida_diligencia")))
                .set(DSL.field("estado", String.class), "COMPLETA")
                .set(DSL.field("aprobada_por", UUID.class), aprobadaPor)
                .set(DSL.field("segunda_revision_por", UUID.class), segundaRevision.orElse(null))
                .set(DSL.field("completada_en", OffsetDateTime.class), momento)
                .where(DSL.field("id", UUID.class).eq(diligenciaId))
                .execute();
    }

    public record Diligencia(UUID id, String tipo, String estado, OffsetDateTime venceEn) {}
}
