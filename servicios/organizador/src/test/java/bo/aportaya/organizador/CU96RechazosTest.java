package bo.aportaya.organizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.organizador.aplicacion.CU95DefinirAutomatizacion.EntradaRegla;
import bo.aportaya.organizador.aplicacion.CU96EjecutarTarea.EntradaEjecucion;
import bo.aportaya.organizador.aplicacion.CU96EjecutarTarea.EntradaProgramacion;
import bo.aportaya.organizador.aplicacion.CU96EjecutarTarea.SalidaProgramacion;
import bo.aportaya.organizador.dominio.ClaveDeTarea;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-96 · las pruebas de RECHAZO, una por restriccion citada. */
class CU96RechazosTest extends BaseDeOrganizador {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(UUID reglaId, UUID grupoId, ContextoSesion ctx) {}

    private Caso caso(String accion, boolean confirmacion, int prioridad) {
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        var definida = transaccion.execute(t -> reglaCU.definir(
                new EntradaRegla(
                        "REGLA-" + corto(),
                        "Regla de prueba",
                        "EVENTO",
                        "aporte.vencido",
                        "dias_para_vencer = 3",
                        accion,
                        confirmacion,
                        prioridad),
                ctx));
        transaccion.execute(t -> reglaCU.activar(definida.reglaId(), ctx));
        return new Caso(definida.reglaId(), fixtura.grupo(), ctx);
    }

    private OffsetDateTime cuando() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
    }

    private OffsetDateTime arranco() {
        return OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Cada ejecucion queda registrada con su resultado y sus registros afectados.
        // Borrarlas dejaria una tarea que dice haber corrido sin poder mostrar que hizo.
        Caso c = caso("ENVIAR_RECORDATORIO", false, 80);
        SalidaProgramacion tarea = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), cuando()), c.ctx()));
        transaccion.execute(t -> tareaCU.anotarEjecucion(
                new EntradaEjecucion(tarea.tareaId(), arranco(), "EXITO", 9, "{}", null), c.ctx()));

        assertThat(rechazaLaBase("UPDATE organizador.ejecucion_tarea SET resultado = NULL WHERE tarea_id = '%s'"
                        .formatted(tarea.tareaId())))
                .isNotEmpty();
        // Y una ejecucion no puede terminar antes de empezar.
        assertThat(rechazaLaBase(
                        "UPDATE organizador.ejecucion_tarea SET finalizada_en = iniciada_en - interval '1 hour' WHERE tarea_id = '%s'"
                                .formatted(tarea.tareaId())))
                .contains("ck_ejecucion_tarea_fin");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        Caso c = caso("ENVIAR_RECORDATORIO", false, 81);
        SalidaProgramacion tarea = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), cuando()), c.ctx()));

        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "organizador.tarea_programada",
                        tarea.tareaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-06")
    void rechazaRBIL06() {
        // Idempotencia de toda operacion con dinero: la clave es determinista, y el
        // mismo disparo produce la misma clave. Si dependiera del reloj de la corrida,
        // una regla de aplicar mora cobraria el recargo tantas veces como se reintente.
        UUID regla = UUID.randomUUID();
        UUID grupo = UUID.randomUUID();
        OffsetDateTime momento = OffsetDateTime.parse("2026-08-28T10:30:00Z");

        assertThat(ClaveDeTarea.de(regla, grupo, momento)).isEqualTo(ClaveDeTarea.de(regla, grupo, momento));
        // Dentro del mismo minuto es el mismo disparo; en otro minuto, otro.
        assertThat(ClaveDeTarea.de(regla, grupo, momento.plusSeconds(30)))
                .isEqualTo(ClaveDeTarea.de(regla, grupo, momento));
        assertThat(ClaveDeTarea.de(regla, grupo, momento.plusMinutes(1)))
                .isNotEqualTo(ClaveDeTarea.de(regla, grupo, momento));
    }

    @Test
    @DisplayName("rechaza por R-ORG-06")
    void rechazaRORG06() {
        // Una tarea que espera confirmacion NO se ejecuta. Es el agujero que la regla
        // cierra: la confirmacion tiene que ser previa al efecto, no posterior.
        Caso c = caso("EJECUTAR_ENTREGA", true, 82);
        SalidaProgramacion tarea = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), cuando()), c.ctx()));

        assertThat(tarea.estado()).isEqualTo("REQUIERE_APROBACION");
        assertThatThrownBy(() -> transaccion.execute(t -> tareaCU.anotarEjecucion(
                        new EntradaEjecucion(tarea.tareaId(), arranco(), "EXITO", 100, "{}", null), c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("espera confirmacion humana");
        assertThat(contar("SELECT count(*)::int FROM organizador.ejecucion_tarea WHERE tarea_id = ?", tarea.tareaId()))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-ORG-07")
    void rechazaRORG07() {
        // Una tarea por clave de idempotencia. La BASE lo sostiene aunque la
        // aplicacion se equivoque: dos tareas con la misma clave son dos ejecuciones
        // del mismo hecho.
        Caso c = caso("ENVIAR_RECORDATORIO", false, 83);
        SalidaProgramacion tarea = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), cuando()), c.ctx()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.tarea_automatizada
                            (id, regla_id, grupo_id, tipo, programada_para, estado, intentos,
                             clave_idempotencia)
                        VALUES (gen_random_uuid(), '%s', '%s', 'ENVIAR_RECORDATORIO', now(), 'PROGRAMADA',
                                0, '%s')
                        """
                                .formatted(c.reglaId(), c.grupoId(), tarea.claveIdempotencia())))
                .contains("uq_tarea_automatizada_clave");
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        // Una regla APAGADA no programa nada. Que exista no significa que este
        // encendida, y programar desde una apagada es ejecutar algo que alguien
        // decidio no ejecutar.
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        var definida = transaccion.execute(t -> reglaCU.definir(
                new EntradaRegla(
                        "APAGADA-" + corto(),
                        "Nunca se encendio",
                        "EVENTO",
                        "aporte.vencido",
                        "siempre",
                        "ENVIAR_RECORDATORIO",
                        false,
                        84),
                ctx));
        UUID grupo = fixtura.grupo();

        assertThatThrownBy(() -> transaccion.execute(
                        t -> tareaCU.programar(new EntradaProgramacion(definida.reglaId(), grupo, cuando()), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("esta inactiva");
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.tarea_automatizada WHERE regla_id = ?",
                        definida.reglaId()))
                .isZero();
    }
}
