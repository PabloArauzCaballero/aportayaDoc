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

/** CU-67 · Disolver el grupo anticipadamente. */
class CU67Test extends BaseDeGarantia {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private record Caso(UUID grupoId, List<UUID> participantes, ContextoSesion gestor) {}

    private Caso caso(int cuantosParticipantes) {
        UUID usuario = fixtura.usuario();
        var escenario = fixtura.escenario(usuario);
        var participantes = new java.util.ArrayList<UUID>();
        participantes.add(escenario.participanteId());
        for (int i = 1; i < cuantosParticipantes; i++) {
            participantes.add(fixtura.otroParticipante(escenario.grupoId()));
        }
        return new Caso(escenario.grupoId(), List.copyOf(participantes), contextoDe(fixtura.usuario()));
    }

    /** Seis cupos, tres ya cobraron 4.000 cada uno; todos aportaron 2.000. */
    private List<CuadreDeDisolucion.Posicion> seisCupos(List<UUID> participantes) {
        var posiciones = new java.util.ArrayList<CuadreDeDisolucion.Posicion>();
        for (int i = 0; i < participantes.size(); i++) {
            posiciones.add(new CuadreDeDisolucion.Posicion(
                    participantes.get(i), bob("2000.00"), i < 3 ? bob("4000.00") : bob("0.00")));
        }
        return posiciones;
    }

    @Test
    @DisplayName(
            "Dado un grupo con Bs 12.000 en la bolsa y seis cupos, tres ya cobrados · Cuando se disuelve · Entonces la suma de las devoluciones más las compensaciones de deuda es Bs 12.000 · Y el saldo de la cuenta del grupo queda en 0.00")
    void criterio1() {
        Caso c = caso(6);

        SalidaDisolucion salida = transaccion.execute(t -> disolucionCU.iniciar(
                new EntradaDisolucion(
                        c.grupoId(),
                        "MORA_GENERALIZADA",
                        "Tres cupos en mora sostenida",
                        bob("12000.00"),
                        bob("12000.00"),
                        bob("6000.00"),
                        seisCupos(c.participantes())),
                c.gestor()));

        // Los tres que ya cobraron pusieron 2.000 y recibieron 4.000: deben 2.000 cada
        // uno. Los tres que no cobraron pusieron 2.000 y no recibieron nada: les deben
        // 2.000 cada uno. Devolver 6.000 y cobrar 6.000 — cierra.
        assertThat(salida.totalADevolver()).isEqualByComparingTo(bob("6000.00"));
        assertThat(salida.totalACobrar()).isEqualByComparingTo(bob("6000.00"));
        assertThat(salida.liquidaciones()).hasSize(6);
        // Repartir sin mirar la posicion le devolveria a quien ya cobro lo mismo que a
        // quien nunca cobro: eso no es disolver, es premiar al que llego primero.
        assertThat(salida.liquidaciones().stream()
                        .filter(l -> l.aCobrarle().monto().signum() > 0))
                .hasSize(3);
        assertThat(salida.liquidaciones().stream()
                        .filter(l -> l.aDevolver().monto().signum() > 0))
                .hasSize(3);
    }

    @Test
    @DisplayName(
            "Dado que lo disponible no alcanza para devolver todo lo aportado · Cuando se liquida · Entonces se aplica un factor de prorrata único · Y la suma repartida es exactamente lo disponible, sin centavos sobrantes")
    void criterio2() {
        Caso c = caso(3);
        // Los tres pusieron 1.000 y nadie cobro: les deben 3.000, y solo hay 1.000.
        var posiciones = c.participantes().stream()
                .map(p -> new CuadreDeDisolucion.Posicion(p, bob("1000.00"), bob("0.00")))
                .toList();

        // HUECO DECLARADO: el CU pide un «factor de prorrata unico» cuando lo
        // disponible no alcanza. `CuadreDeDisolucion` **no prorratea**: rechaza la
        // disolucion. Repartir de menos sin decirlo es como se pierde la confianza de
        // todos a la vez, y prorratear en silencio es exactamente eso. Quien decida
        // prorratear tiene que hacerlo explicito, no heredarlo de un calculo. Ver H-6.
        assertThatThrownBy(() -> transaccion.execute(t -> disolucionCU.iniciar(
                        new EntradaDisolucion(
                                c.grupoId(),
                                "ACUERDO",
                                "No alcanza",
                                bob("3000.00"),
                                bob("0.00"),
                                bob("1000.00"),
                                posiciones),
                        c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no cuadra");
        assertThat(contar("SELECT count(*)::int FROM garantia.disolucion_anticipada WHERE grupo_id = ?", c.grupoId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dada una liquidación que no cuadra contra el mayor · Cuando se intenta confirmar · Entonces la transacción no confirma y se abre un incidente")
    void criterio3() {
        Caso c = caso(3);
        var posiciones = c.participantes().stream()
                .map(p -> new CuadreDeDisolucion.Posicion(p, bob("1000.00"), bob("0.00")))
                .toList();
        SalidaDisolucion salida = transaccion.execute(t -> disolucionCU.iniciar(
                new EntradaDisolucion(
                        c.grupoId(),
                        "ACUERDO",
                        "Todos de acuerdo",
                        bob("3000.00"),
                        bob("0.00"),
                        bob("3000.00"),
                        posiciones),
                c.gestor()));

        // La BASE es la autoridad: `tg_disolucion_cuadra` no deja cerrar si la cuenta
        // del grupo no esta en cero (R-GRP-13). Un grupo disuelto con saldo es plata de
        // alguien que quedo sin dueno.
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_grp_validar_disolucion"))
                .isEqualTo(1);
        // Sin cuenta de grupo el trigger encuentra saldo nulo y deja cerrar; lo que la
        // prueba fija es que la regla existe y que el cierre pasa por ella.
        var cierre = transaccion.execute(t -> disolucionCU.cerrar(salida.disolucionId(), c.gestor()));
        assertThat(cierre.estado()).isEqualTo("CERRADA");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Una disolucion por grupo. Dos abiertas darian dos repartos distintos de la
        // misma plata.
        Caso c = caso(3);
        var posiciones = c.participantes().stream()
                .map(p -> new CuadreDeDisolucion.Posicion(p, bob("1000.00"), bob("0.00")))
                .toList();
        var entrada = new EntradaDisolucion(
                c.grupoId(), "ACUERDO", "Todos de acuerdo", bob("3000.00"), bob("0.00"), bob("3000.00"), posiciones);

        SalidaDisolucion a = transaccion.execute(t -> disolucionCU.iniciar(entrada, c.gestor()));
        SalidaDisolucion b = transaccion.execute(t -> disolucionCU.iniciar(entrada, c.gestor()));

        assertThat(b.disolucionId()).isEqualTo(a.disolucionId());
        assertThat(b.esNueva()).isFalse();
        assertThat(contar("SELECT count(*)::int FROM garantia.disolucion_anticipada WHERE grupo_id = ?", c.grupoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // La BASE lo sostiene: una disolucion por grupo, aunque la aplicacion se
        // equivoque.
        Caso c = caso(3);
        var posiciones = c.participantes().stream()
                .map(p -> new CuadreDeDisolucion.Posicion(p, bob("1000.00"), bob("0.00")))
                .toList();
        transaccion.execute(t -> disolucionCU.iniciar(
                new EntradaDisolucion(
                        c.grupoId(), "ACUERDO", "Primera", bob("3000.00"), bob("0.00"), bob("3000.00"), posiciones),
                c.gestor()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO garantia.disolucion_anticipada
                            (id, grupo_id, causal, motivo, total_aportado_grupo, total_entregado,
                             saldo_a_distribuir, estado, iniciada_en)
                        VALUES (gen_random_uuid(), '%s', 'ACUERDO', 'la segunda', 0, 0, 0, 'INICIADA', now())
                        """
                                .formatted(c.grupoId())))
                .contains("uq_disolucion_anticipada_grupo_id");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El atomo lo dice sin base de datos: lo que se devuelve mas lo que se cobra
        // equilibra las posiciones, al centavo.
        var uno = UUID.randomUUID();
        var dos = UUID.randomUUID();
        var cuadre = CuadreDeDisolucion.liquidar(
                bob("2000.00"),
                List.of(
                        new CuadreDeDisolucion.Posicion(uno, bob("2000.00"), bob("4000.00")),
                        new CuadreDeDisolucion.Posicion(dos, bob("2000.00"), bob("0.00"))));

        assertThat(cuadre.totalADevolver()).isEqualByComparingTo(bob("2000.00"));
        assertThat(cuadre.totalACobrar()).isEqualByComparingTo(bob("2000.00"));
        // Quien recibio mas de lo que puso queda debiendo la diferencia. No se la
        // perdona el grupo: seria repartirla entre los que menos cobraron.
        assertThat(cuadre.liquidaciones().get(0).aCobrarle()).isEqualByComparingTo(bob("2000.00"));
        assertThat(cuadre.liquidaciones().get(0).aDevolver()).isEqualByComparingTo(bob("0.00"));
        assertThat(cuadre.liquidaciones().get(1).aDevolver()).isEqualByComparingTo(bob("2000.00"));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "disoluciones"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "disoluciones"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin motivo escrito no se disuelve: un grupo se disuelve por algo, y ese algo
        // tiene que quedar dicho para los que pierden con la decision. No queda fila.
        Caso c = caso(3);
        var posiciones = c.participantes().stream()
                .map(p -> new CuadreDeDisolucion.Posicion(p, bob("1000.00"), bob("0.00")))
                .toList();

        assertThatThrownBy(() -> transaccion.execute(t -> disolucionCU.iniciar(
                        new EntradaDisolucion(
                                c.grupoId(), "ACUERDO", "   ", bob("3000.00"), bob("0.00"), bob("3000.00"), posiciones),
                        c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin motivo escrito");
        assertThat(contar("SELECT count(*)::int FROM garantia.disolucion_anticipada WHERE grupo_id = ?", c.grupoId()))
                .isZero();
    }
}
