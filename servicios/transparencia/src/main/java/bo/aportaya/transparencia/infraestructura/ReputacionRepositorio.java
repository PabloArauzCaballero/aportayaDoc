package bo.aportaya.transparencia.infraestructura;

import bo.aportaya.transparencia.dominio.PuntajeDeReputacion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code evento_reputacion}, {@code puntaje_reputacion}, sus componentes y las fotos.
 *
 * <p>El evento de reputacion es **append-only**: puntuar un hecho y despues borrarlo
 * dejaria un puntaje que nadie puede reconstruir. Corregir es registrar el evento
 * inverso, con su referencia al original.
 *
 * <p>El puntaje **cuadra con sus componentes** ({@code tg_puntaje_cuadra}, R-REP-03):
 * la base rechaza un total que no sea la suma. Es lo que hace que el numero se pueda
 * apelar.
 */
@Component
public class ReputacionRepositorio {

    /** El evento de este hecho, si ya se registro (R-REP-01). */
    public Optional<UUID> eventoDelHecho(
            DSLContext dsl, UUID usuarioId, String referenciaTipo, UUID referenciaId, String tipo) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("transparencia", "evento_reputacion")))
                .where(DSL.field("usuario_id", UUID.class)
                        .eq(usuarioId)
                        .and(DSL.field("referencia_tipo", String.class).eq(referenciaTipo))
                        .and(DSL.field("referencia_origen_id", UUID.class).eq(referenciaId))
                        .and(DSL.field("tipo", String.class).eq(tipo)))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID registrarEvento(
            DSLContext dsl,
            UUID usuarioId,
            UUID grupoId,
            UUID participanteId,
            String tipo,
            String referenciaTipo,
            UUID referenciaId,
            BigDecimal impacto,
            String factorAfectado,
            String descripcion,
            String modeloVersion,
            boolean esReversible,
            OffsetDateTime ocurridoEn) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("transparencia", "evento_reputacion")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("grupo_id", UUID.class), grupoId)
                .set(DSL.field("participante_id", UUID.class), participanteId)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("referencia_tipo", String.class), referenciaTipo)
                .set(DSL.field("referencia_origen_id", UUID.class), referenciaId)
                .set(DSL.field("impacto", BigDecimal.class), impacto)
                .set(DSL.field("factor_afectado", String.class), factorAfectado)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("modelo_version", String.class), modeloVersion)
                .set(DSL.field("es_reversible", Boolean.class), esReversible)
                .set(DSL.field("ocurrido_en", OffsetDateTime.class), ocurridoEn)
                .execute();
        return id;
    }

    public Optional<Evento> eventoPorId(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("grupo_id", UUID.class),
                        DSL.field("participante_id", UUID.class),
                        DSL.field("tipo", String.class),
                        DSL.field("referencia_tipo", String.class),
                        DSL.field("impacto", BigDecimal.class),
                        DSL.field("factor_afectado", String.class),
                        DSL.field("modelo_version", String.class),
                        DSL.field("es_reversible", Boolean.class))
                .from(DSL.table(DSL.name("transparencia", "evento_reputacion")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Evento(
                        f.get("id", UUID.class),
                        f.get("usuario_id", UUID.class),
                        f.get("grupo_id", UUID.class),
                        f.get("participante_id", UUID.class),
                        f.get("tipo", String.class),
                        f.get("referencia_tipo", String.class),
                        f.get("impacto", BigDecimal.class),
                        f.get("factor_afectado", String.class),
                        f.get("modelo_version", String.class),
                        f.get("es_reversible", Boolean.class)));
    }

    /** La compensacion de un evento, si ya se registro. */
    public Optional<UUID> compensacionDe(DSLContext dsl, UUID eventoOriginalId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("transparencia", "evento_reputacion")))
                .where(DSL.field("revertido_por_id", UUID.class).eq(eventoOriginalId))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    /**
     * El evento compensatorio.
     *
     * <p>Va con {@code referencia_origen_id} nulo **a proposito**: la unicidad de
     * R-REP-01 es (usuario, referencia_tipo, referencia_origen_id, tipo) y el catalogo
     * cerrado de {@code ck_evento_reputacion_tipo} no tiene un valor para «reversa»,
     * asi que el compensatorio repite el tipo del original. Con la referencia puesta,
     * chocaria contra el propio evento que viene a deshacer. El vinculo lo lleva
     * {@code revertido_por_id}.
     */
    public UUID registrarCompensacion(
            DSLContext dsl,
            Evento original,
            BigDecimal impacto,
            String descripcion,
            UUID eventoOriginalId,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("transparencia", "evento_reputacion")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), original.usuarioId())
                .set(DSL.field("grupo_id", UUID.class), original.grupoId())
                .set(DSL.field("participante_id", UUID.class), original.participanteId())
                .set(DSL.field("tipo", String.class), original.tipo())
                .set(DSL.field("referencia_tipo", String.class), original.referenciaTipo())
                .set(DSL.field("referencia_origen_id", UUID.class), (UUID) null)
                .set(DSL.field("impacto", BigDecimal.class), impacto)
                .set(DSL.field("factor_afectado", String.class), original.factorAfectado())
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("modelo_version", String.class), original.modeloVersion())
                .set(DSL.field("es_reversible", Boolean.class), false)
                .set(DSL.field("revertido_por_id", UUID.class), eventoOriginalId)
                .set(DSL.field("ocurrido_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /** Cuantos eventos del usuario entran en la ventana del modelo. */
    public int eventosEnVentana(DSLContext dsl, UUID usuarioId, OffsetDateTime desde) {
        return dsl.fetchCount(
                DSL.table(DSL.name("transparencia", "evento_reputacion")),
                DSL.field("usuario_id", UUID.class)
                        .eq(usuarioId)
                        .and(DSL.field("ocurrido_en", OffsetDateTime.class).ge(desde)));
    }

    /** Cuantas veces se repitio ese tipo de evento: define la reincidencia. */
    public int repeticionesDe(DSLContext dsl, UUID usuarioId, String tipo) {
        return dsl.fetchCount(
                DSL.table(DSL.name("transparencia", "evento_reputacion")),
                DSL.field("usuario_id", UUID.class)
                        .eq(usuarioId)
                        .and(DSL.field("tipo", String.class).eq(tipo)));
    }

    public Optional<Puntaje> puntajeDe(DSLContext dsl, UUID usuarioId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("puntaje", BigDecimal.class),
                        DSL.field("nivel_confianza", String.class),
                        DSL.field("modelo_version", String.class),
                        DSL.field("eventos_considerados", Integer.class),
                        DSL.field("calculado_en", OffsetDateTime.class))
                .from(DSL.table(DSL.name("transparencia", "puntaje_reputacion")))
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .fetchOptional(f -> new Puntaje(
                        f.get("id", UUID.class),
                        f.get("puntaje", BigDecimal.class),
                        f.get("nivel_confianza", String.class),
                        f.get("modelo_version", String.class),
                        f.get("eventos_considerados", Integer.class),
                        f.get("calculado_en", OffsetDateTime.class)));
    }

    /**
     * Borra el puntaje vigente y sus componentes. Un solo puntaje por usuario
     * (R-REP-02, y {@code uq_puntaje_reputacion_usuario_id} no admite ni un historico).
     *
     * <p>Los componentes van **primero**: {@code fk_componente_score_puntaje_id} es
     * {@code ON DELETE RESTRICT} y el puntaje no se puede borrar mientras existan.
     */
    public void borrarPuntaje(DSLContext dsl, UUID usuarioId) {
        dsl.deleteFrom(DSL.table(DSL.name("transparencia", "componente_score")))
                .where(DSL.field("puntaje_id", UUID.class)
                        .in(dsl.select(DSL.field("id", UUID.class))
                                .from(DSL.table(DSL.name("transparencia", "puntaje_reputacion")))
                                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))))
                .execute();
        dsl.deleteFrom(DSL.table(DSL.name("transparencia", "puntaje_reputacion")))
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .execute();
    }

    /**
     * Escribe el puntaje con sus componentes.
     *
     * <p>El orden importa y **es el contrario al que uno esperaria**. La clave foranea
     * {@code fk_componente_score_puntaje_id} no es diferible: un componente no se puede
     * escribir antes que su puntaje. El disparador que verifica que el total cuadre con
     * la suma ({@code tg_puntaje_cuadra}, R-REP-03) si es
     * {@code DEFERRABLE INITIALLY DEFERRED}, y por eso corre al confirmar, cuando los
     * componentes ya estan. Primero el puntaje, despues sus partes, y la base verifica
     * al final.
     */
    public UUID guardarPuntaje(
            DSLContext dsl,
            UUID usuarioId,
            UUID modeloId,
            String modeloVersion,
            PuntajeDeReputacion.Resultado resultado,
            Indicadores indicadores,
            int eventosConsiderados,
            OffsetDateTime ahora,
            OffsetDateTime proximoRecalculo) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("transparencia", "puntaje_reputacion")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("modelo_id", UUID.class), modeloId)
                .set(DSL.field("puntaje", BigDecimal.class), resultado.puntaje())
                .set(DSL.field("nivel_confianza", String.class), resultado.nivelDeConfianza())
                .set(DSL.field("indice_puntualidad", BigDecimal.class), indicadores.puntualidad())
                .set(DSL.field("tasa_incumplimiento", BigDecimal.class), indicadores.incumplimiento())
                .set(DSL.field("monto_total_aportado", BigDecimal.class), indicadores.montoAportado())
                .set(DSL.field("grupos_completados", Short.class), (short) indicadores.gruposCompletados())
                .set(DSL.field("grupos_abandonados", Short.class), (short) indicadores.gruposAbandonados())
                .set(DSL.field("incumplimientos_abiertos", Short.class), (short) indicadores.incumplimientosAbiertos())
                .set(DSL.field("antiguedad_meses", Short.class), (short) indicadores.antiguedadMeses())
                .set(DSL.field("eventos_considerados", Integer.class), eventosConsiderados)
                .set(DSL.field("modelo_version", String.class), modeloVersion)
                .set(DSL.field("vigente_desde", OffsetDateTime.class), ahora)
                .set(DSL.field("calculado_en", OffsetDateTime.class), ahora)
                .set(DSL.field("proximo_recalculo_en", OffsetDateTime.class), proximoRecalculo)
                .execute();

        for (var componente : resultado.componentes()) {
            dsl.insertInto(DSL.table(DSL.name("transparencia", "componente_score")))
                    .set(DSL.field("id", UUID.class), UUID.randomUUID())
                    .set(DSL.field("puntaje_id", UUID.class), id)
                    .set(DSL.field("codigo_factor", String.class), componente.codigo())
                    .set(DSL.field("valor_crudo", BigDecimal.class), componente.valorCrudo())
                    .set(DSL.field("valor_normalizado", BigDecimal.class), componente.valorNormalizado())
                    .set(DSL.field("contribucion", BigDecimal.class), componente.contribucion())
                    .set(DSL.field("tendencia", String.class), componente.tendencia())
                    .execute();
        }
        return id;
    }

    public record Evento(
            UUID id,
            UUID usuarioId,
            UUID grupoId,
            UUID participanteId,
            String tipo,
            String referenciaTipo,
            BigDecimal impacto,
            String factorAfectado,
            String modeloVersion,
            boolean esReversible) {}

    public record Puntaje(
            UUID id,
            BigDecimal puntaje,
            String nivelConfianza,
            String modeloVersion,
            int eventosConsiderados,
            OffsetDateTime calculadoEn) {}

    public record Indicadores(
            BigDecimal puntualidad,
            BigDecimal incumplimiento,
            BigDecimal montoAportado,
            int gruposCompletados,
            int gruposAbandonados,
            int incumplimientosAbiertos,
            int antiguedadMeses) {}
}
