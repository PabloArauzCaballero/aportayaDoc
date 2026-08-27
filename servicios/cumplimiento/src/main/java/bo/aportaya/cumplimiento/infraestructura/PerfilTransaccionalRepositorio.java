package bo.aportaya.cumplimiento.infraestructura;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code cumplimiento.perfil_transaccional}: lo DECLARADO y lo OBSERVADO. */
@Component
public class PerfilTransaccionalRepositorio {

    public Optional<Perfil> masReciente(DSLContext dsl, UUID usuarioId, String tipo) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("monto_mensual_estimado", BigDecimal.class),
                        DSL.field("cantidad_operaciones_estimada", Integer.class))
                .from(DSL.table(DSL.name("cumplimiento", "perfil_transaccional")))
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .and(DSL.field("tipo").eq(tipo))
                .orderBy(DSL.field("vigente_desde").desc())
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Perfil(
                        f.get("id", UUID.class),
                        f.get("monto_mensual_estimado", BigDecimal.class),
                        f.get("cantidad_operaciones_estimada", Integer.class)));
    }

    public record Perfil(UUID id, BigDecimal montoMensual, int cantidadOperaciones) {}
}
