package bo.aportaya.notificaciones.infraestructura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code programacion_recordatorio}: la escalera que definio el grupo.
 *
 * <p>Un grupo sin programacion propia no se queda sin avisos: hereda las filas con
 * {@code grupo_id IS NULL}, que son las obligatorias de la plataforma. Por eso la
 * consulta trae las dos y deja que el caso de uso decida.
 */
@Component
public class ProgramacionRepositorio {

    public List<Escalon> para(DSLContext dsl, Optional<UUID> grupoId) {
        var condicion = DSL.field("activa", Boolean.class).isTrue();
        var deGrupo = grupoId.map(g -> DSL.field("grupo_id", UUID.class)
                        .eq(g)
                        .or(DSL.field("grupo_id").isNull()))
                .orElse(DSL.field("grupo_id").isNull());

        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("evento_id", UUID.class),
                        DSL.field("desfase_dias", Short.class),
                        DSL.field("max_repeticiones", Short.class),
                        DSL.field("grupo_id", UUID.class))
                .from(DSL.table(DSL.name("notificaciones", "programacion_recordatorio")))
                .where(condicion)
                .and(deGrupo)
                // El escalon propio del grupo gana sobre el heredado: se ordena para
                // que el especifico venga primero.
                .orderBy(
                        DSL.field("grupo_id").desc().nullsLast(),
                        DSL.field("desfase_dias").asc())
                .fetch(fila -> new Escalon(
                        fila.get("id", UUID.class),
                        fila.get("evento_id", UUID.class),
                        fila.get("desfase_dias", Short.class),
                        fila.get("max_repeticiones", Short.class),
                        fila.get("grupo_id", UUID.class) != null));
    }

    public record Escalon(UUID id, UUID eventoId, short desfaseDias, short maxRepeticiones, boolean esDelGrupo) {}
}
