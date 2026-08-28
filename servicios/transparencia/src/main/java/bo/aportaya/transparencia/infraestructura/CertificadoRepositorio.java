package bo.aportaya.transparencia.infraestructura;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code certificado_reputacion}.
 *
 * <p>Una foto, un certificado ({@code uq_certificado_reputacion_snapshot_id}). Dos
 * codigos para el mismo documento harian que revocar uno dejara el otro vivo, y el
 * titular creeria haber cerrado algo que sigue abierto.
 */
@Component
public class CertificadoRepositorio {

    public UUID emitirCertificado(
            DSLContext dsl,
            UUID usuarioId,
            UUID snapshotId,
            String codigoVerificacion,
            String hashContenido,
            String firma,
            String urlPublica,
            OffsetDateTime ahora,
            OffsetDateTime expira) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("transparencia", "certificado_reputacion")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("snapshot_id", UUID.class), snapshotId)
                .set(DSL.field("codigo_verificacion", String.class), codigoVerificacion)
                .set(DSL.field("hash_contenido", String.class), hashContenido)
                .set(DSL.field("firma_digital", String.class), firma)
                .set(DSL.field("url_publica", String.class), urlPublica)
                .set(DSL.field("emitido_en", OffsetDateTime.class), ahora)
                .set(DSL.field("expira_en", OffsetDateTime.class), expira)
                .execute();
        return id;
    }

    public Optional<Certificado> certificadoPorCodigo(DSLContext dsl, String codigo) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("snapshot_id", UUID.class),
                        DSL.field("hash_contenido", String.class),
                        DSL.field("emitido_en", OffsetDateTime.class),
                        DSL.field("expira_en", OffsetDateTime.class),
                        DSL.field("revocado_en", OffsetDateTime.class),
                        DSL.field("url_publica", String.class))
                .from(DSL.table(DSL.name("transparencia", "certificado_reputacion")))
                .where(DSL.field("codigo_verificacion", String.class).eq(codigo))
                .fetchOptional(f -> new Certificado(
                        f.get("id", UUID.class),
                        f.get("usuario_id", UUID.class),
                        f.get("snapshot_id", UUID.class),
                        f.get("hash_contenido", String.class),
                        f.get("emitido_en", OffsetDateTime.class),
                        f.get("expira_en", OffsetDateTime.class),
                        f.get("revocado_en", OffsetDateTime.class),
                        codigo,
                        f.get("url_publica", String.class)));
    }

    /** El certificado emitido sobre esa foto, si ya existe. */
    public Optional<Certificado> certificadoPorSnapshot(DSLContext dsl, UUID snapshotId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("snapshot_id", UUID.class),
                        DSL.field("hash_contenido", String.class),
                        DSL.field("codigo_verificacion", String.class),
                        DSL.field("url_publica", String.class),
                        DSL.field("emitido_en", OffsetDateTime.class),
                        DSL.field("expira_en", OffsetDateTime.class),
                        DSL.field("revocado_en", OffsetDateTime.class))
                .from(DSL.table(DSL.name("transparencia", "certificado_reputacion")))
                .where(DSL.field("snapshot_id", UUID.class).eq(snapshotId))
                .fetchOptional(f -> new Certificado(
                        f.get("id", UUID.class),
                        f.get("usuario_id", UUID.class),
                        f.get("snapshot_id", UUID.class),
                        f.get("hash_contenido", String.class),
                        f.get("emitido_en", OffsetDateTime.class),
                        f.get("expira_en", OffsetDateTime.class),
                        f.get("revocado_en", OffsetDateTime.class),
                        f.get("codigo_verificacion", String.class),
                        f.get("url_publica", String.class)));
    }

    public boolean revocarCertificado(DSLContext dsl, UUID id, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("transparencia", "certificado_reputacion")))
                        .set(DSL.field("revocado_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("revocado_en", OffsetDateTime.class)
                                        .isNull()))
                        .execute()
                == 1;
    }

    public record Certificado(
            UUID id,
            UUID usuarioId,
            UUID snapshotId,
            String hashContenido,
            OffsetDateTime emitidoEn,
            OffsetDateTime expiraEn,
            OffsetDateTime revocadoEn,
            String codigoVerificacion,
            String urlPublica) {}
}
