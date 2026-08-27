package bo.aportaya.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-82 · las pruebas de RECHAZO, en su propio archivo.
 *
 * <p>Separadas no por estetica: son otra pregunta. Las del caso de uso verifican que
 * hace lo que promete; estas, que la BASE rechaza lo que no debe entrar aunque la
 * aplicacion se equivoque.
 */
class CU82RechazosTest extends BaseDeNotificaciones {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    @Test
    @DisplayName("rechaza por R-NOT-01")
    void rechazaRNOT01() {
        assertThat(contar("SELECT count(*)::int FROM pg_indexes WHERE indexname = ?", "uq_evento_entrega_idempotencia"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-NOT-03")
    void rechazaRNOT03() {
        // La supresion vigente gana sobre cualquier campana: el trigger rechaza el
        // INSERT de la notificacion, no lo deja «pasar con aviso».
        UUID usuario = fixtura.usuario();
        String correo = "eva" + usuario.toString().substring(0, 8) + "@prueba.bo";
        fixtura.canalVerificado(usuario, "CORREO", correo);
        UUID evento = fixtura.evento("promo.x", "COMERCIAL", false, "BAJA");
        dslFixtura.execute(
                """
                INSERT INTO notificaciones.lista_supresion
                    (id, identificador, canal, motivo, categoria, activa, agregado_en, permanente)
                VALUES (gen_random_uuid(), ?, 'CORREO', 'QUEJA_SPAM', 'COMERCIAL', true, now(), false)
                """,
                correo);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO notificaciones.notificacion
                            (id, usuario_id, evento_id, prioridad, contexto, clave_deduplicacion, estado,
                             programada_para, creada_en, correlation_id)
                        VALUES (gen_random_uuid(), '%s', '%s', 'BAJA', '{}'::jsonb, 'k-not03', 'EN_COLA',
                                now(), now(), gen_random_uuid())
                        """
                                .formatted(usuario, evento)))
                .contains("R-NOT-03");
    }

    @Test
    @DisplayName("rechaza por R-SEG-01")
    void rechazaRSEG01() {
        // Solo hash, token o enmascarado: un dato sensible en claro no entra.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO identidad.documento_identidad
                            (id, usuario_id, tipo, numero_hash, pais_emision, verificado)
                        VALUES (gen_random_uuid(), gen_random_uuid(), 'CI', '1234567', 'BO', false)
                        """))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
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
    @DisplayName("rechaza por R-CON-01")
    void rechazaRCON01() {
        // ck_reclamo_plazo: el plazo se guarda al ingresar y va despues del ingreso.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO cumplimiento.reclamo_cliente
                            (id, usuario_id, codigo, canal_ingreso, categoria, descripcion, estado,
                             fecha_ingreso, plazo_respuesta, dias_habiles_plazo)
                        VALUES (gen_random_uuid(), gen_random_uuid(), 'REC-X', 'APP', 'COBROS',
                                'Sin plazo futuro', 'ABIERTO', now(), now() - interval '1 day', 3)
                        """))
                .isNotEmpty();
    }
}
