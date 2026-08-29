package bo.aportaya.grupos;

import org.jooq.DSLContext;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lo que la base rechaza, dicho por la base y no por una copia de sus reglas.
 *
 * <p>Una prueba de rechazo que comprueba la validacion de Java prueba el `if`, no la
 * restriccion. La restriccion vive en `sql/40_reglas` y es la que va a estar ahi el dia
 * que alguien escriba por fuera del caso de uso: esta clase la ejercita de verdad.
 *
 * <p>Cada intento va en su propia transaccion y se revierte. Sin eso, el primer rechazo
 * deja la transaccion abortada y los siguientes fallan por eso y no por su regla.
 */
final class LaBase {

    private final DSLContext dsl;
    private final TransactionTemplate transaccion;

    LaBase(DSLContext dsl, TransactionTemplate transaccion) {
        this.dsl = dsl;
        this.transaccion = transaccion;
    }

    /** El mensaje del rechazo, o cadena vacia si la base **acepto** lo que no debia. */
    String rechaza(String sql, Object... parametros) {
        try {
            transaccion.execute(estado -> {
                dsl.execute(sql, parametros);
                estado.setRollbackOnly();
                return null;
            });
            return "";
        } catch (RuntimeException e) {
            Throwable raiz = e;
            while (raiz.getCause() != null && raiz.getCause() != raiz) {
                raiz = raiz.getCause();
            }
            return String.valueOf(raiz.getMessage());
        }
    }

    /** Cuantos eventos de ese tipo dejo el agregado. R-AUD-04 pide exactamente uno. */
    int eventos(String tipo, Object agregadoId) {
        return dsl.fetchOne(
                        "SELECT count(*)::int FROM comun.outbox WHERE agregado_id = ? AND tipo = ?",
                        agregadoId,
                        tipo)
                .get(0, Integer.class);
    }

    /** Una fila en la bitacora, para poder intentar borrarla y ver que no se deja. */
    void sembrarBitacora(String entidad) {
        transaccion.execute(estado -> {
            dsl.execute(
                    """
                    INSERT INTO comun.bitacora_evento
                        (id, secuencia, entidad, entidad_id, accion, origen, correlation_id,
                         hash_registro, hash_anterior, fecha_hora)
                    VALUES (gen_random_uuid(),
                            nextval(pg_get_serial_sequence('comun.bitacora_evento','secuencia')),
                            ?, gen_random_uuid(), 'CREACION', 'TAREA_PROGRAMADA',
                            gen_random_uuid(), repeat('a', 64), repeat('0', 64), now())
                    """,
                    entidad);
            return null;
        });
    }
}
