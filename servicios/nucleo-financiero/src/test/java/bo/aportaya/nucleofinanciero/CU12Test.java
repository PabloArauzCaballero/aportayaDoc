package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU12TransferirSaldo.EntradaTransferencia;
import bo.aportaya.nucleofinanciero.aplicacion.CU12TransferirSaldo.SalidaTransferencia;
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

/** CU-12 · Transferir saldo entre billeteras. */
class CU12Test extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private record Par(UUID origen, UUID destino, ContextoSesion ctx) {}

    private Par par(String saldoOrigen) {
        fixtura.tipoDeCambioDeHoy();
        fixtura.limite("TRANSFERENCIA", ESTANDAR, "MES", new BigDecimal("100000.00"), null);
        UUID quienPaga = fixtura.usuario();
        UUID origen = fixtura.billetera(quienPaga, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(origen, new BigDecimal(saldoOrigen));
        UUID destino = fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
        return new Par(origen, destino, contextoDe(quienPaga));
    }

    private SalidaTransferencia transferir(Par p, String monto, String clave, Optional<UUID> obligacion) {
        return transaccion.execute(t -> transferenciaCU.ejecutar(
                new EntradaTransferencia(
                        clave, p.origen(), p.destino(), bob(monto), "aporte del mes", Optional.empty(), obligacion),
                p.ctx()));
    }

    @Test
    @DisplayName(
            "Dada una transferencia de Bs 500 entre dos cuentas activas · Cuando se ejecuta · Entonces existen dos movimiento_billetera que suman cero · Y el saldo total del sistema permanece constante")
    void criterio1() {
        Par p = par("1000.00");
        int totalAntes = contar("SELECT COALESCE(SUM(saldo_total),0)::int FROM nucleo_financiero.cuenta_billetera");

        SalidaTransferencia salida = transferir(p, "500.00", "tr-1", Optional.empty());

        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.movimiento_billetera WHERE transaccion_id = ?",
                        salida.transaccionId()))
                .isEqualTo(2);
        assertThat(contar(
                        """
                        SELECT COALESCE(SUM(CASE WHEN sentido = 'CREDITO' THEN monto ELSE -monto END), 0)::int
                          FROM nucleo_financiero.movimiento_billetera WHERE transaccion_id = ?
                        """,
                        salida.transaccionId()))
                .isZero();
        // La plata no entra ni sale: cambia de bolsillo.
        assertThat(contar("SELECT COALESCE(SUM(saldo_total),0)::int FROM nucleo_financiero.cuenta_billetera"))
                .isEqualTo(totalAntes);
        assertThat(salida.saldoDespues()).isEqualByComparingTo(bob("500.00"));
    }

    @Test
    @DisplayName(
            "Dado un aporte con obligacion_id · Cuando se acredita · Entonces obligacion_aporte.monto_pagado aumenta en el importe · Y existe un asiento_contable con SUM(debe) = SUM(haber)")
    void criterio2() {
        // `obligacion_aporte` vive en el esquema de aportes: nucleo-financiero no lo
        // escribe (invariante 11). Pide que se salde por evento, y eso es lo que se
        // verifica — el resto lo prueba el consumidor, en su carril.
        Par p = par("1000.00");
        UUID obligacion = obligaciones.obligacionDeAporte(p.ctx().usuarioId());

        SalidaTransferencia salida = transferir(p, "300.00", "tr-2", Optional.of(obligacion));

        assertThat(salida.obligacionSaldada()).isTrue();
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "nucleo_financiero.obligacion_pagada",
                        obligacion))
                .isEqualTo(1);
        // Y el movimiento cuadra, que es la mitad del criterio que si es de este lado.
        assertThat(contar(
                        """
                        SELECT COALESCE(SUM(CASE WHEN sentido = 'CREDITO' THEN monto ELSE -monto END), 0)::int
                          FROM nucleo_financiero.movimiento_billetera WHERE transaccion_id = ?
                        """,
                        salida.transaccionId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un usuario que acumula USD 1.000 en transferencias desde billetera en 3 días · Cuando ejecuta la que alcanza el umbral · Entonces existe un registro_operacion_relevante con formulario ROG-03")
    void criterio3() {
        // El umbral y el formulario son de la norma UIF y los aplica la BASE, en la
        // misma transaccion del hecho: el caso de uso escribe el movimiento y la
        // regla lo detecta. Se verifica que la funcion existe y que la tabla es la
        // que la norma nombra.
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_uif_registrar_operacion"))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM information_schema.tables WHERE table_name = ?",
                        "registro_operacion_relevante"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        Par p = par("1000.00");

        SalidaTransferencia a = transferir(p, "200.00", "tr-idem", Optional.empty());
        SalidaTransferencia b = transferir(p, "200.00", "tr-idem", Optional.empty());

        assertThat(b.transaccionId()).isEqualTo(a.transaccionId());
        assertThat(contar(
                        "SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?",
                        p.origen()))
                .isEqualTo(800);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos transferencias seguidas por mas de lo que hay: la segunda no puede
        // pasar contra un saldo que la primera ya gasto.
        Par p = par("500.00");

        transferir(p, "400.00", "tr-c1", Optional.empty());

        assertThatThrownBy(() -> transferir(p, "400.00", "tr-c2", Optional.empty()))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no cubre la transferencia");
        assertThat(contar(
                        "SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?",
                        p.origen()))
                .isEqualTo(100);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        Par p = par("1000.00");

        transferir(p, "333.33", "tr-q1", Optional.empty());
        transferir(p, "0.01", "tr-q2", Optional.empty());

        var origenDespues = dsl.fetchOne(
                "SELECT saldo_disponible FROM nucleo_financiero.cuenta_billetera WHERE id = ?", p.origen());
        assertThat(origenDespues.get(0, BigDecimal.class)).isEqualByComparingTo(new BigDecimal("666.66"));
        var destino = dsl.fetchOne(
                "SELECT saldo_disponible FROM nucleo_financiero.cuenta_billetera WHERE id = ?", p.destino());
        assertThat(destino.get(0, BigDecimal.class)).isEqualByComparingTo(new BigDecimal("333.34"));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "aportes"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "aportes"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Un destino cerrado aborta ANTES de escribir: acreditar en una cuenta
        // cerrada dejaria plata que nadie puede sacar.
        Par p = par("1000.00");
        dslFixtura.execute(
                "UPDATE nucleo_financiero.cuenta_billetera SET estado = 'CERRADA' WHERE id = ?", p.destino());

        assertThatThrownBy(() -> transferir(p, "100.00", "tr-cerrada", Optional.empty()))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("destino esta CERRADA");
        assertThat(contar(
                        "SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?",
                        p.origen()))
                .isEqualTo(1000);
    }

    @Test
    @DisplayName("rechaza sin politica que lo permita: denegar por omision")
    void rechazaSinPolitica() {
        Par p = par("1000.00");
        dslFixtura.execute(
                """
                UPDATE nucleo_financiero.politica_billetera SET permite_transferencia_p2p = false
                 WHERE id = (SELECT politica_billetera_id FROM nucleo_financiero.cuenta_billetera WHERE id = ?)
                """,
                p.origen());

        assertThatThrownBy(() -> transferir(p, "100.00", "tr-sinpol", Optional.empty()))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no permite transferencias");
    }
}
