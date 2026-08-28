package bo.aportaya.organizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.organizador.aplicacion.CU92EvaluarDesempeno.EntradaEvaluacion;
import bo.aportaya.organizador.dominio.NivelDeOrganizador;
import bo.aportaya.organizador.dominio.PuntajeDeDesempeno;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-92 · las pruebas de RECHAZO, una por restriccion citada. */
class CU92RechazosTest extends BaseDeOrganizador {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private record Caso(UUID usuario, UUID organizadorId, ContextoSesion ctx) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        return new Caso(usuario, fixtura.organizadorHabilitado(usuario), contextoDe(usuario));
    }

    private List<PuntajeDeDesempeno.Metrica> metricas() {
        return List.of(new PuntajeDeDesempeno.Metrica(
                "FINALIZACION", new BigDecimal("95.00"), new BigDecimal("90.00"), new BigDecimal("1.000"), true));
    }

    private UUID evaluar(Caso c, String periodo) {
        return transaccion
                .execute(t -> desempenoCU.evaluar(
                        new EntradaEvaluacion(
                                c.organizadorId(),
                                periodo,
                                metricas(),
                                new BigDecimal("2.50"),
                                new BigDecimal("95.00"),
                                new BigDecimal("4.30"),
                                new BigDecimal("6.00"),
                                0,
                                0,
                                8,
                                new BigDecimal("200000.00")),
                        c.ctx()))
                .evaluacionId();
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Una evaluacion no se edita para mejorar el numero: la correccion es una
        // evaluacion nueva. Reescribir la vieja borraria la prueba de que hubo algo
        // que corregir.
        Caso c = caso();
        UUID evaluacionId = evaluar(c, "2028-01");

        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.evaluacion_desempeno WHERE id = ? AND evaluado_en IS NOT NULL",
                        evaluacionId))
                .isEqualTo(1);
        // Y sus metricas son unicas por codigo: no se puede colar una segunda version
        // de la misma metrica dentro de la misma evaluacion.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.metrica_organizador (id, evaluacion_id, codigo, valor, meta, cumple, peso)
                        VALUES (gen_random_uuid(), '%s', 'FINALIZACION', 10, 90, false, 1.000)
                        """
                                .formatted(evaluacionId)))
                .contains("uq_metrica_org_codigo");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        Caso c = caso();
        UUID evaluacionId = evaluar(c, "2028-02");

        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "organizador.desempeno_evaluado",
                        evaluacionId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-ORG-04")
    void rechazaRORG04() {
        // Una evaluacion por organizador y periodo. Dos del mismo mes permiten elegir
        // la que mas convenga, y entonces la evaluacion no significa nada.
        Caso c = caso();
        evaluar(c, "2028-03");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.evaluacion_desempeno
                            (id, organizador_id, periodo_evaluado, indice_morosidad_cartera,
                             tasa_finalizacion_grupos, satisfaccion_participantes,
                             tiempo_respuesta_promedio_horas, incidencias_abiertas, coberturas_consumidas,
                             puntaje_global, nivel_sugerido, accion_recomendada, evaluado_en)
                        VALUES (gen_random_uuid(), '%s', '2028-03', 1, 1, 1, 1, 0, 0, 99, 'MAESTRO',
                                'la segunda', now())
                        """
                                .formatted(c.organizadorId())))
                .contains("uq_evaluacion_org_periodo");
    }

    @Test
    @DisplayName("rechaza por R-REP-03")
    void rechazaRREP03() {
        // El puntaje sale de metricas con su peso, no de una formula escondida. Sin
        // metricas no hay evaluacion: poner cero seria decir que lo hizo pesimo cuando
        // lo cierto es que no se sabe, y con ese cero se le baja el nivel a alguien.
        Caso c = caso();

        assertThatThrownBy(() -> transaccion.execute(t -> desempenoCU.evaluar(
                        new EntradaEvaluacion(
                                c.organizadorId(),
                                "2028-04",
                                List.of(),
                                new BigDecimal("2.50"),
                                new BigDecimal("95.00"),
                                new BigDecimal("4.30"),
                                new BigDecimal("6.00"),
                                0,
                                0,
                                8,
                                new BigDecimal("200000.00")),
                        c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("No hay metricas");

        // Y un nivel sugerido fuera del catalogo no entra: la escalera es de cuatro.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.evaluacion_desempeno
                            (id, organizador_id, periodo_evaluado, indice_morosidad_cartera,
                             tasa_finalizacion_grupos, satisfaccion_participantes,
                             tiempo_respuesta_promedio_horas, incidencias_abiertas, coberturas_consumidas,
                             puntaje_global, nivel_sugerido, accion_recomendada, evaluado_en)
                        VALUES (gen_random_uuid(), '%s', '2028-05', 1, 1, 1, 1, 0, 0, 99, 'INVENTADO',
                                'nivel de fantasia', now())
                        """
                                .formatted(c.organizadorId())))
                .contains("ck_evaluacion_desempeno_nivel_sugerido");
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        // El ascenso salta UN escalon por vez. Saltar dos de golpe le entrega a alguien
        // un limite de plata ajena que nunca sostuvo, y el historial que probaria que
        // puede sostenerlo es justamente el que no tiene.
        assertThat(NivelDeOrganizador.APRENDIZ.admiteMoverseA(NivelDeOrganizador.ESTANDAR))
                .isTrue();
        assertThat(NivelDeOrganizador.APRENDIZ.admiteMoverseA(NivelDeOrganizador.SENIOR))
                .isFalse();
        assertThat(NivelDeOrganizador.APRENDIZ.admiteMoverseA(NivelDeOrganizador.MAESTRO))
                .isFalse();
        // Bajar si puede ser de golpe: cuando algo sale mal, esperar no mejora nada.
        assertThat(NivelDeOrganizador.MAESTRO.admiteMoverseA(NivelDeOrganizador.APRENDIZ))
                .isTrue();
        assertThatThrownBy(() -> NivelDeOrganizador.exigir("SUPREMO"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no admitido");
    }
}
