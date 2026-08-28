package bo.aportaya.organizador.infraestructura;

import bo.aportaya.organizador.dominio.PuntajeDeDesempeno;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code evaluacion_desempeno}, {@code metrica_organizador}, {@code sancion_organizador}
 * y {@code apelacion_sancion_org}.
 *
 * <p>Cada evaluacion guarda **sus metricas**, no solo el total. Si alguien va a perder
 * su habilitacion por un numero, ese numero tiene que poder abrirse metrica por
 * metrica: «el sistema lo calculo» no se puede apelar.
 */
@Component
public class DesempenoRepositorio {

    public Optional<UUID> evaluacionDe(DSLContext dsl, UUID organizadorId, String periodo) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("organizador", "evaluacion_desempeno")))
                .where(DSL.field("organizador_id", UUID.class)
                        .eq(organizadorId)
                        .and(DSL.field("periodo_evaluado", String.class).eq(periodo)))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID registrarEvaluacion(
            DSLContext dsl,
            UUID organizadorId,
            String periodo,
            BigDecimal morosidad,
            BigDecimal finalizacion,
            BigDecimal satisfaccion,
            BigDecimal tiempoRespuesta,
            int incidenciasAbiertas,
            int coberturasConsumidas,
            BigDecimal puntajeGlobal,
            String nivelSugerido,
            String accionRecomendada,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("organizador", "evaluacion_desempeno")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("organizador_id", UUID.class), organizadorId)
                .set(DSL.field("periodo_evaluado", String.class), periodo)
                .set(DSL.field("indice_morosidad_cartera", BigDecimal.class), morosidad)
                .set(DSL.field("tasa_finalizacion_grupos", BigDecimal.class), finalizacion)
                .set(DSL.field("satisfaccion_participantes", BigDecimal.class), satisfaccion)
                .set(DSL.field("tiempo_respuesta_promedio_horas", BigDecimal.class), tiempoRespuesta)
                .set(DSL.field("incidencias_abiertas", Short.class), (short) incidenciasAbiertas)
                .set(DSL.field("coberturas_consumidas", Short.class), (short) coberturasConsumidas)
                .set(DSL.field("puntaje_global", BigDecimal.class), puntajeGlobal)
                .set(DSL.field("nivel_sugerido", String.class), nivelSugerido)
                .set(DSL.field("accion_recomendada", String.class), accionRecomendada)
                .set(DSL.field("evaluado_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /** Las metricas que sostienen el puntaje. Sin ellas el numero no se puede defender. */
    public void guardarMetricas(DSLContext dsl, UUID evaluacionId, List<PuntajeDeDesempeno.Metrica> metricas) {
        for (var metrica : metricas) {
            dsl.insertInto(DSL.table(DSL.name("organizador", "metrica_organizador")))
                    .set(DSL.field("id", UUID.class), UUID.randomUUID())
                    .set(DSL.field("evaluacion_id", UUID.class), evaluacionId)
                    .set(DSL.field("codigo", String.class), metrica.codigo())
                    .set(DSL.field("valor", BigDecimal.class), metrica.valor())
                    .set(DSL.field("meta", BigDecimal.class), metrica.meta())
                    .set(DSL.field("cumple", Boolean.class), metrica.cumple())
                    .set(DSL.field("peso", BigDecimal.class), metrica.peso())
                    .execute();
        }
    }

    public UUID sancionar(
            DSLContext dsl,
            UUID organizadorId,
            UUID evaluacionId,
            String tipo,
            String motivo,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            UUID aplicadaPor) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("organizador", "sancion_organizador")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("organizador_id", UUID.class), organizadorId)
                .set(DSL.field("evaluacion_id", UUID.class), evaluacionId)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("vigente_desde", OffsetDateTime.class), desde)
                .set(DSL.field("vigente_hasta", OffsetDateTime.class), hasta)
                .set(DSL.field("estado", String.class), "VIGENTE")
                .set(DSL.field("aplicada_por", UUID.class), aplicadaPor)
                .execute();
        return id;
    }

    public Optional<Sancion> verSancion(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("organizador_id", UUID.class),
                        DSL.field("tipo", String.class),
                        DSL.field("estado", String.class),
                        DSL.field("vigente_desde", OffsetDateTime.class),
                        DSL.field("aplicada_por", UUID.class))
                .from(DSL.table(DSL.name("organizador", "sancion_organizador")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Sancion(
                        f.get("id", UUID.class),
                        f.get("organizador_id", UUID.class),
                        f.get("tipo", String.class),
                        f.get("estado", String.class),
                        f.get("vigente_desde", OffsetDateTime.class),
                        f.get("aplicada_por", UUID.class)));
    }

    public boolean cambiarEstadoDeSancion(DSLContext dsl, UUID id, String desde, String hacia) {
        return dsl.update(DSL.table(DSL.name("organizador", "sancion_organizador")))
                        .set(DSL.field("estado", String.class), hacia)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).eq(desde)))
                        .execute()
                == 1;
    }

    public Optional<UUID> apelacionDe(DSLContext dsl, UUID sancionId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("organizador", "apelacion_sancion_org")))
                .where(DSL.field("sancion_organizador_id", UUID.class).eq(sancionId))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    /**
     * Escribe la apelacion **ya resuelta**, entera.
     *
     * <p>No hay forma de guardar una abierta: {@code ck_apelacion_org_resuelta} exige
     * el estado {@code PENDIENTE} —que {@code ck_apelacion_sancion_org_estado} no
     * admite— o los tres campos de resolucion presentes. Mientras la apelacion esta
     * abierta, lo que la registra es el estado {@code APELADA} de la sancion y el
     * evento con el argumento. Es el hueco H-7 del informe del carril.
     */
    public UUID registrarApelacionResuelta(
            DSLContext dsl,
            UUID sancionId,
            String argumento,
            String evidenciasJson,
            String estado,
            UUID resueltaPor,
            String resolucion,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("organizador", "apelacion_sancion_org")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("sancion_organizador_id", UUID.class), sancionId)
                .set(DSL.field("argumento", String.class), argumento)
                .set(DSL.field("evidencias", org.jooq.JSONB.class), org.jooq.JSONB.valueOf(evidenciasJson))
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("resuelta_por", UUID.class), resueltaPor)
                .set(DSL.field("resolucion", String.class), resolucion)
                .set(DSL.field("presentada_en", OffsetDateTime.class), ahora)
                .set(DSL.field("resuelta_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public record Sancion(
            UUID id, UUID organizadorId, String tipo, String estado, OffsetDateTime vigenteDesde, UUID aplicadaPor) {}
}
