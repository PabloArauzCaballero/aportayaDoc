package bo.aportaya.organizador.infraestructura;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code contrato_organizador}.
 *
 * <p>Un contrato firmado **no se modifica**: se emite una version nueva (R-ORG-03).
 * Cambiarle una clausula a un documento ya firmado lo vuelve inoponible — que es
 * justo lo contrario de para lo que existe.
 */
@Component
public class ContratoRepositorio {

    /** El contrato vigente de un organizador, si tiene uno (R-ORG-02). */
    public Optional<Contrato> vigente(DSLContext dsl, UUID organizadorId, LocalDate hoy) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("organizador", "contrato_organizador")))
                .where(DSL.field("organizador_id", UUID.class)
                        .eq(organizadorId)
                        .and(DSL.field("firmado_en", OffsetDateTime.class).isNotNull())
                        .and(DSL.field("rescindido_en", OffsetDateTime.class).isNull())
                        .and(DSL.field("vigente_desde", LocalDate.class).le(hoy))
                        .and(DSL.field("vigente_hasta", LocalDate.class)
                                .isNull()
                                .or(DSL.field("vigente_hasta", LocalDate.class).ge(hoy))))
                .fetchOptional(this::aContrato);
    }

    public Optional<Contrato> ver(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("organizador", "contrato_organizador")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(this::aContrato);
    }

    public UUID emitir(
            DSLContext dsl,
            UUID organizadorId,
            String version,
            String contenidoHash,
            String obligaciones,
            String causalesRescision,
            LocalDate vigenteDesde) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("organizador", "contrato_organizador")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("organizador_id", UUID.class), organizadorId)
                .set(DSL.field("version", String.class), version)
                .set(DSL.field("contenido_hash", String.class), contenidoHash)
                .set(DSL.field("obligaciones", String.class), obligaciones)
                .set(DSL.field("causales_rescision", String.class), causalesRescision)
                .set(DSL.field("vigente_desde", LocalDate.class), vigenteDesde)
                .execute();
        return id;
    }

    /**
     * Firma el contrato, una sola vez.
     *
     * <p>El {@code WHERE firmado_en IS NULL} es la barrera: firmar dos veces
     * sobrescribiria la fecha y el token de la firma real, que es la evidencia.
     */
    public boolean firmar(DSLContext dsl, UUID id, UUID tokenFirmaId, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("organizador", "contrato_organizador")))
                        .set(DSL.field("firmado_en", OffsetDateTime.class), ahora)
                        .set(DSL.field("token_firma_id", UUID.class), tokenFirmaId)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("firmado_en", OffsetDateTime.class)
                                        .isNull()))
                        .execute()
                == 1;
    }

    /**
     * Rescinde el contrato.
     *
     * <p>{@code vigente_hasta} nunca queda igual o antes que {@code vigente_desde}
     * ({@code ck_contrato_org_vigencia}): un contrato rescindido el mismo dia que
     * empezo igual estuvo vigente ese dia, y decir lo contrario seria afirmar que
     * nunca existio.
     */
    public boolean rescindir(DSLContext dsl, UUID id, String motivo, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("organizador", "contrato_organizador")))
                        .set(DSL.field("rescindido_en", OffsetDateTime.class), ahora)
                        .set(DSL.field("motivo_rescision", String.class), motivo)
                        .set(
                                DSL.field("vigente_hasta", LocalDate.class),
                                DSL.greatest(
                                        DSL.val(ahora.toLocalDate()),
                                        DSL.field("vigente_desde", LocalDate.class)
                                                .plus(1)))
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("rescindido_en", OffsetDateTime.class)
                                        .isNull()))
                        .execute()
                == 1;
    }

    private java.util.List<org.jooq.Field<?>> campos() {
        return java.util.List.of(
                DSL.field("id", UUID.class),
                DSL.field("organizador_id", UUID.class),
                DSL.field("version", String.class),
                DSL.field("contenido_hash", String.class),
                DSL.field("firmado_en", OffsetDateTime.class),
                DSL.field("rescindido_en", OffsetDateTime.class),
                DSL.field("vigente_desde", LocalDate.class),
                DSL.field("vigente_hasta", LocalDate.class));
    }

    private Contrato aContrato(org.jooq.Record f) {
        return new Contrato(
                f.get("id", UUID.class),
                f.get("organizador_id", UUID.class),
                f.get("version", String.class),
                f.get("contenido_hash", String.class),
                f.get("firmado_en", OffsetDateTime.class),
                f.get("rescindido_en", OffsetDateTime.class),
                f.get("vigente_desde", LocalDate.class),
                f.get("vigente_hasta", LocalDate.class));
    }

    public record Contrato(
            UUID id,
            UUID organizadorId,
            String version,
            String contenidoHash,
            OffsetDateTime firmadoEn,
            OffsetDateTime rescindidoEn,
            LocalDate vigenteDesde,
            LocalDate vigenteHasta) {

        public boolean estaFirmado() {
            return firmadoEn != null;
        }
    }
}
