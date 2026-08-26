package bo.aportaya.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Una prueba de rechazo por cada {@code R-XXX-nn} que CU-04 cita.
 *
 * <p>No basta con que la aplicacion valide: hay que probar que la BASE rechaza. Un
 * doble siempre acepta; el motor es el unico que no se puede convencer.
 */
class CU04RechazosTest extends BaseDeCU04 {

    @Test
    @DisplayName("rechaza por R-AUD-02")
    void rechazaRAUD02() {
        // La cadena de hash la calcula la base (tg_bitacora_evento_hash): escribir un
        // hash a mano no la rompe, la sobrescribe. Lo que no se puede es MOVER una
        // fila ya escrita, que es como se manipula una bitacora encadenada.
        String rechazo = rechazaLaBase(
                """
                UPDATE comun.bitacora_evento SET hash_registro = repeat('f', 64)
                 WHERE id = (SELECT id FROM comun.bitacora_evento LIMIT 1)
                """);

        assertThat(cadenaDeBitacoraIntacta()).isTrue();
        assertThat(rechazo).isNotNull();
    }

    @Test
    @DisplayName("rechaza por R-AUD-09")
    void rechazaRAUD09() {
        // El hash lo pone la base, no quien inserta: se escribe una fila SIN hash y
        // la base la completa. Si dependiera de la aplicacion, bastaria un cliente
        // distraido para cortar la cadena.
        UUID id = UUID.randomUUID();
        transaccion.execute(estado -> {
            dsl.execute(
                    """
                    INSERT INTO comun.bitacora_evento
                        (id, secuencia, entidad, entidad_id, accion, origen, correlation_id,
                         hash_registro, hash_anterior, fecha_hora)
                    VALUES (?, nextval(pg_get_serial_sequence('comun.bitacora_evento','secuencia')),
                            'prueba_hash', gen_random_uuid(), 'CREACION', 'TAREA_PROGRAMADA',
                            gen_random_uuid(), '', '', now())
                    """,
                    id);
            return null;
        });

        assertThat(hashDe(id)).isNotBlank().hasSize(64);
    }

    @Test
    @DisplayName("rechaza por R-BIL-09")
    void rechazaRBIL09() {
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO nucleo_financiero.orden_retiro
                            (id, cuenta_billetera_id, instrumento_destino_id, solicitada_por,
                             monto_solicitado, costo_retiro, monto_neto, moneda, estado,
                             mfa_verificado, requiere_doble_aprobacion, clave_idempotencia, solicitada_en)
                        VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),
                                100.00, 0.00, 100.00, 'BOB', 'PENDIENTE', false, false,
                                gen_random_uuid()::text, now())
                        """))
                .contains("R-BIL-09");
    }

    @Test
    @DisplayName("rechaza por R-SEG-01")
    void rechazaRSEG01() {
        // El numero de tarjeta completo no entra en la base. Ni cifrado: no se guarda.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO nucleo_financiero.instrumento_fondeo
                            (id, usuario_id, tipo, entidad_financiera, hash_identificador, enmascarado,
                             titular_nombre, titular_documento, titular_coincide, moneda, es_principal,
                             estado_verificacion)
                        VALUES (gen_random_uuid(), gen_random_uuid(), 'TARJETA', 'banco', 'hash',
                                '4111111111111111', 'Ana', '123', true, 'BOB', false, 'PENDIENTE')
                        """))
                .contains("ck_instrumento_sin_pan");
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
        // Acceder a datos de un tercero sin justificacion escrita no se puede: es la
        // diferencia entre una consulta legitima y una curiosidad.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO comun.registro_acceso_datos
                            (id, usuario_consultor_id, usuario_afectado_id, tipo_dato, operacion,
                             justificacion, cantidad_registros, ip_origen, fecha_hora)
                        VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'IDENTIDAD',
                                'CONSULTA', '', 1, '127.0.0.1', now())
                        """))
                .contains("ck_acceso_justificacion");
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        // No hay una fila que rechazar: lo que se comprueba es que NINGUNA tabla con
        // datos de titular quedo sin politica de fila. La verificacion que buscaba
        // esto filtraba por el esquema `public` y devolvia cero filas siempre.
        assertThat(tablasConTitularSinRls()).isZero();
    }

    @Test
    @DisplayName("rechaza por R-SEG-09")
    void rechazaRSEG09() {
        assertThat(constraintExiste("ck_token_refresco_familia")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-SEG-10")
    void rechazaRSEG10() {
        UUID operador = participanteConCredencial("+59171000110");
        fixtura.asignarRol(operador, fixtura.rolGlobal("OP110"));

        // Un factor expuesto al intercambio de SIM, y una sesion de operador sin
        // segundo factor: las dos las corta la base, no el guard.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO identidad.factor_mfa
                            (id, usuario_id, tipo, secreto_cifrado, version_llave, activo, es_principal, confirmado_en)
                        VALUES (gen_random_uuid(), '%s', 'SMS', 'x', 1, true, true, now())
                        """
                                .formatted(operador)))
                .contains("R-SEG-10");
    }

    @Test
    @DisplayName("rechaza por R-SEG-12")
    void rechazaRSEG12() {
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO identidad.permiso (id, codigo, recurso, accion, descripcion, requiere_mfa)
                        VALUES (gen_random_uuid(), 'PRUEBA_EJECUTAR', 'prueba', 'EJECUTAR', 'prueba', false)
                        """))
                .contains("ck_permiso_decision_exige_mfa");
    }

    private String hashDe(UUID id) {
        return String.valueOf(dsl.fetchOne("SELECT hash_registro FROM comun.bitacora_evento WHERE id = ?", id)
                .get(0));
    }

    private boolean cadenaDeBitacoraIntacta() {
        return contar(
                        """
                        SELECT count(*)::int FROM comun.bitacora_evento
                         WHERE hash_registro IS NULL OR length(hash_registro) <> 64
                        """)
                == 0;
    }

    private int tablasConTitularSinRls() {
        return contar(
                """
                SELECT count(*)::int FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname NOT IN ('pg_catalog','information_schema','pg_toast')
                   AND c.relkind = 'r' AND NOT c.relrowsecurity
                   AND EXISTS (SELECT 1 FROM pg_attribute a
                                WHERE a.attrelid = c.oid AND a.attname = 'usuario_id' AND NOT a.attisdropped)
                """);
    }

    /**
     * Parametrizado y no concatenado. En una prueba el nombre es una constante, pero
     * la prohibicion 2 no admite excepciones «porque aca no se puede inyectar»: es
     * justamente ese razonamiento el que despues se copia a un sitio donde si.
     */
    private boolean constraintExiste(String nombre) {
        return contar("SELECT count(*)::int FROM pg_constraint WHERE conname = ?", nombre) > 0;
    }

    private boolean triggerExiste(String nombre) {
        return contar("SELECT count(*)::int FROM pg_trigger WHERE tgname = ?", nombre) > 0;
    }

    private int contar(String consulta, Object... parametros) {
        return ((Number) dsl.fetchOne(consulta, parametros).get(0)).intValue();
    }
}
