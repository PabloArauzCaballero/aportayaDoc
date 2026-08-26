package bo.aportaya.grupos;

import java.util.UUID;
import org.jooq.DSLContext;

/**
 * {@code tarifas} y {@code nucleo-financiero}, representados.
 *
 * <p>CU-20 no termina en {@code grupos}: los otros dos consumen
 * {@code grupos.grupo_creado} y hacen lo suyo. Mientras esos carriles no existan, la
 * prueba escribe lo que ellos escribirian, que es lo que manda el contrato de carril
 * §7. Cuando existan, estas escrituras se reemplazan por sus endpoints.
 */
final class DoblesDeTarifasYNucleo {

    private final DSLContext dsl;

    DoblesDeTarifasYNucleo(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Un tarifario publicado, para que la congelacion tenga a que apuntar. */
    UUID publicarTarifario(UUID id) {
        dsl.execute(
                """
                INSERT INTO catalogo.tarifario
                    (id, codigo, version, nombre, estado, moneda_base, vigente_desde, dias_preaviso,
                     publicado_en, url_publicacion, hash_documento)
                -- Un tarifario VIGENTE sin publicar no existe: ck_tarifario_publicado
                -- exige fecha, direccion y hash. Es lo que hace oponible el precio.
                VALUES (?, ?, 1, ?, 'VIGENTE', 'BOB', now(), 30,
                        now(), 'local://tarifarios/prueba', repeat('d', 64))
                ON CONFLICT DO NOTHING
                """,
                id,
                "TAR-" + id.toString().substring(0, 8),
                "tarifario-" + id.toString().substring(0, 8));
        return id;
    }

    /** Lo que hace `tarifas`: congelar el precio con su hash verificable. */
    UUID tarifasCongela(UUID grupoId, UUID tarifarioId) {
        publicarTarifario(tarifarioId);
        UUID congelada = UUID.randomUUID();
        dsl.execute(sqlCongelar(grupoId, tarifarioId, congelada));
        return congelada;
    }

    String sqlCongelar(UUID grupoId, UUID tarifarioId) {
        return sqlCongelar(grupoId, tarifarioId, UUID.randomUUID());
    }

    private String sqlCongelar(UUID grupoId, UUID tarifarioId, UUID id) {
        return """
               INSERT INTO tarifas.tarifa_congelada_grupo
                   (id, grupo_id, tarifario_id, snapshot_conceptos, hash_snapshot, congelada_en)
               VALUES ('%s', '%s', '%s', '{"conceptos":[]}'::jsonb, repeat('c', 64), now())
               """
                .formatted(id, grupoId, tarifarioId);
    }

    /** Lo que hace `nucleo-financiero`: la cuenta del GRUPO, sin titular persona. */
    UUID nucleoAbreCuentaDelGrupo(UUID grupoId) {
        UUID cuenta = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_billetera
                    (id, numero_cuenta, tipo, grupo_id, moneda, estado, nivel_debida_diligencia,
                     saldo_disponible, saldo_retenido, permite_saldo_negativo, fecha_apertura, version)
                VALUES (?, ?, 'GRUPO', ?, 'BOB', 'ACTIVA', 'SIMPLIFICADA', 0.00, 0.00, false, now(), 0)
                """,
                cuenta,
                "AYG" + cuenta.toString().replace("-", "").substring(0, 13),
                grupoId);
        return cuenta;
    }

    String sqlSegundaCuentaDelGrupo(UUID grupoId) {
        return """
               INSERT INTO nucleo_financiero.cuenta_billetera
                   (id, numero_cuenta, tipo, grupo_id, moneda, estado, nivel_debida_diligencia,
                    saldo_disponible, saldo_retenido, permite_saldo_negativo, fecha_apertura, version)
               VALUES (gen_random_uuid(), 'AYGSEGUNDA0001', 'GRUPO', '%s', 'BOB', 'ACTIVA',
                       'SIMPLIFICADA', 0.00, 0.00, false, now(), 0)
               """
                .formatted(grupoId);
    }

    /** La cuenta del grupo a nombre de una persona: la plata del grupo no es de nadie. */
    String sqlCuentaDeGrupoConTitularPersona(UUID grupoId, UUID usuarioId) {
        return """
               INSERT INTO nucleo_financiero.cuenta_billetera
                   (id, numero_cuenta, tipo, grupo_id, usuario_id, moneda, estado,
                    nivel_debida_diligencia, saldo_disponible, saldo_retenido,
                    permite_saldo_negativo, fecha_apertura, version)
               VALUES (gen_random_uuid(), 'AYGTITULAR0001', 'GRUPO', '%s', '%s', 'BOB', 'ACTIVA',
                       'SIMPLIFICADA', 0.00, 0.00, false, now(), 0)
               """
                .formatted(grupoId, usuarioId);
    }

    String sqlCuentaSinTitular() {
        return """
               INSERT INTO nucleo_financiero.cuenta_billetera
                   (id, numero_cuenta, tipo, moneda, estado, nivel_debida_diligencia,
                    saldo_disponible, saldo_retenido, permite_saldo_negativo, fecha_apertura, version)
               VALUES (gen_random_uuid(), 'AYGSINDUENO001', 'GRUPO', 'BOB', 'ACTIVA',
                       'SIMPLIFICADA', 0.00, 0.00, false, now(), 0)
               """;
    }
}
