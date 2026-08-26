package bo.aportaya.plataforma.mensajeria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;

/**
 * El evento se escribe en la MISMA transaccion que el hecho.
 *
 * <p>Si la transaccion revierte, el evento no existio. Publicar a Kafka desde dentro
 * de la transaccion es exactamente el fallo que el outbox existe para impedir:
 * anunciar un hecho que despues no ocurrio, y que nadie puede retirar.
 *
 * <p>Al reves tambien: commitear el hecho y publicar despues «por fuera» pierde el
 * evento si el proceso muere en el medio. El relevo lo recoge porque quedo escrito.
 */
public final class Outbox {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String esquema;

    public Outbox(String esquema) {
        this.esquema = Objects.requireNonNull(esquema, "esquema");
    }

    public void emitir(DSLContext dsl, EventoDominio evento) {
        dsl.insertInto(DSL.table(DSL.name(esquema, "evento_dominio")))
                .columns(
                        DSL.field("id"),
                        DSL.field("tipo"),
                        DSL.field("version"),
                        DSL.field("agregado"),
                        DSL.field("agregado_id"),
                        DSL.field("payload"),
                        DSL.field("metadatos"),
                        DSL.field("correlation_id"),
                        DSL.field("ocurrido_en"),
                        DSL.field("estado"),
                        DSL.field("intentos"))
                .values(
                        DSL.field("gen_random_uuid()"),
                        DSL.val(evento.tipo()),
                        DSL.val("1"),
                        DSL.val(evento.agregado()),
                        DSL.val(evento.agregadoId()),
                        DSL.val(comoJson(evento.carga())),
                        DSL.val(comoJson(Map.of("tema", evento.tema()))),
                        DSL.val(evento.correlationId()),
                        DSL.field("now()"),
                        DSL.val("PENDIENTE"),
                        DSL.val((short) 0))
                .execute();
    }

    private JSONB comoJson(Map<String, Object> datos) {
        try {
            return JSONB.valueOf(JSON.writeValueAsString(datos));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("La carga del evento no es serializable a JSON", e);
        }
    }
}
