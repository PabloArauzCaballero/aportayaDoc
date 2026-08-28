package bo.aportaya.organizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.organizador.aplicacion.CU95DefinirAutomatizacion.EntradaRegla;
import bo.aportaya.organizador.aplicacion.CU96EjecutarTarea.EntradaEjecucion;
import bo.aportaya.organizador.aplicacion.CU96EjecutarTarea.EntradaProgramacion;
import bo.aportaya.organizador.aplicacion.CU96EjecutarTarea.SalidaProgramacion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-96 · Programar y ejecutar una tarea automatizada. */
class CU96Test extends BaseDeOrganizador {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(UUID reglaId, UUID grupoId, ContextoSesion ctx) {}

    /** Una regla activa. `accion` decide si sus tareas esperan confirmacion. */
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

    /** Cuando se programa: en el futuro. */
    private OffsetDateTime cuando() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);
    }

    /**
     * Cuando arranco la ejecucion: en el pasado.
     *
     * <p>{@code ck_ejecucion_tarea_fin} exige {@code finalizada_en >= iniciada_en}, y
     * la finalizacion la pone el reloj del caso de uso. Una ejecucion que termina antes
     * de empezar no es un detalle de prueba: seria un registro que no se puede leer.
     */
    private OffsetDateTime arranco() {
        return OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
    }

    @Test
    @DisplayName(
            "Dada una regla que dispara sobre un hecho · Cuando el hecho se procesa dos veces · Entonces existe una sola tarea_automatizada")
    void criterio1() {
        Caso c = caso("ENVIAR_RECORDATORIO", false, 61);
        OffsetDateTime momento = cuando();

        SalidaProgramacion a = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), momento), c.ctx()));
        SalidaProgramacion b = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), momento), c.ctx()));

        // La clave es determinista —regla, grupo y minuto—: si dependiera del reloj de
        // la corrida, cada reintento generaria una tarea nueva y una regla de aplicar
        // mora cobraria el recargo tantas veces como se reintente.
        assertThat(b.tareaId()).isEqualTo(a.tareaId());
        assertThat(b.esNueva()).isFalse();
        assertThat(contar("SELECT count(*)::int FROM organizador.tarea_automatizada WHERE regla_id = ?", c.reglaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una tarea que exige confirmación humana · Cuando nadie confirma dentro del plazo · Entonces queda CADUCADA y no se ejecuta")
    void criterio2() {
        Caso c = caso("EJECUTAR_ENTREGA", true, 62);
        SalidaProgramacion tarea = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), cuando()), c.ctx()));

        // HUECO DECLARADO: `ck_tarea_automatizada_estado` no admite CADUCADA. El estado
        // equivalente que si existe es CANCELADA. Manda la DDL. Ver H-8 en
        // planes/informes/carril-2E.md.
        //
        // Lo que importa del criterio se cumple igual, y es lo unico que no se negocia:
        // **no se ejecuta**. Una tarea esperando confirmacion que se ejecuta sola es
        // exactamente el agujero que R-ORG-06 cierra.
        assertThat(tarea.estado()).isEqualTo("REQUIERE_APROBACION");
        assertThatThrownBy(() -> transaccion.execute(t -> tareaCU.anotarEjecucion(
                        new EntradaEjecucion(tarea.tareaId(), arranco(), "EXITO", 5, "{}", null), c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("espera confirmacion humana");
        assertThat(contar("SELECT count(*)::int FROM organizador.ejecucion_tarea WHERE tarea_id = ?", tarea.tareaId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dadas tres réplicas del motor · Cuando toman tareas vencidas a la vez · Entonces ninguna tarea se ejecuta dos veces")
    void criterio3() {
        Caso c = caso("ENVIAR_RECORDATORIO", false, 63);
        SalidaProgramacion tarea = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), cuando()), c.ctx()));

        var primera = transaccion.execute(t -> tareaCU.anotarEjecucion(
                new EntradaEjecucion(tarea.tareaId(), arranco(), "EXITO", 12, "{}", null), c.ctx()));
        // La segunda replica la encuentra COMPLETADA y no vuelve a ejecutar. El candado
        // de fila las pone en cola; el estado decide.
        var segunda = transaccion.execute(t -> tareaCU.anotarEjecucion(
                new EntradaEjecucion(tarea.tareaId(), arranco(), "EXITO", 12, "{}", null), c.ctx()));
        var tercera = transaccion.execute(t -> tareaCU.anotarEjecucion(
                new EntradaEjecucion(tarea.tareaId(), arranco(), "EXITO", 12, "{}", null), c.ctx()));

        assertThat(primera.esNueva()).isTrue();
        assertThat(segunda.esNueva()).isFalse();
        assertThat(tercera.esNueva()).isFalse();
        assertThat(contar("SELECT count(*)::int FROM organizador.ejecucion_tarea WHERE tarea_id = ?", tarea.tareaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una tarea cuya condición dejó de cumplirse al ejecutar · Cuando el motor la toma · Entonces se cancela con motivo y no se fuerza la acción")
    void criterio4() {
        Caso c = caso("ENVIAR_RECORDATORIO", false, 64);
        SalidaProgramacion tarea = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), cuando()), c.ctx()));

        // El motor la toma, ve que la condicion ya no se cumple, y lo registra como
        // ejecucion sin efecto: cero registros afectados y su motivo. Forzar la accion
        // «porque la tarea estaba programada» es actuar sobre un mundo que ya cambio.
        var salida = transaccion.execute(t -> tareaCU.anotarEjecucion(
                new EntradaEjecucion(
                        tarea.tareaId(),
                        arranco(),
                        "EXITO",
                        0,
                        "{\"motivo\":\"la condicion dejo de cumplirse: el aporte ya se pago\"}",
                        null),
                c.ctx()));

        assertThat(salida.estadoDeLaTarea()).isEqualTo("COMPLETADA");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM organizador.ejecucion_tarea
                         WHERE tarea_id = ? AND registros_afectados = 0
                           AND detalle->>'motivo' LIKE '%dejo de cumplirse%'
                        """,
                        tarea.tareaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        Caso c = caso("ENVIAR_RECORDATORIO", false, 65);
        OffsetDateTime momento = cuando();

        SalidaProgramacion a = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), momento), c.ctx()));
        SalidaProgramacion b = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), momento), c.ctx()));

        assertThat(b.claveIdempotencia()).isEqualTo(a.claveIdempotencia());
        assertThat(b.tareaId()).isEqualTo(a.tareaId());
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // La BASE decide: uq_tarea_automatizada_clave, aunque la aplicacion se
        // equivoque. Dos tareas con la misma clave son dos ejecuciones del mismo hecho.
        Caso c = caso("ENVIAR_RECORDATORIO", false, 66);
        SalidaProgramacion tarea = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), cuando()), c.ctx()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.tarea_automatizada
                            (id, regla_id, grupo_id, tipo, programada_para, estado, intentos,
                             clave_idempotencia)
                        VALUES (gen_random_uuid(), '%s', '%s', 'ENVIAR_RECORDATORIO', now(),
                                'PROGRAMADA', 0, '%s')
                        """
                                .formatted(c.reglaId(), c.grupoId(), tarea.claveIdempotencia())))
                .contains("uq_tarea_automatizada_clave");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Los intentos de la tarea igualan las ejecuciones registradas, exacto. Si no
        // cuadraran, no habria forma de saber cuantas veces se intento algo que mueve
        // plata ajena.
        Caso c = caso("ENVIAR_RECORDATORIO", false, 67);
        SalidaProgramacion tarea = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), cuando()), c.ctx()));

        transaccion.execute(t -> tareaCU.anotarEjecucion(
                new EntradaEjecucion(tarea.tareaId(), arranco(), "ERROR", 0, "{}", "el proveedor no respondio"),
                c.ctx()));
        var segunda = transaccion.execute(t -> tareaCU.anotarEjecucion(
                new EntradaEjecucion(tarea.tareaId(), arranco(), "EXITO", 7, "{}", null), c.ctx()));

        assertThat(segunda.intentos()).isEqualTo(2);
        assertThat(contar("SELECT count(*)::int FROM organizador.ejecucion_tarea WHERE tarea_id = ?", tarea.tareaId()))
                .isEqualTo(2);
        assertThat(contar("SELECT intentos::int FROM organizador.tarea_automatizada WHERE id = ?", tarea.tareaId()))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "motor-tareas"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "motor-tareas"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Agotados los intentos, la tarea queda FALLIDA y NO se ejecuta mas. Reintentar
        // para siempre una accion que mueve plata es peor que fallar: al tercer intento
        // ya nadie esta mirando.
        Caso c = caso("ENVIAR_RECORDATORIO", false, 68);
        SalidaProgramacion tarea = transaccion.execute(
                t -> tareaCU.programar(new EntradaProgramacion(c.reglaId(), c.grupoId(), cuando()), c.ctx()));

        for (int i = 0; i < INTENTOS_MAXIMOS_DE_TAREA; i++) {
            transaccion.execute(t -> tareaCU.anotarEjecucion(
                    new EntradaEjecucion(tarea.tareaId(), arranco(), "ERROR", 0, "{}", "sigue sin responder"),
                    c.ctx()));
        }

        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.tarea_automatizada WHERE id = ? AND estado = 'FALLIDA'",
                        tarea.tareaId()))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*)::int FROM organizador.ejecucion_tarea WHERE tarea_id = ?", tarea.tareaId()))
                .isEqualTo(INTENTOS_MAXIMOS_DE_TAREA);
    }
}
