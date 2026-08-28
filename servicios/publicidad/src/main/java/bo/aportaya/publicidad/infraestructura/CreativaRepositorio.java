package bo.aportaya.publicidad.infraestructura;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code pieza_creativa} y {@code revision_creativa}. */
@Component
public class CreativaRepositorio {

    private static final String ESQUEMA = "publicidad";

    public UUID subirPieza(
            DSLContext dsl,
            UUID anuncianteId,
            String titulo,
            String texto,
            String urlRecurso,
            String tipoRecurso,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name(ESQUEMA, "pieza_creativa")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("anunciante_id", UUID.class), anuncianteId)
                .set(DSL.field("titulo", String.class), titulo)
                .set(DSL.field("texto", String.class), texto)
                .set(DSL.field("url_recurso", String.class), urlRecurso)
                .set(DSL.field("tipo_recurso", String.class), tipoRecurso)
                .set(DSL.field("estado_moderacion", String.class), "PENDIENTE")
                .set(DSL.field("creada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /**
     * La pieza, con bloqueo.
     *
     * <p>{@code FOR UPDATE} porque «ya fue revisada» se decide leyendo su estado, y no
     * hay unico en {@code revision_creativa} que impida dos revisiones simultaneas.
     */
    public Optional<Pieza> bloqueada(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("anunciante_id", UUID.class),
                        DSL.field("titulo", String.class),
                        DSL.field("estado_moderacion", String.class))
                .from(DSL.table(DSL.name(ESQUEMA, "pieza_creativa")))
                .where(DSL.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOptional(f -> new Pieza(
                        f.get("id", UUID.class),
                        f.get("anunciante_id", UUID.class),
                        f.get("titulo", String.class),
                        f.get("estado_moderacion", String.class)));
    }

    public boolean tieneRevision(DSLContext dsl, UUID piezaId) {
        return dsl.fetchExists(DSL.selectOne()
                .from(DSL.table(DSL.name(ESQUEMA, "revision_creativa")))
                .where(DSL.field("pieza_creativa_id", UUID.class).eq(piezaId)));
    }

    public UUID revisar(
            DSLContext dsl, UUID piezaId, UUID revisadaPor, String decision, String motivo, OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name(ESQUEMA, "revision_creativa")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("pieza_creativa_id", UUID.class), piezaId)
                .set(DSL.field("revisada_por", UUID.class), revisadaPor)
                .set(DSL.field("decision", String.class), decision)
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("revisada_en", OffsetDateTime.class), ahora)
                .execute();
        dsl.update(DSL.table(DSL.name(ESQUEMA, "pieza_creativa")))
                .set(DSL.field("estado_moderacion", String.class), decision)
                .where(DSL.field("id", UUID.class).eq(piezaId))
                .execute();
        return id;
    }

    /** El usuario dueno del anunciante, cuando el anunciante es un organizador. */
    public Optional<UUID> usuarioDelAnunciante(DSLContext dsl, UUID anuncianteId) {
        return dsl.select(DSL.field("o.usuario_id", UUID.class))
                .from(DSL.table(DSL.name(ESQUEMA, "anunciante")).as("a"))
                .join(DSL.table(DSL.name("organizador", "organizador")).as("o"))
                .on(DSL.field("o.id", UUID.class).eq(DSL.field("a.organizador_id", UUID.class)))
                .where(DSL.field("a.id", UUID.class).eq(anuncianteId))
                .fetchOptional(f -> f.get("o.usuario_id", UUID.class));
    }

    public record Pieza(UUID id, UUID anuncianteId, String titulo, String estadoModeracion) {}
}
