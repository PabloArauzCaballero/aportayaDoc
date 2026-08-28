package bo.aportaya.garantia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.garantia.aplicacion.CU67DisolverGrupo.EntradaDisolucion;
import bo.aportaya.garantia.aplicacion.CU67DisolverGrupo.SalidaDisolucion;
import bo.aportaya.garantia.dominio.CuadreDeDisolucion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-67 · las pruebas de RECHAZO, una por restriccion citada. */
class CU67RechazosTest extends BaseDeGarantia {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private record Caso(UUID grupoId, List<UUID> participantes, ContextoSesion gestor) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        var escenario = fixtura.escenario(usuario);
        return new Caso(
                escenario.grupoId(),
                List.of(escenario.participanteId(), fixtura.otroParticipante(escenario.grupoId())),
                contextoDe(fixtura.usuario()));
    }

    private List<CuadreDeDisolucion.Posicion> posiciones(Caso c) {
        return c.participantes().stream()
                .map(p -> new CuadreDeDisolucion.Posicion(p, bob("1000.00"), bob("0.00")))
                .toList();
    }

    private SalidaDisolucion iniciar(Caso c) {
        return transaccion.execute(t -> disolucionCU.iniciar(
                new EntradaDisolucion(
                        c.grupoId(),
                        "ACUERDO",
                        "Todos de acuerdo",
                        bob("2000.00"),
                        bob("0.00"),
                        bob("2000.00"),
                        posiciones(c)),
                c.gestor()));
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // El asiento lo escribe nucleo-financiero con los totales que este servicio le
        // manda. Sin ellos el cierre contable del grupo no se puede armar.
        Caso c = caso();
        SalidaDisolucion salida = iniciar(c);

        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM garantia.evento_dominio
                         WHERE tipo = 'garantia.disolucion_calculada'
                           AND payload->>'totalADevolver' IS NOT NULL AND payload->>'totalACobrar' IS NOT NULL
                        """))
                .isEqualTo(1);
        assertThat(salida.totalADevolver()).isEqualByComparingTo(bob("2000.00"));
    }

    @Test
    @DisplayName("rechaza por R-BIL-01")
    void rechazaRBIL01() {
        // El atomo lo dice sin base de datos: lo que se devuelve y lo que se cobra
        // equilibran las posiciones al centavo. Repartir sin mirar la posicion le
        // devolveria a quien ya cobro lo mismo que a quien nunca cobro.
        var uno = UUID.randomUUID();
        var dos = UUID.randomUUID();
        var cuadre = CuadreDeDisolucion.liquidar(
                bob("1500.00"),
                List.of(
                        new CuadreDeDisolucion.Posicion(uno, bob("1500.00"), bob("3000.00")),
                        new CuadreDeDisolucion.Posicion(dos, bob("1500.00"), bob("0.00"))));

        assertThat(cuadre.totalADevolver()).isEqualByComparingTo(bob("1500.00"));
        assertThat(cuadre.totalACobrar()).isEqualByComparingTo(bob("1500.00"));
        assertThat(cuadre.liquidaciones()).hasSize(2);
    }

    @Test
    @DisplayName("rechaza por R-BIL-12")
    void rechazaRBIL12() {
        // El dia no cierra con descuadre: la regla vive en la BASE. Una disolucion es
        // el peor momento para dejar un descuadre suelto.
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_bil_validar_cierre_diario"))
                .isEqualTo(1);
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO nucleo_financiero.cierre_diario
                            (id, fecha, total_recaudado, total_conciliado, total_excepciones,
                             cantidad_pagos, cuadrado, cerrado_por, cerrado_en)
                        VALUES (gen_random_uuid(), current_date, 100.00, 50.00, 50.00, 1, true,
                                gen_random_uuid(), now())
                        """))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-GRP-13")
    void rechazaRGRP13() {
        // La cuenta del grupo cierra EN CERO (tg_disolucion_cuadra). Un grupo disuelto
        // con saldo es plata de alguien que quedo sin dueno.
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_grp_validar_disolucion"))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*)::int FROM pg_trigger WHERE tgname = ?", "tg_disolucion_cuadra"))
                .isEqualTo(1);

        // Y no se disuelve sin motivo escrito ni con una masa que no alcanza: las dos
        // dejarian a los participantes sin poder saber que paso con su plata.
        Caso c = caso();
        assertThatThrownBy(() -> transaccion.execute(t -> disolucionCU.iniciar(
                        new EntradaDisolucion(
                                c.grupoId(),
                                "ACUERDO",
                                "  ",
                                bob("2000.00"),
                                bob("0.00"),
                                bob("2000.00"),
                                posiciones(c)),
                        c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin motivo escrito");
        assertThatThrownBy(() -> transaccion.execute(t -> disolucionCU.iniciar(
                        new EntradaDisolucion(
                                c.grupoId(),
                                "ACUERDO",
                                "No alcanza",
                                bob("2000.00"),
                                bob("0.00"),
                                bob("500.00"),
                                posiciones(c)),
                        c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no cuadra");
        assertThat(contar("SELECT count(*)::int FROM garantia.disolucion_anticipada WHERE grupo_id = ?", c.grupoId()))
                .isZero();
    }
}
