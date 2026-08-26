package bo.aportaya.identidad;

import java.util.UUID;
import org.jooq.DSLContext;

/**
 * Los otros dos servicios, representados.
 *
 * <p>CU-01 no termina en {@code identidad}: {@code cumplimiento} y
 * {@code nucleo-financiero} consumen {@code identidad.usuario_registrado} y hacen lo
 * suyo. Como esos carriles todavia no existen, la prueba **los representa**: escribe
 * lo que cada uno escribiria al consumir el evento.
 *
 * <p>Es lo que manda el contrato de carril §7 —«programar contra su OpenAPI + doble»—
 * y sirve para lo que importa: comprobar que la coreografia entera llega al estado
 * que el caso de uso describe, y que el modelo lo admite. Cuando 1B y 1C existan,
 * estas escrituras se reemplazan por sus endpoints y la prueba sigue diciendo lo
 * mismo.
 */
final class DoblesDeLaCoreografia {

    private final DSLContext dsl;

    DoblesDeLaCoreografia(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Lo que hace `cumplimiento` al ver el alta: diligencia y calificacion. */
    UUID cumplimientoEvalua(UUID usuarioId, String nivel) {
        UUID calificacion = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.calificacion_riesgo_cliente
                    (id, usuario_id, nivel, puntaje_total, nivel_dd_requerido,
                     periodicidad_revision_meses, vigente_desde, proxima_revision, es_automatica)
                VALUES (?, ?, 'BAJO', 20, ?, 12, now(), CURRENT_DATE + 365, true)
                """,
                calificacion,
                usuarioId,
                nivel);
        dsl.execute(
                """
                INSERT INTO cumplimiento.debida_diligencia
                    (id, usuario_id, calificacion_riesgo_id, tipo, estado,
                     documentos_requeridos, documentos_recibidos, iniciada_en, completada_en)
                VALUES (gen_random_uuid(), ?, ?, ?, 'COMPLETA',
                        '["CI"]'::jsonb, '["CI"]'::jsonb, now(), now())
                """,
                usuarioId,
                calificacion,
                nivel);
        return calificacion;
    }

    /** Lo que hace `nucleo-financiero`: abrir la billetera en cero. */
    UUID nucleoAbreBilletera(UUID usuarioId, String nivel) {
        UUID cuenta = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO nucleo_financiero.cuenta_billetera
                    (id, numero_cuenta, tipo, usuario_id, moneda, estado, nivel_debida_diligencia,
                     saldo_disponible, saldo_retenido, permite_saldo_negativo, fecha_apertura, version)
                VALUES (?, ?, 'USUARIO', ?, 'BOB', 'ACTIVA', ?, 0.00, 0.00, false, now(), 0)
                """,
                cuenta,
                "AY" + cuenta.toString().replace("-", "").substring(0, 14),
                usuarioId,
                nivel);
        return cuenta;
    }

    /** Una coincidencia con lista restrictiva, confirmada: **bloquea la apertura**. */
    void listaRestrictivaConfirma(UUID usuarioId, String nombre) {
        UUID lista = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO auditoria.lista_restrictiva_externa
                    (id, nombre_lista, version, fecha_actualizacion, registros)
                VALUES (?, ?, '1', now(), 1)
                """,
                lista,
                "lista-" + lista.toString().substring(0, 8));
        dsl.execute(
                """
                INSERT INTO auditoria.coincidencia_lista
                    (id, lista_id, usuario_id, nombre_coincidente, puntaje_similitud, estado)
                VALUES (gen_random_uuid(), ?, ?, ?, 0.95, 'CONFIRMADA')
                """,
                lista,
                usuarioId,
                nombre);
    }

    boolean hayCoincidenciaConfirmada(UUID usuarioId) {
        Integer cuantas = (Integer) dsl.fetchOne(
                        """
                        SELECT count(*)::int FROM auditoria.coincidencia_lista
                         WHERE usuario_id = ? AND estado = 'CONFIRMADA'
                        """,
                        usuarioId)
                .get(0);
        return cuantas != null && cuantas > 0;
    }
}
