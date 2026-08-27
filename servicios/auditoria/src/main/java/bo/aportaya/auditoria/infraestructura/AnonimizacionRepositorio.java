package bo.aportaya.auditoria.infraestructura;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** Escribe {@code auditoria.proceso_anonimizacion}. */
@Component
public class AnonimizacionRepositorio {

    private static final org.jooq.Name TABLA = DSL.name("auditoria", "proceso_anonimizacion");

    /**
     * Deja el proceso PLANIFICADO, no ejecutado.
     *
     * <p>Anonimizar toca datos de catorce servicios y no se hace dentro de la
     * transaccion que atiende la solicitud: se planifica, se emite el evento y cada
     * servicio anonimiza lo suyo. Hacerlo todo aca seria escribir en esquemas ajenos,
     * que es exactamente lo que el invariante 11 impide.
     */
    public UUID planificar(
            DSLContext dsl,
            UUID usuarioId,
            UUID solicitudId,
            String estrategia,
            List<String> afectadas,
            List<String> retenidasPorLey) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(TABLA))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("solicitud_id", UUID.class),
                        DSL.field("estrategia", String.class),
                        DSL.field("entidades_afectadas", JSONB.class),
                        DSL.field("datos_retenidos_por_ley", JSONB.class),
                        DSL.field("estado", String.class),
                        DSL.field("ejecutado_en", OffsetDateTime.class))
                .values(
                        id,
                        usuarioId,
                        solicitudId,
                        estrategia,
                        comoJson(afectadas),
                        comoJson(retenidasPorLey),
                        "PLANIFICADO",
                        null)
                .execute();
        return id;
    }

    /** Arreglo JSON de cadenas, escapado a mano: la lista no lleva estructura anidada. */
    private static JSONB comoJson(List<String> valores) {
        String cuerpo = valores.stream()
                .map(v -> '"' + v.replace("\\", "\\\\").replace("\"", "\\\"") + '"')
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return JSONB.valueOf("[" + cuerpo + "]");
    }
}
