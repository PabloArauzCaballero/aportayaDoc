package bo.aportaya.transparencia;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/** Las filas que las pruebas de transparencia necesitan. */
class FixturaDeTransparencia {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(60_600_000);

    private final DSLContext dsl;

    FixturaDeTransparencia(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Transparencia', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'COMPLETO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "TRA-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

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
                VALUES (?, ?, 'Grupo de transparencia', 500.00, 'BOB', 'MENSUAL', 5, 3, 3, 3,
                        current_date, current_date + 90, 'ACTIVO', 'MANUAL_POR_INVITACION',
                        'SORTEO_ALEATORIO', 'PRIVADO', true, 'BASICO', 0, 3, true, true, 10.00, 0.600)
                """,
                id,
                "GRP-" + id.toString().substring(0, 8));
        return id;
    }

    /** Un participante del grupo. {@code tg_resena_convivencia} lo exige para resenar. */
    UUID participante(UUID grupoId, UUID usuarioId) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.participante
                    (id, grupo_id, usuario_id, estado, es_organizador, fecha_ingreso,
                     reputacion_al_ingresar, aportes_realizados, aportes_en_mora)
                VALUES (?, ?, ?, 'ACTIVO', false, now(), 50, 2, 0)
                """,
                id,
                grupoId,
                usuarioId);
        return id;
    }

    UUID periodo(UUID grupoId, int numero) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.periodo
                    (id, grupo_id, numero, fecha_inicio, fecha_limite_pago, fecha_fin_gracia,
                     fecha_entrega_prevista, estado, monto_objetivo, monto_recaudado, cupos_morosos)
                VALUES (?, ?, ?, current_date - 60, current_date - 30, current_date - 27,
                        current_date - 25, 'CERRADO', 1500.00, 1500.00, 0)
                """,
                id,
                grupoId,
                numero);
        return id;
    }

    UUID snapshot(UUID usuarioId, String puntaje, String nivel) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO transparencia.snapshot_reputacion
                    (id, usuario_id, puntaje, nivel_confianza, fotografia_factores, motivo, tomado_en)
                VALUES (?, ?, ?::numeric, ?, ?::jsonb, 'AUDITORIA', now())
                """,
                id,
                usuarioId,
                puntaje,
                nivel,
                "{\"puntaje\":\"" + puntaje + "\"}");
        return id;
    }

    /**
     * Aplica las semillas del modelo de reputacion.
     *
     * <p>{@code sql/aplicar.sql} monta el esquema pero **no las semillas**, y el modelo
     * de scoring, sus pesos, sus reglas de impacto y las insignias son catalogo
     * (invariante 10): sin ellos no hay nada que probar. Se lee el archivo de la boveda
     * en lugar de reescribir sus valores aca, para que una prueba no pueda pasar contra
     * numeros que la boveda ya no tiene.
     */
    void catalogoDeReputacion() {
        if (dsl.fetchCount(DSL.table(DSL.name("transparencia", "modelo_scoring"))) > 0) {
            return;
        }
        try {
            var semilla = java.nio.file.Path.of("").toAbsolutePath();
            while (semilla != null && !java.nio.file.Files.isDirectory(semilla.resolve("sql/60_semillas"))) {
                semilla = semilla.getParent();
            }
            dsl.execute(java.nio.file.Files.readString(semilla.resolve("sql/60_semillas/16-reputacion-y-scoring.sql")));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo leer la semilla del modelo de reputacion", e);
        }
    }

    /** El identificador del modelo que traen las semillas. Es catalogo, no se inventa. */
    UUID modeloVigente() {
        return dsl.fetchOne("SELECT id FROM transparencia.modelo_scoring WHERE version = 'v1'")
                .get(0, UUID.class);
    }

    UUID insignia(String codigo) {
        return dsl.fetchOne("SELECT id FROM transparencia.insignia_logro WHERE codigo = ?", codigo)
                .get(0, UUID.class);
    }

    /** Deja el esquema como estaba. Sin SQL concatenado: lo prohibe el contrato. */
    void limpiar(UUID... grupos) {
        for (UUID grupo : grupos) {
            dsl.deleteFrom(DSL.table(DSL.name("transparencia", "metrica_grupo")))
                    .where(DSL.field("grupo_id", UUID.class).eq(grupo))
                    .execute();
        }
    }
}
