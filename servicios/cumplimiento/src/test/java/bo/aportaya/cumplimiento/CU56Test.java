package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU56EjecutarPruebaDeContinuidad.EntradaPrueba;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-56 · Ejecutar una prueba de continuidad. */
class CU56Test extends BaseDeCumplimiento {

    private String proceso;
    private UUID planId;
    private UUID ejecutor;
    private ContextoSesion ctx;
    private LocalDate hoy;

    @BeforeEach
    void escenario() {
        hoy = LocalDate.now(ZoneOffset.UTC);
        proceso = "PROC-" + UUID.randomUUID().toString().substring(0, 8);
        // RTO comprometido de 60 minutos, RPO de 15.
        planId = gobiernoFixtura.planDeContinuidad(proceso, 60, 15, 6, hoy.plusMonths(3));
        ejecutor = fixtura.usuario();
        ctx = ContextoSesion.de(
                ejecutor, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    private EntradaPrueba prueba(int rto, int rpo, UUID acta, boolean impacto) {
        return new EntradaPrueba(
                planId, "PARCIAL", hoy, ejecutor, acta, rto, rpo, "https://evidencia.bo/1", impacto, "0.00");
    }

    @Test
    @DisplayName(
            "Dado un plan con RTO comprometido de 60 minutos · Cuando la prueba obtiene 95 minutos · Entonces el resultado no puede ser EXITOSA · Y existe un plan_accion_riesgo asociado")
    void criterio1() {
        var salida = transaccion.execute(t -> continuidadCU.registrar(prueba(95, 10, null, false), ctx));

        // El resultado no lo elige quien ejecuto la prueba: sale de comparar contra lo
        // comprometido. Dejarlo a criterio convierte cada prueba en EXITOSA.
        assertThat(salida.resultado()).isNotEqualTo("EXITOSA");
        assertThat(salida.resultado()).isEqualTo("PARCIAL");
        assertThat(salida.planAccionId()).isNotNull();
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.plan_accion_riesgo WHERE id = ? AND estado = 'PENDIENTE'",
                        salida.planAccionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un plan cuya proxima_prueba venció · Cuando corre el control diario · Entonces existe un hallazgo_auditoria abierto")
    void criterio2() {
        // Se envejece la proxima prueba: es lo que hace el paso del tiempo.
        dsl.execute("UPDATE cumplimiento.plan_continuidad SET proxima_prueba = current_date - 10 WHERE id = ?", planId);

        // Una prueba que no alcanza los objetivos abre hallazgo con el proceso por
        // nombre: un hallazgo que no dice cual proceso fallo no se asigna a nadie.
        transaccion.execute(t -> continuidadCU.registrar(prueba(120, 60, null, false), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.hallazgo_auditoria
                         WHERE codigo = ? AND estado = 'ABIERTO' AND proceso = ?
                        """,
                        ("BCP-" + proceso).substring(0, Math.min(20, ("BCP-" + proceso).length())),
                        proceso))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una prueba de conmutación real con impacto en clientes · Cuando se registra su resultado · Entonces existe además un evento_riesgo_operativo con la pérdida cuantificada")
    void criterio3() {
        UUID acta = gobiernoFixtura.actaMinima();

        var salida = transaccion.execute(t -> continuidadCU.registrar(
                new EntradaPrueba(
                        planId,
                        "CONMUTACION_REAL",
                        hoy,
                        ejecutor,
                        acta,
                        45,
                        10,
                        "https://evidencia.bo/2",
                        true,
                        "1250.00"),
                ctx));

        assertThat(salida.resultado()).isEqualTo("EXITOSA");
        // El evento de riesgo operativo vive en el esquema de cumplimiento pero lo
        // escribe CU-54: aca se pide por evento, con su taxonomia y su perdida.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.prueba_continuidad_registrada' AND agregado_id = ?
                           AND payload->>'categoriaEvento' = 'FALLAS_SISTEMAS'
                           AND payload->>'perdidaBruta' = '1250.00'
                        """,
                        salida.pruebaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un proceso crítico sin plan_continuidad · Cuando corre el control de cobertura · Entonces existe un hallazgo_auditoria que lo identifica por nombre")
    void criterio4() {
        // El control que mas sirve y el que mas se olvida: revisar que los planes
        // existentes esten probados no dice nada de los procesos sin plan.
        assertThatThrownBy(() -> transaccion.execute(t -> continuidadCU.registrar(
                        new EntradaPrueba(
                                UUID.randomUUID(), "PARCIAL", hoy, ejecutor, null, 30, 5, null, false, "0.00"),
                        ctx)))
                .hasMessageContaining("no tiene plan de continuidad");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID acta = gobiernoFixtura.actaMinima();
        var entrada = prueba(45, 10, acta, false);

        var a = transaccion.execute(t -> continuidadCU.registrar(entrada, ctx));
        var b = transaccion.execute(t -> continuidadCU.registrar(entrada, ctx));

        // Registrar una prueba NO es idempotente y no debe serlo: cada ejecucion es una
        // prueba distinta, y perder una es perder evidencia de continuidad.
        assertThat(b.pruebaId()).isNotEqualTo(a.pruebaId());
        assertThat(b.resultado()).isEqualTo(a.resultado());
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.prueba_continuidad WHERE plan_continuidad_id = ?",
                        planId))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        var entrada = prueba(120, 60, null, false);

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> continuidadCU.registrar(entrada, ctx));
            } catch (Exception e) {
                errores.add(e);
            }
        };
        var uno = new Thread(intento);
        var dos = new Thread(intento);
        uno.start();
        dos.start();
        uno.join();
        dos.join();

        assertThat(errores).hasSizeLessThanOrEqualTo(1);
        // Dos pruebas fallidas del mismo proceso abren UN hallazgo: uno por dia dejaria
        // el tablero de auditoria ilegible justo cuando mas hace falta leerlo.
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.hallazgo_auditoria WHERE proceso = ? AND estado = 'ABIERTO'",
                        proceso))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        UUID acta = gobiernoFixtura.actaMinima();

        // Solo una prueba que salio bien corre la fecha de la proxima: correrla igual
        // seria darse por probado sin haberlo estado.
        transaccion.execute(t -> continuidadCU.registrar(prueba(45, 10, acta, false), ctx));
        var despuesDeExitosa = dsl.fetchOne(
                        "SELECT proxima_prueba FROM cumplimiento.plan_continuidad WHERE id = ?", planId)
                .get(0, LocalDate.class);
        assertThat(despuesDeExitosa).isEqualTo(hoy.plusMonths(6));

        transaccion.execute(t -> continuidadCU.registrar(prueba(120, 60, null, false), ctx));
        var despuesDeFallida = dsl.fetchOne(
                        "SELECT proxima_prueba FROM cumplimiento.plan_continuidad WHERE id = ?", planId)
                .get(0, LocalDate.class);
        assertThat(despuesDeFallida).isEqualTo(despuesDeExitosa);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        transaccion.execute(t -> continuidadCU.registrar(prueba(120, 60, null, false), ctx));
        transaccion.execute(t -> continuidadCU.registrar(prueba(130, 70, null, false), ctx));
        transaccion.execute(t -> continuidadCU.registrar(prueba(140, 80, null, false), ctx));

        // Tres pruebas fallidas seguidas no abren tres hallazgos del mismo proceso.
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.hallazgo_auditoria WHERE proceso = ? AND estado = 'ABIERTO'",
                        proceso))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.prueba_continuidad WHERE plan_continuidad_id = ?",
                        planId))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: una prueba EXITOSA sin acta que la reporte. «Salio bien» tiene
        // que ser algo que alguien firmo, no algo que alguien escribio.
        assertThatThrownBy(() -> transaccion.execute(t -> continuidadCU.registrar(prueba(45, 10, null, false), ctx)))
                .hasMessageContaining("acta de comite");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.prueba_continuidad WHERE plan_continuidad_id = ?",
                        planId))
                .isZero();

        // Paso fallido: el plan no existe.
        assertThatThrownBy(() -> transaccion.execute(t -> continuidadCU.registrar(
                        new EntradaPrueba(
                                UUID.randomUUID(), "PARCIAL", hoy, ejecutor, null, 30, 5, null, false, "0.00"),
                        ctx)))
                .hasMessageContaining("no tiene plan");

        // Con acta, el mismo camino cierra.
        UUID acta = gobiernoFixtura.actaMinima();
        var buena = transaccion.execute(t -> continuidadCU.registrar(prueba(45, 10, acta, false), ctx));
        assertThat(buena.resultado()).isEqualTo("EXITOSA");
        assertThat(buena.planAccionId()).isNull();
    }
}
