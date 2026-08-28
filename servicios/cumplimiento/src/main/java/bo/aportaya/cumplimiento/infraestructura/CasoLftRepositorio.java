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

    public java.util.Optional<Caso> porId(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("analista_id", UUID.class),
                        DSL.field("estado", String.class),
                        DSL.field("plazo_limite", OffsetDateTime.class))
                .from(DSL.table(DSL.name("cumplimiento", "caso_investigacion_lft")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Caso(
                        f.get("id", UUID.class),
                        f.get("usuario_id", UUID.class),
                        f.get("analista_id", UUID.class),
                        f.get("estado", String.class),
                        f.get("plazo_limite", OffsetDateTime.class)));
    }

    /**
     * Cierra el caso con su decision.
     *
     * <p>{@code ck_caso_revision} exige que quien revisa no sea el analista y
     * {@code ck_caso_reporte} exige el ROS cuando la decision es REPORTAR. Las dos las
     * comprueba antes el caso de uso, para poder explicar el rechazo en vez de devolver
     * el mensaje de la base.
     */
    public boolean decidir(
            DSLContext dsl,
            UUID casoId,
            String decision,
            String hallazgos,
            UUID revisadoPor,
            UUID reporteSospechosoId,
            OffsetDateTime ahora) {

        return dsl.update(DSL.table(DSL.name("cumplimiento", "caso_investigacion_lft")))
                        .set(DSL.field("estado", String.class), "CERRADO")
                        .set(DSL.field("decision", String.class), decision)
                        .set(DSL.field("hallazgos", String.class), hallazgos)
                        .set(DSL.field("revisado_por", UUID.class), revisadoPor)
                        .set(DSL.field("reporte_operacion_sospechosa_id", UUID.class), reporteSospechosoId)
                        .set(DSL.field("cerrado_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(casoId)
                                .and(DSL.field("estado", String.class).ne("CERRADO")))
                        .execute()
                == 1;
    }

    public record Caso(UUID id, UUID usuarioId, UUID analistaId, String estado, OffsetDateTime plazoLimite) {}

    public boolean hayCasoAbiertoDe(DSLContext dsl, UUID usuarioId) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("cumplimiento", "caso_investigacion_lft")),
                        DSL.field("usuario_id", UUID.class).eq(usuarioId),
                        DSL.field("cerrado_en").isNull())
                > 0;
    }
}
