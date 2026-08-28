package bo.aportaya.garantia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.garantia.aplicacion.CU29DevolverFondo.EntradaDevolucion;
import bo.aportaya.garantia.aplicacion.CU29DevolverFondo.SalidaDevolucion;
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

/** CU-29 · Devolver los aportes del fondo de garantia. */
class CU29Test extends BaseDeGarantia {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private record Caso(UUID grupoId, UUID fondoId, UUID participanteId, ContextoSesion gestor) {}

    /** Un fondo con aportantes. `saldo` es lo que quedo tras las coberturas. */
    private Caso caso(String saldo, String... aportes) {
        UUID usuario = fixtura.usuario();
        var escenario = fixtura.escenario(usuario);
        UUID politica = fixtura.politica(escenario.grupoId(), "100.00", "50000.00", "50000.00", 3, "100000.00", 15);
        UUID fondo = fixtura.fondo(escenario.grupoId(), politica, saldo);

        // El primero es el participante del escenario; los demas, participantes reales
        // del mismo grupo: la devolucion los referencia por clave foranea.
        fixtura.aportarAlFondo(fondo, escenario.participanteId(), aportes[0], saldo);
        for (int i = 1; i < aportes.length; i++) {
            fixtura.aportarAlFondo(fondo, fixtura.otroParticipante(escenario.grupoId()), aportes[i], saldo);
        }
        return new Caso(escenario.grupoId(), fondo, escenario.participanteId(), contextoDe(fixtura.usuario()));
    }

    @Test
    @DisplayName(
            "Dado un participante que aportó Bs 300 al fondo y consumió Bs 100 en coberturas · Cuando se cierra el fondo · Entonces su monto_a_devolver es 200")
    void criterio1() {
        // Aporto 300 y es el unico aportante; el fondo quedo con 200 tras cubrir 100.
        Caso c = caso("200.00", "300.00");

        SalidaDevolucion salida = transaccion.execute(
                t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), true, 0), c.gestor()));

        assertThat(salida.totalDevuelto()).isEqualByComparingTo(bob("200.00"));
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.devolucion_fondo
                         WHERE fondo_id = ? AND participante_id = ? AND monto_aportado = 300.00
                           AND monto_consumido = 100.00 AND monto_a_devolver = 200.00
                        """,
                        c.fondoId(),
                        c.participanteId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un participante que consumió más de lo que aportó · Cuando se calcula su devolución · Entonces monto_a_devolver es 0 · Y la diferencia queda como deuda_participante")
    void criterio2() {
        // Dos aportantes: uno puso 300 y el otro 100. El fondo quedo con 100 tras
        // cubrir 300, asi que se reparte proporcionalmente y el que menos puso recibe
        // casi nada.
        Caso c = caso("100.00", "300.00", "100.00");

        SalidaDevolucion salida = transaccion.execute(
                t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), true, 0), c.gestor()));

        // R-GAR-06: **nunca mas de lo aportado, nunca negativo**. Lo que el fondo gasto
        // lo pierden todos en proporcion — esa es la idea de un fondo mutual. La deuda
        // de quien incumplio la deja la COBERTURA (CU-23), no la devolucion:
        // cobrarsela aca seria cobrarle el mismo incumplimiento dos veces, como deuda
        // y como menor devolucion.
        assertThat(salida.totalDevuelto()).isEqualByComparingTo(bob("100.00"));
        assertThat(salida.devoluciones())
                .allSatisfy(
                        d -> assertThat(d.aDevolver().esMayorQue(d.aportado())).isFalse());
        assertThat(salida.devoluciones())
                .allSatisfy(d -> assertThat(d.aDevolver().monto().signum()).isNotNegative());
        // El que puso 300 de 400 recibe 75 de los 100 que quedaron.
        assertThat(salida.devoluciones().get(0).aDevolver()).isEqualByComparingTo(bob("75.00"));
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.devolucion_fondo WHERE fondo_id = ? AND monto_consumido > 0",
                        c.fondoId()))
                .isEqualTo(2);
    }

    @Test
    @DisplayName(
            "Dado un fondo cuyo reparto no coincide con su saldo · Cuando se intenta ejecutar la devolución · Entonces la transacción no confirma y se abre un descuadre")
    void criterio3() {
        // Tres aportantes iguales sobre 100: 33,33 + 33,33 + el remanente. El ultimo se
        // lleva 33,34 para que la suma cierre exacta contra el saldo. Sin ese ajuste,
        // repartir entre tres deja centavos flotando que nadie sabe de quien son.
        Caso c = caso("100.00", "100.00", "100.00", "100.00");

        SalidaDevolucion salida = transaccion.execute(
                t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), true, 0), c.gestor()));

        assertThat(salida.totalDevuelto()).isEqualByComparingTo(bob("100.00"));
        var suma = dsl.fetchOne(
                        "SELECT sum(monto_a_devolver) AS total FROM garantia.devolucion_fondo WHERE fondo_id = ?",
                        c.fondoId())
                .get("total", java.math.BigDecimal.class);
        assertThat(suma).isEqualByComparingTo(new java.math.BigDecimal("100.00"));
        // Y el saldo del fondo queda en cero: no sobra ni falta un centavo.
        assertThat(dsl.fetchOne("SELECT saldo_disponible FROM garantia.fondo_garantia WHERE id = ?", c.fondoId())
                        .get("saldo_disponible", java.math.BigDecimal.class))
                .isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }

    @Test
    @DisplayName(
            "Dado un fondo con una cobertura todavía abierta · Cuando se intenta cerrarlo · Entonces se rechaza con COBERTURA_EN_CURSO")
    void criterio4() {
        Caso c = caso("500.00", "500.00");

        // Devolver mientras alguien debe es repartir plata que todavia tiene dueno.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), true, 2), c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("deuda(s) sin resolver");
        assertThat(contar("SELECT count(*)::int FROM garantia.devolucion_fondo WHERE fondo_id = ?", c.fondoId()))
                .isZero();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Tras devolver, el fondo queda en cero: la segunda devolucion reparte cero, no
        // vuelve a repartir lo mismo. La version optimista del fondo es la barrera.
        Caso c = caso("200.00", "200.00");

        SalidaDevolucion a = transaccion.execute(
                t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), true, 0), c.gestor()));
        SalidaDevolucion b = transaccion.execute(
                t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), true, 0), c.gestor()));

        assertThat(a.totalDevuelto()).isEqualByComparingTo(bob("200.00"));
        assertThat(b.totalDevuelto()).isEqualByComparingTo(bob("0.00"));
        assertThat(dsl.fetchOne("SELECT saldo_disponible FROM garantia.fondo_garantia WHERE id = ?", c.fondoId())
                        .get("saldo_disponible", java.math.BigDecimal.class))
                .isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // El saldo del fondo nunca queda negativo: la BASE lo impide aunque la
        // aplicacion se equivoque. Un fondo en rojo es plata que se repartio dos veces.
        Caso c = caso("200.00", "200.00");
        transaccion.execute(t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), true, 0), c.gestor()));

        assertThat(rechazaLaBase("UPDATE garantia.fondo_garantia SET saldo_disponible = -1 WHERE id = '%s'"
                        .formatted(c.fondoId())))
                .contains("ck_fondo_garantia_saldo_disponible");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El atomo lo dice sin base de datos: la suma repartida es EXACTAMENTE el saldo,
        // y nadie recibe mas de lo que puso.
        var reparto = DevolucionDelFondo.repartir(
                bob("100.00"),
                List.of(
                        new DevolucionDelFondo.Aportante(UUID.randomUUID(), bob("100.00")),
                        new DevolucionDelFondo.Aportante(UUID.randomUUID(), bob("100.00")),
                        new DevolucionDelFondo.Aportante(UUID.randomUUID(), bob("100.00"))));

        Dinero suma = reparto.devoluciones().stream()
                .map(DevolucionDelFondo.Devolucion::aDevolver)
                .reduce(bob("0.00"), Dinero::mas);
        assertThat(suma).isEqualByComparingTo(bob("100.00"));
        assertThat(reparto.devoluciones())
                .allSatisfy(
                        d -> assertThat(d.aDevolver().esMayorQue(d.aportado())).isFalse());
        // El ultimo se lleva el remanente: 33,33 + 33,33 + 33,34.
        assertThat(reparto.devoluciones().get(2).aDevolver()).isEqualByComparingTo(bob("33.34"));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "devoluciones"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "devoluciones"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Con el grupo en curso no se devuelve nada: el fondo esta ahi para cubrir lo
        // que falta, y devolverlo antes lo deja sin respaldo justo cuando hace falta.
        Caso c = caso("500.00", "500.00");

        assertThatThrownBy(() -> transaccion.execute(
                        t -> devolucionCU.devolver(new EntradaDevolucion(c.grupoId(), false, 0), c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("todavia no termino");
        assertThat(contar("SELECT count(*)::int FROM garantia.devolucion_fondo WHERE fondo_id = ?", c.fondoId()))
                .isZero();
        assertThat(dsl.fetchOne("SELECT saldo_disponible FROM garantia.fondo_garantia WHERE id = ?", c.fondoId())
                        .get("saldo_disponible", java.math.BigDecimal.class))
                .isEqualByComparingTo(new java.math.BigDecimal("500.00"));
    }
}
