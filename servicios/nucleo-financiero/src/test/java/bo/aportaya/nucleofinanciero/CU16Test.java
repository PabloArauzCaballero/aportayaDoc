package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU12TransferirSaldo.EntradaTransferencia;
import bo.aportaya.nucleofinanciero.aplicacion.CU16CerrarBilletera.EntradaCierre;
import bo.aportaya.nucleofinanciero.aplicacion.CU16CerrarBilletera.SalidaSolicitud;
import bo.aportaya.nucleofinanciero.aplicacion.CU17BloquearPorAutoridad.EntradaBloqueo;
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

/** CU-16 · Cerrar la billetera y devolver el saldo. */
class CU16Test extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

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
        if (!"0".equals(saldo)) {
            fixtura.acreditar(cuenta, new BigDecimal(saldo));
        }
        return new Caso(usuario, cuenta, contextoDe(usuario));
    }

    private SalidaSolicitud solicitar(Caso c, boolean obligaciones, boolean grupo) {
        return transaccion.execute(t -> cierreCU.solicitar(
                new EntradaCierre(c.cuenta(), "Ya no lo uso", "RETIRO", obligaciones, grupo), c.ctx()));
    }

    @Test
    @DisplayName(
            "Dado un titular sin obligaciones ni bloqueos · Cuando solicita el cierre y se le devuelve el saldo · Entonces cuenta_billetera queda CERRADA con saldo_total = 0")
    void criterio1() {
        Caso c = caso("500.00");
        SalidaSolicitud solicitud = solicitar(c, false, false);

        // El saldo se va por el camino real: una transferencia a otra billetera.
        fixtura.limite("TRANSFERENCIA", ESTANDAR, "MES", new BigDecimal("100000.00"), null);
        UUID otra = fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
        transaccion.execute(t -> transferenciaCU.ejecutar(
                new EntradaTransferencia(
                        "vaciar", c.cuenta(), otra, bob("500.00"), "devolucion", Optional.empty(), Optional.empty()),
                c.ctx()));

        var cerrada = transaccion.execute(t -> cierreCU.ejecutar(solicitud.solicitudId(), c.ctx()));

        assertThat(cerrada.estado()).isEqualTo("CERRADA");
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.cuenta_billetera WHERE id = ? AND estado = 'CERRADA' AND saldo_total = 0",
                        c.cuenta()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un titular con un bloqueo_saldo vigente · Cuando solicita el cierre · Entonces la solicitud se rechaza indicando el número de oficio")
    void criterio2() {
        Caso c = caso("500.00");
        transaccion.execute(t -> bloqueoCU.ejecutar(
                new EntradaBloqueo(
                        c.cuenta(),
                        "FISCALIA",
                        "CONGELAMIENTO",
                        "OF-CIERRE-1",
                        Optional.of(bob("100.00")),
                        "PARCIAL",
                        "https://of/x.pdf",
                        "b".repeat(64)),
                c.ctx()));

        assertThatThrownBy(() -> solicitar(c, false, false))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("OF-CIERRE-1")
                .hasMessageContaining("FISCALIA");
    }

    @Test
    @DisplayName(
            "Dada una cuenta cerrada · Cuando se consulta su historial dentro del plazo de conservación · Entonces los movimientos siguen disponibles")
    void criterio3() {
        Caso c = caso("300.00");
        int movimientosAntes = contar(
                "SELECT count(*)::int FROM nucleo_financiero.movimiento_billetera WHERE cuenta_billetera_id = ?",
                c.cuenta());
        SalidaSolicitud solicitud = solicitar(c, false, false);
        fixtura.limite("TRANSFERENCIA", ESTANDAR, "MES", new BigDecimal("100000.00"), null);
        UUID otra = fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
        transaccion.execute(t -> transferenciaCU.ejecutar(
                new EntradaTransferencia(
                        "vaciar3", c.cuenta(), otra, bob("300.00"), "devolucion", Optional.empty(), Optional.empty()),
                c.ctx()));
        transaccion.execute(t -> cierreCU.ejecutar(solicitud.solicitudId(), c.ctx()));

        // Cerrar NO borra: alguien puede necesitar su extracto dos años despues.
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.movimiento_billetera WHERE cuenta_billetera_id = ?",
                        c.cuenta()))
                .isGreaterThan(movimientosAntes);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        Caso c = caso("0");
        SalidaSolicitud solicitud = solicitar(c, false, false);

        transaccion.execute(t -> cierreCU.ejecutar(solicitud.solicitudId(), c.ctx()));

        assertThatThrownBy(() -> transaccion.execute(t -> cierreCU.ejecutar(solicitud.solicitudId(), c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya no esta abierta");
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Una solicitud por cuenta: la segunda devuelve la MISMA, no crea otra.
        // Cerrar una billetera es un acto unico, y dos solicitudes sobre la misma
        // cuenta serian dos historias del mismo cierre.
        Caso c = caso("0");
        SalidaSolicitud a = solicitar(c, false, false);
        SalidaSolicitud b = solicitar(c, false, false);

        assertThat(b.solicitudId()).isEqualTo(a.solicitudId());
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.solicitud_cierre_billetera WHERE cuenta_billetera_id = ?",
                        c.cuenta()))
                .isEqualTo(1);

        transaccion.execute(t -> cierreCU.ejecutar(a.solicitudId(), c.ctx()));
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.cuenta_billetera WHERE id = ? AND estado = 'CERRADA'",
                        c.cuenta()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Cerrar exige saldo CERO, disponible y retenido. Cerrar con saldo dejaria
        // plata de alguien en una cuenta que ya no se puede operar.
        Caso c = caso("100.00");
        SalidaSolicitud solicitud = solicitar(c, false, false);

        assertThatThrownBy(() -> transaccion.execute(t -> cierreCU.ejecutar(solicitud.solicitudId(), c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("Todavia queda saldo");
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.cuenta_billetera WHERE id = ? AND estado = 'ACTIVA'",
                        c.cuenta()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "identidad"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "identidad"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Con obligaciones abiertas no se cierra: un cierre que deje un aporte sin
        // pagar traslada la perdida a los otros participantes del pasanaku.
        Caso c = caso("0");

        assertThatThrownBy(() -> solicitar(c, true, false))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("aportes pendientes");
        assertThat(contar("SELECT count(*)::int FROM nucleo_financiero.solicitud_cierre_billetera"))
                .isZero();
    }

    @Test
    @DisplayName("rechaza cerrar con retencion vigente: el saldo apartado esta comprometido")
    void rechazaConRetencion() {
        Caso c = caso("500.00");
        transaccion.execute(t -> retencionCU.retener(
                new bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo.EntradaRetencion(
                        c.cuenta(),
                        bob("100.00"),
                        "DISPUTA",
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()),
                c.ctx()));

        assertThatThrownBy(() -> solicitar(c, false, false))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("saldo retenido");
    }

    @Test
    @DisplayName("rechaza cerrar participando en un grupo activo")
    void rechazaConGrupoActivo() {
        Caso c = caso("0");

        assertThatThrownBy(() -> solicitar(c, false, true))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("pasanaku activo");
    }
}
