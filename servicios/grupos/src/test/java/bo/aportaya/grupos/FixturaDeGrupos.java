package bo.aportaya.grupos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;

/** Filas minimas para probar el sorteo, con las columnas del modelo. */
final class FixturaDeGrupos {

    /** Un telefono E.164 distinto por usuario: uq_usuario_telefono_e164 no perdona. */
    private static final java.util.concurrent.atomic.AtomicInteger SECUENCIA =
            new java.util.concurrent.atomic.AtomicInteger(10_000_000);

    private static String telefonoUnico() {
        return "+591" + SECUENCIA.incrementAndGet();
    }

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
                telefonoUnico());
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

    /** Participantes activos, cada uno con su cupo ocupado: de ahi sale el peso del voto. */
    List<UUID> participantesConCupo(UUID grupoId, int cuantos) {
        List<UUID> creados = new ArrayList<>();
        for (int numero = 1; numero <= cuantos; numero++) {
            UUID participante = UUID.randomUUID();
            dsl.execute(
                    """
                    INSERT INTO grupos.participante
                        (id, grupo_id, usuario_id, estado, es_organizador, fecha_ingreso,
                         reputacion_al_ingresar, aportes_realizados, aportes_en_mora)
                    VALUES (?, ?, ?, 'ACTIVO', false, now(), 0, 0, 0)
                    """,
                    participante,
                    grupoId,
                    usuario());
            dsl.execute(
                    """
                    INSERT INTO grupos.cupo (id, grupo_id, numero, participante_id, estado, fraccion, asignado_en)
                    VALUES (gen_random_uuid(), ?, ?, ?, 'OCUPADO', 1.00, now())
                    """,
                    grupoId,
                    (short) numero,
                    participante);
            creados.add(participante);
        }
        return creados;
    }

    /**
     * Un token de invitacion emitido por identidad.
     *
     * <p>`invitacion.token_id` apunta a `identidad.token_verificacion`: la clave
     * foranea cruza esquemas y la verifica el motor. Es de las 325 que el modelo
     * conserva a proposito por estar todo en un solo cluster.
     */
    UUID tokenDeInvitacion() {
        UUID politica = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.politica_token
                    (id, proposito, ttl_segundos, longitud_codigo, max_intentos_validacion,
                     max_reenvios_por_hora, cooldown_reenvio_segundos, max_emisiones_por_dia,
                     canales_permitidos, exige_dispositivo_conocido, invalida_anteriores, vigente_desde)
                VALUES (?, 'INVITACION_GRUPO', 604800, 8, 3, 3, 60, 10,
                        ARRAY['IN_APP','CORREO'], false, true, now())
                """,
                politica);

        UUID token = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.token_verificacion
                    (id, politica_id, tipo_token, proposito, hash_token, algoritmo_hash,
                     canal_entrega, destino_enmascarado, estado, emitido_en, expira_en,
                     intentos_fallidos, max_intentos, reenvios, ip_origen, agente_usuario,
                     correlation_id, clave_idempotencia, uso_unico)
                VALUES (?, ?, 'ENLACE', 'INVITACION_GRUPO', encode(sha256(?::bytea), 'hex'), 'SHA256',
                        'IN_APP', '+591*****01', 'EMITIDO', now(), now() + interval '7 days',
                        0, 3, 0, '127.0.0.1'::inet, 'prueba', gen_random_uuid(),
                        gen_random_uuid()::text, true)
                """,
                token,
                politica,
                // El hash es unico: dos tokens con el mismo hash serian el mismo token.
                token.toString());
        return token;
    }

    /** Alguien esperando entrar: participante sin cupo, todavia INVITADO. */
    UUID participanteSuelto(UUID grupoId) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO grupos.participante
                    (id, grupo_id, usuario_id, estado, es_organizador, fecha_ingreso,
                     reputacion_al_ingresar, aportes_realizados, aportes_en_mora)
                VALUES (?, ?, ?, 'INVITADO', false, now(), 0, 0, 0)
                """,
                id,
                grupoId,
                usuario());
        return id;
    }

    /** Turnos PROGRAMADOS, uno por periodo, en el orden en que llegan los cupos. */
    List<UUID> turnos(UUID grupoId, List<UUID> periodos, List<UUID> cupos) {
        List<UUID> creados = new ArrayList<>();
        for (int i = 0; i < periodos.size(); i++) {
            UUID id = UUID.randomUUID();
            dsl.execute(
                    """
                    INSERT INTO grupos.turno
                        (id, grupo_id, periodo_id, cupo_id, orden_asignado, estado,
                         criterio_asignacion, monto_estimado_cobro)
                    VALUES (?, ?, ?, ?, ?, 'PROGRAMADO', 'SORTEO', 500.00)
                    """,
                    id,
                    grupoId,
                    periodos.get(i),
                    cupos.get(i),
                    (short) (i + 1));
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
