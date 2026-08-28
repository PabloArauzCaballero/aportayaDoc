package bo.aportaya.tarifas.infraestructura;

import bo.aportaya.tarifas.dominio.SegmentoAplicable;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code segmento_comercial}.
 *
 * <p>El criterio es JSON evaluable, no texto libre: un criterio en prosa no se puede
 * verificar despues, y «se le aplico porque es buen cliente» no es una explicacion que
 * sirva ante un reclamo.
 */
@Component
public class SegmentoRepositorio {

    private final ObjectMapper json = new ObjectMapper();

    public List<SegmentoAplicable.Candidato> activos(DSLContext dsl) {
        return dsl.select(
                        DSL.field("codigo", String.class),
                        DSL.field("prioridad", Short.class),
                        DSL.field("criterio", JSONB.class))
                .from(DSL.table(DSL.name("tarifas", "segmento_comercial")))
                .where(DSL.field("activo", Boolean.class).isTrue())
                .orderBy(DSL.field("prioridad").asc())
                .fetch(f -> new SegmentoAplicable.Candidato(
                        f.get("codigo", String.class),
                        f.get("prioridad", Short.class),
                        aCriterio(f.get("criterio", JSONB.class))));
    }

    public boolean hayPrioridadOcupada(DSLContext dsl, int prioridad) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("tarifas", "segmento_comercial")),
                        DSL.field("prioridad", Short.class)
                                .eq((short) prioridad)
                                .and(DSL.field("activo", Boolean.class).isTrue()))
                > 0;
    }

    public UUID crear(DSLContext dsl, String codigo, String descripcion, String criterio, int prioridad) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("tarifas", "segmento_comercial")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("codigo", String.class), codigo)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("criterio", JSONB.class), JSONB.valueOf(criterio))
                .set(DSL.field("prioridad", Short.class), (short) prioridad)
                .set(DSL.field("activo", Boolean.class), true)
                .execute();
        return id;
    }

    /**
     * Traduce el JSON del criterio a hechos exigidos.
     *
     * <p>Solo admite pares hecho → minimo numerico. Cualquier otra forma no se
     * interpreta: adivinar que quiso decir un criterio malformado es como se aplica un
     * descuento que nadie aprobo.
     */
    private Map<String, Integer> aCriterio(JSONB criterio) {
        try {
            Map<?, ?> crudo = json.readValue(criterio.data(), Map.class);
            Map<String, Integer> exigencias = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entrada : crudo.entrySet()) {
                if (entrada.getValue() instanceof Number minimo) {
                    exigencias.put(String.valueOf(entrada.getKey()), minimo.intValue());
                }
            }
            return exigencias;
        } catch (com.fasterxml.jackson.core.JsonProcessingException mal) {
            throw new IllegalStateException("Criterio de segmento mal formado en la base", mal);
        }
    }
}
