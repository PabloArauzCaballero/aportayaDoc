package bo.aportaya.tarifas;

import java.util.UUID;
import org.jooq.DSLContext;

/**
 * El grupo, su entrega y la tarifa congelada.
 *
 * <p>Separada porque no es catalogo de tarifas: es el escenario sobre el que se cobra.
 * La tarifa congelada es la pieza que hace que el precio pactado al entrar no se mueva
 * cuando el tarifario cambia, y por eso vive con el grupo y no con el tarifario.
 */
class FixturaDeGrupo {

    private final DSLContext dsl;

    /** El usuario lo crea la fixtura de tarifas: hay un solo lugar donde nace un usuario. */
    private final FixturaDeTarifas tarifas;

    FixturaDeGrupo(DSLContext dsl, FixturaDeTarifas tarifas) {
        this.dsl = dsl;
        this.tarifas = tarifas;
    }

    /** Un grupo minimo: lo que la tarifa congelada exige por clave foranea. */
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
                VALUES (?, ?, 'Grupo de prueba', 500.00, 'BOB', 'MENSUAL', 5, 3, 3, 0,
                        current_date, current_date + 90, 'ACTIVO', 'MANUAL_POR_INVITACION', 'SORTEO_ALEATORIO', 'PRIVADO',
                        true, 'BASICO', 0, 3, true, false, 0.00, 0.600)
                """,
                id,
                "GRP-" + id.toString().substring(0, 8));
        return id;
    }

    /**
     * Una deduccion de entrega, con toda la cadena que su clave foranea exige.
     *
     * <p>Es larga a proposito: se podria «probar» R-TAR-06 con un UUID inventado y un
     * assert sobre el nombre del indice, pero eso no verifica que la regla funcione —
     * verifica que el indice existe. La cadena real es lo que hace que la prueba
     * pruebe algo.
     */
    UUID deduccionDeEntrega(UUID grupoId) {
        UUID usuario = tarifas.usuario();
        UUID participante = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.participante
                    (id, grupo_id, usuario_id, estado, es_organizador, fecha_ingreso,
                     reputacion_al_ingresar, aportes_realizados, aportes_en_mora)
                VALUES (?, ?, ?, 'ACTIVO', false, now(), 50, 0, 0)
                """,
                participante,
                grupoId,
                usuario);

        UUID cupo = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.cupo (id, grupo_id, numero, participante_id, estado, fraccion, asignado_en)
                VALUES (?, ?, 1, ?, 'OCUPADO', 1.0, now())
                """,
                cupo,
                grupoId,
                participante);

        UUID periodo = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.periodo
                    (id, grupo_id, numero, fecha_inicio, fecha_limite_pago, fecha_fin_gracia,
                     fecha_entrega_prevista, estado, monto_objetivo, monto_recaudado, cupos_morosos)
                VALUES (?, ?, 1, current_date - 30, current_date + 10, current_date + 13,
                        current_date + 40, 'ABIERTO', 1500.00, 0, 0)
                """,
                periodo,
                grupoId);

        UUID turno = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.turno
                    (id, grupo_id, periodo_id, cupo_id, orden_asignado, estado, criterio_asignacion,
                     monto_estimado_cobro)
                VALUES (?, ?, ?, ?, 1, 'PROGRAMADO', 'SORTEO', 1500.00)
                """,
                turno,
                grupoId,
                periodo,
                cupo);

        UUID entrega = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO entregas.entrega_fondo
                    (id, grupo_id, periodo_id, turno_id, cupo_id, beneficiario_participante_id,
                     monto_bolsa_bruto, total_deducciones, monto_neto_a_entregar,
                     monto_efectivamente_entregado, moneda, estado, metodo_desembolso,
                     fecha_programada, version)
                VALUES (?, ?, ?, ?, ?, ?, 1500.00, 18.00, 1482.00, 0, 'BOB', 'PROGRAMADA',
                        'BILLETERA_MOVIL', current_date, 0)
                """,
                entrega,
                grupoId,
                periodo,
                turno,
                cupo,
                participante);

        UUID deduccion = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO entregas.deduccion_entrega
                    (id, entrega_id, tipo, descripcion, monto, es_obligatoria, aplicada_en)
                VALUES (?, ?, 'COMISION_PLATAFORMA', 'Comision de la plataforma', 18.00, true, now())
                """,
                deduccion,
                entrega);
        return deduccion;
    }

    /** El snapshot congelado del grupo: el precio pactado que no se mueve. */
    void congelarTarifa(UUID grupoId, UUID tarifarioId) {
        dsl.execute(
                """
                INSERT INTO tarifas.tarifa_congelada_grupo
                    (id, grupo_id, tarifario_id, snapshot_conceptos, hash_snapshot, congelada_en)
                VALUES (gen_random_uuid(), ?, ?, '[]'::jsonb, repeat('c', 64), now())
                """,
                grupoId,
                tarifarioId);
    }
}
