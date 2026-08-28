package bo.aportaya.transparencia;

import static bo.aportaya.transparencia.EscenarioDeRiesgo.CODIGOS;
import static bo.aportaya.transparencia.EscenarioDeRiesgo.tasaDePago;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.transparencia.aplicacion.CU97EvaluarRiesgo.EntradaRiesgo;
import bo.aportaya.transparencia.dominio.SenalDeRiesgo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-97 · Lo que la base y el caso de uso rechazan. */
class CU97RechazosTest extends BaseDeTransparencia {

    private record Caso(UUID grupo, UUID periodo) {}

    private Caso caso() {
        UUID grupo = fixtura.grupo();
        return new Caso(grupo, fixtura.periodo(grupo, 1));
    }

    private UUID abrirAlerta(Caso c) {
        return transaccion
                .execute(t -> riesgoCU.evaluar(
                        new EntradaRiesgo(
                                "GRUPO",
                                c.grupo(),
                                c.periodo(),
                                true,
                                List.of(tasaDePago("0.4000", "0.8000")),
                                CODIGOS,
                                12,
                                null),
                        contextoDeSistema()))
                .alertas()
                .get(0)
                .alertaId();
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El aviso de la alerta no se borra: es lo que despues permite calibrar el
        // modelo contra lo que realmente paso.
        Caso c = caso();
        UUID alertaId = abrirAlerta(c);

        assertThat(rechazaLaBase("DELETE FROM transparencia.evento_dominio WHERE agregado_id = ?", alertaId))
                .isEmpty();
        // HUECO H-6: evento_dominio no esta en la lista de append-only de la boveda.
        // Lo que si es cierto y se afirma: la alerta queda, con su evidencia.
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.alerta_riesgo WHERE id = ? AND evidencia IS NOT NULL",
                        alertaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // El acompanamiento se dispara por evento, en la misma transaccion: una alerta
        // que se genera y no avisa a nadie no acompana a nadie.
        Caso c = caso();
        abrirAlerta(c);

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.alerta_temprana_generada'
                           AND payload->>'ambitoId' = ? AND payload->>'accionSugerida' IS NOT NULL
                        """,
                        c.grupo().toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-GAR-07")
    void rechazaRGAR07() {
        // Una alerta abierta por causa, y el cierre lleva desenlace. Sin desenlace, la
        // alerta no ensena nada y el modelo repite el mismo error.
        Caso c = caso();
        UUID alertaId = abrirAlerta(c);

        assertThatThrownBy(() -> transaccion.execute(t -> {
                    riesgoCU.cerrar(alertaId, "ABIERTA", contextoDeSistema());
                    return null;
                }))
                .hasMessageContaining("CONFIRMADA o DESCARTADA");

        // Y el catalogo de estados es cerrado: 'CERRADA' no existe. La base rechaza la
        // fila, pero por dos motivos a la vez.
        assertThat(rechazaLaBase("UPDATE transparencia.alerta_riesgo SET estado = 'CERRADA' WHERE id = ?", alertaId))
                .contains("ck_alerta_riesgo_");
        // HUECO H-8: ck_alerta_riesgo_cierre exige cerrada_en solo cuando el estado es
        // 'CERRADA', un valor que ck_alerta_riesgo_estado no admite. Nunca protege una
        // fila que se pueda guardar: cerrar como CONFIRMADA o DESCARTADA sin fecha
        // pasa. Quien exige el desenlace y la fecha es este caso de uso.
        assertThat(rechazaLaBase("UPDATE transparencia.alerta_riesgo SET estado = 'DESCARTADA' WHERE id = ?", alertaId))
                .isEmpty();
    }

    @Test
    @DisplayName("rechaza por R-REP-03")
    void rechazaRREP03() {
        // La alerta lleva su evidencia: que metrica, con que valor y contra que umbral.
        // Una alerta que no dice de donde salio no se puede discutir ni corregir.
        Caso c = caso();
        UUID alertaId = abrirAlerta(c);

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.alerta_riesgo
                         WHERE id = ? AND evidencia->>'metrica' = 'TASA_PAGO_EN_TERMINO'
                           AND evidencia->>'umbral' IS NOT NULL AND evidencia->>'modeloVersion' = 'v1'
                        """,
                        alertaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-RIS-01")
    void rechazaRRIS01() {
        // Taxonomia cerrada: el ambito y el codigo de la alerta salen de un catalogo, no
        // de lo que a alguien le parezca. Un codigo inventado deja la alerta fuera de
        // todo tablero.
        Caso c = caso();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO transparencia.alerta_riesgo
                            (ambito, ambito_id, codigo, severidad, descripcion, evidencia, estado)
                        VALUES ('CARTERA', ?, 'GRUPO_INVIABLE', 'ALTA', 'Ambito inventado', '{}'::jsonb, 'ABIERTA')
                        """,
                        c.grupo()))
                .contains("ck_alerta_riesgo_ambito");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO transparencia.alerta_riesgo
                            (ambito, ambito_id, codigo, severidad, descripcion, evidencia, estado)
                        VALUES ('GRUPO', ?, 'RIESGO_RARO', 'ALTA', 'Codigo inventado', '{}'::jsonb, 'ABIERTA')
                        """,
                        c.grupo()))
                .contains("ck_alerta_riesgo_codigo");

        // Y una metrica sin codigo de alerta declarado no abre nada: no se inventa uno
        // para poder guardarla.
        var salida = transaccion.execute(t -> riesgoCU.evaluar(
                new EntradaRiesgo(
                        "GRUPO",
                        c.grupo(),
                        c.periodo(),
                        true,
                        List.of(new SenalDeRiesgo.Metrica(
                                "COBERTURA_DEL_FONDO",
                                new BigDecimal("0.2000"),
                                "RATIO",
                                new BigDecimal("0.5000"),
                                false)),
                        Map.of(),
                        12,
                        null),
                contextoDeSistema()));
        assertThat(salida.alertas()).isEmpty();
        // La metrica igual se guarda: la serie sirve aunque todavia no tenga alerta.
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.metrica_grupo WHERE grupo_id = ? AND codigo = 'COBERTURA_DEL_FONDO'",
                        c.grupo()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        // Al participante nunca se le muestra un puntaje. Quien conoce el numero lo
        // puede jugar, y a quien lo recibe no le sirve para nada.
        UUID usuario = fixtura.usuario();
        var caida = EscenarioDeRiesgo.caidaDePuntaje("90", "50");

        var salida = transaccion.execute(t -> riesgoCU.evaluar(
                new EntradaRiesgo("USUARIO", usuario, null, true, List.of(caida), CODIGOS, 25, new BigDecimal("37")),
                contextoDeSistema()));

        assertThat(salida.nivelRiesgo()).isEqualTo("ALTO");
        // El nivel existe para el tablero de riesgos, pero no viaja en el mensaje.
        assertThat(salida.alertas().get(0).mensajeAlUsuario())
                .doesNotContain("37")
                .doesNotContain("ALTO");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.alerta_temprana_generada'
                           AND payload->>'ambitoId' = ? AND payload->>'nivelRiesgo' IS NULL
                        """,
                        usuario.toString()))
                .isEqualTo(1);
    }
}
