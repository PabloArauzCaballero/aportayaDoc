package bo.aportaya.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-07 · las pruebas de RECHAZO.
 *
 * <p>Otra pregunta que las de {@link CU07Test}: aquellas verifican que el caso de uso
 * hace lo que promete; estas, que la BASE rechaza lo que no debe entrar aunque la
 * aplicacion se equivoque. En un expediente de datos personales la diferencia es todo:
 * si la garantia viviera solo en el codigo, un `UPDATE` desde una consola dejaria sin
 * efecto una retencion legal y nadie se enteraria.
 */
class CU07RechazosTest extends BaseDeAuditoria {

    @Test
    @DisplayName("rechaza por R-SEG-06")
    void rechazaRSEG06() {
        // La anonimizacion respeta la retencion legal. Es la regla que impide que el
        // derecho de supresion se lleve puesta la obligacion de conservar: no depende
        // de que el caso de uso haya calculado bien, la valida un trigger.
        assertThat(funcionExiste("fn_seg_validar_anonimizacion")).isTrue();
        assertThat(triggerExiste("tg_anonimizacion_retencion")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        // Nada se depura antes de su fecha de conservacion. Diez anos no son una
        // sugerencia: `ck_expediente_retencion_futura` no deja poner una fecha de
        // retencion que ya paso, que es como se «adelantaria» un borrado.
        assertThat(constraintExiste("ck_expediente_retencion_futura")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
        // Todo acceso a datos sensibles queda registrado CON JUSTIFICACION. Un registro
        // de acceso sin motivo no sirve para nada: al auditar, la pregunta no es quien
        // miro sino por que, y la base exige la respuesta.
        assertThat(constraintExiste("ck_acceso_justificacion")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-CON-05")
    void rechazaRCON05() {
        // Los reclamos se conservan diez anos. CU-07 lo cita porque una supresion no
        // puede llevarse un reclamo abierto ni uno cerrado dentro del plazo: el
        // expediente de defensa del consumidor sobrevive al derecho de supresion.
        assertThat(constraintExiste("ck_reclamo_conservacion")).isTrue();
    }

    @Test
    @DisplayName("rechaza · una solicitud sobre alguien que no existe no entra")
    void rechazaTitularInexistente() {
        // La clave foranea cruza a `identidad.usuario` y la base la verifica igual. Un
        // expediente de datos personales sobre un titular inventado seria un expediente
        // que nadie puede contestar.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO auditoria.solicitud_datos_personales
                            (id, usuario_id, tipo, descripcion, estado, fecha_limite_legal, recibida_en)
                        VALUES (gen_random_uuid(), gen_random_uuid(), 'ACCESO', 'inventada',
                                'RECIBIDA', now(), now())
                        """))
                .contains("fk_solicitud_datos_personales_usuario_id");
    }

    @Test
    @DisplayName("rechaza · un tipo de derecho que la tabla no admite")
    void rechazaTipoInexistente() {
        // «SUPRESION» es la palabra del caso de uso y el modelo la llama CANCELACION.
        // El servicio traduce; la base no acepta la otra, y por eso la traduccion vive
        // en un solo lugar en vez de en cada llamador.
        //
        // El titular tiene que existir: si el SELECT no devolviera filas, el INSERT no
        // insertaria nada y la prueba pasaria sin haber probado el CHECK.
        java.util.UUID titular = fixtura.usuario();
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO auditoria.solicitud_datos_personales
                            (id, usuario_id, tipo, descripcion, estado, fecha_limite_legal, recibida_en)
                        SELECT gen_random_uuid(), id, 'SUPRESION', 'palabra del CU',
                               'RECIBIDA', now(), now()
                          FROM identidad.usuario WHERE id = '%s'
                        """
                                .formatted(titular)))
                .contains("ck_solicitud_datos_personales_tipo");
    }
}
