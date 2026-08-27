package bo.aportaya.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-83 · las pruebas de RECHAZO, en su propio archivo.
 *
 * <p>Separadas no por estetica: son otra pregunta. Las del caso de uso verifican que
 * hace lo que promete; estas, que la BASE rechaza lo que no debe entrar aunque la
 * aplicacion se equivoque.
 */
class CU83RechazosTest extends BaseDeNotificaciones {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    @Test
    @DisplayName("rechaza por R-NOT-01")
    void rechazaRNOT01() {
        assertThat(contar("SELECT count(*)::int FROM pg_indexes WHERE indexname = ?", "uq_envio_idempotencia"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-NOT-02")
    void rechazaRNOT02() {
        assertThat(rechazaLaBase("SELECT fn_not_puede_enviar('%s', true)".formatted(UUID.randomUUID())))
                .isEmpty();
    }

    @Test
    @DisplayName("rechaza por R-NOT-03")
    void rechazaRNOT03() {
        assertThat(contar("SELECT count(*)::int FROM pg_trigger WHERE tgname = ?", "tg_notificacion_supresion"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La bitacora no se borra: es append-only y el trigger lo hace cumplir.
        dslFixtura.execute(
                """
                INSERT INTO comun.bitacora_evento
                    (id, secuencia, entidad, entidad_id, accion, origen, correlation_id,
                     hash_registro, hash_anterior, fecha_hora)
                VALUES (gen_random_uuid(),
                        nextval(pg_get_serial_sequence('comun.bitacora_evento','secuencia')),
                        'envio', gen_random_uuid(), 'CREACION', 'TAREA_PROGRAMADA',
                        gen_random_uuid(), repeat('a', 64), repeat('0', 64), now())
                """);

        assertThat(rechazaLaBase("DELETE FROM comun.bitacora_evento")).contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO comun.bitacora_evento
                            (id, secuencia, entidad, entidad_id, accion, origen, correlation_id,
                             hash_registro, hash_anterior, fecha_hora)
                        VALUES (gen_random_uuid(),
                                nextval(pg_get_serial_sequence('comun.bitacora_evento','secuencia')),
                                'envio', gen_random_uuid(), 'CREACION', 'USUARIO',
                                gen_random_uuid(), repeat('a', 64), repeat('0', 64), now())
                        """))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-RIS-03")
    void rechazaRRIS03() {
        // ck_plan_objetivos: un plan de continuidad sin objetivos medibles no existe.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO cumplimiento.plan_continuidad
                            (id, proceso_critico, rto_minutos, rpo_minutos, estrategia,
                             periodicidad_prueba_meses, vigente_desde, proxima_prueba)
                        VALUES (gen_random_uuid(), 'Despacho de avisos', 0, 0, 'RESPALDO',
                                6, now(), current_date + 30)
                        """))
                .contains("ck_plan_objetivos");
    }
}
