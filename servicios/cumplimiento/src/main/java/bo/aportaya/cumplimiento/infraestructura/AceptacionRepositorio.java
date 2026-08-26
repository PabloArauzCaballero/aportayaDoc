package bo.aportaya.cumplimiento.infraestructura;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** Escribe {@code cumplimiento.aceptacion_contrato}: append-only, nunca UPDATE. */
@Component
public class AceptacionRepositorio {

    /**
     * Registra la aceptacion.
     *
     * <p>{@code ip} se castea a {@code inet} explicitamente: la columna es inet y jOOQ
     * no adivina el tipo desde un {@code String}. Se usa {@code columns()/values()} en
     * vez de {@code set()} para que no quede ambiguo cual valor va a cual columna.
     */
    public UUID registrar(
            DSLContext dsl,
            UUID contratoId,
            UUID usuarioId,
            int version,
            Optional<String> ip,
            Optional<UUID> dispositivoId,
            Optional<UUID> tokenFirmaId,
            String hashEvidencia,
            OffsetDateTime momento) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(
                        DSL.table(DSL.name("cumplimiento", "aceptacion_contrato")),
                        DSL.field("id", UUID.class),
                        DSL.field("contrato_adhesion_id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("dispositivo_id", UUID.class),
                        DSL.field("token_firma_id", UUID.class),
                        DSL.field("version_aceptada", Short.class),
                        DSL.field("ip", Object.class),
                        DSL.field("hash_evidencia", String.class),
                        DSL.field("aceptado_en", OffsetDateTime.class))
                .values(
                        DSL.val(id),
                        DSL.val(contratoId),
                        DSL.val(usuarioId),
                        DSL.val(dispositivoId.orElse(null)),
                        DSL.val(tokenFirmaId.orElse(null)),
                        DSL.val((short) version),
                        ip.map(valor -> DSL.field("cast({0} as inet)", Object.class, DSL.val(valor)))
                                .orElse(DSL.field("cast(NULL as inet)", Object.class)),
                        DSL.val(hashEvidencia),
                        DSL.val(momento))
                .execute();
        return id;
    }

    /** La aceptacion mas reciente de ese contrato por esa persona, si la hay. */
    public Optional<Aceptacion> ultimaDe(DSLContext dsl, UUID usuarioId, UUID contratoId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("version_aceptada", Short.class),
                        DSL.field("hash_evidencia", String.class),
                        DSL.field("aceptado_en", OffsetDateTime.class))
                .from(DSL.table(DSL.name("cumplimiento", "aceptacion_contrato")))
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .and(DSL.field("contrato_adhesion_id", UUID.class).eq(contratoId))
                .orderBy(
                        DSL.field("version_aceptada").desc(),
                        DSL.field("aceptado_en").desc())
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Aceptacion(
                        f.get("id", UUID.class),
                        f.get("version_aceptada", Short.class),
                        f.get("hash_evidencia", String.class),
                        f.get("aceptado_en", OffsetDateTime.class)));
    }

    public record Aceptacion(UUID id, short version, String hashEvidencia, OffsetDateTime aceptadoEn) {}
}
