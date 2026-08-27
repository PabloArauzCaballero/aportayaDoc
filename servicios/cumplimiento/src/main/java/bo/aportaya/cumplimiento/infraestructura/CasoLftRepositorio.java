package bo.aportaya.cumplimiento.infraestructura;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code cumplimiento.caso_investigacion_lft}: lo que se abre cuando algo no cierra. */
@Component
public class CasoLftRepositorio {

    /**
     * Abre un caso.
     *
     * <p>{@code plazo_limite} se persiste al crear y **no se recalcula al consultar**
     * (invariante 8): el plazo que corre es el que se fijo el dia que se abrio, aunque
     * despues cambie la politica.
     */
    public UUID abrir(
            DSLContext dsl,
            UUID usuarioId,
            UUID analistaId,
            String origen,
            String prioridad,
            String resumen,
            OffsetDateTime abiertoEn,
            OffsetDateTime plazoLimite) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "caso_investigacion_lft")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("codigo", String.class), "LFT-" + id.toString().substring(0, 8))
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("analista_id", UUID.class), analistaId)
                .set(DSL.field("origen", String.class), origen)
                .set(DSL.field("estado", String.class), "ABIERTO")
                .set(DSL.field("prioridad", String.class), prioridad)
                .set(DSL.field("resumen", String.class), resumen)
                .set(DSL.field("abierto_en", OffsetDateTime.class), abiertoEn)
                .set(DSL.field("plazo_limite", OffsetDateTime.class), plazoLimite)
                .execute();
        return id;
    }

    public boolean hayCasoAbiertoDe(DSLContext dsl, UUID usuarioId) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("cumplimiento", "caso_investigacion_lft")),
                        DSL.field("usuario_id", UUID.class).eq(usuarioId),
                        DSL.field("cerrado_en").isNull())
                > 0;
    }
}
