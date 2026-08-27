package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo.EntradaRetencion;
import bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo.SalidaCierre;
import bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo.SalidaRetencion;
import bo.aportaya.nucleofinanciero.dominio.VigenciaDeRetencion;
import bo.aportaya.nucleofinanciero.dominio.VigenciaDeRetencion.Motivo;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-13 · Retener y liberar saldo. */
class CU13Test extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    /** Una billetera con saldo puesto por el camino real: un movimiento de credito. */
    private UUID billeteraCon(String saldo, UUID usuario) {
        UUID cuenta = fixtura.billetera(usuario, ESTANDAR, BigDecimal.ZERO);
        fixtura.acreditar(cuenta, new BigDecimal(saldo));
        return cuenta;
    }

    @Test
    @DisplayName(
            "Dada una cuenta con Bs 1.000 disponibles · Cuando se retienen Bs 400 · Entonces saldo_disponible es 600 y saldo_retenido es 400 · Y saldo_total sigue siendo 1.000")
    void criterio1() {
        UUID usuario = fixtura.usuario();
        UUID cuenta = billeteraCon("1000.00", usuario);
        ContextoSesion ctx = contextoDe(usuario);

        SalidaRetencion salida = transaccion.execute(
                e -> retencionCU.retener(EntradaRetencion.simple(cuenta, bob("400.00"), "APORTE_PROGRAMADO"), ctx));

        assertThat(salida.saldoDisponible()).isEqualByComparingTo(bob("600.00"));
        assertThat(salida.saldoRetenido()).isEqualByComparingTo(bob("400.00"));
        // El total no se mueve: retener no gasta, aparta.
        assertThat(contar("SELECT saldo_total::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuenta))
                .isEqualTo(1000);
    }

    @Test
    @DisplayName(
            "Dada una retención vencida y no ejecutada · Cuando corre el proceso diario · Entonces queda LIBERADA y el saldo vuelve a disponible")
    void criterio2() {
        UUID usuario = fixtura.usuario();
        UUID cuenta = billeteraCon("1000.00", usuario);
        ContextoSesion ctx = contextoDe(usuario);
        SalidaRetencion retencion = transaccion.execute(
                e -> retencionCU.retener(EntradaRetencion.simple(cuenta, bob("300.00"), "DISPUTA"), ctx));

        // Se la manda al pasado: retener sin fin es plata ajena inmovilizada porque
        // un proceso se olvido de soltarla.
        dslFixtura.execute(
                "UPDATE nucleo_financiero.retencion_saldo SET expira_en = now() - interval '1 hour' WHERE id = ?",
                retencion.retencionId());

        int vencidas = transaccion.execute(e -> retencionCU.vencerCaducadas(ctx));

        assertThat(vencidas).isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.retencion_saldo WHERE id = ? AND estado = 'LIBERADA'",
                        retencion.retencionId()))
                .isEqualTo(1);
        assertThat(contar("SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuenta))
                .isEqualTo(1000);
    }

    @Test
    @DisplayName(
            "Dado un intento de crear una retención sin expira_en y motivo distinto de ORDEN_AUTORIDAD · Cuando se inserta · Entonces la base de datos la rechaza (R-BIL-08)")
    void criterio3() {
        UUID usuario = fixtura.usuario();
        UUID cuenta = billeteraCon("500.00", usuario);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.retencion_saldo
                            (id, cuenta_billetera_id, motivo, monto, estado, expira_en, creada_en)
                        VALUES (gen_random_uuid(), '%s', 'DISPUTA', 10.00, 'VIGENTE', NULL, now())
                        """
                                .formatted(cuenta)))
                .contains("ck_retencion_expira");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Cerrar dos veces la misma retencion no devuelve la plata dos veces: el
        // segundo intento choca con el estado, no con un saldo ya movido.
        UUID usuario = fixtura.usuario();
        UUID cuenta = billeteraCon("1000.00", usuario);
        ContextoSesion ctx = contextoDe(usuario);
        SalidaRetencion retencion = transaccion.execute(
                e -> retencionCU.retener(EntradaRetencion.simple(cuenta, bob("400.00"), "DISPUTA"), ctx));

        SalidaCierre primera = transaccion.execute(e -> retencionCU.liberar(retencion.retencionId(), ctx));

        assertThat(primera.saldoDisponible()).isEqualByComparingTo(bob("1000.00"));
        assertThatThrownBy(() -> transaccion.execute(e -> retencionCU.liberar(retencion.retencionId(), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya esta LIBERADA");
        assertThat(contar("SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuenta))
                .isEqualTo(1000);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // El WHERE estado = 'VIGENTE' del UPDATE es la barrera: liberar y ejecutar
        // la misma retencion, y solo una gana.
        UUID usuario = fixtura.usuario();
        UUID cuenta = billeteraCon("1000.00", usuario);
        ContextoSesion ctx = contextoDe(usuario);
        SalidaRetencion retencion = transaccion.execute(
                e -> retencionCU.retener(EntradaRetencion.simple(cuenta, bob("400.00"), "ENTREGA_EN_CURSO"), ctx));

        transaccion.execute(e -> retencionCU.ejecutar(retencion.retencionId(), ctx));

        assertThatThrownBy(() -> transaccion.execute(e -> retencionCU.liberar(retencion.retencionId(), ctx)))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.retencion_saldo WHERE id = ? AND estado = 'EJECUTADA'",
                        retencion.retencionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Disponible + retenido = total, siempre y al centavo, pase lo que pase.
        UUID usuario = fixtura.usuario();
        UUID cuenta = billeteraCon("1000.00", usuario);
        ContextoSesion ctx = contextoDe(usuario);

        transaccion.execute(e -> retencionCU.retener(EntradaRetencion.simple(cuenta, bob("333.33"), "DISPUTA"), ctx));
        transaccion.execute(e -> retencionCU.retener(EntradaRetencion.simple(cuenta, bob("333.34"), "DISPUTA"), ctx));

        var fila = dsl.fetchOne(
                "SELECT saldo_disponible, saldo_retenido, saldo_total FROM nucleo_financiero.cuenta_billetera WHERE id = ?",
                cuenta);
        BigDecimal disponible = fila.get(0, BigDecimal.class);
        BigDecimal retenido = fila.get(1, BigDecimal.class);
        BigDecimal total = fila.get(2, BigDecimal.class);

        assertThat(disponible.add(retenido)).isEqualByComparingTo(total);
        assertThat(total).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(retenido).isEqualByComparingTo(new BigDecimal("666.67"));
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
        // Retener mas de lo disponible aborta ANTES de escribir: no puede quedar una
        // retencion que deje el disponible en negativo.
        UUID usuario = fixtura.usuario();
        UUID cuenta = billeteraCon("100.00", usuario);
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() -> transaccion.execute(
                        e -> retencionCU.retener(EntradaRetencion.simple(cuenta, bob("100.01"), "DISPUTA"), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("No hay disponible");

        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.retencion_saldo WHERE cuenta_billetera_id = ?",
                        cuenta))
                .isZero();
        assertThat(contar("SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuenta))
                .isEqualTo(100);
    }

    @Test
    @DisplayName("rechaza una retencion sin fin: solo la orden de autoridad puede no vencer")
    void rechazaRetencionSinFin() {
        OffsetDateTime ahora = OffsetDateTime.of(2026, 8, 26, 12, 0, 0, 0, ZoneOffset.UTC);

        // Sin fecha pedida y motivo comun: se calcula por politica, nunca queda vacia.
        assertThat(VigenciaDeRetencion.resolver(Motivo.DISPUTA, Optional.empty(), 30, ahora))
                .isPresent();
        // Orden de autoridad: la levanta el mismo que la puso.
        assertThat(VigenciaDeRetencion.resolver(Motivo.ORDEN_AUTORIDAD, Optional.empty(), 30, ahora))
                .isEmpty();
        // Y una fecha en el pasado no es un vencimiento, es un error.
        assertThatThrownBy(
                        () -> VigenciaDeRetencion.resolver(Motivo.DISPUTA, Optional.of(ahora.minusDays(1)), 30, ahora))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
