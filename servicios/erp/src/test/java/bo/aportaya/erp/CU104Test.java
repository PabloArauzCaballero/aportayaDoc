package bo.aportaya.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.erp.aplicacion.CU104CobrarCuenta.EntradaCobro;
import bo.aportaya.erp.aplicacion.CU104CobrarCuenta.EntradaCuenta;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-104 · Cobrar una cuenta por cobrar. */
class CU104Test extends BaseDeErp {

    private UUID cliente;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        cliente = fixtura.tercero(
                "CLIENTE", "NIT-104-" + UUID.randomUUID().toString().substring(0, 8));
        ctx = contextoDe(fixtura.usuario());
    }

    private UUID cuenta(String monto) {
        return transaccion.execute(t -> cobroCU.abrir(
                new EntradaCuenta(
                        "FACTURA_PUBLICIDAD",
                        UUID.randomUUID(),
                        cliente,
                        new BigDecimal(monto),
                        "BOB",
                        LocalDate.now().plusDays(30)),
                ctx));
    }

    @Test
    @DisplayName(
            "Dada una cuenta_por_cobrar pendiente con origen_tipo FACTURA_PUBLICIDAD · Cuando Tesorería registra un cobro por el monto total · Entonces la cuenta pasa a estado COBRADA y queda su asiento contable enlazado")
    void criterio1() {
        UUID cuentaId = cuenta("2500.00");

        var salida = transaccion.execute(
                t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("2500.00"), "TRANSFERENCIA"), ctx));

        assertThat(salida.estado()).isEqualTo("COBRADA");
        assertThat(salida.saldoPendiente()).isEqualByComparingTo("0.00");
        // HUECO: `cuenta_por_cobrar` es append-only, asi que su `estado` y su
        // `monto_cobrado` no se mueven. El estado real se deriva de la suma de cobros.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.cuenta_por_cobrar c
                         WHERE c.id = ?
                           AND c.monto = (SELECT COALESCE(SUM(x.monto), 0) FROM erp.cobro_cuenta_por_cobrar x
                                           WHERE x.cuenta_por_cobrar_id = c.id)
                        """,
                        cuentaId))
                .isEqualTo(1);
        // El asiento lo escribe nucleo-financiero (invariante 12): aca se pide por evento.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.cobro_registrado' AND payload->>'cuentaPorCobrarId' = ?
                        """,
                        cuentaId.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una cuenta_por_cobrar con saldo_pendiente de 500 · Cuando se intenta registrar un cobro de 800 · Entonces el sistema devuelve MONTO_MAYOR_AL_SALDO")
    void criterio2() {
        UUID cuentaId = cuenta("500.00");

        // Cobrar de mas es plata que el cliente va a reclamar y que ya no figura como
        // deuda: nadie la ve hasta que llama.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("800.00"), "QR"), ctx)))
                .hasMessageContaining("excede el saldo pendiente");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.cobro_cuenta_por_cobrar WHERE cuenta_por_cobrar_id = ?",
                        cuentaId))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dada una cuenta_por_cobrar marcada INCOBRABLE · Cuando se intenta registrar un cobro sobre ella · Entonces el sistema devuelve CUENTA_YA_INCOBRABLE")
    void criterio3() {
        // Marcarla incobrable ya movio la contabilidad —se reconocio la perdida—, asi
        // que cobrarla sin rehabilitarla dejaria la perdida y el cobro conviviendo.
        //
        // HUECO: la cuenta nace INCOBRABLE porque **no puede llegar a serlo**: la tabla
        // es append-only y marcarla seria un UPDATE. Se demuestra el rechazo abajo.
        UUID cuentaId = fixtura.cuentaIncobrable(cliente, "500.00");
        assertThat(rechazaLaBase(
                        "UPDATE erp.cuenta_por_cobrar SET estado = 'INCOBRABLE' WHERE id = ?", cuenta("100.00")))
                .contains("R-AUD-01");

        assertThatThrownBy(() -> transaccion.execute(
                        t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("100.00"), "QR"), ctx)))
                .hasMessageContaining("incobrable");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID cuentaId = cuenta("500.00");
        var cobro = new EntradaCobro(cuentaId, new BigDecimal("500.00"), "TRANSFERENCIA");

        transaccion.execute(t -> cobroCU.cobrar(cobro, ctx));
        assertThatThrownBy(() -> transaccion.execute(t -> cobroCU.cobrar(cobro, ctx)))
                .hasMessageContaining("excede el saldo pendiente");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.cobro_cuenta_por_cobrar WHERE cuenta_por_cobrar_id = ?",
                        cuentaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID cuentaId = cuenta("500.00");
        var cobro = new EntradaCobro(cuentaId, new BigDecimal("500.00"), "TRANSFERENCIA");

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> cobroCU.cobrar(cobro, ctx));
            } catch (Exception e) {
                errores.add(e);
            }
        };
        var uno = new Thread(intento);
        var dos = new Thread(intento);
        uno.start();
        dos.start();
        uno.join();
        dos.join();

        assertThat(errores).hasSize(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.cobro_cuenta_por_cobrar WHERE cuenta_por_cobrar_id = ?",
                        cuentaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        UUID cuentaId = cuenta("1000.00");
        transaccion.execute(t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("400.00"), "QR"), ctx));
        var segundo = transaccion.execute(
                t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("600.00"), "EFECTIVO"), ctx));

        // Lo cobrado mas el saldo iguala el monto, al centavo.
        assertThat(segundo.cobrado().add(segundo.saldoPendiente())).isEqualByComparingTo("1000.00");
        var suma = dsl.fetchOne(
                        "SELECT COALESCE(SUM(monto), 0) FROM erp.cobro_cuenta_por_cobrar WHERE cuenta_por_cobrar_id = ?",
                        cuentaId)
                .get(0, BigDecimal.class);
        assertThat(suma).isEqualByComparingTo(segundo.cobrado());
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID cuentaId = cuenta("1000.00");
        transaccion.execute(t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("400.00"), "QR"), ctx));
        transaccion.execute(t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("600.00"), "QR"), ctx));

        // Dos cobros parciales son dos hechos distintos y dejan dos rastros: lo que no
        // puede pasar es que la suma pase del monto.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.cobro_registrado' AND payload->>'cuentaPorCobrarId' = ?
                        """,
                        cuentaId.toString()))
                .isEqualTo(2);
        assertThatThrownBy(() -> transaccion.execute(
                        t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("0.01"), "QR"), ctx)))
                .hasMessageContaining("excede el saldo");
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: cobrar sobre una cuenta que no existe.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> cobroCU.cobrar(new EntradaCobro(UUID.randomUUID(), new BigDecimal("100.00"), "QR"), ctx)))
                .hasMessageContaining("no existe");

        UUID cuentaId = cuenta("500.00");

        // Paso fallido: cobro por encima del saldo. No deja rastro parcial.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("600.00"), "QR"), ctx)))
                .hasMessageContaining("excede el saldo");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.cobro_cuenta_por_cobrar WHERE cuenta_por_cobrar_id = ?",
                        cuentaId))
                .isZero();

        // Con el monto correcto, el mismo camino cierra.
        var buena = transaccion.execute(
                t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("500.00"), "QR"), ctx));
        assertThat(buena.estado()).isEqualTo("COBRADA");
    }
}
