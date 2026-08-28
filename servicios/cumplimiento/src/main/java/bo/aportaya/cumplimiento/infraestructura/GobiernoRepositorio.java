package bo.aportaya.cumplimiento.infraestructura;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code comite_gobierno}, {@code acta_comite}, {@code oficial_cumplimiento},
 * {@code capacitacion_cumplimiento} y {@code hallazgo_auditoria}.
 *
 * <p>Todo lo que hace que una decision de gobierno se pueda defender: quien estuvo,
 * quien voto que, con que fundamento y quien quedo comprometido a hacer que.
 *
 * <p>{@code uq_oficial_titular_activo} es un indice unico parcial sobre una constante:
 * **hay un titular activo o no hay ninguno** (R-UIF-12). Dos titulares es no tener
 * ninguno, porque ante el regulador responde una sola persona.
 */
@Component
public class GobiernoRepositorio {

    // ----------------------------------------------------------------- comites

    public Optional<Comite> comitePorTipo(DSLContext dsl, String tipo) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("quorum_minimo", Short.class),
                        DSL.field("composicion_requerida", JSONB.class),
                        DSL.field("periodicidad_minima", String.class),
                        DSL.field("activo", Boolean.class))
                .from(DSL.table(DSL.name("cumplimiento", "comite_gobierno")))
                .where(DSL.field("tipo", String.class).eq(tipo))
                .fetchOptional(f -> new Comite(
                        f.get("id", UUID.class),
                        f.get("quorum_minimo", Short.class).intValue(),
                        rolesDe(f.get("composicion_requerida", JSONB.class)),
                        f.get("periodicidad_minima", String.class),
                        f.get("activo", Boolean.class)));
    }

    /** La composicion llega como arreglo JSON de roles. Se lee, no se adivina. */
    private Set<String> rolesDe(JSONB composicion) {
        String texto = composicion == null ? "" : composicion.data();
        var roles = new java.util.LinkedHashSet<String>();
        var m = java.util.regex.Pattern.compile("\"([A-Z_]{2,40})\"").matcher(texto);
        while (m.find()) {
            roles.add(m.group(1));
        }
        return roles;
    }

    public UUID levantarActa(
            DSLContext dsl,
            UUID comiteId,
            UUID elaboradaPor,
            String numero,
            LocalDate fecha,
            String asistentesJson,
            boolean cumpleQuorum,
            String temasJson,
            String decisionesJson,
            String url,
            String hash) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "acta_comite")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("comite_gobierno_id", UUID.class), comiteId)
                .set(DSL.field("elaborada_por", UUID.class), elaboradaPor)
                .set(DSL.field("numero", String.class), numero)
                .set(DSL.field("fecha", LocalDate.class), fecha)
                .set(DSL.field("asistentes", JSONB.class), JSONB.valueOf(asistentesJson))
                .set(DSL.field("cumple_quorum", Boolean.class), cumpleQuorum)
                .set(DSL.field("temas_tratados", JSONB.class), JSONB.valueOf(temasJson))
                .set(DSL.field("decisiones", JSONB.class), JSONB.valueOf(decisionesJson))
                .set(DSL.field("url_documento", String.class), url)
                .set(DSL.field("hash_documento", String.class), hash)
                .execute();
        return id;
    }

    public Optional<LocalDate> ultimaSesion(DSLContext dsl, UUID comiteId) {
        return dsl.select(DSL.max(DSL.field("fecha", LocalDate.class)).as("fecha"))
                .from(DSL.table(DSL.name("cumplimiento", "acta_comite")))
                .where(DSL.field("comite_gobierno_id", UUID.class).eq(comiteId))
                .fetchOptional(f -> f.get("fecha", LocalDate.class));
    }

    // ---------------------------------------------------------- oficial y capacitacion

    public Optional<Oficial> titularActivo(DSLContext dsl) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("tipo", String.class),
                        DSL.field("fecha_designacion", LocalDate.class))
                .from(DSL.table(DSL.name("cumplimiento", "oficial_cumplimiento")))
                .where(DSL.field("activo", Boolean.class)
                        .isTrue()
                        .and(DSL.field("tipo", String.class).eq("TITULAR")))
                .fetchOptional(f -> new Oficial(
                        f.get("id", UUID.class),
                        f.get("usuario_id", UUID.class),
                        f.get("tipo", String.class),
                        f.get("fecha_designacion", LocalDate.class)));
    }

    public Optional<Oficial> suplenteActivo(DSLContext dsl) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("tipo", String.class),
                        DSL.field("fecha_designacion", LocalDate.class))
                .from(DSL.table(DSL.name("cumplimiento", "oficial_cumplimiento")))
                .where(DSL.field("activo", Boolean.class)
                        .isTrue()
                        .and(DSL.field("tipo", String.class).eq("SUPLENTE")))
                .orderBy(DSL.field("fecha_designacion").desc())
                .limit(1)
                .fetchOptional(f -> new Oficial(
                        f.get("id", UUID.class),
                        f.get("usuario_id", UUID.class),
                        f.get("tipo", String.class),
                        f.get("fecha_designacion", LocalDate.class)));
    }

    public UUID designar(DSLContext dsl, UUID usuarioId, String tipo, LocalDate fecha, String acta, boolean activo) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "oficial_cumplimiento")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("fecha_designacion", LocalDate.class), fecha)
                .set(DSL.field("acta_designacion", String.class), acta)
                .set(DSL.field("activo", Boolean.class), activo)
                .execute();
        return id;
    }

    /** La baja exige fecha ({@code ck_oficial_baja_coherente}): sin fecha no hay baja. */
    public boolean darDeBaja(DSLContext dsl, UUID oficialId, LocalDate fechaBaja) {
        return dsl.update(DSL.table(DSL.name("cumplimiento", "oficial_cumplimiento")))
                        .set(DSL.field("activo", Boolean.class), false)
                        .set(DSL.field("fecha_baja", LocalDate.class), fechaBaja)
                        .where(DSL.field("id", UUID.class)
                                .eq(oficialId)
                                .and(DSL.field("activo", Boolean.class).isTrue()))
                        .execute()
                == 1;
    }

    /** Promueve al suplente en la MISMA transaccion: no puede haber un dia sin oficial. */
    public boolean promoverASuplente(DSLContext dsl, UUID suplenteId, LocalDate fecha, String acta) {
        return dsl.update(DSL.table(DSL.name("cumplimiento", "oficial_cumplimiento")))
                        .set(DSL.field("tipo", String.class), "TITULAR")
                        .set(DSL.field("fecha_designacion", LocalDate.class), fecha)
                        .set(DSL.field("acta_designacion", String.class), acta)
                        .where(DSL.field("id", UUID.class)
                                .eq(suplenteId)
                                .and(DSL.field("activo", Boolean.class).isTrue()))
                        .execute()
                == 1;
    }

    public UUID registrarCapacitacion(
            DSLContext dsl,
            UUID usuarioId,
            String tema,
            String modalidad,
            java.math.BigDecimal horas,
            LocalDate fecha,
            java.math.BigDecimal calificacion,
            boolean aprobada,
            String evidenciaUrl,
            String periodo) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "capacitacion_cumplimiento")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("tema", String.class), tema)
                .set(DSL.field("modalidad", String.class), modalidad)
                .set(DSL.field("horas", java.math.BigDecimal.class), horas)
                .set(DSL.field("fecha", LocalDate.class), fecha)
                .set(DSL.field("calificacion", java.math.BigDecimal.class), calificacion)
                .set(DSL.field("aprobada", Boolean.class), aprobada)
                .set(DSL.field("evidencia_url", String.class), evidenciaUrl)
                .set(DSL.field("periodo", String.class), periodo)
                .execute();
        return id;
    }

    public List<UUID> aprobaronEnElPeriodo(DSLContext dsl, String periodo) {
        return dsl.selectDistinct(DSL.field("usuario_id", UUID.class))
                .from(DSL.table(DSL.name("cumplimiento", "capacitacion_cumplimiento")))
                .where(DSL.field("periodo", String.class)
                        .eq(periodo)
                        .and(DSL.field("aprobada", Boolean.class).isTrue()))
                .fetch(f -> f.get("usuario_id", UUID.class));
    }

    // ---------------------------------------------------------------- hallazgos

    /**
     * Abre un hallazgo si no hay ya uno abierto con el mismo codigo.
     *
     * <p>El control diario corre todos los dias: sin esta comprobacion, un reporte
     * vencido abriria un hallazgo por dia y el tablero de auditoria se volveria ilegible
     * justo cuando mas hace falta leerlo.
     */
    public Optional<UUID> abrirHallazgo(
            DSLContext dsl,
            String codigo,
            String origen,
            String descripcion,
            String severidad,
            String proceso,
            LocalDate identificacion,
            LocalDate plazo) {

        boolean yaExiste = dsl.fetchExists(DSL.selectFrom(DSL.table(DSL.name("cumplimiento", "hallazgo_auditoria")))
                .where(DSL.field("codigo", String.class)
                        .eq(codigo)
                        .and(DSL.field("estado", String.class).in("ABIERTO", "EN_REMEDIACION"))));
        if (yaExiste) {
            return Optional.empty();
        }
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "hallazgo_auditoria")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("codigo", String.class), codigo)
                .set(DSL.field("origen", String.class), origen)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("severidad", String.class), severidad)
                .set(DSL.field("proceso", String.class), proceso)
                .set(DSL.field("fecha_identificacion", LocalDate.class), identificacion)
                .set(DSL.field("plazo_regularizacion", LocalDate.class), plazo)
                .set(DSL.field("estado", String.class), "ABIERTO")
                .execute();
        return Optional.of(id);
    }

    public UUID abrirPlanDeAccion(
            DSLContext dsl, UUID hallazgoId, UUID responsableId, String descripcion, LocalDate fechaCompromiso) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "plan_accion_riesgo")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("hallazgo_id", UUID.class), hallazgoId)
                .set(DSL.field("responsable_id", UUID.class), responsableId)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("fecha_compromiso", LocalDate.class), fechaCompromiso)
                .set(DSL.field("estado", String.class), "PENDIENTE")
                .execute();
        return id;
    }

    public record Comite(
            UUID id, int quorumMinimo, Set<String> composicionRequerida, String periodicidad, boolean activo) {}

    public record Oficial(UUID id, UUID usuarioId, String tipo, LocalDate fechaDesignacion) {}
}
