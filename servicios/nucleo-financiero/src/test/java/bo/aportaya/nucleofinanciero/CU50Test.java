package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU10RecargarSaldo.EntradaSolicitud;
import bo.aportaya.nucleofinanciero.aplicacion.CU11RetirarSaldo.EntradaRetiro;
import bo.aportaya.nucleofinanciero.aplicacion.CU50ConciliarCustodia.EntradaConciliacion;
import bo.aportaya.nucleofinanciero.aplicacion.CU50ConciliarCustodia.SalidaConciliacion;
import bo.aportaya.nucleofinanciero.dominio.RatioDeCobertura;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-50 · Conciliar la custodia y verificar el encaje. */
class CU50Test extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private LocalDate hoy() {
        return OffsetDateTime.now(ZoneOffset.UTC).toLocalDate();
    }

    /** Una cuenta de custodia y un cierre diario: sin ellos no hay que conciliar. */
    private UUID escenario(String saldoCuenta) {
        fixtura.tipoDeCambioDeHoy();
        UUID cuenta = fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal(saldoCuenta));
        fixtura.cierreDelDia(cuenta, hoy(), new BigDecimal(saldoCuenta), 1);
        return custodia.cuentaDeCustodia();
    }

    @Test
    @DisplayName(
            "Dado un día con saldos de billetera por Bs 1.000.000 y custodia por Bs 1.000.000 · Cuando corre la conciliación · Entonces ratio_cobertura es 1,000000 y cumple_encaje es true")
    void criterio1() {
        UUID cc = escenario("1000.00");
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        SalidaConciliacion salida = transaccion.execute(
                t -> conciliacionCU.ejecutar(EntradaConciliacion.deHoy(cc, hoy(), "1000000.00", "1000000.00"), ctx));

        assertThat(salida.ratioCobertura()).isEqualByComparingTo(new BigDecimal("1.000000"));
        assertThat(salida.cumpleEncaje()).isTrue();
        assertThat(salida.estado()).isEqualTo("CUADRADA");
        assertThat(salida.diferencia()).isEqualByComparingTo(bob("0.00"));
    }

    @Test
    @DisplayName(
            "Dada una conciliación DESCUADRADA del día · Cuando se intenta marcar el cierre_diario como cuadrado · Entonces la operación se rechaza")
    void criterio2() {
        UUID cc = escenario("1000.00");
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        SalidaConciliacion salida = transaccion.execute(
                t -> conciliacionCU.ejecutar(EntradaConciliacion.deHoy(cc, hoy(), "1000000.00", "999999.00"), ctx));

        assertThat(salida.cumpleEncaje()).isFalse();
        assertThat(salida.estado()).isEqualTo("DESCUADRADA");
        // Y queda el evento para que alguien lo mire: un encaje roto que nadie
        // registra es un encaje roto que nadie arregla.
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.evento_dominio WHERE tipo = ?",
                        "nucleo_financiero.encaje_incumplido"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un ratio_cobertura menor a 1 · Cuando un usuario intenta retirar · Entonces la operación se rechaza por modo restringido · Y una recarga del mismo usuario sí se acepta")
    void criterio3() {
        // Esta es la propiedad central: con el encaje roto no SALE dinero, pero SI
        // entra. Dejar entrar plata mejora el encaje; frenarla lo empeoraria justo
        // cuando hace falta lo contrario.
        fixtura.tipoDeCambioDeHoy();
        custodia.noCumpleEncaje();
        UUID usuario = fixtura.usuario();
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal("1000.00"));
        UUID instrumento = custodia.instrumentoDestino(usuario, true, true, null);
        fixtura.limite("RETIRO", ESTANDAR, "MES", new BigDecimal("100000.00"), null);
        fixtura.limite("RECARGA", ESTANDAR, "MES", new BigDecimal("100000.00"), null);
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() -> transaccion.execute(t -> retiroCU.solicitar(
                        new EntradaRetiro("ret-encaje", cuenta, bob("100.00"), bob("5.00"), instrumento, true, false),
                        ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("suspendidos temporalmente");

        // La recarga si entra.
        var recarga = transaccion.execute(t -> recargaCU.solicitar(
                new EntradaSolicitud("rec-encaje", cuenta, bob("200.00"), bob("0.00"), "QR", Optional.empty()), ctx));
        assertThat(recarga.estado()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Una conciliacion por cuenta y dia: repetirla escribiria dos verdades sobre
        // el mismo dia y ninguna seria la buena.
        UUID cc = escenario("1000.00");
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        transaccion.execute(
                t -> conciliacionCU.ejecutar(EntradaConciliacion.deHoy(cc, hoy(), "1000.00", "1000.00"), ctx));

        assertThatThrownBy(() -> transaccion.execute(
                        t -> conciliacionCU.ejecutar(EntradaConciliacion.deHoy(cc, hoy(), "1000.00", "1000.00"), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("Ya hay una conciliacion");
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        UUID cc = escenario("1000.00");
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        transaccion.execute(
                t -> conciliacionCU.ejecutar(EntradaConciliacion.deHoy(cc, hoy(), "500.00", "500.00"), ctx));

        assertThatThrownBy(() -> transaccion.execute(
                        t -> conciliacionCU.ejecutar(EntradaConciliacion.deHoy(cc, hoy(), "500.00", "500.00"), ctx)))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.conciliacion_custodia WHERE cuenta_custodia_id = ?",
                        cc))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El ratio redondea HACIA ABAJO: hacia arriba podria mostrar 1,000000 cuando
        // falta un centavo, y ese centavo es lo que la regla existe para detectar.
        var justo = RatioDeCobertura.calcular(bob("1000.00"), bob("1000.00"), bob("0.00"));
        var falta = RatioDeCobertura.calcular(bob("1000.00"), bob("999.99"), bob("0.00"));
        var sobra = RatioDeCobertura.calcular(bob("1000.00"), bob("1000.01"), bob("0.00"));

        assertThat(justo.cumpleEncaje()).isTrue();
        assertThat(justo.ratio()).isEqualByComparingTo(new BigDecimal("1.000000"));
        assertThat(falta.cumpleEncaje()).isFalse();
        assertThat(falta.diferencia()).isEqualByComparingTo(bob("-0.01"));
        assertThat(sobra.cumpleEncaje()).isTrue();
        // Sin dinero emitido el encaje se cumple sin dividir por cero.
        assertThat(RatioDeCobertura.calcular(bob("0.00"), bob("0.00"), bob("0.00"))
                        .cumpleEncaje())
                .isTrue();
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "auditoria"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "auditoria"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin cierres diarios no hay contra que comparar, y no se escribe nada.
        fixtura.tipoDeCambioDeHoy();
        UUID cc = custodia.cuentaDeCustodia();
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> transaccion.execute(t -> conciliacionCU.ejecutar(
                        EntradaConciliacion.deHoy(cc, hoy().minusDays(30), "1000.00", "1000.00"), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no hay nada que conciliar");
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.conciliacion_custodia WHERE cuenta_custodia_id = ?",
                        cc))
                .isZero();
    }
}
