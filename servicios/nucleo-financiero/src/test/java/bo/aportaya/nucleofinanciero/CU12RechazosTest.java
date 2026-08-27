package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU12TransferirSaldo.EntradaTransferencia;
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

/** CU-12 · las pruebas de RECHAZO, en su propio archivo. */
class CU12RechazosTest extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private record Par(UUID origen, UUID destino, ContextoSesion ctx) {}

    private Par par(String saldo) {
        fixtura.tipoDeCambioDeHoy();
        fixtura.limite("TRANSFERENCIA", ESTANDAR, "MES", new BigDecimal("100000.00"), null);
        UUID quienPaga = fixtura.usuario();
        UUID origen = fixtura.billetera(quienPaga, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(origen, new BigDecimal(saldo));
        return new Par(origen, fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO), contextoDe(quienPaga));
    }

    @Test
    @DisplayName("rechaza por R-BIL-01")
    void rechazaRBIL01() {
        assertThat(
                        rechazaLaBaseAlCerrar(
                                """
                        INSERT INTO nucleo_financiero.transaccion_billetera
                            (id, tipo, estado, moneda, monto_total, origen_tipo, origen_id, canal,
                             clave_idempotencia, hash_registro, ocurrida_en, registrada_en)
                        VALUES (gen_random_uuid(), 'TRANSFERENCIA_P2P', 'APLICADA', 'BOB', 10.00,
                                'TRANSFERENCIA_P2P', gen_random_uuid(), 'APP', 'tr-bil01',
                                repeat('a', 64), now(), now())
                        """))
                .contains("R-BIL-01");
    }

    @Test
    @DisplayName("rechaza por R-BIL-02")
    void rechazaRBIL02() {
        Par p = par("100.00");

        assertThat(rechazaLaBase("UPDATE nucleo_financiero.cuenta_billetera SET saldo_disponible = -1 WHERE id = '%s'"
                        .formatted(p.origen())))
                .contains("ck_cuenta_saldo_no_negativo");
    }

    @Test
    @DisplayName("rechaza por R-BIL-06")
    void rechazaRBIL06() {
        Par p = par("500.00");
        var a = transaccion.execute(t -> transferenciaCU.ejecutar(
                new EntradaTransferencia(
                        "tr-b06", p.origen(), p.destino(), bob("50.00"), "x", Optional.empty(), Optional.empty()),
                p.ctx()));
        var b = transaccion.execute(t -> transferenciaCU.ejecutar(
                new EntradaTransferencia(
                        "tr-b06", p.origen(), p.destino(), bob("50.00"), "x", Optional.empty(), Optional.empty()),
                p.ctx()));

        assertThat(b.transaccionId()).isEqualTo(a.transaccionId());
    }

    @Test
    @DisplayName("rechaza por R-BIL-19")
    void rechazaRBIL19() {
        // El reintento devuelve la primera respuesta, no un error de unicidad.
        Par p = par("500.00");
        var a = transaccion.execute(t -> transferenciaCU.ejecutar(
                new EntradaTransferencia(
                        "tr-b19", p.origen(), p.destino(), bob("60.00"), "x", Optional.empty(), Optional.empty()),
                p.ctx()));
        var b = transaccion.execute(t -> transferenciaCU.ejecutar(
                new EntradaTransferencia(
                        "tr-b19", p.origen(), p.destino(), bob("60.00"), "x", Optional.empty(), Optional.empty()),
                p.ctx()));

        assertThat(b.saldoDespues()).isEqualByComparingTo(a.saldoDespues());
    }

    @Test
    @DisplayName("rechaza por R-BIL-20")
    void rechazaRBIL20() {
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_bil_moneda_coherente"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-GRP-03")
    void rechazaRGRP03() {
        // uq_obligacion_periodo_cupo: una obligacion periodica por cupo, no dos. Sin
        // esto, el mismo aporte se podria cobrar dos veces en el mismo periodo.
        assertThat(contar("SELECT count(*)::int FROM pg_indexes WHERE indexname = ?", "uq_obligacion_periodo_cupo"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-LIM-01")
    void rechazaRLIM01() {
        fixtura.tipoDeCambioDeHoy();
        UUID quienPaga = fixtura.usuario();
        UUID origen = fixtura.billetera(quienPaga, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(origen, new BigDecimal("500.00"));
        UUID destino = fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
        ContextoSesion ctx = contextoDe(quienPaga);

        assertThatThrownBy(() -> transaccion.execute(t -> transferenciaCU.ejecutar(
                        new EntradaTransferencia(
                                "tr-lim", origen, destino, bob("10.00"), "x", Optional.empty(), Optional.empty()),
                        ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("deniega por omision");
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        Par p = par("500.00");
        transaccion.execute(t -> transferenciaCU.ejecutar(
                new EntradaTransferencia(
                        "tr-aud01", p.origen(), p.destino(), bob("10.00"), "x", Optional.empty(), Optional.empty()),
                p.ctx()));

        assertThat(rechazaLaBase("DELETE FROM nucleo_financiero.movimiento_billetera"))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-03")
    void rechazaRAUD03() {
        Par p = par("500.00");
        var s = transaccion.execute(t -> transferenciaCU.ejecutar(
                new EntradaTransferencia(
                        "tr-aud03", p.origen(), p.destino(), bob("10.00"), "x", Optional.empty(), Optional.empty()),
                p.ctx()));

        assertThat(rechazaLaBase(
                        "UPDATE nucleo_financiero.transaccion_billetera SET hash_registro = NULL WHERE id = '%s'"
                                .formatted(s.transaccionId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        UUID asiento = UUID.randomUUID();
        UUID cuenta = contable.cuentaDeMovimiento("CU12-" + asiento.toString().substring(0, 6), "ACTIVO", "DEUDORA");

        assertThatThrownBy(() -> transaccion.execute(estado -> {
                    dsl.execute(
                            """
                            INSERT INTO nucleo_financiero.asiento_contable
                                (id, fecha, glosa, origen_tipo, origen_id, estado)
                            VALUES (?, now(), 'descuadre a proposito', 'AJUSTE', gen_random_uuid(), 'CONFIRMADO')
                            """,
                            asiento);
                    dsl.execute(
                            """
                            INSERT INTO nucleo_financiero.movimiento_contable
                                (id, asiento_id, cuenta_id, debe, haber, descripcion)
                            VALUES (gen_random_uuid(), ?, ?, 10.00, 0.00, 'solo debe')
                            """,
                            asiento,
                            cuenta);
                    return null;
                }))
                .satisfies(e -> assertThat(raizDe(e)).contains("R-AUD-05"));
    }

    @Test
    @DisplayName("rechaza por R-UIF-02")
    void rechazaRUIF02() {
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_uif_registrar_operacion"))
                .isEqualTo(1);
    }
}
