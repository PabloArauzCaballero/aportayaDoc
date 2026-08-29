package bo.aportaya.garantia.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * Como termina un grupo que ya no puede seguir.
 *
 * <p>Esta aparte de {@link GestionRepositorio} porque disolver no es gestionar un
 * incumplimiento: los otros escriben sobre una persona —el avalista, el moroso, el
 * reemplazo—, y esto cierra el grupo entero y reparte lo que quedaba.
 */
@Component
public class DisolucionRepositorio {

    public Optional<UUID> disolucionDe(DSLContext dsl, UUID grupoId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("garantia", "disolucion_anticipada")))
                .where(DSL.field("grupo_id", UUID.class).eq(grupoId))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID iniciarDisolucion(
            DSLContext dsl,
            UUID grupoId,
            String causal,
            String motivo,
            Dinero totalAportado,
            Dinero totalEntregado,
            Dinero saldoADistribuir,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "disolucion_anticipada")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("grupo_id", UUID.class), grupoId)
                .set(DSL.field("causal", String.class), causal)
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("total_aportado_grupo", BigDecimal.class), totalAportado.monto())
                .set(DSL.field("total_entregado", BigDecimal.class), totalEntregado.monto())
                .set(DSL.field("saldo_a_distribuir", BigDecimal.class), saldoADistribuir.monto())
                .set(DSL.field("estado", String.class), "CALCULADA")
                .set(DSL.field("iniciada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /**
     * Cierra la disolucion.
     *
     * <p>{@code tg_disolucion_cuadra} exige que la cuenta del grupo cierre **en cero**
     * (R-GRP-13): un grupo disuelto con saldo es plata de alguien que quedo sin dueno.
     */
    public boolean cerrarDisolucion(DSLContext dsl, UUID id, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("garantia", "disolucion_anticipada")))
                        .set(DSL.field("estado", String.class), "CERRADA")
                        .set(DSL.field("cerrada_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).in("CALCULADA", "EJECUTADA")))
                        .execute()
                == 1;
    }
}
