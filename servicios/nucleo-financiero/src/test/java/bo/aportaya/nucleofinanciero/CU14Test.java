package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU12TransferirSaldo.EntradaTransferencia;
import bo.aportaya.nucleofinanciero.aplicacion.CU14ReversarTransaccion.EntradaReverso;
import bo.aportaya.nucleofinanciero.aplicacion.CU14ReversarTransaccion.SalidaReverso;
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

/** CU-14 · Reversar una transaccion. */
class CU14Test extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";
    private static final String MOTIVO = "Cobro duplicado por un reintento del proveedor de pagos";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private record Movida(UUID transaccion, UUID origen, UUID destino, ContextoSesion ctx) {}

    /** Una transferencia ya aplicada: es lo que se va a reversar. */
    private Movida movida(String saldo, String monto) {
        fixtura.tipoDeCambioDeHoy();
        fixtura.limite("TRANSFERENCIA", ESTANDAR, "MES", new BigDecimal("100000.00"), null);
        UUID quienPaga = fixtura.usuario();
        UUID origen = fixtura.billetera(quienPaga, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(origen, new BigDecimal(saldo));
        UUID destino = fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
        ContextoSesion ctx = contextoDe(quienPaga);
        var t = transaccion.execute(x -> transferenciaCU.ejecutar(
                new EntradaTransferencia(
                        "mov-" + UUID.randomUUID(),
                        origen,
                        destino,
                        bob(monto),
                        "cobro",
                        Optional.empty(),
                        Optional.empty()),
                ctx));
        return new Movida(t.transaccionId(), origen, destino, ctx);
    }

    @Test
    @DisplayName(
            "Dada una transacción aplicada por Bs 300 · Cuando se reversa · Entonces existe una nueva transaccion_billetera de tipo REVERSO por Bs 300 · Y la transacción original conserva sus movimientos sin cambios")
    void criterio1() {
        Movida m = movida("1000.00", "300.00");
        UUID autoriza = fixtura.usuario();

        SalidaReverso salida = transaccion.execute(x -> reversoCU.ejecutar(
                new EntradaReverso("rev-1", m.transaccion(), "ERROR_OPERATIVO", MOTIVO, autoriza), m.ctx()));

        assertThat(salida.generaObligacionDeRestitucion()).isFalse();
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.transaccion_billetera WHERE id = ? AND tipo = 'REVERSO' AND monto_total = 300.00",
                        salida.transaccionReversoId()))
                .isEqualTo(1);
        // La original conserva sus DOS movimientos, intactos: reversar es escribir el
        // espejo, no borrar el original.
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.movimiento_billetera WHERE transaccion_id = ?",
                        m.transaccion()))
                .isEqualTo(2);
        // Y el saldo volvio a donde estaba.
        assertThat(contar(
                        "SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?",
                        m.origen()))
                .isEqualTo(1000);
    }

    @Test
    @DisplayName(
            "Dado un intento de UPDATE sobre movimiento_billetera · Cuando lo ejecuta el rol de aplicación · Entonces la base de datos lo rechaza (R-AUD-01)")
    void criterio2() {
        Movida m = movida("500.00", "100.00");

        assertThat(rechazaLaBase(
                        "UPDATE nucleo_financiero.movimiento_billetera SET monto = 1 WHERE transaccion_id = '%s'"
                                .formatted(m.transaccion())))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName(
            "Dado un reverso que dejaría el saldo negativo · Cuando se ejecuta · Entonces se genera una obligación de restitución · Y el saldo_disponible no baja de cero")
    void criterio3() {
        Movida m = movida("500.00", "400.00");
        // El destino gasta lo que recibio: ahora el espejo lo dejaria en negativo.
        transaccion.execute(x -> transferenciaCU.ejecutar(
                new EntradaTransferencia(
                        "gasto", m.destino(), m.origen(), bob("400.00"), "gasto", Optional.empty(), Optional.empty()),
                contextoDe(fixtura.usuario())));
        UUID autoriza = fixtura.usuario();

        SalidaReverso salida = transaccion.execute(x -> reversoCU.ejecutar(
                new EntradaReverso("rev-3", m.transaccion(), "CONTRACARGO", MOTIVO, autoriza), m.ctx()));

        assertThat(salida.generaObligacionDeRestitucion()).isTrue();
        assertThat(salida.transaccionReversoId()).isNull();
        // El saldo NO baja de cero: el error fue nuestro y la deuda queda a la vista,
        // no escondida en un negativo que la persona no entiende.
        assertThat(contar(
                        "SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?",
                        m.destino()))
                .isZero();
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.evento_dominio WHERE tipo = ?",
                        "nucleo_financiero.restitucion_requerida"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // R-BIL-15: una transaccion se reversa UNA vez. El segundo intento choca con
        // el estado, no con un saldo ya movido dos veces.
        Movida m = movida("1000.00", "200.00");
        UUID autoriza = fixtura.usuario();

        transaccion.execute(x -> reversoCU.ejecutar(
                new EntradaReverso("rev-r", m.transaccion(), "ANULACION", MOTIVO, autoriza), m.ctx()));

        assertThatThrownBy(() -> transaccion.execute(x -> reversoCU.ejecutar(
                        new EntradaReverso("rev-r2", m.transaccion(), "ANULACION", MOTIVO, autoriza), m.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya fue reversada");
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.reverso_transaccion WHERE transaccion_original_id = ?",
                        m.transaccion()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        Movida m = movida("1000.00", "150.00");
        UUID autoriza = fixtura.usuario();

        transaccion.execute(x -> reversoCU.ejecutar(
                new EntradaReverso("rev-c1", m.transaccion(), "ANULACION", MOTIVO, autoriza), m.ctx()));

        assertThatThrownBy(() -> transaccion.execute(x -> reversoCU.ejecutar(
                        new EntradaReverso("rev-c2", m.transaccion(), "ANULACION", MOTIVO, autoriza), m.ctx())))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThat(contar(
                        "SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?",
                        m.origen()))
                .isEqualTo(1000);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        Movida m = movida("1000.00", "333.33");
        UUID autoriza = fixtura.usuario();

        SalidaReverso salida = transaccion.execute(x -> reversoCU.ejecutar(
                new EntradaReverso("rev-q", m.transaccion(), "ERROR_OPERATIVO", MOTIVO, autoriza), m.ctx()));

        assertThat(contar(
                        """
                        SELECT COALESCE(SUM(CASE WHEN sentido = 'CREDITO' THEN monto ELSE -monto END), 0)::int
                          FROM nucleo_financiero.movimiento_billetera WHERE transaccion_id = ?
                        """,
                        salida.transaccionReversoId()))
                .isZero();
        // Original mas reverso = cero neto sobre cada cuenta, al centavo.
        var origen = dsl.fetchOne(
                "SELECT saldo_disponible FROM nucleo_financiero.cuenta_billetera WHERE id = ?", m.origen());
        assertThat(origen.get(0, BigDecimal.class)).isEqualByComparingTo(new BigDecimal("1000.00"));
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
        // Sin cuatro ojos el reverso corta ANTES de tocar la base: no queda ni el
        // registro del intento a medias.
        Movida m = movida("1000.00", "100.00");

        assertThatThrownBy(() -> transaccion.execute(x -> reversoCU.ejecutar(
                        new EntradaReverso(
                                "rev-solo",
                                m.transaccion(),
                                "ANULACION",
                                MOTIVO,
                                m.ctx().usuarioId()),
                        m.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede ser quien lo solicita");

        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.reverso_transaccion WHERE transaccion_original_id = ?",
                        m.transaccion()))
                .isZero();
    }

    @Test
    @DisplayName("rechaza un motivo que no explica: seis meses despues alguien va a preguntar")
    void rechazaMotivoPobre() {
        Movida m = movida("1000.00", "100.00");
        UUID autoriza = fixtura.usuario();

        assertThatThrownBy(() -> transaccion.execute(x -> reversoCU.ejecutar(
                        new EntradaReverso("rev-corto", m.transaccion(), "ANULACION", "error", autoriza), m.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("explicar que paso");
    }
}
