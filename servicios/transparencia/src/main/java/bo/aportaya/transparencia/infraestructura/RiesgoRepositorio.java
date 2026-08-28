package bo.aportaya.transparencia.infraestructura;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code alerta_riesgo} y {@code metrica_grupo}.
 *
 * <p>Las metricas se guardan **alerten o no**: la serie es lo que despues permite ver
 * que algo venia empeorando desde hace tres meses. Y una alerta descartada queda con su
 * desenlace, que es lo unico que despues deja calibrar el modelo contra lo que
 * realmente paso.
 */
@Component
public class RiesgoRepositorio {

    /** Una alerta abierta por ambito y codigo (R-GAR-07). */
    public Optional<UUID> alertaAbierta(DSLContext dsl, String ambito, UUID ambitoId, String codigo) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("transparencia", "alerta_riesgo")))
                .where(DSL.field("ambito", String.class)
                        .eq(ambito)
                        .and(DSL.field("ambito_id", UUID.class).eq(ambitoId))
                        .and(DSL.field("codigo", String.class).eq(codigo))
                        .and(DSL.field("estado", String.class).in("ABIERTA", "EN_REVISION")))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID abrirAlerta(
            DSLContext dsl,
            String ambito,
            UUID ambitoId,
            String codigo,
            String severidad,
            String descripcion,
            String evidenciaJson,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("transparencia", "alerta_riesgo")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("ambito", String.class), ambito)
                .set(DSL.field("ambito_id", UUID.class), ambitoId)
                .set(DSL.field("codigo", String.class), codigo)
                .set(DSL.field("severidad", String.class), severidad)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("evidencia", org.jooq.JSONB.class), org.jooq.JSONB.valueOf(evidenciaJson))
                .set(DSL.field("estado", String.class), "ABIERTA")
                .set(DSL.field("detectada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /** Cerrar una alerta exige desenlace: CONFIRMADA o DESCARTADA (R-GAR-07). */
    public boolean cerrarAlerta(DSLContext dsl, UUID id, String desenlace, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("transparencia", "alerta_riesgo")))
                        .set(DSL.field("estado", String.class), desenlace)
                        .set(DSL.field("cerrada_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).in("ABIERTA", "EN_REVISION")))
                        .execute()
                == 1;
    }

    /** Una metrica del grupo, con su umbral. Es lo que dispara la alerta. */
    public void registrarMetrica(
            DSLContext dsl,
            UUID grupoId,
            UUID periodoId,
            String codigo,
            BigDecimal valor,
            String unidad,
            BigDecimal umbral,
            boolean enAlerta,
            OffsetDateTime ahora) {

        var tabla = DSL.table(DSL.name("transparencia", "metrica_grupo"));
        dsl.insertInto(tabla)
                .set(DSL.field("id", UUID.class), UUID.randomUUID())
                .set(DSL.field("grupo_id", UUID.class), grupoId)
                .set(DSL.field("periodo_id", UUID.class), periodoId)
                .set(DSL.field("codigo", String.class), codigo)
                .set(DSL.field("valor", BigDecimal.class), valor)
                .set(DSL.field("unidad", String.class), unidad)
                .set(DSL.field("umbral_alerta", BigDecimal.class), umbral)
                .set(DSL.field("en_alerta", Boolean.class), enAlerta)
                .set(DSL.field("calculada_en", OffsetDateTime.class), ahora)
                .onConflict(
                        DSL.field("grupo_id", UUID.class),
                        DSL.field("periodo_id", UUID.class),
                        DSL.field("codigo", String.class))
                .doUpdate()
                .set(DSL.field("valor", BigDecimal.class), valor)
                .set(DSL.field("umbral_alerta", BigDecimal.class), umbral)
                .set(DSL.field("en_alerta", Boolean.class), enAlerta)
                .set(DSL.field("calculada_en", OffsetDateTime.class), ahora)
                .execute();
    }
}
