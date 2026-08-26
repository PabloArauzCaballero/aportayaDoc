package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU24RegistrarAsiento.EntradaAsiento;
import bo.aportaya.nucleofinanciero.aplicacion.CU24RegistrarAsiento.SalidaAsiento;
import bo.aportaya.nucleofinanciero.dominio.OrigenAsiento;
import bo.aportaya.nucleofinanciero.dominio.Partida;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-24 · Registrar el asiento contable de una operación — el organismo completo, contra Postgres real. */
class CU24Test extends BaseDeCU24 {

    @Test
    @DisplayName(
            "Dada una entrega de fondo liquidada · Cuando se confirma el asiento · Entonces SUM(debe) = SUM(haber) para ese asiento")
    void criterio1() {
        Cuenta caja = cuenta("1.1.01", "ACTIVO", "DEUDORA");
        Cuenta ingreso = cuenta("4.1.01", "INGRESO", "ACREEDORA");

        SalidaAsiento salida = registrarComoSistema(caja, ingreso, new BigDecimal("150.00"));

        var totales = sumaDeMovimientos(salida.asientoId());
        assertThat(totales.get(0)).isEqualByComparingTo(totales.get(1));
        assertThat(salida.totalDebe().toString()).isEqualTo("150.00");
    }

    @Test
    @DisplayName(
            "Dada una corrección contable · Cuando se ejecuta · Entonces existe un asiento nuevo con asiento_reversa_id apuntando al original")
    void criterio3() {
        Cuenta caja = cuenta("1.1.06", "ACTIVO", "DEUDORA");
        Cuenta ingreso = cuenta("4.1.06", "INGRESO", "ACREEDORA");
        SalidaAsiento original = registrarComoSistema(caja, ingreso, new BigDecimal("60.00"));

        SalidaAsiento reversa = transaccion.execute(
                e -> registrar.reversar(dsl, original.asientoId(), "monto equivocado", comoSistema()));

        var fila = dsl.fetchOne(
                "SELECT asiento_reversa_id, estado FROM nucleo_financiero.asiento_contable WHERE id = ?",
                reversa.asientoId());
        assertThat((UUID) fila.get(0)).isEqualTo(original.asientoId());
        // R-AUD-11: el asiento que corrige es el que lleva el estado REVERSADO — el
        // original no se puede tocar, es append-only.
        assertThat((String) fila.get(1)).isEqualTo("REVERSADO");
        // La reversa intercambia debe y haber: el total que era debe en el original es
        // ahora haber, y viceversa — el saldo de las dos cuentas vuelve a cero.
        assertThat(fixtura.saldoDe(caja.id())).isEqualByComparingTo("0.00");
        assertThat(fixtura.saldoDe(ingreso.id())).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("cuadre: el saldo de cada cuenta se actualiza según su naturaleza, sin escribirlo directo")
    void actualizaSaldos() {
        Cuenta caja = cuenta("1.1.02", "ACTIVO", "DEUDORA");
        Cuenta ingreso = cuenta("4.1.02", "INGRESO", "ACREEDORA");

        registrarComoSistema(caja, ingreso, new BigDecimal("80.00"));

        assertThat(fixtura.saldoDe(caja.id())).isEqualByComparingTo("80.00");
        assertThat(fixtura.saldoDe(ingreso.id())).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("rechaza una cuenta que no está en el plan de cuentas, con AP-CU24-02")
    void cuentaInexistente() {
        Cuenta caja = cuenta("1.1.03", "ACTIVO", "DEUDORA");

        assertThatThrownBy(() -> transaccion.execute(e -> registrar.ejecutar(
                        dsl,
                        new EntradaAsiento(
                                OrigenAsiento.AJUSTE,
                                UUID.randomUUID(),
                                List.of(
                                        new Partida(caja.codigo(), new BigDecimal("10.00"), BigDecimal.ZERO),
                                        new Partida("CUENTA-QUE-NO-EXISTE", BigDecimal.ZERO, new BigDecimal("10.00"))),
                                "prueba con cuenta inexistente"),
                        comoSistema())))
                .isInstanceOf(ErrorDeNegocio.class)
                .satisfies(
                        e -> assertThat(((ErrorDeNegocio) e).codigo().valor()).isEqualTo("AP-CU24-02"));
    }

    @Test
    @DisplayName("rechaza un asiento descuadrado antes de tocar la base, con AP-CU24-01")
    void asientoDescuadrado() {
        Cuenta caja = cuenta("1.1.04", "ACTIVO", "DEUDORA");
        Cuenta ingreso = cuenta("4.1.04", "INGRESO", "ACREEDORA");

        assertThatThrownBy(() -> transaccion.execute(e -> registrar.ejecutar(
                        dsl,
                        new EntradaAsiento(
                                OrigenAsiento.AJUSTE,
                                UUID.randomUUID(),
                                List.of(
                                        new Partida(caja.codigo(), new BigDecimal("10.00"), BigDecimal.ZERO),
                                        new Partida(ingreso.codigo(), BigDecimal.ZERO, new BigDecimal("5.00"))),
                                "prueba descuadrada"),
                        comoSistema())))
                .isInstanceOf(ErrorDeNegocio.class)
                .satisfies(
                        e -> assertThat(((ErrorDeNegocio) e).codigo().valor()).isEqualTo("AP-CU24-01"));

        assertThat(fixtura.saldoDe(caja.id())).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("concurrencia: dos asientos a la vez sobre la misma cuenta no pierden ningún movimiento")
    void concurrencia() throws Exception {
        Cuenta caja = cuenta("1.1.05", "ACTIVO", "DEUDORA");
        Cuenta ingreso = cuenta("4.1.05", "INGRESO", "ACREEDORA");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<SalidaAsiento> uno = pool.submit(() -> registrarComoSistema(caja, ingreso, new BigDecimal("30.00")));
            Future<SalidaAsiento> dos = pool.submit(() -> registrarComoSistema(caja, ingreso, new BigDecimal("45.00")));
            uno.get();
            dos.get();
        } finally {
            pool.shutdown();
        }

        assertThat(fixtura.saldoDe(caja.id())).isEqualByComparingTo("75.00");
        assertThat(fixtura.saldoDe(ingreso.id())).isEqualByComparingTo("75.00");
    }

    private Cuenta cuenta(String prefijoCodigo, String tipo, String naturaleza) {
        // codigo es VARCHAR(20): un UUID entero no entra, un fragmento alcanza para que
        // no choque entre pruebas de la misma corrida.
        String codigo = prefijoCodigo + "." + UUID.randomUUID().toString().substring(0, 8);
        UUID id = fixtura.cuentaDeMovimiento(codigo, tipo, naturaleza);
        return new Cuenta(id, codigo);
    }

    private SalidaAsiento registrarComoSistema(Cuenta debe, Cuenta haber, BigDecimal monto) {
        return transaccion.execute(e -> registrar.ejecutar(
                dsl,
                new EntradaAsiento(
                        OrigenAsiento.AJUSTE,
                        UUID.randomUUID(),
                        List.of(
                                new Partida(debe.codigo(), monto, BigDecimal.ZERO),
                                new Partida(haber.codigo(), BigDecimal.ZERO, monto)),
                        "prueba CU-24"),
                comoSistema()));
    }

    private List<BigDecimal> sumaDeMovimientos(UUID asientoId) {
        var fila = dsl.fetchOne(
                "SELECT SUM(debe), SUM(haber) FROM nucleo_financiero.movimiento_contable WHERE asiento_id = ?",
                asientoId);
        return List.of((BigDecimal) fila.get(0), (BigDecimal) fila.get(1));
    }

    private record Cuenta(UUID id, String codigo) {}
}
