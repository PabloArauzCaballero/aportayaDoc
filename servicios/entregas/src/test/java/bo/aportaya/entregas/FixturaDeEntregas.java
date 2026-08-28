package bo.aportaya.entregas;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;

/** Las filas que las pruebas de entregas necesitan. */
class FixturaDeEntregas {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(40_000_000);

    private final DSLContext dsl;

    FixturaDeEntregas(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Entrega', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'COMPLETO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "ENT-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

    /** Un grupo con su periodo, cupo, participante y turno: lo que la entrega exige. */
    record Escenario(UUID grupoId, UUID periodoId, UUID cupoId, UUID turnoId, UUID participanteId) {}

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
                VALUES (?, ?, 'Grupo de prueba', 2000.00, 'BOB', 'MENSUAL', 5, 3, 3, 3,
                        current_date, current_date + 90, 'ACTIVO', 'MANUAL_POR_INVITACION',
                        'SORTEO_ALEATORIO', 'PRIVADO', true, 'BASICO', 0, 3, true, false, 0.00, 0.600)
                """,
                grupo,
                "GRP-" + grupo.toString().substring(0, 8));

        UUID participante = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.participante
                    (id, grupo_id, usuario_id, estado, es_organizador, fecha_ingreso,
                     reputacion_al_ingresar, aportes_realizados, aportes_en_mora)
                VALUES (?, ?, ?, 'ACTIVO', false, now(), 50, 3, 0)
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
                VALUES (?, ?, 1, current_date - 30, current_date, current_date + 3,
                        current_date + 5, 'ABIERTO', 6000.00, 6000.00, 0)
                """,
                periodo,
                grupo);

        UUID turno = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.turno
                    (id, grupo_id, periodo_id, cupo_id, orden_asignado, estado, criterio_asignacion,
                     monto_estimado_cobro)
                VALUES (?, ?, ?, ?, 1, 'PROGRAMADO', 'SORTEO', 6000.00)
                """,
                turno,
                grupo,
                periodo,
                cupo);

        return new Escenario(grupo, periodo, cupo, turno, participante);
    }

    /** Una regla de validacion previa: la que puede frenar una entrega. */
    UUID regla(String codigo, boolean esBloqueante) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO entregas.regla_entrega
                    (id, codigo, descripcion, es_bloqueante, permite_omision, orden, activa)
                VALUES (?, ?, 'Regla de prueba', ?, false, 1, true)
                """,
                id,
                codigo,
                esBloqueante);
        return id;
    }

    /** Un proveedor de desembolso. Vive en `aportes`, y aca solo se referencia. */
    UUID proveedor() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO aportes.proveedor_pago
                    (id, codigo, nombre, tipo, url_base, referencia_credenciales, comision_fija,
                     comision_porcentual, soporta_webhook, soporta_consulta_estado, activo, prioridad)
                VALUES (?, ?, 'Proveedor de prueba', 'BANCO', 'https://banco.test', 'vault://pagos/b',
                        0.50, 0.0100, true, true, true, 1)
                """,
                id,
                "PRV-" + SECUENCIA.incrementAndGet());
        return id;
    }

    void limpiar() {
        // El nombre de la tabla va como IDENTIFICADOR de jOOQ, no concatenado.
        for (String[] tabla : new String[][] {
            {"entregas", "intento_desembolso"},
            {"entregas", "orden_desembolso"},
            {"entregas", "confirmacion_recepcion"},
            {"entregas", "incidencia_entrega"},
            {"entregas", "validacion_pre_entrega"},
            {"entregas", "deduccion_entrega"},
            {"entregas", "historial_estado_entrega"},
            {"entregas", "entrega_fondo"},
            {"entregas", "regla_entrega"},
            {"entregas", "cuenta_bancaria_beneficiario"},
            {"entregas", "evento_dominio"},
            {"entregas", "evento_consumido"},
            {"aportes", "proveedor_pago"},
            {"grupos", "turno"},
            {"grupos", "periodo"},
            {"grupos", "cupo"},
            {"grupos", "participante"},
            {"grupos", "grupo"}
        }) {
            dsl.deleteFrom(org.jooq.impl.DSL.table(org.jooq.impl.DSL.name(tabla[0], tabla[1])))
                    .execute();
        }
    }
}
