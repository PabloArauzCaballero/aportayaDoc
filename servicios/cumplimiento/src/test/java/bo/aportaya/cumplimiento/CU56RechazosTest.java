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

/** CU-56 · Lo que la base y el caso de uso rechazan. */
class CU56RechazosTest extends BaseDeCumplimiento {

    private String proceso;
    private UUID planId;
    private UUID ejecutor;
    private ContextoSesion ctx;
    private LocalDate hoy;

    @BeforeEach
    void escenario() {
        hoy = LocalDate.now(ZoneOffset.UTC);
        proceso = "PROC-" + UUID.randomUUID().toString().substring(0, 8);
        planId = gobiernoFixtura.planDeContinuidad(proceso, 60, 15, 6, hoy.plusMonths(3));
        ejecutor = fixtura.usuario();
        ctx = ContextoSesion.de(
                ejecutor, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    private EntradaPrueba prueba(int rto, int rpo, UUID acta) {
        return new EntradaPrueba(
                planId, "PARCIAL", hoy, ejecutor, acta, rto, rpo, "https://evidencia.bo/1", false, "0.00");
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        // La prueba conserva su evidencia y su resultado: son lo que se le muestra al
        // regulador cuando pregunta si el plan de continuidad se prueba de verdad.
        UUID acta = gobiernoFixtura.actaMinima();
        var salida = transaccion.execute(t -> continuidadCU.registrar(prueba(45, 10, acta), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.prueba_continuidad
                         WHERE id = ? AND evidencia_url IS NOT NULL AND resultado IS NOT NULL
                           AND fecha IS NOT NULL
                        """,
                        salida.pruebaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-LIC-03")
    void rechazaRLIC03() {
        // Una prueba EXITOSA exige acta de comite que la reporte: «salio bien» tiene que
        // ser algo que alguien firmo.
        assertThatThrownBy(() -> transaccion.execute(t -> continuidadCU.registrar(prueba(45, 10, null), ctx)))
                .hasMessageContaining("acta de comite");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.prueba_continuidad
                            (plan_continuidad_id, ejecutada_por, tipo, fecha, rto_obtenido_minutos,
                             rpo_obtenido_minutos, resultado)
                        VALUES (?, ?, 'PARCIAL', current_date, 45, 10, 'EXITOSA')
                        """,
                        planId,
                        ejecutor))
                .contains("ck_prueba_resultado");
    }

    @Test
    @DisplayName("rechaza por R-RIS-03")
    void rechazaRRIS03() {
        // Todo plan lleva RTO y RPO con sentido, y su proxima prueba por delante: un
        // plan con RTO cero o con la prueba en el pasado no compromete nada.
        assertThat(rechazaLaBase("UPDATE cumplimiento.plan_continuidad SET rto_minutos = 0 WHERE id = ?", planId))
                .contains("ck_plan_objetivos");
        assertThat(rechazaLaBase(
                        "UPDATE cumplimiento.plan_continuidad SET proxima_prueba = vigente_desde - 1 WHERE id = ?",
                        planId))
                .contains("ck_plan_prueba");

        // Y una prueba que no alcanza los objetivos no puede quedar como EXITOSA: el
        // resultado sale de la comparacion, no del criterio de quien la ejecuto.
        var salida = transaccion.execute(t -> continuidadCU.registrar(prueba(120, 60, null), ctx));
        assertThat(salida.resultado()).isEqualTo("FALLIDA");
        assertThat(salida.planAccionId()).isNotNull();
    }
}
