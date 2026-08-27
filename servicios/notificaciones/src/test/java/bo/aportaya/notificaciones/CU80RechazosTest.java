package bo.aportaya.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-80 · las pruebas de RECHAZO, en su propio archivo.
 *
 * <p>Separadas no por estetica: son otra pregunta. Las del caso de uso verifican que
 * hace lo que promete; estas, que la BASE rechaza lo que no debe entrar aunque la
 * aplicacion se equivoque.
 */
class CU80RechazosTest extends BaseDeNotificaciones {

    private static final String PLANTILLA = "APORTE_POR_VENCER";
    private static final String CUERPO = "Hola {{nombre}}, tu aporte de {{monto}} vence pronto.";

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    @Test
    @DisplayName("rechaza por R-NOT-01")
    void rechazaRNOT01() {
        // uq_envio_idempotencia: el mismo envio con la misma clave no entra dos veces.
        UUID usuario = fixtura.usuario();
        UUID evento = fixtura.evento("aporte.por_vencer", "COBRANZA", false, "NORMAL");
        UUID version = fixtura.plantilla(PLANTILLA, evento, "IN_APP", CUERPO);
        UUID proveedor = fixtura.proveedor("SIMULADO", "IN_APP", 1, new java.math.BigDecimal("0.00"), 100, true);
        dslFixtura.execute(
                """
                INSERT INTO notificaciones.notificacion
                    (id, usuario_id, evento_id, prioridad, contexto, clave_deduplicacion, estado,
                     programada_para, creada_en, correlation_id)
                VALUES ('11111111-1111-1111-1111-111111111111', ?, ?, 'NORMAL', '{}'::jsonb, 'k-not01',
                        'EN_COLA', now(), now(), gen_random_uuid())
                """,
                usuario,
                evento);
        String insertEnvio =
                """
                INSERT INTO notificaciones.envio_notificacion
                    (id, notificacion_id, proveedor_id, version_plantilla_id, canal, destinatario,
                     clave_idempotencia, encolado_en, contenido_enviado, estado, orden, intentos,
                     max_intentos, costo, moneda)
                VALUES (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', '%s', '%s', 'IN_APP',
                        'app:x', 'clave-repetida', now(), 'texto', 'PENDIENTE', 1, 0, 3, 0.00, 'BOB')
                """
                        .formatted(proveedor, version);
        dslFixtura.execute(insertEnvio);

        assertThat(rechazaLaBase(insertEnvio)).contains("uq_envio_idempotencia");
    }

    @Test
    @DisplayName("rechaza por R-NOT-02")
    void rechazaRNOT02() {
        // fn_not_puede_enviar deniega por omision: sin preferencia, tope conservador.
        UUID usuario = fixtura.usuario();

        // Un obligatorio nunca topea; el que topea es el que no lo es.
        assertThat(rechazaLaBase("SELECT fn_not_puede_enviar('%s', true)".formatted(usuario)))
                .isEmpty();
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
        // registro_acceso_datos exige justificacion NO NULA de al menos diez caracteres.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO auditoria.registro_acceso_datos
                            (id, usuario_id, entidad, entidad_id, campos, justificacion, fecha_hora)
                        VALUES (gen_random_uuid(), gen_random_uuid(), 'usuario', gen_random_uuid(),
                                'nombres', NULL, now())
                        """))
                .isNotEmpty();
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
                                'notificacion', gen_random_uuid(), 'CREACION', 'USUARIO',
                                gen_random_uuid(), repeat('a', 64), repeat('0', 64), now())
                        """))
                .isNotEmpty();
    }
}
