package bo.aportaya.auditoria.infraestructura;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * Lee {@code auditoria.indicador_kpi}.
 *
 * <p>Solo lectura: el valor lo escribe el trabajo que calcula los indicadores contra
 * la replica, no esta operacion. **El tablero publica, no calcula** — si publicara
 * calculando, habria dos lugares donde nace el mismo numero, y entonces hay dos
 * numeros.
 */
@Component
public class IndicadorRepositorio {

    private static final org.jooq.Name TABLA = DSL.name("auditoria", "indicador_kpi");

    /** Un indicador tal como quedo guardado, sin interpretar. */
    public record Fila(
            String codigo,
            String nombre,
            BigDecimal valor,
            String unidad,
            Optional<BigDecimal> meta,
            Optional<BigDecimal> variacion,
            OffsetDateTime calculadoEn) {}

    public record PuntoDeSerie(String periodo, BigDecimal valor) {}

    /**
     * Los indicadores del periodo. Se ordenan por codigo y no por valor: el orden del
     * tablero tiene que ser estable entre visitas, o dos personas mirando la misma
     * pantalla discuten sobre filas distintas.
     */
    public List<Fila> delPeriodo(DSLContext dsl, String periodo, String dimension, UUID dimensionId) {
        return dsl.select(
                        DSL.field("codigo", String.class),
                        DSL.field("nombre", String.class),
                        DSL.field("valor", BigDecimal.class),
                        DSL.field("unidad", String.class),
                        DSL.field("meta", BigDecimal.class),
                        DSL.field("variacion_periodo_anterior", BigDecimal.class),
                        DSL.field("calculado_en", OffsetDateTime.class))
                .from(DSL.table(TABLA))
                .where(DSL.field("periodo").eq(periodo))
                .and(DSL.field("dimension").eq(dimension))
                .and(condicionDeDimension(dimensionId))
                .orderBy(DSL.field("codigo").asc())
                .fetch()
                .map(IndicadorRepositorio::aFila);
    }

    /**
     * La serie de un indicador: los periodos anteriores al pedido, del mas viejo al
     * mas nuevo.
     *
     * <p>Se compara `periodo` como texto a proposito. El formato es AAAA, AAAA-MM o
     * AAAA-MM-DD, y en los tres el orden lexicografico coincide con el cronologico —
     * que es exactamente por lo que se eligio ese formato.
     */
    public List<PuntoDeSerie> serieDe(
            DSLContext dsl, String codigo, String dimension, UUID dimensionId, String hastaPeriodo, int cuantos) {
        if (cuantos <= 0) {
            return List.of();
        }
        List<PuntoDeSerie> descendente = dsl.select(
                        DSL.field("periodo", String.class), DSL.field("valor", BigDecimal.class))
                .from(DSL.table(TABLA))
                .where(DSL.field("codigo").eq(codigo))
                .and(DSL.field("dimension").eq(dimension))
                .and(condicionDeDimension(dimensionId))
                .and(DSL.field("periodo", String.class).lessThan(hastaPeriodo))
                .orderBy(DSL.field("periodo").desc())
                .limit(cuantos)
                .fetch()
                .map(f -> new PuntoDeSerie(f.get("periodo", String.class), f.get("valor", BigDecimal.class)));

        return descendente.reversed();
    }

    /** El valor del periodo inmediatamente anterior, si existe. */
    public Optional<BigDecimal> anteriorA(
            DSLContext dsl, String codigo, String dimension, UUID dimensionId, String periodo) {
        return serieDe(dsl, codigo, dimension, dimensionId, periodo, 1).stream()
                .map(PuntoDeSerie::valor)
                .findFirst();
    }

    /**
     * `dimension_id` es nulo en GLOBAL. Con `eq(null)` jOOQ produce `= NULL`, que en
     * SQL no es falso ni verdadero: no devuelve nada. Tiene que ser `IS NULL`.
     */
    private static org.jooq.Condition condicionDeDimension(UUID dimensionId) {
        return dimensionId == null
                ? DSL.field("dimension_id").isNull()
                : DSL.field("dimension_id").eq(dimensionId);
    }

    private static Fila aFila(Record f) {
        return new Fila(
                f.get("codigo", String.class),
                f.get("nombre", String.class),
                f.get("valor", BigDecimal.class),
                f.get("unidad", String.class),
                Optional.ofNullable(f.get("meta", BigDecimal.class)),
                Optional.ofNullable(f.get("variacion_periodo_anterior", BigDecimal.class)),
                f.get("calculado_en", OffsetDateTime.class));
    }
}
