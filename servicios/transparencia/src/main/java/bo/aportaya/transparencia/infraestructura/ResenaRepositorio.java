package bo.aportaya.transparencia.infraestructura;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code resena_participante}.
 *
 * <p>Una resena por autor, evaluado, grupo y dimension, y nadie se resena a si mismo:
 * las dos mitades de R-REP-06 las sostiene la base ({@code uq_resena_autor_evaluado} y
 * {@code tg_resena_convivencia}). Una resena rechazada tampoco se borra: queda con su
 * estado, porque el autor tiene derecho a saber que paso con lo que escribio.
 */
@Component
public class ResenaRepositorio {

    public Optional<UUID> resenaDe(DSLContext dsl, UUID grupoId, UUID autorId, UUID evaluadoId, String dimension) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("transparencia", "resena_participante")))
                .where(DSL.field("grupo_id", UUID.class)
                        .eq(grupoId)
                        .and(DSL.field("autor_participante_id", UUID.class).eq(autorId))
                        .and(DSL.field("evaluado_usuario_id", UUID.class).eq(evaluadoId))
                        .and(DSL.field("dimension", String.class).eq(dimension)))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID resenar(
            DSLContext dsl,
            UUID grupoId,
            UUID autorId,
            UUID evaluadoId,
            int calificacion,
            String comentario,
            String dimension,
            String estadoModeracion,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("transparencia", "resena_participante")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("grupo_id", UUID.class), grupoId)
                .set(DSL.field("autor_participante_id", UUID.class), autorId)
                .set(DSL.field("evaluado_usuario_id", UUID.class), evaluadoId)
                .set(DSL.field("calificacion", Short.class), (short) calificacion)
                .set(DSL.field("comentario", String.class), comentario)
                .set(DSL.field("dimension", String.class), dimension)
                .set(DSL.field("estado_moderacion", String.class), estadoModeracion)
                .set(DSL.field("creada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public boolean moderar(DSLContext dsl, UUID id, String estado, UUID moderadaPor) {
        return dsl.update(DSL.table(DSL.name("transparencia", "resena_participante")))
                        .set(DSL.field("estado_moderacion", String.class), estado)
                        .set(DSL.field("moderada_por", UUID.class), moderadaPor)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado_moderacion", String.class)
                                        .eq("PENDIENTE")))
                        .execute()
                == 1;
    }
}
