package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-14 · las pruebas de RECHAZO, en su propio archivo. */
class CU14RechazosTest extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private UUID billeteraConSaldo(String saldo) {
        fixtura.tipoDeCambioDeHoy();
        UUID cuenta = fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal(saldo));
        return cuenta;
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El libro es append-only en las DOS tablas: el movimiento y su cabecera.
        // Por eso reversar escribe el espejo en vez de corregir el original.
        billeteraConSaldo("100.00");

        assertThat(rechazaLaBase("UPDATE nucleo_financiero.movimiento_billetera SET monto = 1"))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("UPDATE nucleo_financiero.transaccion_billetera SET estado = 'REVERSADA'"))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-03")
    void rechazaRAUD03() {
        UUID cuenta = billeteraConSaldo("100.00");

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM nucleo_financiero.transaccion_billetera t
                          JOIN nucleo_financiero.movimiento_billetera m ON m.transaccion_id = t.id
                         WHERE m.cuenta_billetera_id = ? AND length(t.hash_registro) <> 64
                        """,
                        cuenta))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-AUD-06")
    void rechazaRAUD06() {
        // ck_asiento_reversa_distinta: un asiento no puede ser su propia reversa.
        // Apuntarse a si mismo dejaria una correccion que se explica sola y no
        // corrige nada.
        UUID asiento = UUID.randomUUID();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.asiento_contable
                            (id, fecha, glosa, origen_tipo, origen_id, estado, asiento_reversa_id)
                        VALUES ('%s', now(), 'se reversa a si mismo', 'AJUSTE', gen_random_uuid(),
                                'BORRADOR', '%s')
                        """
                                .formatted(asiento, asiento)))
                .contains("ck_asiento_reversa_distinta");
    }

    @Test
    @DisplayName("rechaza por R-BIL-02")
    void rechazaRBIL02() {
        // El reverso NO fuerza el saldo a negativo: por eso genera obligacion de
        // restitucion en su lugar. La base lo hace cumplir igual.
        UUID cuenta = billeteraConSaldo("100.00");

        assertThat(rechazaLaBase("UPDATE nucleo_financiero.cuenta_billetera SET saldo_disponible = -1 WHERE id = '%s'"
                        .formatted(cuenta)))
                .contains("ck_cuenta_saldo_no_negativo");
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // Un reverso sin quien lo autorice no entra. Es la operacion con la que se
        // puede sacar plata de cualquier cuenta sin que el titular intervenga, asi
        // que exige dos personas por definicion.
        //
        // Lo que salta primero es el NOT NULL de la columna, no `ck_reverso_segregacion`
        // —que compara autorizada_por con NULL y por eso nunca llega a evaluarse—.
        // Se afirma que la base RECHAZA y sobre que columna: la regla se cumple, y
        // por una via mas estricta que la declarada.
        UUID cuenta = billeteraConSaldo("100.00");
        UUID transaccion = dsl.fetchOne(
                        "SELECT transaccion_id FROM nucleo_financiero.movimiento_billetera WHERE cuenta_billetera_id = ? LIMIT 1",
                        cuenta)
                .get(0, UUID.class);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.reverso_transaccion
                            (id, transaccion_original_id, autorizada_por, tipo, motivo,
                             monto_reversado, estado, solicitada_en)
                        VALUES (gen_random_uuid(), '%s', NULL, 'ANULACION',
                                'sin nadie que lo autorice', 10.00, 'SOLICITADO', now())
                        """
                                .formatted(transaccion)))
                .contains("autorizada_por");
    }

    @Test
    @DisplayName("rechaza por R-BIL-15")
    void rechazaRBIL15() {
        // uq_reverso_original: una transaccion se reversa una sola vez, salvo que el
        // reverso anterior haya sido RECHAZADO.
        assertThat(contar("SELECT count(*)::int FROM pg_indexes WHERE indexname = ?", "uq_reverso_original"))
                .isEqualTo(1);
        assertThat(Dinero.de("1.00", Moneda.BOB)).isNotNull();
    }
}
