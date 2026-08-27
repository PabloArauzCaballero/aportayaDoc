package bo.aportaya.cumplimiento.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code revision_periodica_kyc}, {@code desvio_perfil} y {@code alerta_monitoreo_lft}. */
@Component
public class RevisionKycRepositorio {

    /**
     * ¿Ya hay una revision para ese usuario en esa fecha programada?
     *
     * <p>Es lo que hace idempotente al trabajo programado: si el planificador dispara
     * dos veces el mismo dia —o si se reintenta tras un fallo—, no se abren dos
     * revisiones del mismo periodo.
     */
    public Optional<UUID> deLaFecha(DSLContext dsl, UUID usuarioId, LocalDate fechaProgramada) {
        Record fila = dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("cumplimiento", "revision_periodica_kyc")))
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .and(DSL.field("fecha_programada", LocalDate.class).eq(fechaProgramada))
                .fetchOne();
        return Optional.ofNullable(fila).map(f -> f.get("id", UUID.class));
    }

    public UUID programar(DSLContext dsl, UUID usuarioId, Optional<UUID> calificacionId, LocalDate fechaProgramada) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "revision_periodica_kyc")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("calificacion_riesgo_id", UUID.class), calificacionId.orElse(null))
                .set(DSL.field("fecha_programada", LocalDate.class), fechaProgramada)
                .set(DSL.field("estado", String.class), "PROGRAMADA")
                .execute();
        return id;
    }

    public void ejecutar(
            DSLContext dsl, UUID revisionId, String resultado, UUID ejecutadaPor, LocalDate fechaEjecutada) {
        dsl.update(DSL.table(DSL.name("cumplimiento", "revision_periodica_kyc")))
                .set(DSL.field("estado", String.class), "EJECUTADA")
                .set(DSL.field("resultado", String.class), resultado)
                .set(DSL.field("ejecutada_por", UUID.class), ejecutadaPor)
                .set(DSL.field("fecha_ejecutada", LocalDate.class), fechaEjecutada)
                .where(DSL.field("id", UUID.class).eq(revisionId))
                .execute();
    }

    /** Las que ya pasaron su fecha y nadie ejecuto. */
    public int marcarVencidas(DSLContext dsl, LocalDate hoy) {
        return dsl.update(DSL.table(DSL.name("cumplimiento", "revision_periodica_kyc")))
                .set(DSL.field("estado", String.class), "VENCIDA")
                .where(DSL.field("estado").eq("PROGRAMADA"))
                .and(DSL.field("fecha_programada", LocalDate.class).lt(hoy))
                .execute();
    }

    /**
     * Registra —o actualiza— el desvio del periodo.
     *
     * <p>{@code uq_desvio_perfil_usuario_id_periodo} manda: **un desvio por cliente y
     * por periodo**, no uno por corrida. Si el trabajo vuelve a correr dentro del
     * mismo mes con un observado distinto, la fila se actualiza; abrir una segunda
     * contaria dos veces el mismo mes y falsearia el historial de desvios.
     */
    public UUID registrarDesvio(
            DSLContext dsl,
            UUID usuarioId,
            UUID perfilId,
            Optional<UUID> alertaId,
            String periodo,
            BigDecimal observado,
            BigDecimal esperado,
            BigDecimal desvioPorcentual,
            String severidad,
            OffsetDateTime momento) {

        UUID id = UUID.randomUUID();
        var tabla = DSL.table(DSL.name("cumplimiento", "desvio_perfil"));
        dsl.insertInto(tabla)
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("perfil_transaccional_id", UUID.class), perfilId)
                .set(DSL.field("alerta_monitoreo_id", UUID.class), alertaId.orElse(null))
                .set(DSL.field("periodo", String.class), periodo)
                .set(DSL.field("monto_observado", BigDecimal.class), observado)
                .set(DSL.field("monto_esperado", BigDecimal.class), esperado)
                .set(DSL.field("desvio_porcentual", BigDecimal.class), desvioPorcentual)
                .set(DSL.field("severidad", String.class), severidad)
                .set(DSL.field("estado", String.class), "DETECTADO")
                .set(DSL.field("detectado_en", OffsetDateTime.class), momento)
                .onConflict(DSL.field("usuario_id", UUID.class), DSL.field("periodo", String.class))
                .doUpdate()
                .set(DSL.field("monto_observado", BigDecimal.class), observado)
                .set(DSL.field("desvio_porcentual", BigDecimal.class), desvioPorcentual)
                .set(DSL.field("severidad", String.class), severidad)
                .set(DSL.field("detectado_en", OffsetDateTime.class), momento)
                .execute();

        return dsl.select(DSL.field("id", UUID.class))
                .from(tabla)
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .and(DSL.field("periodo", String.class).eq(periodo))
                .fetchOne(DSL.field("id", UUID.class));
    }

    /**
     * Abre la alerta de monitoreo.
     *
     * <p>Exige una {@code regla_monitoreo_lft}: la clave foranea es RESTRICT, y con
     * razon — una alerta sin la regla que la origino no se puede explicar ni defender
     * ante el supervisor.
     */
    public UUID abrirAlerta(
            DSLContext dsl,
            UUID reglaId,
            UUID usuarioId,
            BigDecimal montoInvolucrado,
            String detalle,
            String severidad,
            OffsetDateTime momento) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "alerta_monitoreo_lft")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("regla_monitoreo_id", UUID.class), reglaId)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("monto_involucrado", BigDecimal.class), montoInvolucrado)
                // `detalle` es jsonb: el motivo viaja como objeto, no como frase suelta,
                // para que el backoffice pueda filtrar por sus campos.
                .set(DSL.field("detalle", JSONB.class), JSONB.valueOf(detalle))
                .set(DSL.field("severidad", String.class), severidad)
                .set(DSL.field("estado", String.class), "ABIERTA")
                .set(DSL.field("detectada_en", OffsetDateTime.class), momento)
                .execute();
        return id;
    }

    /** La regla vigente de desvio de perfil, si esta cargada. */
    public Optional<UUID> reglaDeDesvio(DSLContext dsl, String codigo) {
        Record fila = dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("cumplimiento", "regla_monitoreo_lft")))
                .where(DSL.field("codigo").eq(codigo))
                .and(DSL.field("activa", Boolean.class).isTrue())
                .fetchOne();
        return Optional.ofNullable(fila).map(f -> f.get("id", UUID.class));
    }
}
