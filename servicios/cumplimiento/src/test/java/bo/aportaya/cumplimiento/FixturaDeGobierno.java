package bo.aportaya.cumplimiento;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;

/**
 * Las filas de gobierno, consumidor financiero y continuidad.
 *
 * <p>Separada de {@link FixturaDeUif} porque son dos mundos: uno arma operaciones y
 * umbrales, el otro arma comites, reclamos y planes. Juntas pasaban las 300 lineas, que
 * es la señal de que nadie las habia separado.
 */
class FixturaDeGobierno {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(90_200_000);

    private final DSLContext dsl;

    FixturaDeGobierno(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID puntoDeReclamo(String codigo, String tipo, boolean activo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.punto_reclamo (id, codigo, tipo, descripcion, horario, activo)
                VALUES (?, ?, ?, 'Canal de prueba', '08:00-18:00', ?)
                """,
                id,
                codigo,
                tipo,
                activo);
        return id;
    }

    UUID comite(String tipo, int quorum, String composicionJson, String periodicidad) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.comite_gobierno
                    (id, tipo, periodicidad_minima, composicion_requerida, quorum_minimo, activo)
                VALUES (?, ?, ?, ?::jsonb, ?, true)
                """,
                id,
                tipo,
                periodicidad,
                composicionJson,
                (short) quorum);
        return id;
    }

    UUID planDeContinuidad(String proceso, int rto, int rpo, int periodicidadMeses, LocalDate proximaPrueba) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.plan_continuidad
                    (id, proceso_critico, rto_minutos, rpo_minutos, estrategia,
                     periodicidad_prueba_meses, vigente_desde, proxima_prueba)
                VALUES (?, ?, ?, ?, 'Conmutacion a sitio alterno', ?, current_date - 30, ?)
                """,
                id,
                proceso,
                rto,
                rpo,
                (short) periodicidadMeses,
                proximaPrueba);
        return id;
    }

    UUID reglaDeMonitoreo(String codigo, String severidad, boolean activa) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.regla_monitoreo_lft
                    (id, codigo, tipologia, descripcion, expresion, ventana_evaluacion, severidad,
                     accion_automatica, fuente_normativa, activa, vigente_desde)
                VALUES (?, ?, 'FRACCIONAMIENTO', 'Regla de prueba', '{}'::jsonb, 'DIA', ?,
                        'SOLO_ALERTAR', 'Instructivo EIF art. 54', ?, now())
                """,
                id,
                codigo,
                severidad,
                activa);
        return id;
    }

    UUID alerta(UUID reglaId, UUID usuarioId, String severidad, String monto, OffsetDateTime cuando) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.alerta_monitoreo_lft
                    (id, regla_monitoreo_id, usuario_id, monto_involucrado, detalle, severidad,
                     estado, detectada_en)
                VALUES (?, ?, ?, ?::numeric, '{"patron":"fraccionamiento"}'::jsonb, ?, 'ABIERTA', ?::timestamptz)
                """,
                id,
                reglaId,
                usuarioId,
                monto,
                severidad,
                cuando.toString());
        return id;
    }
}
