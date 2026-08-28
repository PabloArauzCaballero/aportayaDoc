package bo.aportaya.transparencia.infraestructura;

import bo.aportaya.transparencia.dominio.PuntajeDeReputacion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * El catalogo del modelo de scoring: {@code modelo_scoring}, {@code peso_factor} y
 * {@code regla_impacto_evento}.
 *
 * <p>Vive aparte del resto a proposito. **Todo lo que decide cuanto vale una conducta
 * esta en estas tres tablas** (invariante 10): los pesos, los topes por factor y el
 * impacto de cada tipo de hecho. Cambiarlos es cambiar el modelo, no desplegar codigo,
 * y quien audite tiene que poder leerlos sin abrir un repositorio de eventos.
 */
@Component
public class ModeloRepositorio {

    /** El modelo de scoring en produccion. Es catalogo, no constantes del codigo. */
    public Optional<Modelo> modeloVigente(DSLContext dsl, OffsetDateTime momento) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("version", String.class),
                        DSL.field("puntaje_base", BigDecimal.class),
                        DSL.field("puntaje_minimo", BigDecimal.class),
                        DSL.field("puntaje_maximo", BigDecimal.class),
                        DSL.field("ventana_historica_meses", Short.class),
                        DSL.field("min_eventos_para_score", Short.class))
                .from(DSL.table(DSL.name("transparencia", "modelo_scoring")))
                .where(DSL.field("es_produccion", Boolean.class)
                        .isTrue()
                        .and(DSL.field("vigente_desde", OffsetDateTime.class).le(momento))
                        .and(DSL.field("vigente_hasta", OffsetDateTime.class)
                                .isNull()
                                .or(DSL.field("vigente_hasta", OffsetDateTime.class)
                                        .gt(momento))))
                .fetchOptional(f -> new Modelo(
                        f.get("id", UUID.class),
                        f.get("version", String.class),
                        f.get("puntaje_base", BigDecimal.class),
                        f.get("puntaje_minimo", BigDecimal.class),
                        f.get("puntaje_maximo", BigDecimal.class),
                        f.get("ventana_historica_meses", Short.class),
                        f.get("min_eventos_para_score", Short.class)));
    }

    public List<PuntajeDeReputacion.Factor> factoresDe(DSLContext dsl, UUID modeloId) {
        return dsl.select(
                        DSL.field("codigo_factor", String.class),
                        DSL.field("peso", BigDecimal.class),
                        DSL.field("tope_aporte_al_score", BigDecimal.class),
                        DSL.field("es_penalizador", Boolean.class))
                .from(DSL.table(DSL.name("transparencia", "peso_factor")))
                .where(DSL.field("modelo_id", UUID.class).eq(modeloId))
                .orderBy(DSL.field("codigo_factor").asc())
                .fetch(f -> new PuntajeDeReputacion.Factor(
                        f.get("codigo_factor", String.class),
                        f.get("peso", BigDecimal.class),
                        f.get("tope_aporte_al_score", BigDecimal.class),
                        f.get("es_penalizador", Boolean.class)));
    }

    /** La regla que dice cuanto pesa un tipo de evento. Sin regla, no se puntua. */
    public Optional<Regla> reglaDe(DSLContext dsl, UUID modeloId, String tipoEvento) {
        return dsl.select(
                        DSL.field("codigo_factor", String.class),
                        DSL.field("impacto_base", BigDecimal.class),
                        DSL.field("multiplicador_por_reincidencia", BigDecimal.class),
                        DSL.field("impacto_maximo", BigDecimal.class),
                        DSL.field("requiere_confirmacion", Boolean.class))
                .from(DSL.table(DSL.name("transparencia", "regla_impacto_evento")))
                .where(DSL.field("modelo_id", UUID.class)
                        .eq(modeloId)
                        .and(DSL.field("tipo_evento", String.class).eq(tipoEvento)))
                .fetchOptional(f -> new Regla(
                        f.get("codigo_factor", String.class),
                        f.get("impacto_base", BigDecimal.class),
                        f.get("multiplicador_por_reincidencia", BigDecimal.class),
                        f.get("impacto_maximo", BigDecimal.class),
                        f.get("requiere_confirmacion", Boolean.class)));
    }

    public record Modelo(
            UUID id,
            String version,
            BigDecimal puntajeBase,
            BigDecimal puntajeMinimo,
            BigDecimal puntajeMaximo,
            int ventanaMeses,
            int minimoDeEventos) {}

    public record Regla(
            String codigoFactor,
            BigDecimal impactoBase,
            BigDecimal multiplicadorPorReincidencia,
            BigDecimal impactoMaximo,
            boolean requiereConfirmacion) {}
}
