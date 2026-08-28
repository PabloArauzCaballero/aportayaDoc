package bo.aportaya.garantia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.garantia.aplicacion.CU23CubrirIncumplimiento.EntradaCobertura;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.EntradaDeclaracion;
import bo.aportaya.garantia.aplicacion.CU26EjecutarAval.SalidaEjecucion;
import bo.aportaya.garantia.dominio.TopeDelAval;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-26 · las pruebas de RECHAZO, una por restriccion citada. */
class CU26RechazosTest extends BaseDeGarantia {

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

    private record Caso(UUID avalId, UUID expedienteId, UUID fondoId, ContextoSesion gestor) {}

    private Caso caso(String tope, String porcentaje) {
        UUID usuario = fixtura.usuario();
        UUID avalista = fixtura.usuario();
        var escenario = fixtura.escenario(usuario);
        ContextoSesion gestor = contextoDe(fixtura.usuario());
        UUID politica = fixtura.politica(escenario.grupoId(), "100.00", "50000.00", "50000.00", 3, "100000.00", 15);
        UUID fondo = fixtura.fondo(escenario.grupoId(), politica, "50000.00");
        fixtura.aportarAlFondo(fondo, escenario.participanteId(), "50000.00", "50000.00");

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
                        bob("1500.00"),
                        30,
                        true,
                        "LOG_SISTEMA",
                        "Sin pago",
                        null,
                        null),
                gestor));
        transaccion.execute(t ->
                coberturaCU.cubrir(new EntradaCobertura(expediente.expedienteId(), bob("1500.00"), 30, null), gestor));
        UUID aval = fixtura.aval(escenario.grupoId(), escenario.participanteId(), avalista, tope, porcentaje);
        return new Caso(aval, expediente.expedienteId(), fondo, gestor);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La ejecucion conserva su monto y su plazo de respuesta: sin ellos el avalista
        // no puede probar por cuanto ni hasta cuando se le reclamo.
        Caso c = caso("1000.00", "100.00");
        transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));

        assertThat(rechazaLaBase("UPDATE garantia.ejecucion_aval SET plazo_respuesta = NULL WHERE registro_id = '%s'"
                        .formatted(c.expedienteId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        Caso c = caso("1000.00", "100.00");
        transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));

        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM garantia.evento_dominio
                         WHERE tipo = 'garantia.aval_ejecutado' AND payload->>'montoEjecutado' = '1000.00'
                        """))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-01")
    void rechazaRBIL01() {
        // Lo ejecutado iguala lo subrogado, al centavo: si no cuadrara, el avalista
        // pagaria una cifra y recuperaria otra.
        Caso c = caso("1000.00", "100.00");
        SalidaEjecucion salida = transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));

        var fila = dsl.fetchOne(
                """
                SELECT e.monto_ejecutado AS ejecutado, s.monto_subrogado AS subrogado
                  FROM garantia.ejecucion_aval e
                  JOIN garantia.subrogacion s ON s.id = ?
                 WHERE e.id = ?
                """,
                salida.subrogacionId(),
                salida.ejecucionId());
        assertThat(fila.get("ejecutado", BigDecimal.class))
                .isEqualByComparingTo(fila.get("subrogado", BigDecimal.class));
    }

    @Test
    @DisplayName("rechaza por R-BIL-06")
    void rechazaRBIL06() {
        // Idempotencia: una ejecucion por aval y expediente. La BASE lo sostiene.
        Caso c = caso("1000.00", "60.00");
        SalidaEjecucion salida = transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));
        UUID deudaId = dsl.fetchOne(
                        "SELECT id FROM garantia.deuda_participante WHERE registro_id = ?", c.expedienteId())
                .get("id", UUID.class);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO garantia.ejecucion_aval
                            (id, aval_id, registro_id, deuda_id, monto_ejecutado, estado, notificada_en,
                             plazo_respuesta, genera_deuda_del_avalista)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', 1.00, 'NOTIFICADA', now(),
                                now() + interval '10 days', true)
                        """
                                .formatted(c.avalId(), c.expedienteId(), deudaId)))
                .contains("uq_ejecucion_aval_registro");
        assertThat(salida.esNueva()).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-GAR-03")
    void rechazaRGAR03() {
        // Una ejecucion por aval y expediente: dos le cobran dos veces al avalista.
        Caso c = caso("1000.00", "100.00");

        var a = transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));
        var b = transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));

        assertThat(b.ejecucionId()).isEqualTo(a.ejecucionId());
        assertThat(contar("SELECT count(*)::int FROM garantia.ejecucion_aval WHERE registro_id = ?", c.expedienteId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-GAR-04")
    void rechazaRGAR04() {
        // Nunca mas alla del tope firmado. Lo impide la aplicacion con su mensaje y la
        // BASE con su trigger: un avalista acepto responder por una cantidad concreta,
        // y cobrarle mas es cobrarle algo que nunca acepto.
        Caso c = caso("1000.00", "100.00");
        transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));
        UUID deudaId = dsl.fetchOne(
                        "SELECT id FROM garantia.deuda_participante WHERE registro_id = ?", c.expedienteId())
                .get("id", UUID.class);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO garantia.ejecucion_aval
                            (id, aval_id, registro_id, deuda_id, monto_ejecutado, estado, notificada_en,
                             plazo_respuesta, genera_deuda_del_avalista)
                        VALUES (gen_random_uuid(), '%s', gen_random_uuid(), '%s', 1.00, 'NOTIFICADA', now(),
                                now() + interval '10 days', true)
                        """
                                .formatted(c.avalId(), deudaId)))
                .contains("R-GAR-04");

        // Y el atomo lo dice sin base de datos: agotado el tope, no queda nada por
        // ejecutar — el avalista cumplio.
        var agotado = new TopeDelAval(bob("1000.00"), new BigDecimal("100.00"), bob("1000.00"));
        assertThat(agotado.disponible()).isEqualByComparingTo(bob("0.00"));
        assertThatThrownBy(() -> agotado.ejecutable(bob("500.00")))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya se ejecuto hasta su tope");
    }
}
