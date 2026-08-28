package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.transparencia.aplicacion.CU70RegistrarEventoReputacion.EntradaEvento;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-70 · Lo que la base y el caso de uso rechazan. */
class CU70RechazosTest extends BaseDeTransparencia {

    private record Caso(UUID usuario, UUID grupo, UUID participante, ContextoSesion ctx) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        UUID grupo = fixtura.grupo();
        return new Caso(usuario, grupo, fixtura.participante(grupo, usuario), contextoDe(usuario));
    }

    private EntradaEvento evento(Caso c, UUID referencia) {
        return new EntradaEvento(
                c.usuario(),
                c.grupo(),
                c.participante(),
                "APORTE_PUNTUAL",
                "OBLIGACION_APORTE",
                referencia,
                "Aporte acreditado en fecha",
                true,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // evento_reputacion es append-only. Borrar un evento dejaria un puntaje que
        // nadie puede reconstruir; editarlo dejaria uno que nadie puede discutir.
        Caso c = caso();
        var registrado = transaccion.execute(t -> eventoCU.registrar(evento(c, UUID.randomUUID()), c.ctx()));

        assertThat(rechazaLaBase(
                        "UPDATE transparencia.evento_reputacion SET impacto = 999 WHERE id = ?", registrado.eventoId()))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM transparencia.evento_reputacion WHERE id = ?", registrado.eventoId()))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Todo cambio relevante emite su evento de dominio en la MISMA transaccion. Un
        // evento emitido despues, por fuera, puede perderse justo cuando importa.
        Caso c = caso();
        UUID referencia = UUID.randomUUID();

        transaccion.execute(t -> eventoCU.registrar(evento(c, referencia), c.ctx()));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.evento_reputacion_registrado'
                           AND payload->>'usuarioId' = ? AND payload->>'factorAfectado' IS NOT NULL
                           AND payload->>'modeloVersion' IS NOT NULL
                        """,
                        c.usuario().toString()))
                .isEqualTo(1);

        // Y si la transaccion se deshace, el evento se va con ella.
        UUID otra = UUID.randomUUID();
        try {
            transaccion.execute(t -> {
                eventoCU.registrar(evento(c, otra), c.ctx());
                throw new IllegalStateException("fallo despues de emitir");
            });
        } catch (RuntimeException esperado) {
            // el rollback es el punto de la prueba
        }
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_reputacion WHERE referencia_origen_id = ?",
                        otra))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-REP-01")
    void rechazaRREP01() {
        // Un hecho puntua una sola vez. Contarlo dos veces le sube el puntaje a alguien
        // por algo que hizo una vez, y el numero deja de significar lo que dice.
        Caso c = caso();
        UUID referencia = UUID.randomUUID();
        transaccion.execute(t -> eventoCU.registrar(evento(c, referencia), c.ctx()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO transparencia.evento_reputacion
                            (usuario_id, grupo_id, participante_id, tipo, referencia_tipo, referencia_origen_id,
                             impacto, factor_afectado, descripcion, modelo_version, es_reversible, ocurrido_en)
                        VALUES (?, ?, ?, 'APORTE_PUNTUAL', 'OBLIGACION_APORTE', ?, 5.00,
                                'PUNTUALIDAD_DE_APORTE', 'Duplicado', 'v1', true, now())
                        """,
                        c.usuario(),
                        c.grupo(),
                        c.participante(),
                        referencia))
                .contains("uq_evento_reputacion_hecho");

        // Y un tipo de evento que el modelo no tiene reglado no se puntua: inventarle un
        // impacto es decidir a mano cuanto vale la conducta de alguien.
        assertThatThrownBy(() -> transaccion.execute(t -> eventoCU.registrar(
                        new EntradaEvento(
                                c.usuario(),
                                c.grupo(),
                                c.participante(),
                                "FRAUDE_CONFIRMADO",
                                "PARTICIPANTE",
                                UUID.randomUUID(),
                                "Sin regla declarada en el modelo v1",
                                false,
                                OffsetDateTime.now(ZoneOffset.UTC)),
                        c.ctx())))
                .hasMessageContaining("no tiene regla");
    }
}
