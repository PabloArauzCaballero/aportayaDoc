package bo.aportaya.nucleofinanciero;

import java.util.UUID;
import org.jooq.DSLContext;

/**
 * La cadena de grupo que una obligacion de aporte necesita para existir.
 *
 * <p>Vive aparte de {@link FixturaDeBilletera} porque es de otro dominio: grupo,
 * participante, cupo y periodo son de `grupos`, y la obligacion de `aportes`. La
 * billetera solo la referencia por una clave foranea que cruza esquemas —una de las
 * 630 que el modelo conserva a proposito porque todo vive en un cluster.
 *
 * <p>Falsear el identificador con un UUID inventado no probaria nada: la base lo
 * rechaza, que es justamente lo correcto.
 */
final class FixturaDeObligacion {

    private final DSLContext dsl;

    FixturaDeObligacion(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Una obligacion de aporte real, con su cadena completa.
     *
     * <p>`transferencia_p2p.obligacion_id` referencia `aportes.obligacion_aporte`, y
     * esa clave foranea cruza esquemas a proposito: el modelo conserva 630 asi porque
     * todo vive en un solo cluster. Falsear el id con un UUID inventado no probaria
     * nada — la base lo rechaza, que es justamente lo correcto.
     */
    UUID obligacionDeAporte(UUID usuarioId) {
        UUID grupo = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.grupo
                    (id, codigo_publico, nombre, monto_aporte, moneda, periodicidad, dia_cobro,
                     num_periodos, cupos_totales, cupos_ocupados, fecha_inicio, fecha_fin_estimada,
                     estado, tipo_conformacion, modalidad_turnos, visibilidad, es_autogestionado,
                     requiere_kyc_minimo, reputacion_minima, dias_gracia, aplica_recargo_mora,
                     usa_fondo_garantia, porcentaje_fondo_garantia, quorum_decisiones)
                VALUES (?, ?, 'Grupo de prueba', 500.00, 'BOB', 'MENSUAL', 5, 3, 3, 1,
                        current_date, current_date + 90, 'ACTIVO', 'MANUAL_POR_INVITACION', 'SORTEO_ALEATORIO', 'PRIVADO',
                        true, 'BASICO', 0, 3, true, false, 0, 0.600)
                """,
                grupo,
                "GRP-" + grupo.toString().substring(0, 8));

        UUID participante = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.participante
                    (id, grupo_id, usuario_id, estado, es_organizador, fecha_ingreso,
                     reputacion_al_ingresar, aportes_realizados, aportes_en_mora)
                VALUES (?, ?, ?, 'ACTIVO', false, now(), 50, 0, 0)
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
                VALUES (?, ?, 1, current_date, current_date + 5, current_date + 8,
                        current_date + 10, 'ABIERTO', 1500.00, 0, 0)
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
                        'PENDIENTE', current_date + 5, current_date + 8, 0, 0)
                """,
                obligacion,
                grupo,
                periodo,
                cupo,
                participante);
        return obligacion;
    }
}
