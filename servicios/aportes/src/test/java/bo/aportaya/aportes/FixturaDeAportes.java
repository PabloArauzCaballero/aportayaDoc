package bo.aportaya.aportes;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;

/** Filas minimas para probar el cobro del aporte, con las columnas del modelo real. */
final class FixturaDeAportes {

    /** Un telefono E.164 distinto por usuario: uq_usuario_telefono_e164 no perdona. */
    private static final AtomicInteger SECUENCIA = new AtomicInteger(70_000_000);

    private final DSLContext dsl;

    FixturaDeAportes(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Aporta', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'BASICO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "APO-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

    /** Un grupo con su participante, cupo y periodo: la cadena que la obligacion exige. */
    UUID grupo() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.grupo
                    (id, codigo_publico, nombre, monto_aporte, moneda, periodicidad, dia_cobro,
                     num_periodos, cupos_totales, cupos_ocupados, fecha_inicio, fecha_fin_estimada,
                     estado, tipo_conformacion, modalidad_turnos, visibilidad, es_autogestionado,
                     requiere_kyc_minimo, reputacion_minima, dias_gracia, aplica_recargo_mora,
                     usa_fondo_garantia, porcentaje_fondo_garantia, quorum_decisiones)
                VALUES (?, ?, 'Grupo de prueba', 500.00, 'BOB', 'MENSUAL', 5, 3, 3, 1,
                        current_date, current_date + 90, 'ACTIVO', 'MANUAL_POR_INVITACION',
                        'SORTEO_ALEATORIO', 'PRIVADO', true, 'BASICO', 0, 3, true, false, 0, 0.600)
                """,
                id,
                "GRP-" + id.toString().substring(0, 8));
        return id;
    }

    /**
     * Una obligacion de aporte con toda su cadena.
     *
     * @param diasHastaVencer negativo para que ya este vencida
     */
    Obligacion obligacion(UUID usuarioId, String monto, int diasHastaVencer) {
        UUID grupo = grupo();
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
                VALUES (?, ?, 1, current_date - 30, current_date + ?, current_date + ?,
                        current_date + 40, 'ABIERTO', 1500.00, 0, 0)
                """,
                periodo,
                grupo,
                diasHastaVencer,
                diasHastaVencer);

        UUID obligacion = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO aportes.obligacion_aporte
                    (id, grupo_id, periodo_id, cupo_id, participante_id, tipo, monto_esperado,
                     moneda, monto_pagado, monto_recargo, monto_condonado, monto_cubierto_garantia,
                     estado, fecha_vencimiento, fecha_fin_gracia, dias_mora, version)
                VALUES (?, ?, ?, ?, ?, 'APORTE_PERIODICO', ?, 'BOB', 0, 0, 0, 0,
                        'PENDIENTE', current_date + ?, current_date + ?, 0, 0)
                """,
                obligacion,
                grupo,
                periodo,
                cupo,
                participante,
                new BigDecimal(monto),
                diasHastaVencer,
                diasHastaVencer);
        return new Obligacion(obligacion, grupo, participante);
    }

    /** La politica de mora del grupo. Sin ella no se genera recargo, y esta bien. */
    UUID politicaDeMora(UUID grupoId, int diasGracia, String tipo, String valor, String tope) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO aportes.politica_mora
                    (id, grupo_id, dias_gracia, tipo_recargo, valor_recargo, tope_recargo,
                     dias_para_mora_grave, dias_para_incumplimiento, aplica_automatico, vigente_desde)
                VALUES (?, ?, ?, ?, ?, ?, 15, 30, true, now() - interval '1 day')
                """,
                id,
                grupoId,
                (short) diasGracia,
                tipo,
                new BigDecimal(valor),
                new BigDecimal(tope));
        return id;
    }

    UUID proveedor(String codigo, boolean consultaEstado, int prioridad) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO aportes.proveedor_pago
                    (id, codigo, nombre, tipo, url_base, referencia_credenciales, comision_fija,
                     comision_porcentual, soporta_webhook, soporta_consulta_estado, activo, prioridad)
                VALUES (?, ?, ?, 'PASARELA', 'https://pasarela.test', 'vault://pagos/', 0.50,
                        0.0250, true, ?, true, ?)
                """,
                id,
                codigo,
                codigo,
                consultaEstado,
                (short) prioridad);
        return id;
    }

    void limpiar() {
        for (String tabla : new String[] {
            "disputa_pago",
            "reembolso",
            "pago",
            "obligacion_aporte",
            "politica_mora",
            "proveedor_pago",
            "evento_dominio",
            "evento_consumido"
        }) {
            dsl.deleteFrom(org.jooq.impl.DSL.table(org.jooq.impl.DSL.name("aportes", tabla)))
                    .execute();
        }
        dsl.execute("DELETE FROM grupos.periodo");
        dsl.execute("DELETE FROM grupos.cupo");
        dsl.execute("DELETE FROM grupos.participante");
        dsl.execute("DELETE FROM grupos.grupo");
    }

    record Obligacion(UUID id, UUID grupoId, UUID participanteId) {}
}
