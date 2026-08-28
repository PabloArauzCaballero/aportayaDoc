package bo.aportaya.transparencia.infraestructura;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code insignia_logro} e {@code insignia_otorgada}.
 *
 * <p>**Nada se borra.** Una insignia revocada conserva su fila con el motivo
 * (R-REP-05): borrar el reconocimiento seria borrar la razon por la que se dio, y
 * dejaria a la persona sin poder saber por que perdio algo que tenia.
 */
@Component
public class InsigniaRepositorio {

    public Optional<Insignia> insigniaPorCodigo(DSLContext dsl, String codigo) {
        return dsl.select(DSL.field("id", UUID.class), DSL.field("criterio", String.class))
                .from(DSL.table(DSL.name("transparencia", "insignia_logro")))
                .where(DSL.field("codigo", String.class).eq(codigo))
                .fetchOptional(f -> new Insignia(f.get("id", UUID.class), f.get("criterio", String.class)));
    }

    public Optional<Otorgada> otorgadaA(DSLContext dsl, UUID usuarioId, UUID insigniaId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("otorgada_en", OffsetDateTime.class),
                        DSL.field("revocada_en", OffsetDateTime.class))
                .from(DSL.table(DSL.name("transparencia", "insignia_otorgada")))
                .where(DSL.field("usuario_id", UUID.class)
                        .eq(usuarioId)
                        .and(DSL.field("insignia_id", UUID.class).eq(insigniaId)))
                .fetchOptional(f -> new Otorgada(
                        f.get("id", UUID.class),
                        f.get("otorgada_en", OffsetDateTime.class),
                        f.get("revocada_en", OffsetDateTime.class)));
    }

    public UUID otorgar(DSLContext dsl, UUID usuarioId, UUID insigniaId, OffsetDateTime ahora) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("transparencia", "insignia_otorgada")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("insignia_id", UUID.class), insigniaId)
                .set(DSL.field("otorgada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /** La revocacion **no borra** la insignia (R-REP-05): la marca con su motivo. */
    public boolean revocar(DSLContext dsl, UUID id, String motivo, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("transparencia", "insignia_otorgada")))
                        .set(DSL.field("revocada_en", OffsetDateTime.class), ahora)
                        .set(DSL.field("motivo_revocacion", String.class), motivo)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("revocada_en", OffsetDateTime.class)
                                        .isNull()))
                        .execute()
                == 1;
    }

    public record Insignia(UUID id, String criterio) {}

    public record Otorgada(UUID id, OffsetDateTime otorgadaEn, OffsetDateTime revocadaEn) {}
}
