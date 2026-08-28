package bo.aportaya.nucleofinanciero.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code bloqueo_saldo}: la plata inmovilizada por orden de una autoridad. */
@Component
public class BloqueoRepositorio {

    public UUID registrar(
            DSLContext dsl,
            UUID cuentaId,
            UUID retencionId,
            String autoridad,
            String tipoOrden,
            String numeroOficio,
            Optional<Dinero> montoBloqueado,
            String alcance,
            String documentoUrl,
            String hashDocumento,
            OffsetDateTime recibidoEn) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("nucleo_financiero", "bloqueo_saldo")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("cuenta_billetera_id", UUID.class), cuentaId)
                .set(DSL.field("retencion_id", UUID.class), retencionId)
                .set(DSL.field("autoridad", String.class), autoridad)
                .set(DSL.field("tipo_orden", String.class), tipoOrden)
                .set(DSL.field("numero_oficio", String.class), numeroOficio)
                .set(
                        DSL.field("monto_bloqueado", BigDecimal.class),
                        montoBloqueado.map(Dinero::monto).orElse(null))
                .set(DSL.field("alcance", String.class), alcance)
                .set(DSL.field("documento_url", String.class), documentoUrl)
                .set(DSL.field("hash_documento", String.class), hashDocumento)
                .set(DSL.field("estado", String.class), "VIGENTE")
                .set(DSL.field("recibido_en", OffsetDateTime.class), recibidoEn)
                .execute();
        return id;
    }

    public boolean existeOficio(DSLContext dsl, String numeroOficio) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("nucleo_financiero", "bloqueo_saldo")),
                        DSL.field("numero_oficio").eq(numeroOficio))
                > 0;
    }

    public Optional<Bloqueo> vigenteDe(DSLContext dsl, UUID cuentaId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("numero_oficio", String.class),
                        DSL.field("autoridad", String.class),
                        DSL.field("retencion_id", UUID.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "bloqueo_saldo")))
                .where(DSL.field("cuenta_billetera_id", UUID.class).eq(cuentaId))
                .and(DSL.field("estado").eq("VIGENTE"))
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Bloqueo(
                        f.get("id", UUID.class),
                        f.get("numero_oficio", String.class),
                        f.get("autoridad", String.class),
                        Optional.ofNullable(f.get("retencion_id", UUID.class))));
    }

    /** Levantar el bloqueo es decision de la misma autoridad que lo puso, nunca nuestra. */
    public boolean levantar(DSLContext dsl, UUID bloqueoId, UUID levantadaPor, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("nucleo_financiero", "bloqueo_saldo")))
                        .set(DSL.field("estado", String.class), "LEVANTADO")
                        .set(DSL.field("levantada_por", UUID.class), levantadaPor)
                        .set(DSL.field("levantado_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class).eq(bloqueoId))
                        .and(DSL.field("estado").eq("VIGENTE"))
                        .execute()
                > 0;
    }

    public record Bloqueo(UUID id, String numeroOficio, String autoridad, Optional<UUID> retencionId) {}
}
