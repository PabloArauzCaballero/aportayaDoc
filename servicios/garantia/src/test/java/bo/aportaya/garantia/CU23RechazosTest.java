package bo.aportaya.garantia;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.garantia.aplicacion.CU23CubrirIncumplimiento.EntradaCobertura;
import bo.aportaya.garantia.aplicacion.CU23CubrirIncumplimiento.SalidaCobertura;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.EntradaDeclaracion;
import bo.aportaya.garantia.dominio.CoberturaAplicable;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-23 · las pruebas de RECHAZO, una por restriccion citada. */
class CU23RechazosTest extends BaseDeGarantia {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(
            UUID fondoId, UUID expedienteId, FixturaDeGarantia.Escenario escenario, ContextoSesion gestor) {}

    private Caso caso(String saldo, String tope) {
        UUID usuario = fixtura.usuario();
        var escenario = fixtura.escenario(usuario);
        ContextoSesion gestor = contextoDe(fixtura.usuario());
        UUID politica = fixtura.politica(escenario.grupoId(), "100.00", tope, "50000.00", 3, "100000.00", 15);
        UUID fondo = fixtura.fondo(escenario.grupoId(), politica, saldo);
        fixtura.aportarAlFondo(fondo, escenario.participanteId(), saldo, saldo);

        var expediente = transaccion.execute(t -> expedienteCU.declarar(
                new EntradaDeclaracion(
                        "EXP-" + corto(),
                        usuario,
                        escenario.participanteId(),
                        escenario.grupoId(),
                        escenario.periodoId(),
                        escenario.cupoId(),
                        escenario.obligacionId(),
                        "APORTE_IMPAGO",
                        "GRAVE",
                        "AUTOMATICO_VENCIMIENTO",
                        bob("500.00"),
                        30,
                        true,
                        "LOG_SISTEMA",
                        "Sin pago",
                        null,
                        null),
                gestor));
        return new Caso(fondo, expediente.expedienteId(), escenario, gestor);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El movimiento del fondo es append-only: el saldo se DERIVA de la cadena.
        // Corregir un movimiento en el lugar borraria la historia de por que el fondo
        // tiene lo que tiene.
        Caso c = caso("5000.00", "50000.00");
        transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(c.expedienteId(), bob("500.00"), 30, null), c.gestor()));

        assertThat(rechazaLaBase(
                        "UPDATE garantia.movimiento_fondo SET monto = 1 WHERE fondo_id = '%s'".formatted(c.fondoId())))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM garantia.movimiento_fondo WHERE fondo_id = '%s'".formatted(c.fondoId())))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // El asiento lo escribe nucleo-financiero (invariante 12) con el monto que este
        // servicio le manda. Un evento de cobertura sin monto deja un asiento a medias.
        Caso c = caso("5000.00", "50000.00");
        transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(c.expedienteId(), bob("500.00"), 30, null), c.gestor()));

        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM garantia.evento_dominio
                         WHERE tipo = 'garantia.incumplimiento_cubierto'
                           AND payload->>'montoCubierto' = '500.00' AND payload->>'saldoDelFondo' IS NOT NULL
                        """))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-01")
    void rechazaRBIL01() {
        // Lo que sale del fondo iguala lo que entra como deuda, al centavo. Si no
        // cuadrara, la diferencia la absorberia el grupo sin enterarse.
        Caso c = caso("5000.00", "50000.00");
        SalidaCobertura salida = transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(c.expedienteId(), bob("500.00"), 30, null), c.gestor()));

        var fila = dsl.fetchOne(
                """
                SELECT c.monto_cubierto AS cubierto, d.capital_original AS deuda, m.monto AS movido
                  FROM garantia.cobertura_incumplimiento c
                  JOIN garantia.deuda_participante d ON d.cobertura_id = c.id
                  JOIN garantia.movimiento_fondo m ON m.referencia_id = c.id
                 WHERE c.id = ?
                """,
                salida.coberturaId());
        assertThat(fila.get("cubierto", BigDecimal.class))
                .isEqualByComparingTo(fila.get("deuda", BigDecimal.class))
                .isEqualByComparingTo(fila.get("movido", BigDecimal.class));

        // Y el fondo nunca queda negativo: la BASE lo impide.
        assertThat(rechazaLaBase("UPDATE garantia.fondo_garantia SET saldo_disponible = -1 WHERE id = '%s'"
                        .formatted(c.fondoId())))
                .contains("ck_fondo_garantia_saldo_disponible");
    }

    @Test
    @DisplayName("rechaza por R-GRP-02")
    void rechazaRGRP02() {
        // Una cobertura por obligacion y una por expediente: cubrir dos veces el mismo
        // incumplimiento vacia el fondo por un solo caso.
        Caso c = caso("5000.00", "50000.00");
        transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(c.expedienteId(), bob("500.00"), 30, null), c.gestor()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO garantia.cobertura_incumplimiento
                            (id, fondo_id, registro_id, obligacion_id, periodo_id, monto_solicitado,
                             monto_cubierto, porcentaje_cobertura, estado, requirio_aprobacion_manual,
                             solicitada_en)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', '%s', 500.00, 500.00, 100.00,
                                'APLICADA', false, now())
                        """
                                .formatted(
                                        c.fondoId(),
                                        c.expedienteId(),
                                        c.escenario().obligacionId(),
                                        c.escenario().periodoId())))
                .contains("uq_cobertura_incumplimiento_");

        // Y el atomo dice, sin base de datos, que gana el limite mas chico de los
        // cuatro. Aplicar solo uno deja los otros tres como decoracion.
        var politica = new CoberturaAplicable.Politica(
                new BigDecimal("100.00"), bob("400.00"), bob("50000.00"), 3, bob("100000.00"), 15);
        var consumido = new CoberturaAplicable.Consumido(bob("0.00"), bob("0.00"), 0);
        var calculo = CoberturaAplicable.calcular(bob("500.00"), politica, consumido, bob("5000.00"), 30);
        assertThat(calculo.montoCubierto()).isEqualByComparingTo(bob("400.00"));
        assertThat(calculo.limiteQueMando()).isEqualTo("TOPE_POR_PARTICIPANTE");
    }
}
