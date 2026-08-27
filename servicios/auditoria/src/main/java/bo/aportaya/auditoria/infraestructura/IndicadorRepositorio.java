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
    private static final org.jooq.Name DEFINICION = DSL.name("auditoria", "definicion_indicador");

    /** Un indicador tal como quedo guardado, con la definicion que lo explica. */
    public record Fila(
            String codigo,
            String nombre,
            BigDecimal valor,
            String unidad,
            Optional<BigDecimal> meta,
            Optional<BigDecimal> variacion,
            boolean provisorio,
            Optional<Integer> casos,
            String familia,
            String duenoFamilia,
            String sentidoMeta,
            String definicionVersion,
            int minimoCasos,
            OffsetDateTime calculadoEn) {}

    public record PuntoDeSerie(String periodo, BigDecimal valor) {}

    /**
     * Los indicadores del periodo. Se ordenan por codigo y no por valor: el orden del
     * tablero tiene que ser estable entre visitas, o dos personas mirando la misma
     * pantalla discuten sobre filas distintas.
     */
    public List<Fila> delPeriodo(DSLContext dsl, String periodo, String dimension, UUID dimensionId) {
        // La union es interna a proposito: un indicador sin definicion vigente NO se
        // publica. CU-98 pone la definicion escrita como precondicion, y una fila que
        // se cuela sin ella es un numero que nadie sabe interpretar.
        return dsl.select(
                        DSL.field("k.codigo", String.class).as("codigo"),
                        DSL.field("k.nombre", String.class).as("nombre"),
                        DSL.field("k.valor", BigDecimal.class).as("valor"),
                        DSL.field("k.unidad", String.class).as("unidad"),
                        DSL.field("k.meta", BigDecimal.class).as("meta"),
                        DSL.field("k.variacion_periodo_anterior", BigDecimal.class)
                                .as("variacion_periodo_anterior"),
                        DSL.field("k.provisorio", Boolean.class).as("provisorio"),
                        DSL.field("k.casos", Integer.class).as("casos"),
                        DSL.field("d.familia", String.class).as("familia"),
                        DSL.field("d.dueno_familia", String.class).as("dueno_familia"),
                        DSL.field("d.sentido_meta", String.class).as("sentido_meta"),
                        DSL.field("d.version", String.class).as("version"),
                        DSL.field("d.minimo_casos", Integer.class).as("minimo_casos"),
                        DSL.field("k.calculado_en", OffsetDateTime.class).as("calculado_en"))
                .from(DSL.table(TABLA).as("k"))
                .join(DSL.table(DEFINICION).as("d"))
                .on(DSL.field("k.definicion_indicador_id").eq(DSL.field("d.id")))
                .where(DSL.field("k.periodo").eq(periodo))
                .and(DSL.field("k.dimension").eq(dimension))
                .and(condicionDeDimension(dimensionId))
                // La tabla es append-only: un indicador recalculado deja fila nueva.
                // Se publica la ULTIMA por codigo, y las anteriores siguen ahi para la
                // serie y para explicar el corte.
                .and(DSL.field("k.calculado_en")
                        .eq(DSL.select(DSL.max(DSL.field("k2.calculado_en", java.time.OffsetDateTime.class)))
                                .from(DSL.table(TABLA).as("k2"))
                                .where(DSL.field("k2.codigo").eq(DSL.field("k.codigo")))
                                .and(DSL.field("k2.periodo").eq(DSL.field("k.periodo")))
                                .and(DSL.field("k2.dimension").eq(DSL.field("k.dimension")))))
                .orderBy(DSL.field("k.codigo").asc())
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
                .orderBy(DSL.field("periodo").desc(), DSL.field("calculado_en").desc())
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
                Boolean.TRUE.equals(f.get("provisorio", Boolean.class)),
                Optional.ofNullable(f.get("casos", Integer.class)),
                f.get("familia", String.class),
                f.get("dueno_familia", String.class),
                f.get("sentido_meta", String.class),
                f.get("version", String.class),
                f.get("minimo_casos", Integer.class),
                f.get("calculado_en", OffsetDateTime.class));
    }
}
