package bo.aportaya.organizador;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;

/** Las filas que las pruebas de organizador necesitan. */
class FixturaDeOrganizador {

    private static final AtomicInteger SECUENCIA = new AtomicInteger(90_000_000);

    private final DSLContext dsl;

    FixturaDeOrganizador(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID usuario() {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Organiza', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'COMPLETO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "ORG-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

    /**
     * Una verificacion de identidad aprobada: lo que la postulacion exige por clave
     * foranea, y lo que la norma exige antes de dejar administrar plata ajena.
     */
    UUID kycAprobado(UUID usuarioId) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.verificacion_kyc
                    (id, usuario_id, nivel_solicitado, estado, proveedor, iniciada_en, resuelta_en,
                     vigente_hasta)
                VALUES (?, ?, 'AVANZADO', 'APROBADA', 'simulador', now() - interval '5 days', now(),
                        current_date + 365)
                """,
                id,
                usuarioId);
        return id;
    }

    /** El token con el que se firma el contrato. La firma es evidencia, no un booleano. */
    UUID tokenDeFirma(UUID usuarioId) {
        UUID politica = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.politica_token
                    (id, proposito, ttl_segundos, longitud_codigo, max_intentos_validacion,
                     max_reenvios_por_hora, cooldown_reenvio_segundos, max_emisiones_por_dia,
                     canales_permitidos, exige_dispositivo_conocido, invalida_anteriores, vigente_desde)
                VALUES (?, 'FIRMA_CONTRATO', 900, 6, 3, 3, 60, 10, 'CORREO', false, true, now())
                """,
                politica);

        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.token_verificacion
                    (id, usuario_id, politica_id, tipo_token, proposito, hash_token, algoritmo_hash,
                     canal_entrega, destino_enmascarado, estado, emitido_en, expira_en, consumido_en,
                     max_intentos, reenvios, ip_origen, agente_usuario, correlation_id,
                     clave_idempotencia)
                VALUES (?, ?, ?, 'OTP', 'FIRMA_CONTRATO', ?, 'argon2id', 'CORREO',
                        'f****@aportaya.test', 'CONSUMIDO', now(), now() + interval '15 minutes',
                        now(), 3, 0, '198.51.100.10', 'pruebas/1.0', gen_random_uuid(), ?)
                """,
                id,
                usuarioId,
                politica,
                // El hash es unico por token: dos tokens con el mismo hash serian el
                // mismo secreto emitido dos veces, que es justo lo que no puede pasar.
                "%064d".formatted(SECUENCIA.incrementAndGet()),
                "firma-" + SECUENCIA.incrementAndGet());
        return id;
    }

    /** Un requisito del catalogo. Es catalogo, no una constante en el codigo. */
    UUID requisito(String codigo, String tipo, String valorMinimo, boolean obligatorio, String nivel) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO organizador.requisito_habilitacion
                    (id, codigo, descripcion, tipo, valor_minimo, es_obligatorio, nivel_requerido, activo)
                VALUES (?, ?, 'Requisito de prueba', ?, ?, ?, ?, true)
                """,
                id,
                codigo,
                tipo,
                new BigDecimal(valorMinimo),
                obligatorio,
                nivel);
        return id;
    }

    /** Un organizador ya habilitado: el punto de partida de casi todo lo demas. */
    UUID organizadorHabilitado(UUID usuarioId) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO organizador.organizador
                    (id, usuario_id, estado, nivel, limite_grupos_simultaneos, limite_monto_administrado,
                     grupos_activos, grupos_historicos, monto_administrado_actual, calificacion_promedio,
                     indice_morosidad_cartera, fecha_postulacion, fecha_habilitacion, version)
                VALUES (?, ?, 'HABILITADO', 'ESTANDAR', 5, 100000.00, 0, 3, 0, 4.20, 2.50,
                        now() - interval '90 days', now() - interval '60 days', 0)
                """,
                id,
                usuarioId);
        return id;
    }

    /** Con grupos activos: el que no se puede retirar dejando gente a mitad del ciclo. */
    void conGruposActivos(UUID organizadorId, int cuantos) {
        dsl.execute(
                "UPDATE organizador.organizador SET grupos_activos = ? WHERE id = ?", (short) cuantos, organizadorId);
    }

    /** Una capacitacion aprobada y vigente: sin ella no se habilita. */
    UUID capacitacion(UUID organizadorId, boolean aprobada, boolean vigente) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO organizador.capacitacion_organizador
                    (id, organizador_id, modulo, completada_en, puntaje_evaluacion, aprobada, vigente_hasta)
                VALUES (?, ?, 'Administracion de un pasanaku', now() - interval '10 days', 90.00, ?, ?)
                """,
                id,
                organizadorId,
                aprobada,
                vigente
                        ? java.time.LocalDate.now().plusYears(1)
                        : java.time.LocalDate.now().minusDays(1));
        return id;
    }

    /** Un contrato ya firmado y vigente. */
    UUID contratoFirmado(UUID organizadorId) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO organizador.contrato_organizador
                    (id, organizador_id, version, contenido_hash, obligaciones, causales_rescision,
                     firmado_en, vigente_desde)
                VALUES (?, ?, ?, repeat('a', 64), 'Administrar con diligencia', 'Fraude, abandono',
                        now() - interval '30 days', current_date - 30)
                """,
                id,
                organizadorId,
                "v" + SECUENCIA.incrementAndGet());
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
                VALUES (?, ?, 'Grupo de prueba', 500.00, 'BOB', 'MENSUAL', 5, 3, 3, 0,
                        current_date, current_date + 90, 'ACTIVO', 'MANUAL_POR_INVITACION',
                        'SORTEO_ALEATORIO', 'PRIVADO', true, 'BASICO', 0, 3, true, false, 0.00, 0.600)
                """,
                id,
                "GRP-" + id.toString().substring(0, 8));
        return id;
    }

    void limpiar() {
        // El nombre de la tabla va como IDENTIFICADOR de jOOQ, no concatenado.
        for (String[] tabla : new String[][] {
            {"organizador", "ejecucion_tarea"},
            {"organizador", "tarea_automatizada"},
            {"organizador", "regla_automatizacion"},
            {"organizador", "apelacion_sancion_org"},
            {"organizador", "sancion_organizador"},
            {"organizador", "metrica_organizador"},
            {"organizador", "evaluacion_desempeno"},
            {"organizador", "contrato_organizador"},
            {"organizador", "capacitacion_organizador"},
            {"organizador", "organizador"},
            {"organizador", "solicitud_organizador"},
            {"organizador", "requisito_habilitacion"},
            {"organizador", "evento_dominio"},
            {"organizador", "evento_consumido"},
            {"grupos", "grupo"},
            {"identidad", "token_verificacion"},
            {"identidad", "politica_token"},
            {"identidad", "verificacion_kyc"}
        }) {
            dsl.deleteFrom(org.jooq.impl.DSL.table(org.jooq.impl.DSL.name(tabla[0], tabla[1])))
                    .execute();
        }
    }
}
