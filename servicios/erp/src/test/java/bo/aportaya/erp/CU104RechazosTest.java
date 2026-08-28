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

/** CU-104 · Lo que la base y el caso de uso rechazan. */
class CU104RechazosTest extends BaseDeErp {

    private UUID cliente;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        cliente = fixtura.tercero(
                "CLIENTE", "NIT-104R-" + UUID.randomUUID().toString().substring(0, 8));
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
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        UUID cuentaId = cuenta("1800.00");
        transaccion.execute(
                t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("600.00"), "TRANSFERENCIA"), ctx));

        // Cuenta y cobro son append-only: el monto adeudado no se reescribe y un cobro
        // no se borra. Una cobranza corregible a mano no se puede auditar.
        assertThat(rechazaLaBase("UPDATE erp.cuenta_por_cobrar SET monto = 1 WHERE id = ?", cuentaId))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM erp.cobro_cuenta_por_cobrar WHERE cuenta_por_cobrar_id = ?", cuentaId))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        UUID cuentaId = cuenta("900.00");
        var cobro = transaccion.execute(
                t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("900.00"), "TRANSFERENCIA"), ctx));

        // Apertura y cobro dejan su evento en la misma transaccion que la fila.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.cuenta_por_cobrar_abierta' AND agregado_id = ?
                        """,
                        cuentaId))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.cobro_registrado' AND agregado_id = ?
                        """,
                        cobro.cobroId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-06")
    void rechazaRAUD06() {
        UUID cuentaId = cuenta("750.00");
        var cobro = transaccion.execute(
                t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("750.00"), "TRANSFERENCIA"), ctx));

        // El cobro guarda su fecha y su forma: sin eso, conciliar contra el banco es
        // adivinar cual deposito corresponde a cual cuenta.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.cobro_cuenta_por_cobrar
                         WHERE id = ? AND fecha_cobro IS NOT NULL AND forma_cobro IS NOT NULL
                        """,
                        cobro.cobroId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-CTB-06")
    void rechazaRCTB06() {
        UUID cuentaId = cuenta("1000.00");
        transaccion.execute(
                t -> cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("400.00"), "TRANSFERENCIA"), ctx));

        // No se cobra por encima del saldo: el excedente no es una cobranza, es dinero
        // que le pertenece al cliente y hay que devolverle.
        assertThatThrownBy(() -> transaccion.execute(t ->
                        cobroCU.cobrar(new EntradaCobro(cuentaId, new BigDecimal("700.00"), "TRANSFERENCIA"), ctx)))
                .satisfies(e -> assertThat(raizDe(e)).contains("excede el saldo pendiente"));

        // Y la base sostiene el limite aunque se escriba por fuera.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO erp.cuenta_por_cobrar
                            (origen_tipo, origen_id, tercero_comercial_id, monto, moneda,
                             monto_cobrado, fecha_vencimiento, estado)
                        VALUES ('OTRO', gen_random_uuid(), ?, 100, 'BOB', 150, current_date + 10, 'PENDIENTE')
                        """,
                        cliente))
                .contains("ck_cxc_cobrado");

        // Una cuenta dada por incobrable no admite cobros.
        UUID incobrable = fixtura.cuentaIncobrable(cliente, "500.00");
        assertThatThrownBy(() -> transaccion.execute(t ->
                        cobroCU.cobrar(new EntradaCobro(incobrable, new BigDecimal("100.00"), "TRANSFERENCIA"), ctx)))
                .satisfies(e -> assertThat(raizDe(e)).contains("incobrable"));
    }
}
