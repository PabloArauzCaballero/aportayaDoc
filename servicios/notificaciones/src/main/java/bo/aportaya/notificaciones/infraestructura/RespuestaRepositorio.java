package bo.aportaya.notificaciones.infraestructura;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code respuesta_entrante}: lo que la persona contesto por el canal. */
@Component
public class RespuestaRepositorio {

    /**
     * Quien es el remitente, por su identificador de canal.
     *
     * <p>Si no corresponde a un canal verificado, **no se revela nada**: el webhook es
     * publico y responder «ese numero no existe» le confirmaria a cualquiera quien es
     * cliente y quien no.
     */
    public Optional<Remitente> porIdentificador(DSLContext dsl, String identificador, String tipo) {
        Record fila = dsl.select(DSL.field("id", UUID.class), DSL.field("usuario_id", UUID.class))
                .from(DSL.table(DSL.name("notificaciones", "canal_vinculado")))
                .where(DSL.field("identificador").eq(identificador))
                .and(DSL.field("tipo").eq(tipo))
                .and(DSL.field("verificado", Boolean.class).isTrue())
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Remitente(f.get("id", UUID.class), f.get("usuario_id", UUID.class)));
    }

    public UUID registrar(
            DSLContext dsl,
            UUID canalVinculadoId,
            Optional<UUID> notificacionRelacionadaId,
            String contenido,
            String intencionSegunLaBase,
            String accion,
            OffsetDateTime recibidaEn,
            OffsetDateTime procesadaEn) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("notificaciones", "respuesta_entrante")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("canal_vinculado_id", UUID.class), canalVinculadoId)
                .set(DSL.field("notificacion_relacionada_id", UUID.class), notificacionRelacionadaId.orElse(null))
                .set(DSL.field("contenido", String.class), contenido)
                .set(DSL.field("intencion_detectada", String.class), intencionSegunLaBase)
                .set(DSL.field("recibida_en", OffsetDateTime.class), recibidaEn)
                .set(DSL.field("procesada_en", OffsetDateTime.class), procesadaEn)
                .set(DSL.field("accion_ejecutada", String.class), accion)
                .execute();
        return id;
    }

    public record Remitente(UUID canalVinculadoId, UUID usuarioId) {}
}
