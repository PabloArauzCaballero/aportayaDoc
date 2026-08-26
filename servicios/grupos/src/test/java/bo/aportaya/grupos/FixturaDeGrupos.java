package bo.aportaya.grupos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;

/** Filas minimas para probar el sorteo, con las columnas del modelo. */
final class FixturaDeGrupos {

    private final DSLContext dsl;

    FixturaDeGrupos(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Un usuario real en {@code identidad}. La clave foranea de {@code ejecutado_por}
     * cruza esquemas y la verifica el motor: es una de las 325 que el modelo conserva
     * a proposito por estar todo en un solo cluster.
     */
    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Organiza', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'BASICO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "GRU-" + id.toString().substring(0, 8),
                "+5917" + String.valueOf(System.nanoTime()).substring(0, 7));
        return id;
    }

    UUID grupoConformado(int cupos) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.grupo
                    (id, codigo_publico, nombre, monto_aporte, moneda, periodicidad, dia_cobro,
                     num_periodos, cupos_totales, cupos_ocupados, fecha_inicio, fecha_fin_estimada,
                     estado, tipo_conformacion, modalidad_turnos, visibilidad, es_autogestionado,
                     requiere_kyc_minimo, reputacion_minima, dias_gracia, aplica_recargo_mora,
                     usa_fondo_garantia, porcentaje_fondo_garantia, quorum_decisiones)
                    -- quorum_decisiones es numeric(4,3): una FRACCION, no un porcentaje.
                    -- Poner 60 ahi desborda; el sesenta por ciento es 0.600.
                VALUES (?, ?, 'Grupo de prueba', 500.00, 'BOB', 'MENSUAL', 5, ?, ?, ?,
                        CURRENT_DATE, CURRENT_DATE + 180, 'CONFORMADO', 'MANUAL_POR_INVITACION', 'SORTEO_ALEATORIO',
                        'PRIVADO', true, 'BASICO', 0, 3, true, false, 0.00, 0.600)
                """,
                id,
                "GR-" + id.toString().substring(0, 8),
                cupos,
                cupos,
                cupos);
        return id;
    }

    List<UUID> cuposOcupados(UUID grupoId, int cuantos) {
        List<UUID> creados = new ArrayList<>();
        for (int numero = 1; numero <= cuantos; numero++) {
            UUID id = UUID.randomUUID();
            dsl.execute(
                    """
                    INSERT INTO grupos.cupo (id, grupo_id, numero, estado, fraccion, asignado_en)
                    VALUES (?, ?, ?, 'OCUPADO', 1.00, now())
                    """,
                    id,
                    grupoId,
                    (short) numero);
            creados.add(id);
        }
        return creados;
    }

    /** Un periodo por turno: cada periodo tiene un solo beneficiario. */
    List<UUID> periodos(UUID grupoId, int cuantos, BigDecimal objetivo) {
        List<UUID> creados = new ArrayList<>();
        for (int numero = 1; numero <= cuantos; numero++) {
            creados.add(periodo(grupoId, numero, objetivo));
        }
        return creados;
    }

    UUID periodo(UUID grupoId, int numero, BigDecimal objetivo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.periodo
                    (id, grupo_id, numero, fecha_inicio, fecha_limite_pago, fecha_fin_gracia,
                     fecha_entrega_prevista, estado, monto_objetivo, monto_recaudado, cupos_morosos)
                VALUES (?, ?, ?, CURRENT_DATE + (? * 30), CURRENT_DATE + (? * 30) + 5,
                        CURRENT_DATE + (? * 30) + 8, CURRENT_DATE + (? * 30) + 10,
                        'ABIERTO', ?, 0.00, 0)
                """,
                id,
                grupoId,
                (short) numero,
                numero,
                numero,
                numero,
                numero,
                objetivo);
        return id;
    }
}
