package bo.aportaya.transparencia.infraestructura;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code snapshot_reputacion}: la foto congelada de un puntaje.
 *
 * <p>Existe porque el puntaje vigente se reemplaza en cada recalculo y un certificado
 * emitido hace tres meses tiene que seguir diciendo lo que decia. La foto guarda los
 * factores, no solo el numero: un certificado que dice «85» sin decir de que esta hecho
 * no se puede verificar, solo creer.
 */
@Component
public class SnapshotRepositorio {

    /** Una foto del puntaje: es lo que un certificado congela y despues se verifica. */
    public UUID tomarSnapshot(
            DSLContext dsl,
            UUID usuarioId,
            BigDecimal puntaje,
            String nivel,
            String factoresJson,
            String motivo,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("transparencia", "snapshot_reputacion")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("puntaje", BigDecimal.class), puntaje)
                .set(DSL.field("nivel_confianza", String.class), nivel)
                .set(DSL.field("fotografia_factores", JSONB.class), JSONB.valueOf(factoresJson))
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("tomado_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }
}
