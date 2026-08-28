package bo.aportaya.garantia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.garantia.aplicacion.CU29DevolverFondo.EntradaDevolucion;
import bo.aportaya.garantia.dominio.DevolucionDelFondo;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-29 · las pruebas de RECHAZO, una por restriccion citada. */
class CU29RechazosTest extends BaseDeGarantia {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private record Caso(UUID grupoId, UUID fondoId, UUID participanteId, ContextoSesion gestor) {}

    private Caso caso(String saldo, String aporte) {
        UUID usuario = fixtura.usuario();
        var escenario = fixtura.escenario(usuario);
        UUID politica = fixtura.politica(escenario.grupoId(), "100.00", "50000.00", "50000.00", 3, "100000.00", 15);
        UUID fondo = fixtura.fondo(escenario.grupoId(), politica, saldo);
        fixtura.aportarAlFondo(fondo, escenario.participanteId(), aporte, saldo);
        return new Caso(escenario.grupoId(), fondo, escenario.participanteId(), contextoDe(fixtura.usuario()));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El movimiento de la devolucion es append-only: el saldo del fondo se deriva
        // de la cadena, y editarla borraria la historia de a quien se le devolvio que.
        Caso c = caso("200.00", "200.00");
        transaccion.execute(t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), true, 0), c.gestor()));

        assertThat(rechazaLaBase(
                        "UPDATE garantia.movimiento_fondo SET monto = 1 WHERE fondo_id = '%s'".formatted(c.fondoId())))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        Caso c = caso("200.00", "200.00");
        transaccion.execute(t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), true, 0), c.gestor()));

        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM garantia.evento_dominio
                         WHERE tipo = 'garantia.fondo_devuelto' AND payload->>'totalDevuelto' = '200.00'
                        """))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-01")
    void rechazaRBIL01() {
        // La suma de lo devuelto iguala lo que salio del fondo, al centavo.
        Caso c = caso("200.00", "200.00");
        transaccion.execute(t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), true, 0), c.gestor()));

        var fila = dsl.fetchOne(
                """
                SELECT (SELECT sum(monto_a_devolver) FROM garantia.devolucion_fondo WHERE fondo_id = ?) AS filas,
                       (SELECT monto FROM garantia.movimiento_fondo
                         WHERE fondo_id = ? AND tipo = 'DEVOLUCION_A_PARTICIPANTES') AS movimiento
                """,
                c.fondoId(),
                c.fondoId());
        assertThat(fila.get("filas", java.math.BigDecimal.class))
                .isEqualByComparingTo(fila.get("movimiento", java.math.BigDecimal.class));
    }

    @Test
    @DisplayName("rechaza por R-BIL-02")
    void rechazaRBIL02() {
        // El fondo nunca queda negativo: la BASE lo impide aunque la aplicacion se
        // equivoque. Un fondo en rojo es plata que se repartio dos veces.
        Caso c = caso("200.00", "200.00");

        assertThat(rechazaLaBase("UPDATE garantia.fondo_garantia SET saldo_disponible = -0.01 WHERE id = '%s'"
                        .formatted(c.fondoId())))
                .contains("ck_fondo_garantia_saldo_disponible");
    }

    @Test
    @DisplayName("rechaza por R-BIL-06")
    void rechazaRBIL06() {
        // Una devolucion por participante y fondo: la BASE lo sostiene. Repetir el
        // reparto le devolveria dos veces a la misma persona.
        Caso c = caso("200.00", "200.00");
        transaccion.execute(t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), true, 0), c.gestor()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO garantia.devolucion_fondo
                            (id, fondo_id, participante_id, monto_aportado, monto_consumido,
                             monto_a_devolver, estado, fecha)
                        VALUES (gen_random_uuid(), '%s', '%s', 200.00, 0, 200.00, 'CALCULADA', now())
                        """
                                .formatted(c.fondoId(), c.participanteId())))
                .contains("uq_devolucion_fondo_participante");
    }

    @Test
    @DisplayName("rechaza por R-GAR-06")
    void rechazaRGAR06() {
        // No se devuelve mas de lo aportado ni un importe negativo. El atomo lo dice
        // sin base de datos, y la fila tampoco lo admite.
        var reparto = DevolucionDelFondo.repartir(
                bob("500.00"), List.of(new DevolucionDelFondo.Aportante(UUID.randomUUID(), bob("100.00"))));
        // Aunque el saldo sea mayor, nadie recibe mas de lo que puso.
        assertThat(reparto.devoluciones().get(0).aDevolver()).isEqualByComparingTo(bob("100.00"));

        assertThatThrownBy(() -> DevolucionDelFondo.repartir(bob("100.00"), List.of()))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("No hay aportantes");

        Caso c = caso("200.00", "200.00");
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO garantia.devolucion_fondo
                            (id, fondo_id, participante_id, monto_aportado, monto_consumido,
                             monto_a_devolver, estado, fecha)
                        VALUES (gen_random_uuid(), '%s', '%s', 200.00, 0, -1.00, 'CALCULADA', now())
                        """
                                .formatted(c.fondoId(), c.participanteId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-GRP-13")
    void rechazaRGRP13() {
        // La cuenta del grupo cierra en cero. Un grupo con saldo tras devolver el
        // fondo es plata de alguien que quedo sin dueno. La regla vive en la BASE.
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_grp_validar_disolucion"))
                .isEqualTo(1);

        Caso c = caso("200.00", "200.00");
        transaccion.execute(t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), true, 0), c.gestor()));

        assertThat(dsl.fetchOne("SELECT saldo_disponible FROM garantia.fondo_garantia WHERE id = ?", c.fondoId())
                        .get("saldo_disponible", java.math.BigDecimal.class))
                .isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }
}
