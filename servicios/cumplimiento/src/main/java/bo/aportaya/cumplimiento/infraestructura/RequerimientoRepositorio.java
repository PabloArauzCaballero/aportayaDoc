package bo.aportaya.cumplimiento.infraestructura;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code requerimiento_autoridad}.
 *
 * <p>**El plazo se guarda y no se recalcula.** Un oficio con cinco dias tiene cinco
 * dias desde que llego, no desde que alguien lo abrio; recalcularlo al consultar es
 * regalarse tiempo que la autoridad no dio.
 *
 * <p>Y **no se actua sin el documento y su hash**: entregar informacion de una persona
 * porque alguien dijo por telefono que habia un oficio es exactamente lo que la reserva
 * de datos existe para impedir.
 */
@Component
public class RequerimientoRepositorio {

    public Optional<UUID> porNumeroDeOficio(DSLContext dsl, String numero) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("cumplimiento", "requerimiento_autoridad")))
                .where(DSL.field("numero_oficio", String.class).eq(numero))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID registrar(
            DSLContext dsl,
            UUID usuarioAfectadoId,
            String autoridad,
            String numeroOficio,
            OffsetDateTime recepcion,
            OffsetDateTime plazoRespuesta,
            String alcance,
            String documentoUrl,
            String hashDocumento) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "requerimiento_autoridad")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_afectado_id", UUID.class), usuarioAfectadoId)
                .set(DSL.field("autoridad", String.class), autoridad)
                .set(DSL.field("numero_oficio", String.class), numeroOficio)
                .set(DSL.field("fecha_recepcion", OffsetDateTime.class), recepcion)
                .set(DSL.field("plazo_respuesta", OffsetDateTime.class), plazoRespuesta)
                .set(DSL.field("alcance", String.class), alcance)
                .set(DSL.field("documento_url", String.class), documentoUrl)
                .set(DSL.field("hash_documento", String.class), hashDocumento)
                .set(DSL.field("estado", String.class), "RECIBIDO")
                .execute();
        return id;
    }

    public boolean anotarBloqueo(DSLContext dsl, UUID requerimientoId, UUID bloqueoSaldoId) {
        return dsl.update(DSL.table(DSL.name("cumplimiento", "requerimiento_autoridad")))
                        .set(DSL.field("bloqueo_saldo_id", UUID.class), bloqueoSaldoId)
                        .set(DSL.field("estado", String.class), "EN_PROCESO")
                        .where(DSL.field("id", UUID.class).eq(requerimientoId))
                        .execute()
                == 1;
    }

    public boolean responder(
            DSLContext dsl, UUID requerimientoId, UUID respondidoPor, String respuestaUrl, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("cumplimiento", "requerimiento_autoridad")))
                        .set(DSL.field("estado", String.class), "RESPONDIDO")
                        .set(DSL.field("respondido_por", UUID.class), respondidoPor)
                        .set(DSL.field("respuesta_url", String.class), respuestaUrl)
                        .set(DSL.field("respondido_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(requerimientoId)
                                .and(DSL.field("estado", String.class).in("RECIBIDO", "EN_PROCESO")))
                        .execute()
                == 1;
    }

    /** Oficios con plazo vencido sin respuesta. Se responde igual, y se abre hallazgo. */
    public List<Vencido> vencidosSinResponder(DSLContext dsl, OffsetDateTime corte) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("numero_oficio", String.class),
                        DSL.field("autoridad", String.class),
                        DSL.field("plazo_respuesta", OffsetDateTime.class))
                .from(DSL.table(DSL.name("cumplimiento", "requerimiento_autoridad")))
                .where(DSL.field("respondido_en", OffsetDateTime.class)
                        .isNull()
                        .and(DSL.field("plazo_respuesta", OffsetDateTime.class).lt(corte))
                        .and(DSL.field("estado", String.class).ne("ARCHIVADO")))
                .fetch(f -> new Vencido(
                        f.get("id", UUID.class),
                        f.get("numero_oficio", String.class),
                        f.get("autoridad", String.class),
                        f.get("plazo_respuesta", OffsetDateTime.class)));
    }

    public record Vencido(UUID id, String numeroOficio, String autoridad, OffsetDateTime plazo) {}
}
