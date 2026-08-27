package bo.aportaya.cumplimiento.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * Historico de {@code calificacion_riesgo_cliente}.
 *
 * <p>R-UIF-11 lo hace cumplir con un EXCLUDE sobre {@code tstzrange}: dos
 * calificaciones vigentes del mismo cliente **no pueden coexistir**. Por eso cerrar
 * la anterior y abrir la nueva van juntas, en la misma transaccion y en ese orden.
 * Al reves, el EXCLUDE rechaza y no hay reintento que lo salve.
 */
@Component
public class CalificacionRiesgoRepositorio {

    public Optional<Calificacion> vigenteDe(DSLContext dsl, UUID usuarioId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("nivel", String.class),
                        DSL.field("nivel_dd_requerido", String.class),
                        DSL.field("periodicidad_revision_meses", Short.class),
                        DSL.field("proxima_revision", LocalDate.class))
                .from(DSL.table(DSL.name("cumplimiento", "calificacion_riesgo_cliente")))
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .and(DSL.field("vigente_hasta").isNull())
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Calificacion(
                        f.get("id", UUID.class),
                        f.get("nivel", String.class),
                        f.get("nivel_dd_requerido", String.class),
                        f.get("periodicidad_revision_meses", Short.class),
                        f.get("proxima_revision", LocalDate.class)));
    }

    /** Cierra la vigente. Su {@code vigente_hasta} queda escrito: el pasado se conserva. */
    public void cerrarVigente(DSLContext dsl, UUID usuarioId, OffsetDateTime momento) {
        dsl.update(DSL.table(DSL.name("cumplimiento", "calificacion_riesgo_cliente")))
                .set(DSL.field("vigente_hasta", OffsetDateTime.class), momento)
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .and(DSL.field("vigente_hasta").isNull())
                .execute();
    }

    public UUID calificar(
            DSLContext dsl,
            UUID usuarioId,
            String nivel,
            String nivelDdRequerido,
            int periodicidadMeses,
            BigDecimal puntaje,
            Optional<UUID> calificadoPor,
            String motivo,
            OffsetDateTime desde) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "calificacion_riesgo_cliente")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("nivel", String.class), nivel)
                .set(DSL.field("puntaje_total", BigDecimal.class), puntaje)
                .set(DSL.field("nivel_dd_requerido", String.class), nivelDdRequerido)
                .set(DSL.field("periodicidad_revision_meses", Short.class), (short) periodicidadMeses)
                .set(DSL.field("vigente_desde", OffsetDateTime.class), desde)
                .set(
                        DSL.field("proxima_revision", LocalDate.class),
                        desde.toLocalDate().plusMonths(periodicidadMeses))
                .set(DSL.field("calificado_por", UUID.class), calificadoPor.orElse(null))
                .set(DSL.field("es_automatica", Boolean.class), calificadoPor.isEmpty())
                .set(DSL.field("motivo_cambio", String.class), motivo)
                .execute();
        return id;
    }

    public record Calificacion(
            UUID id, String nivel, String nivelDdRequerido, short periodicidadMeses, LocalDate proximaRevision) {}
}
