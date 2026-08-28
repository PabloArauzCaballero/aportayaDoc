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

    /**
     * Un reporte de operacion sospechosa ya radicado.
     *
     * <p>Vive en el esquema de {@code auditoria}: cumplimiento lo referencia pero no lo
     * escribe (invariante 11). La fixtura lo arma porque la clave foranea lo exige.
     */
    UUID reporteSospechoso(UUID usuarioId) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO auditoria.reporte_operacion_sospechosa
                    (id, usuario_id, tipologia, monto_total, periodo_analizado, narrativa, estado)
                VALUES (?, ?, 'FRACCIONAMIENTO', 5000.00, ?, 'Narrativa de prueba del carril', 'BORRADOR')
                """,
                id,
                usuarioId,
                "2026-08");
        return id;
    }

    /** Un limite operativo del catalogo, con su base normativa. */
    UUID limite(String concepto, String nivel, String ventana, String montoMaximo, Integer cantidadMaxima) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO catalogo.limite_operativo_billetera
                    (id, concepto, nivel_debida_diligencia, ventana, monto_maximo, cantidad_maxima,
                     moneda, base_normativa, vigente_desde, activo)
                VALUES (?, ?, ?, ?, ?::numeric, ?, 'BOB', 'Limites BCB — prueba del carril',
                        current_date - 30, true)
                """,
                id,
                concepto,
                nivel,
                ventana,
                montoMaximo,
                cantidadMaxima);
        return id;
    }

    /**
     * El consumo acumulado de una ventana vigente.
     *
     * <p>Lo escribe el nucleo financiero al aplicar cada operacion; la fixtura lo arma
     * porque CU-40 solo lo LEE para decidir (invariante 11).
     */
    void consumo(UUID cuentaId, UUID limiteId, String monto, int cantidad) {
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.consumo_limite
                    (id, cuenta_billetera_id, limite_id, ventana_inicio, ventana_fin,
                     monto_acumulado, cantidad_acumulada, actualizado_en)
                VALUES (gen_random_uuid(), ?, ?, date_trunc('month', now()),
                        date_trunc('month', now()) + interval '1 month' - interval '1 second',
                        ?::numeric, ?, now())
                """,
                cuentaId,
                limiteId,
                monto,
                cantidad);
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
        // `uq_comite_gobierno_tipo`: hay UN comite de riesgos, no varios. Si ya esta, se
        // reusa con la composicion que la prueba pide.
        var existente = dsl.fetchOne("SELECT id FROM cumplimiento.comite_gobierno WHERE tipo = ?", tipo);
        if (existente != null) {
            UUID previo = existente.get(0, UUID.class);
            dsl.execute(
                    """
                    UPDATE cumplimiento.comite_gobierno
                       SET quorum_minimo = ?, composicion_requerida = ?::jsonb,
                           periodicidad_minima = ?, activo = true
                     WHERE id = ?
                    """,
                    (short) quorum,
                    composicionJson,
                    periodicidad,
                    previo);
            return previo;
        }
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

    /**
     * Un acta de comite minima.
     *
     * <p>La exige {@code ck_prueba_resultado}: una prueba EXITOSA sin acta que la
     * reporte no se puede guardar, y esta bien que asi sea.
     */
    UUID actaMinima() {
        UUID comiteId = comite("RIESGOS", 3, "[\"RIESGOS\", \"TECNOLOGIA\", \"CUMPLIMIENTO\"]", "TRIMESTRAL");
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.acta_comite
                    (id, comite_gobierno_id, numero, fecha, asistentes, cumple_quorum,
                     temas_tratados, decisiones, url_documento, hash_documento)
                VALUES (?, ?, ?, current_date, '[]'::jsonb, true, '[]'::jsonb, '[]'::jsonb,
                        'https://actas.bo/x', ?)
                """,
                id,
                comiteId,
                "ACTA-" + SECUENCIA.incrementAndGet(),
                "a".repeat(64));
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
