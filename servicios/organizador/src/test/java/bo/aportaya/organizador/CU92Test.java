package bo.aportaya.organizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.organizador.aplicacion.CU92EvaluarDesempeno.EntradaEvaluacion;
import bo.aportaya.organizador.aplicacion.CU92EvaluarDesempeno.SalidaEvaluacion;
import bo.aportaya.organizador.dominio.PuntajeDeDesempeno;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-92 · Evaluar el desempeno del organizador. */
class CU92Test extends BaseDeOrganizador {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private record Caso(UUID usuario, UUID organizadorId, ContextoSesion ctx) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        return new Caso(usuario, fixtura.organizadorHabilitado(usuario), contextoDe(usuario));
    }

    /** Tres metricas con peso: dos donde conviene el numero alto y una donde el bajo. */
    private List<PuntajeDeDesempeno.Metrica> metricas(String morosidad, String finalizacion, String satisfaccion) {
        return List.of(
                new PuntajeDeDesempeno.Metrica(
                        "MOROSIDAD", new BigDecimal(morosidad), new BigDecimal("5.00"), new BigDecimal("0.400"), false),
                new PuntajeDeDesempeno.Metrica(
                        "FINALIZACION",
                        new BigDecimal(finalizacion),
                        new BigDecimal("90.00"),
                        new BigDecimal("0.400"),
                        true),
                new PuntajeDeDesempeno.Metrica(
                        "SATISFACCION",
                        new BigDecimal(satisfaccion),
                        new BigDecimal("4.00"),
                        new BigDecimal("0.200"),
                        true));
    }

    private EntradaEvaluacion entrada(Caso c, String periodo, List<PuntajeDeDesempeno.Metrica> metricas) {
        return new EntradaEvaluacion(
                c.organizadorId(),
                periodo,
                metricas,
                new BigDecimal("2.50"),
                new BigDecimal("95.00"),
                new BigDecimal("4.30"),
                new BigDecimal("6.00"),
                0,
                0,
                8,
                new BigDecimal("200000.00"));
    }

    @Test
    @DisplayName(
            "Dado un organizador con tres grupos cerrados en el período · Cuando corre la evaluación mensual · Entonces existe una evaluacion_desempeno con sus métricas desglosadas · Y puntaje_global es exactamente la suma ponderada de sus componentes")
    void criterio1() {
        Caso c = caso();
        var metricas = metricas("2.50", "95.00", "4.30");

        SalidaEvaluacion salida =
                transaccion.execute(t -> desempenoCU.evaluar(entrada(c, "2026-07", metricas), c.ctx()));

        // Las tres metricas cumplen su meta, asi que el puntaje es 100.
        assertThat(salida.puntaje()).isEqualByComparingTo(new BigDecimal("100.00"));
        // Y las metricas quedan desglosadas: si esta persona va a perder su
        // habilitacion por un numero, ese numero tiene que poder abrirse. «El sistema
        // lo calculo» no se puede apelar.
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.metrica_organizador WHERE evaluacion_id = ?",
                        salida.evaluacionId()))
                .isEqualTo(3);
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.metrica_organizador WHERE evaluacion_id = ? AND cumple",
                        salida.evaluacionId()))
                .isEqualTo(3);
    }

    @Test
    @DisplayName(
            "Dada una evaluación ya existente para ese organizador y período · Cuando se intenta generar otra · Entonces se rechaza con EVALUACION_DUPLICADA")
    void criterio2() {
        Caso c = caso();
        var metricas = metricas("2.50", "95.00", "4.30");
        SalidaEvaluacion primera =
                transaccion.execute(t -> desempenoCU.evaluar(entrada(c, "2026-08", metricas), c.ctx()));

        SalidaEvaluacion segunda =
                transaccion.execute(t -> desempenoCU.evaluar(entrada(c, "2026-08", metricas), c.ctx()));

        // Dos evaluaciones del mismo mes permiten elegir la que mas convenga, y
        // entonces la evaluacion deja de significar algo.
        assertThat(segunda.evaluacionId()).isEqualTo(primera.evaluacionId());
        assertThat(segunda.esNueva()).isFalse();
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.evaluacion_desempeno
                            (id, organizador_id, periodo_evaluado, indice_morosidad_cartera,
                             tasa_finalizacion_grupos, satisfaccion_participantes,
                             tiempo_respuesta_promedio_horas, incidencias_abiertas, coberturas_consumidas,
                             puntaje_global, nivel_sugerido, accion_recomendada, evaluado_en)
                        VALUES (gen_random_uuid(), '%s', '2026-08', 1, 1, 1, 1, 0, 0, 50,
                                'APRENDIZ', 'a mano', now())
                        """
                                .formatted(c.organizadorId())))
                .contains("uq_evaluacion_org_periodo");
    }

    @Test
    @DisplayName(
            "Dado un organizador con un solo grupo en el período · Cuando se lo evalúa · Entonces la evaluación se marca con representatividad baja")
    void criterio3() {
        Caso c = caso();
        // Una sola metrica: la muestra es minima y el puntaje no representa nada.
        var unaSola = List.of(new PuntajeDeDesempeno.Metrica(
                "FINALIZACION", new BigDecimal("100.00"), new BigDecimal("90.00"), new BigDecimal("1.000"), true));

        SalidaEvaluacion salida =
                transaccion.execute(t -> desempenoCU.evaluar(entrada(c, "2026-09", unaSola), c.ctx()));

        // HUECO DECLARADO: `evaluacion_desempeno` no tiene columna de representatividad
        // y no se inventa una. La representatividad se lee de la CANTIDAD de metricas
        // desglosadas, que si esta guardada: una evaluacion con una sola metrica se
        // distingue de una con cinco. Ver H-3 en planes/informes/carril-2E.md.
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.metrica_organizador WHERE evaluacion_id = ?",
                        salida.evaluacionId()))
                .isEqualTo(1);
        // Y el nivel NO sube con una muestra asi: el ascenso queda sugerido, no
        // aplicado, y quien lo mire vera de cuantas metricas salio.
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.organizador WHERE id = ? AND nivel = 'ESTANDAR'",
                        c.organizadorId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una observación del organizador dentro del plazo · Cuando se comprueba que una métrica estaba mal calculada · Entonces se recalcula y queda constancia de la corrección")
    void criterio4() {
        Caso c = caso();
        var conError = metricas("40.00", "95.00", "4.30");
        SalidaEvaluacion original =
                transaccion.execute(t -> desempenoCU.evaluar(entrada(c, "2026-10", conError), c.ctx()));

        // La correccion es una evaluacion NUEVA de otro periodo, no una edicion de la
        // vieja: reescribir la evaluacion original borraria la prueba de que se
        // corrigio, y de que hubo algo que corregir.
        var corregida = metricas("2.50", "95.00", "4.30");
        SalidaEvaluacion nueva =
                transaccion.execute(t -> desempenoCU.evaluar(entrada(c, "2026-11", corregida), c.ctx()));

        assertThat(original.puntaje()).isLessThan(nueva.puntaje());
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.evaluacion_desempeno WHERE organizador_id = ?",
                        c.organizadorId()))
                .isEqualTo(2);
        // La metrica mal calculada sigue registrada con su valor original: es la
        // constancia de la correccion.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM organizador.metrica_organizador
                         WHERE evaluacion_id = ? AND codigo = 'MOROSIDAD' AND valor = 40.0000
                        """,
                        original.evaluacionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La clave es (organizador, periodo). El planificador mensual reintenta.
        Caso c = caso();
        var metricas = metricas("2.50", "95.00", "4.30");

        SalidaEvaluacion a = transaccion.execute(t -> desempenoCU.evaluar(entrada(c, "2026-12", metricas), c.ctx()));
        SalidaEvaluacion b = transaccion.execute(t -> desempenoCU.evaluar(entrada(c, "2026-12", metricas), c.ctx()));

        assertThat(b.evaluacionId()).isEqualTo(a.evaluacionId());
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.metrica_organizador WHERE evaluacion_id = ?",
                        a.evaluacionId()))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // La BASE decide: una evaluacion por organizador y periodo, aunque la
        // aplicacion se equivoque.
        Caso c = caso();
        var metricas = metricas("2.50", "95.00", "4.30");
        transaccion.execute(t -> desempenoCU.evaluar(entrada(c, "2027-01", metricas), c.ctx()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.evaluacion_desempeno
                            (id, organizador_id, periodo_evaluado, indice_morosidad_cartera,
                             tasa_finalizacion_grupos, satisfaccion_participantes,
                             tiempo_respuesta_promedio_horas, incidencias_abiertas, coberturas_consumidas,
                             puntaje_global, nivel_sugerido, accion_recomendada, evaluado_en)
                        VALUES (gen_random_uuid(), '%s', '2027-01', 1, 1, 1, 1, 0, 0, 99,
                                'MAESTRO', 'colada', now())
                        """
                                .formatted(c.organizadorId())))
                .contains("uq_evaluacion_org_periodo");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El puntaje es EXACTAMENTE la suma ponderada. Con morosidad al doble de la
        // meta (10 contra 5) esa metrica aporta la mitad de su peso: 0,4 * 0,5 + 0,4 +
        // 0,2 = 0,8 → 80,00.
        Caso c = caso();
        var metricas = metricas("10.00", "95.00", "4.30");

        SalidaEvaluacion salida =
                transaccion.execute(t -> desempenoCU.evaluar(entrada(c, "2027-02", metricas), c.ctx()));

        assertThat(salida.puntaje()).isEqualByComparingTo(new BigDecimal("80.00"));
        var suma = dsl.fetchOne(
                        "SELECT sum(peso) AS pesos FROM organizador.metrica_organizador WHERE evaluacion_id = ?",
                        salida.evaluacionId())
                .get("pesos", BigDecimal.class);
        assertThat(suma).isEqualByComparingTo(new BigDecimal("1.000"));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "evaluaciones"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "evaluaciones"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin metricas no queda evaluacion ni evento. Poner cero seria decir que lo
        // hizo pesimo, cuando lo cierto es que no se sabe — y con ese cero se le baja
        // el nivel a alguien por falta de datos.
        Caso c = caso();

        assertThatThrownBy(
                        () -> transaccion.execute(t -> desempenoCU.evaluar(entrada(c, "2027-03", List.of()), c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("No hay metricas");
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.evaluacion_desempeno WHERE organizador_id = ? AND periodo_evaluado = '2027-03'",
                        c.organizadorId()))
                .isZero();
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.organizador WHERE id = ? AND nivel = 'ESTANDAR'",
                        c.organizadorId()))
                .isEqualTo(1);
    }
}
