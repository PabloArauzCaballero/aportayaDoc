package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU54RegistrarRiesgoOperativo;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-54 · Registrar un evento de riesgo operativo.
 *
 * <p>Lo que se pone a prueba es que <b>la base de perdidas sirva para decidir</b>: la
 * taxonomia es la de la norma y no una lista libre, la perdida neta la deriva el motor
 * y no la aplicacion, y el plan de accion vencido no se queda vencido en silencio.
 */
class CU54Test extends BaseDeCumplimiento {

    private static final OffsetDateTime AYER = OffsetDateTime.now().minusDays(1);
    private static final OffsetDateTime HOY = OffsetDateTime.now();

    @Test
    @DisplayName("Dado un descuadre de custodia no explicado por Bs 1.240 · Cuando se registra el evento · Entonces"
            + " existe evento_riesgo_operativo con categoria_evento y factor_riesgo válidos · Y perdida_neta se"
            + " calcula como bruta menos recuperación")
    void criterio1() {
        var salida = registrar("FALLAS_SISTEMAS", "PROCESOS_INTERNOS", "1240.00", Optional.of("240.00"));

        assertThat(salida.codigo()).startsWith("ERO-");
        // La neta la devolvio el MOTOR: la columna es GENERATED y esta prueba comprueba
        // la de la fila, no la resta que hizo la aplicacion. Si algun dia dejaran de
        // coincidir, la que vale es la de la base (`R-RIS-02`).
        assertThat(salida.perdidaNeta()).isEqualTo("1000.00");
        assertThat(riesgos.categoriaDelEvento(salida.eventoId())).isEqualTo("FALLAS_SISTEMAS");
        assertThat(riesgos.factorDelEvento(salida.eventoId())).isEqualTo("PROCESOS_INTERNOS");
    }

    @Test
    @DisplayName("Dado un intento de modificar perdida_bruta de un evento registrado · Cuando se ejecuta · Entonces la"
            + " base de datos lo rechaza")
    void criterio2() {
        var salida = registrar("FRAUDE_EXTERNO", "EVENTOS_EXTERNOS", "500.00", Optional.empty());

        // Append-only por privilegios, no por convencion: si la garantia viviera en el
        // codigo, un UPDATE desde una consola cambiaria la perdida de un evento ya
        // reportado al supervisor y nadie se enteraria (`R-AUD-01`).
        assertThat(rechazaLaBase("UPDATE cumplimiento.evento_riesgo_operativo SET perdida_bruta = 1 WHERE id = '%s'"
                        .formatted(salida.eventoId())))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("Dado un plan de acción vencido · Cuando corre el control diario · Entonces existe un"
            + " hallazgo_auditoria abierto")
    void criterio3() {
        UUID responsable = fixtura.usuario();
        var salida =
                registrarCon("DANOS_ACTIVOS", "INFRAESTRUCTURA", "800.00", Optional.empty(), Optional.of(responsable));
        UUID plan = salida.planAccionId().orElseThrow();
        riesgos.vencerPlan(plan, LocalDate.now().minusDays(10));

        var abiertos = transaccion.execute(estado -> escalarCU.ejecutar(deSistema()));

        assertThat(abiertos).isNotEmpty();
        assertThat(riesgos.estadoDelPlan(plan)).isEqualTo("VENCIDO");
        assertThat(riesgos.hallazgoAbiertoDelPlan(plan)).isTrue();
    }

    @Test
    @DisplayName("rechaza · un evento sin pérdida se registra igual, porque la frecuencia también es información")
    void rechazaDescartarLaCasiPerdida() {
        var salida = registrar("RELACIONES_LABORALES", "PERSONAS", "0.00", Optional.empty());

        // Filtrar las casi-perdidas por no costar plata deja el analisis ciego justo
        // para lo que todavia no salio caro. Es el flujo alternativo 1a del caso de uso.
        assertThat(salida.perdidaNeta()).isEqualTo("0.00");
        assertThat(riesgos.categoriaDelEvento(salida.eventoId())).isEqualTo("RELACIONES_LABORALES");
    }

    @Test
    @DisplayName("rechaza · la detección no puede ser anterior a la ocurrencia")
    void rechazaFechasInvertidas() {
        assertThatThrownBy(() -> transaccion.execute(estado -> riesgoCU.ejecutar(
                        new CU54RegistrarRiesgoOperativo.EntradaEvento(
                                "FRAUDE_INTERNO",
                                "PERSONAS",
                                "Custodia",
                                "Un hecho descrito con el detalle suficiente.",
                                HOY,
                                AYER,
                                "10.00",
                                Optional.empty(),
                                "BOB",
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()),
                        contexto())))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU54-01");
    }

    @Test
    @DisplayName("rechaza · una recuperación mayor que la pérdida dejaría la base mostrando una ganancia")
    void rechazaRecuperacionExcesiva() {
        assertThatThrownBy(() -> registrar("FRAUDE_EXTERNO", "PERSONAS", "100.00", Optional.of("150.00")))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU54-02");
    }

    @Test
    @DisplayName("rechaza · una categoría fuera de la taxonomía de la norma")
    void rechazaTaxonomiaInventada() {
        // No hay «OTROS» a proposito: una septima categoria cajon se lleva todo lo
        // incomodo y el analisis por categoria deja de decir nada.
        assertThatThrownBy(() -> registrar("OTROS", "PERSONAS", "10.00", Optional.empty()))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU54-03");
    }

    @Test
    @DisplayName("rechaza · correr el control dos veces no abre dos hallazgos por el mismo plan")
    void rechazaDuplicarElHallazgo() {
        UUID responsable = fixtura.usuario();
        var salida = registrarCon(
                "FALLAS_SISTEMAS", "TECNOLOGIA_INFORMACION", "300.00", Optional.empty(), Optional.of(responsable));
        UUID plan = salida.planAccionId().orElseThrow();
        riesgos.vencerPlan(plan, LocalDate.now().minusDays(3));

        transaccion.execute(estado -> escalarCU.ejecutar(deSistema()));
        transaccion.execute(estado -> escalarCU.ejecutar(deSistema()));

        // La idempotencia viene del HECHO —el codigo del hallazgo se deriva del plan—
        // y no de que alguien acuerde no repetir la corrida. Un UUID aleatorio aca
        // convertiria cada reintento en un expediente nuevo.
        assertThat(riesgos.hallazgosDelPlan(plan)).isEqualTo(1);
    }

    private CU54RegistrarRiesgoOperativo.SalidaEvento registrar(
            String categoria, String factor, String bruta, Optional<String> recuperacion) {
        return registrarCon(categoria, factor, bruta, recuperacion, Optional.empty());
    }

    private CU54RegistrarRiesgoOperativo.SalidaEvento registrarCon(
            String categoria, String factor, String bruta, Optional<String> recuperacion, Optional<UUID> responsable) {
        return transaccion.execute(estado -> riesgoCU.ejecutar(
                new CU54RegistrarRiesgoOperativo.EntradaEvento(
                        categoria,
                        factor,
                        "Custodia y conciliacion",
                        "Un hecho descrito con el detalle suficiente para analizarlo despues.",
                        AYER,
                        HOY,
                        bruta,
                        recuperacion,
                        "BOB",
                        responsable,
                        Optional.of("Revisar el control de conciliacion diaria."),
                        Optional.of(LocalDate.now().plusDays(15))),
                contexto()));
    }

    /**
     * El contexto del control diario. Es {@code deSistema} y no un operador: un trabajo
     * programado usa un rol con sus propias politicas de fila, no una excepcion a las
     * politicas.
     */
    private static ContextoSesion deSistema() {
        return ContextoSesion.deSistema(
                UUID.fromString("00000000-0000-4000-8000-000000000054"),
                new Traza(UUID.randomUUID().toString()));
    }
}
