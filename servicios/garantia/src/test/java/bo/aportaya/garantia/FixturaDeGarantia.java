package bo.aportaya.garantia;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;

/** Las filas que las pruebas de garantia necesitan. */
class FixturaDeGarantia {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(80_500_000);

    private final DSLContext dsl;

    FixturaDeGarantia(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Garantia', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'COMPLETO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "GAR-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

    record Escenario(UUID grupoId, UUID periodoId, UUID cupoId, UUID participanteId, UUID obligacionId) {}

    /** Un grupo con su periodo, cupo, participante y obligacion impaga. */
    Escenario escenario(UUID usuarioId) {
        UUID grupo = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.grupo
                    (id, codigo_publico, nombre, monto_aporte, moneda, periodicidad, dia_cobro,
                     num_periodos, cupos_totales, cupos_ocupados, fecha_inicio, fecha_fin_estimada,
                     estado, tipo_conformacion, modalidad_turnos, visibilidad, es_autogestionado,
                     requiere_kyc_minimo, reputacion_minima, dias_gracia, aplica_recargo_mora,
                     usa_fondo_garantia, porcentaje_fondo_garantia, quorum_decisiones)
                VALUES (?, ?, 'Grupo de prueba', 500.00, 'BOB', 'MENSUAL', 5, 3, 3, 3,
                        current_date, current_date + 90, 'ACTIVO', 'MANUAL_POR_INVITACION',
                        'SORTEO_ALEATORIO', 'PRIVADO', true, 'BASICO', 0, 3, true, true, 10.00, 0.600)
                """,
                grupo,
                "GRP-" + grupo.toString().substring(0, 8));

        UUID participante = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.participante
                    (id, grupo_id, usuario_id, estado, es_organizador, fecha_ingreso,
                     reputacion_al_ingresar, aportes_realizados, aportes_en_mora)
                VALUES (?, ?, ?, 'ACTIVO', false, now(), 50, 2, 1)
                """,
                participante,
                grupo,
                usuarioId);

        UUID cupo = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.cupo (id, grupo_id, numero, participante_id, estado, fraccion, asignado_en)
                VALUES (?, ?, 1, ?, 'OCUPADO', 1.0, now())
                """,
                cupo,
                grupo,
                participante);

        UUID periodo = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.periodo
                    (id, grupo_id, numero, fecha_inicio, fecha_limite_pago, fecha_fin_gracia,
                     fecha_entrega_prevista, estado, monto_objetivo, monto_recaudado, cupos_morosos)
                VALUES (?, ?, 1, current_date - 60, current_date - 30, current_date - 27,
                        current_date - 25, 'ABIERTO', 1500.00, 1000.00, 1)
                """,
                periodo,
                grupo);

        UUID obligacion = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO aportes.obligacion_aporte
                    (id, grupo_id, periodo_id, cupo_id, participante_id, tipo, monto_esperado,
                     moneda, monto_pagado, monto_recargo, monto_condonado, monto_cubierto_garantia,
                     estado, fecha_vencimiento, fecha_fin_gracia, dias_mora, version)
                VALUES (?, ?, ?, ?, ?, 'APORTE_PERIODICO', 500.00, 'BOB', 0, 0, 0, 0,
                        'EN_MORA', current_date - 30, current_date - 27, 30, 0)
                """,
                obligacion,
                grupo,
                periodo,
                cupo,
                participante);

        return new Escenario(grupo, periodo, cupo, participante, obligacion);
    }

    /**
     * Otro participante del mismo grupo.
     *
     * <p>Los aportantes del fondo tienen que existir de verdad: la devolucion los
     * referencia por clave foranea, y con identificadores inventados la prueba
     * verificaria un reparto que en produccion no podria escribirse.
     */
    UUID otroParticipante(UUID grupoId) {
        UUID usuario = usuario();
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.participante
                    (id, grupo_id, usuario_id, estado, es_organizador, fecha_ingreso,
                     reputacion_al_ingresar, aportes_realizados, aportes_en_mora)
                VALUES (?, ?, ?, 'ACTIVO', false, now(), 50, 3, 0)
                """,
                id,
                grupoId,
                usuario);
        return id;
    }

    UUID cuentaContable() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_contable
                    (id, codigo, nombre, tipo, naturaleza, nivel, es_cuenta_de_movimiento, saldo)
                VALUES (?, ?, 'Fondo de garantia', 'PASIVO', 'ACREEDORA', 4, true, 0)
                """,
                id,
                "2" + SECUENCIA.incrementAndGet());
        return id;
    }

    /** La politica del fondo. Es catalogo, no constantes del codigo. */
    UUID politica(
            UUID grupoId,
            String porcentajeMaximoPorAporte,
            String topePorParticipante,
            String topePorPeriodo,
            int maxCoberturas,
            String desdeAprobacionManual,
            int diasMoraParaActivar) {

        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO garantia.politica_cobertura
                    (id, grupo_id, porcentaje_constitucion, dias_mora_para_activar,
                     porcentaje_maximo_cobertura_por_aporte, tope_cobertura_por_participante,
                     tope_cobertura_por_periodo, max_coberturas_por_participante, exige_aval_previo,
                     requiere_aprobacion_manual_desde, plazo_recuperacion_dias,
                     tasa_recargo_recuperacion, vigente_desde)
                VALUES (?, ?, 10.00, ?, ?, ?, ?, ?, false, ?, 30, 2.00, now() - interval '1 day')
                """,
                id,
                grupoId,
                (short) diasMoraParaActivar,
                new BigDecimal(porcentajeMaximoPorAporte),
                new BigDecimal(topePorParticipante),
                new BigDecimal(topePorPeriodo),
                (short) maxCoberturas,
                new BigDecimal(desdeAprobacionManual));
        return id;
    }

    /** Un fondo del grupo con saldo. Los aportes entran como movimientos. */
    UUID fondo(UUID grupoId, UUID politicaId, String saldo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO garantia.fondo_garantia
                    (id, ambito, grupo_id, politica_cobertura_id, cuenta_contable_id, moneda,
                     saldo_disponible, saldo_comprometido, total_aportado, total_cubierto,
                     total_recuperado, total_castigado, estado, version)
                VALUES (?, 'POR_GRUPO', ?, ?, ?, 'BOB', ?, 0, ?, 0, 0, 0, 'ACTIVO', 0)
                """,
                id,
                grupoId,
                politicaId,
                cuentaContable(),
                new BigDecimal(saldo),
                new BigDecimal(saldo));
        return id;
    }

    /** Un aporte al fondo: es lo que define cuanto le vuelve a cada uno al cerrarlo. */
    void aportarAlFondo(UUID fondoId, UUID participanteId, String monto, String saldoResultante) {
        dsl.execute(
                """
                INSERT INTO garantia.movimiento_fondo
                    (id, fondo_id, tipo, monto, saldo_resultante, referencia_tipo, referencia_id,
                     descripcion, fecha)
                VALUES (gen_random_uuid(), ?, 'APORTE_PERIODICO', ?, ?, 'PARTICIPANTE', ?,
                        'Aporte al fondo', now())
                """,
                fondoId,
                new BigDecimal(monto),
                new BigDecimal(saldoResultante),
                participanteId);
    }

    /** Un aval vigente. El avalista acepto responder por una cantidad concreta. */
    UUID aval(UUID grupoId, UUID participanteAvaladoId, UUID avalistaUsuarioId, String tope, String porcentaje) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO garantia.aval_participante
                    (id, grupo_id, participante_avalado_id, avalista_usuario_id,
                     es_participante_del_grupo, monto_maximo_avalado, alcance,
                     porcentaje_responsabilidad, aceptado_en, estado)
                VALUES (?, ?, ?, ?, false, ?, 'PORCENTAJE', ?, now() - interval '30 days', 'VIGENTE')
                """,
                id,
                grupoId,
                participanteAvaladoId,
                avalistaUsuarioId,
                new BigDecimal(tope),
                new BigDecimal(porcentaje));
        return id;
    }

    void limpiar() {
        // El nombre de la tabla va como IDENTIFICADOR de jOOQ, no concatenado.
        //
        // `registro_incumplimiento`, `historial_estado_incumplimiento`,
        // `movimiento_fondo` y `abono_recuperacion` NO se borran: son append-only por
        // trigger (R-AUD-01), igual que el libro contable. Tampoco hace falta: cada
        // prueba trae su grupo y su fondo.
        for (String[] tabla : new String[][] {
            {"garantia", "subrogacion"},
            {"garantia", "ejecucion_aval"},
            {"garantia", "aval_participante"},
            {"garantia", "devolucion_fondo"},
            {"garantia", "lista_restriccion_interna"},
            {"garantia", "reemplazo_participante"},
            {"garantia", "disolucion_anticipada"},
            {"garantia", "evento_dominio"},
            {"garantia", "evento_consumido"}
        }) {
            dsl.deleteFrom(org.jooq.impl.DSL.table(org.jooq.impl.DSL.name(tabla[0], tabla[1])))
                    .execute();
        }
    }
}
