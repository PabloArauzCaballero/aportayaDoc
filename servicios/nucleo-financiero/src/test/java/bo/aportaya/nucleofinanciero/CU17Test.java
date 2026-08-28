package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU11RetirarSaldo.EntradaRetiro;
import bo.aportaya.nucleofinanciero.aplicacion.CU17BloquearPorAutoridad.EntradaBloqueo;
import bo.aportaya.nucleofinanciero.aplicacion.CU17BloquearPorAutoridad.SalidaBloqueo;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-17 · Bloquear saldo por orden de autoridad. */
class CU17Test extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";
    private static final String HASH = "a".repeat(64);

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private record Caso(UUID usuario, UUID cuenta, ContextoSesion ctx) {}

    private Caso caso(String saldo) {
        fixtura.tipoDeCambioDeHoy();
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal(saldo));
        return new Caso(usuario, cuenta, contextoDe(usuario));
    }

    private SalidaBloqueo bloquear(Caso c, String monto, String oficio) {
        return transaccion.execute(t -> bloqueoCU.ejecutar(
                new EntradaBloqueo(
                        c.cuenta(),
                        "JUZGADO",
                        "INMOVILIZACION",
                        oficio,
                        Optional.of(bob(monto)),
                        "PARCIAL",
                        "https://oficios/x.pdf",
                        HASH),
                c.ctx()));
    }

    @Test
    @DisplayName(
            "Dado un oficio judicial que ordena inmovilizar Bs 5.000 · Cuando se registra · Entonces existe un bloqueo_saldo con numero_oficio único · Y una retencion_saldo VIGENTE con motivo ORDEN_AUTORIDAD y sin expira_en")
    void criterio1() {
        Caso c = caso("8000.00");

        SalidaBloqueo salida = bloquear(c, "5000.00", "OF-2026-001");

        assertThat(salida.montoBloqueado()).isEqualByComparingTo(bob("5000.00"));
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.bloqueo_saldo WHERE numero_oficio = ? AND estado = 'VIGENTE'",
                        "OF-2026-001"))
                .isEqualTo(1);
        // Sin expira_en, y a proposito: la levanta el mismo juez que la puso.
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.retencion_saldo WHERE id = ? AND motivo = 'ORDEN_AUTORIDAD' AND estado = 'VIGENTE' AND expira_en IS NULL",
                        salida.retencionId()))
                .isEqualTo(1);
        assertThat(salida.saldoDisponible()).isEqualByComparingTo(bob("3000.00"));
    }

    @Test
    @DisplayName(
            "Dado un bloqueo vigente · Cuando el titular intenta retirar el importe bloqueado · Entonces la operación se rechaza")
    void criterio2() {
        Caso c = caso("8000.00");
        bloquear(c, "5000.00", "OF-2026-002");
        custodia.cumpleEncaje();
        fixtura.limite("RETIRO", ESTANDAR, "MES", new BigDecimal("100000.00"), null);
        UUID instrumento = custodia.instrumentoDestino(c.usuario(), true, true, null);

        // Se pide MENOS de lo disponible a proposito: con 3.000 libres, un retiro de
        // 1.000 entraria de sobra. Lo que lo frena es el oficio, no el saldo — y esa
        // es exactamente la propiedad que hay que probar. Pedir mas de lo disponible
        // habria fallado por «insuficiente» y la prueba pasaria por otra razon.
        assertThatThrownBy(() -> transaccion.execute(t -> retiroCU.solicitar(
                        new EntradaRetiro(
                                "ret-bloq", c.cuenta(), bob("1000.00"), bob("5.00"), instrumento, true, false),
                        c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("orden de autoridad");
    }

    @Test
    @DisplayName(
            "Dado un intento de registrar dos bloqueos con el mismo numero_oficio · Cuando se inserta el segundo · Entonces la base de datos lo rechaza (R-BIL-14)")
    void criterio3() {
        Caso c = caso("8000.00");
        bloquear(c, "1000.00", "OF-REPETIDO");

        assertThatThrownBy(() -> bloquear(c, "1000.00", "OF-REPETIDO"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("Ya hay un bloqueo con el oficio");
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.bloqueo_saldo WHERE numero_oficio = ?",
                        "OF-REPETIDO"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La clave de este caso de uso es el numero de oficio: un oficio, un bloqueo.
        Caso c = caso("8000.00");
        bloquear(c, "2000.00", "OF-IDEM");

        assertThatThrownBy(() -> bloquear(c, "2000.00", "OF-IDEM")).isInstanceOf(ErrorDeNegocio.class);
        assertThat(contar(
                        "SELECT saldo_retenido::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?", c.cuenta()))
                .isEqualTo(2000);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        Caso c = caso("8000.00");
        SalidaBloqueo b = bloquear(c, "3000.00", "OF-CARRERA");

        transaccion.execute(t -> bloqueoCU.levantar(b.bloqueoId(), c.ctx()));

        assertThatThrownBy(() -> transaccion.execute(t -> bloqueoCU.levantar(b.bloqueoId(), c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no esta vigente");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Bloquear no mueve dinero: lo aparta. Disponible mas retenido sigue dando el
        // total, al centavo.
        Caso c = caso("8000.00");

        bloquear(c, "1234.56", "OF-CUADRE");

        var fila = dsl.fetchOne(
                "SELECT saldo_disponible, saldo_retenido, saldo_total FROM nucleo_financiero.cuenta_billetera WHERE id = ?",
                c.cuenta());
        assertThat(fila.get(0, BigDecimal.class).add(fila.get(1, BigDecimal.class)))
                .isEqualByComparingTo(fila.get(2, BigDecimal.class));
        assertThat(fila.get(1, BigDecimal.class)).isEqualByComparingTo(new BigDecimal("1234.56"));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "cumplimiento"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "cumplimiento"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin documento de respaldo corta ANTES de tocar la base: inmovilizar la
        // plata de alguien sin poder mostrar el papel que lo ordena es indefendible.
        Caso c = caso("8000.00");

        assertThatThrownBy(() -> transaccion.execute(t -> bloqueoCU.ejecutar(
                        new EntradaBloqueo(
                                c.cuenta(),
                                "JUZGADO",
                                "EMBARGO",
                                "OF-SIN-PAPEL",
                                Optional.of(bob("100.00")),
                                "PARCIAL",
                                "",
                                null),
                        c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("documento que lo ordena");

        assertThat(contar("SELECT count(*)::int FROM nucleo_financiero.bloqueo_saldo"))
                .isZero();
        assertThat(contar(
                        "SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?",
                        c.cuenta()))
                .isEqualTo(8000);
    }

    @Test
    @DisplayName("rechaza bloquear mas de lo que hay: el oficio inmoviliza lo que habia")
    void rechazaBloquearDeMas() {
        // Un alcance total sobre una cuenta que despues recibe plata no la alcanza, y
        // eso es correcto: el oficio inmoviliza lo que habia al momento.
        Caso c = caso("1000.00");

        SalidaBloqueo salida = bloquear(c, "5000.00", "OF-EXCESO");

        assertThat(salida.montoBloqueado()).isEqualByComparingTo(bob("1000.00"));
        assertThat(salida.saldoDisponible()).isEqualByComparingTo(bob("0.00"));
    }
}
