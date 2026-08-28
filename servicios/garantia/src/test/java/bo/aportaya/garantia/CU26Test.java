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

/** CU-26 · Ejecutar el aval y subrogar la deuda. */
class CU26Test extends BaseDeGarantia {

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
            UUID usuario,
            UUID avalista,
            FixturaDeGarantia.Escenario escenario,
            UUID expedienteId,
            UUID fondoId,
            ContextoSesion gestor) {}

    /** Un expediente cubierto, con su deuda, y un aval de tope conocido. */
    private Caso caso(String tope, String porcentaje, boolean conAval, boolean conCobertura) {
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
                        "Vencido y sin pago",
                        null,
                        null),
                gestor));

        if (conCobertura) {
            transaccion.execute(t -> coberturaCU.cubrir(
                    new EntradaCobertura(expediente.expedienteId(), bob("1500.00"), 30, null), gestor));
        }
        if (conAval) {
            fixtura.aval(escenario.grupoId(), escenario.participanteId(), avalista, tope, porcentaje);
        }
        return new Caso(usuario, avalista, escenario, expediente.expedienteId(), fondo, gestor);
    }

    @Test
    @DisplayName(
            "Dado un aval con tope de Bs 1.000 y una deuda confirmada de Bs 1.500 · Cuando se ejecuta el aval · Entonces monto_ejecutado es 1.000 · Y el saldo de Bs 500 sigue como deuda del incumplido")
    void criterio1() {
        Caso c = caso("1000.00", "100.00", true, true);

        SalidaEjecucion salida = transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));

        // El avalista acepto responder por 1.000. Cobrarle 1.500 seria cobrarle algo
        // que nunca acepto, y es la clase de cosa que hace que nadie vuelva a avalar.
        assertThat(salida.montoEjecutado()).isEqualByComparingTo(bob("1000.00"));
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.ejecucion_aval WHERE registro_id = ? AND monto_ejecutado = 1000.00",
                        c.expedienteId()))
                .isEqualTo(1);
        // Los 500 restantes siguen siendo deuda del incumplido: el aval cubre parte, no
        // borra el resto.
        assertThat(dsl.fetchOne(
                                "SELECT saldo_actual FROM garantia.deuda_participante WHERE registro_id = ?",
                                c.expedienteId())
                        .get("saldo_actual", BigDecimal.class))
                .isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    @DisplayName(
            "Dado un incumplimiento todavía PRESUNTO · Cuando se intenta ejecutar el aval · Entonces se rechaza con INCUMPLIMIENTO_NO_FIRME")
    void criterio2() {
        // Sin cobertura no hay deuda que ejecutar: el expediente todavia no llego al
        // punto en que alguien deba algo. Ejecutarle el aval a un avalista por una
        // deuda que aun no existe es cobrarle sin causa.
        Caso c = caso("1000.00", "100.00", true, false);

        assertThatThrownBy(() -> transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no tiene deuda que ejecutar");
        assertThat(contar("SELECT count(*)::int FROM garantia.ejecucion_aval WHERE registro_id = ?", c.expedienteId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un avalista que paga la ejecución · Cuando se acredita su pago · Entonces existe una subrogacion a su favor por el monto pagado · Y el asiento contable cuadra")
    void criterio3() {
        Caso c = caso("1000.00", "100.00", true, true);

        SalidaEjecucion salida = transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));

        // El avalista que paga se SUBROGA: la deuda no desaparece, cambia de acreedor.
        // Si desapareciera, el deudor original se quedaria sin deber nada porque otro
        // pago por el, y el avalista sin nada que reclamar.
        assertThat(salida.subrogacionId()).isNotNull();
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.subrogacion
                         WHERE id = ? AND acreedor_original = 'FONDO_GARANTIA'
                           AND acreedor_subrogado = 'AVALISTA' AND monto_subrogado = 1000.00
                        """,
                        salida.subrogacionId()))
                .isEqualTo(1);
        // El asiento lo escribe nucleo-financiero (invariante 12) con el monto que este
        // servicio le manda.
        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM garantia.evento_dominio
                         WHERE tipo = 'garantia.aval_ejecutado' AND payload->>'montoEjecutado' = '1000.00'
                        """))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un incumplido que paga después de la subrogación · Cuando se acredita su pago · Entonces el dinero se imputa al avalista subrogado")
    void criterio4() {
        Caso c = caso("1000.00", "100.00", true, true);
        transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));

        // La deuda queda marcada como subrogada: es lo que le dice a quien acredite el
        // pago que el dinero va al avalista, no al fondo. Sin esa marca, el fondo
        // cobraria dos veces lo mismo y el avalista se quedaria sin recuperar nada.
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.deuda_participante WHERE registro_id = ? AND es_subrogada",
                        c.expedienteId()))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.subrogacion s
                          JOIN garantia.deuda_participante d ON d.id = s.deuda_id
                         WHERE d.registro_id = ?
                        """,
                        c.expedienteId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Una ejecucion por aval y expediente (R-GAR-03). La segunda devuelve la que hay.
        Caso c = caso("1000.00", "100.00", true, true);

        SalidaEjecucion a = transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));
        SalidaEjecucion b = transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));

        assertThat(b.ejecucionId()).isEqualTo(a.ejecucionId());
        assertThat(b.esNueva()).isFalse();
        assertThat(contar("SELECT count(*)::int FROM garantia.ejecucion_aval WHERE registro_id = ?", c.expedienteId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // La BASE lo sostiene: uq_ejecucion_aval_registro, aunque la aplicacion se
        // equivoque. Dos ejecuciones del mismo aval por el mismo expediente le cobran
        // dos veces al avalista.
        // Con 60% sobre 1.500 se ejecutan 900 de un tope de 1.000: quedan 100 de
        // margen, asi que la segunda insercion llega al INDICE en vez de frenarse
        // antes en el trigger del tope — que es la otra barrera y tiene su prueba.
        Caso c = caso("1000.00", "60.00", true, true);
        SalidaEjecucion salida = transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));
        UUID avalId = dsl.fetchOne("SELECT aval_id FROM garantia.ejecucion_aval WHERE id = ?", salida.ejecucionId())
                .get("aval_id", UUID.class);
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
                                .formatted(avalId, c.expedienteId(), deudaId)))
                .contains("uq_ejecucion_aval_registro");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Con 60% de responsabilidad sobre 1.500 son 900, por debajo del tope de 1.000:
        // manda el porcentaje. El calculo es exacto, sin redondeos que sumen a favor
        // de nadie.
        Caso c = caso("1000.00", "60.00", true, true);

        SalidaEjecucion salida = transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor()));

        assertThat(salida.montoEjecutado()).isEqualByComparingTo(bob("900.00"));
        // Y el atomo lo dice sin base de datos: el tope se mide contra lo YA ejecutado.
        var tope = new TopeDelAval(bob("1000.00"), new BigDecimal("60.00"), bob("900.00"));
        assertThat(tope.disponible()).isEqualByComparingTo(bob("100.00"));
        assertThat(tope.ejecutable(bob("1500.00"))).isEqualByComparingTo(bob("100.00"));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "avales"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "avales"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin aval vigente no hay a quien ejecutarle, y no queda nada a medias.
        // Ejecutarle a alguien que no acepto avalar es cobrarle sin causa.
        Caso c = caso("1000.00", "100.00", false, true);

        assertThatThrownBy(() -> transaccion.execute(t -> avalCU.ejecutar(c.expedienteId(), c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no tiene aval vigente");
        assertThat(contar("SELECT count(*)::int FROM garantia.ejecucion_aval WHERE registro_id = ?", c.expedienteId()))
                .isZero();
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.deuda_participante WHERE registro_id = ? AND NOT es_subrogada",
                        c.expedienteId()))
                .isEqualTo(1);
    }
}
