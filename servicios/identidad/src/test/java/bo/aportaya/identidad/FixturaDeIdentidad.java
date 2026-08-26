package bo.aportaya.identidad;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;

/**
 * Filas minimas para probar el ingreso, con las columnas del modelo y no de la
 * memoria.
 *
 * <p>Se inserta como dueno de la base —superusuario, sin RLS— porque sembrar no es lo
 * que se esta probando. Lo que se prueba corre despues, con el rol del servicio.
 */
final class FixturaDeIdentidad {

    private final DSLContext dsl;

    FixturaDeIdentidad(DSLContext dsl) {
        this.dsl = dsl;
    }

    UUID usuario(String telefono) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Ana', 'Quispe', ?, DATE '1990-01-01', 'ACTIVO', 'BASICO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "AY-" + id.toString().substring(0, 8),
                telefono);
        return id;
    }

    void credencial(UUID usuarioId, String hash) {
        dsl.execute(
                """
                INSERT INTO identidad.credencial_acceso
                    (id, usuario_id, hash_contrasena, algoritmo, parametros_kdf, requiere_cambio, cambiada_en)
                VALUES (gen_random_uuid(), ?, ?, 'ARGON2ID', '{}'::jsonb, false, now())
                """,
                usuarioId,
                hash);
    }

    /** Un rol de ambito GLOBAL es lo que convierte al usuario en operador. */
    UUID rolGlobal(String codigo) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.rol (id, codigo, nombre, ambito, es_sistema)
                VALUES (?, ?, ?, 'GLOBAL', false)
                """,
                id,
                codigo,
                codigo);
        return id;
    }

    /**
     * Nadie se asigna un rol a si mismo: lo impide {@code ck_asignacion_no_autoasignada}
     * (R-SEG-07), y por eso la fixtura tiene su propio otorgante.
     */
    private UUID otorgante;

    UUID otorgante() {
        if (otorgante == null) {
            otorgante = usuario("+59179" + String.valueOf(System.nanoTime()).substring(0, 6));
        }
        return otorgante;
    }

    void asignarRol(UUID usuarioId, UUID rolId) {
        asignarRol(usuarioId, rolId, otorgante());
    }

    void asignarRol(UUID usuarioId, UUID rolId, UUID asignadaPor) {
        dsl.execute(
                """
                INSERT INTO identidad.asignacion_rol
                    (id, usuario_id, rol_id, ambito, otorgada_por, otorgada_en)
                VALUES (gen_random_uuid(), ?, ?, 'GLOBAL', ?, now())
                """,
                usuarioId,
                rolId,
                asignadaPor);
    }

    void factor(UUID usuarioId, String tipo, boolean activo, boolean confirmado) {
        dsl.execute(
                """
                INSERT INTO identidad.factor_mfa
                    (id, usuario_id, tipo, secreto_cifrado, version_llave, activo, es_principal, confirmado_en)
                VALUES (gen_random_uuid(), ?, ?, 'cifrado', 1, ?, true, ?::timestamptz)
                """,
                usuarioId,
                tipo,
                activo,
                confirmado ? OffsetDateTime.now().toString() : null);
    }

    UUID dispositivoConfiable(UUID usuarioId, String huella) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO identidad.dispositivo
                    (id, usuario_id, huella, plataforma, modelo, version_app, es_confiable, ultimo_uso_en)
                VALUES (?, ?, ?, 'ANDROID', 'prueba', '1.0', true, now())
                """,
                id,
                usuarioId,
                huella);
        return id;
    }

    void intentosFallidos(UUID usuarioId, int cuantos) {
        for (int i = 0; i < cuantos; i++) {
            dsl.execute(
                    """
                    INSERT INTO identidad.intento_autenticacion
                        (id, usuario_id, identificador_usado, fecha_hora, exitoso, motivo_fallo,
                         ip_origen, agente_usuario, puntaje_riesgo)
                    VALUES (gen_random_uuid(), ?, 'prueba', now(), false, 'CREDENCIAL_INVALIDA',
                            '127.0.0.1', 'prueba', 0)
                    """,
                    usuarioId);
        }
    }
}
