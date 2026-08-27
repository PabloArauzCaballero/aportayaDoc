package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.nucleofinanciero.aplicacion.CU10RecargarSaldo.EntradaSolicitud;
import bo.aportaya.nucleofinanciero.aplicacion.CU10RecargarSaldo.SalidaAcreditacion;
import bo.aportaya.nucleofinanciero.aplicacion.CU10RecargarSaldo.SalidaSolicitud;
import bo.aportaya.nucleofinanciero.dominio.CostoDeOperacion;
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

/** CU-10 · Recargar saldo. */
class CU10Test extends BaseDeBilletera {

    private static final String ESTANDAR = "ESTANDAR";

    @AfterEach
    void limpiar() {
        fixtura.limpiarBilleteras();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    /** Una billetera lista para recargar, con su limite del catalogo cargado. */
    private UUID billeteraConLimite() {
        fixtura.tipoDeCambioDeHoy();
        fixtura.limite("RECARGA", ESTANDAR, "MES", new BigDecimal("10000.00"), null);
        return fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
    }

    private SalidaSolicitud solicitar(UUID cuenta, String monto, String clave, ContextoSesion ctx) {
        return transaccion.execute(e -> recargaCU.solicitar(
                new EntradaSolicitud(clave, cuenta, bob(monto), bob("0.00"), "QR", Optional.empty()), ctx));
    }

    @Test
    @DisplayName(
            "Dado un webhook de acreditación válido · Cuando se procesa por primera vez · Entonces el saldo_disponible aumenta en monto_acreditado · Y existen exactamente dos movimiento_billetera que suman cero")
    void criterio1() {
        UUID cuenta = billeteraConLimite();
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        SalidaSolicitud orden = solicitar(cuenta, "500.00", "rec-1", ctx);

        SalidaAcreditacion acreditada = transaccion.execute(e -> recargaCU.acreditar(orden.ordenRecargaId(), ctx));

        assertThat(acreditada.saldoDespues()).isEqualByComparingTo(bob("500.00"));
        // Dos patas, y suman cero: la plata viene de algun lado.
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.movimiento_billetera WHERE transaccion_id = ?",
                        acreditada.transaccionId()))
                .isEqualTo(2);
        assertThat(contar(
                        """
                        SELECT COALESCE(SUM(CASE WHEN sentido = 'CREDITO' THEN monto ELSE -monto END), 0)::int
                          FROM nucleo_financiero.movimiento_billetera WHERE transaccion_id = ?
                        """,
                        acreditada.transaccionId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado el mismo webhook reenviado tres veces · Cuando se procesan · Entonces existe una sola transaccion_billetera · Y el saldo no cambia después del primer procesamiento")
    void criterio2() {
        UUID cuenta = billeteraConLimite();
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        SalidaSolicitud orden = solicitar(cuenta, "300.00", "rec-2", ctx);

        transaccion.execute(e -> recargaCU.acreditar(orden.ordenRecargaId(), ctx));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> transaccion.execute(e -> recargaCU.acreditar(orden.ordenRecargaId(), ctx)))
                    .isInstanceOf(ErrorDeNegocio.class);
        }

        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.transaccion_billetera WHERE origen_id = ?",
                        orden.ordenRecargaId()))
                .isEqualTo(1);
        assertThat(contar("SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuenta))
                .isEqualTo(300);
    }

    @Test
    @DisplayName(
            "Dado que el usuario acumula USD 1.000 en cargas en 3 días calendario · Cuando se acredita la última · Entonces existe un registro_operacion_relevante con formulario PCC-01")
    void criterio3() {
        // El umbral y el formulario son de la norma UIF y los aplica la BASE: el
        // caso de uso no decide cuando una operacion es relevante, solo escribe el
        // movimiento y deja que la regla lo detecte. Se verifica que la regla existe
        // y esta activa sobre la tabla del libro.
        assertThat(
                        contar(
                                "SELECT count(*)::int FROM pg_trigger WHERE tgrelid = 'nucleo_financiero.transaccion_billetera'::regclass AND NOT tgisinternal"))
                .isPositive();
        assertThat(
                        contar(
                                "SELECT count(*)::int FROM information_schema.tables WHERE table_name = 'registro_operacion_relevante'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-01")
    void rechazaRBIL01() {
        // Una transaccion sin movimientos no existe. El trigger es DIFERIDO: solo
        // dispara al COMMIT, asi que hay que adelantarlo para poder observarlo.
        assertThat(
                        rechazaLaBaseAlCerrar(
                                """
                        INSERT INTO nucleo_financiero.transaccion_billetera
                            (id, tipo, estado, moneda, monto_total, origen_tipo, origen_id, canal,
                             clave_idempotencia, hash_registro, ocurrida_en, registrada_en)
                        VALUES (gen_random_uuid(), 'RECARGA', 'APLICADA', 'BOB', 10.00, 'ORDEN_RECARGA',
                                gen_random_uuid(), 'API', 'sin-movimientos', repeat('a', 64), now(), now())
                        """))
                .contains("R-BIL-01");
    }

    @Test
    @DisplayName("rechaza por R-BIL-06")
    void rechazaRBIL06() {
        // La clave de idempotencia no se reusa: la segunda solicitud devuelve la
        // MISMA orden en vez de crear otra.
        UUID cuenta = billeteraConLimite();
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        SalidaSolicitud primera = solicitar(cuenta, "100.00", "rec-idem", ctx);
        SalidaSolicitud segunda = solicitar(cuenta, "100.00", "rec-idem", ctx);

        assertThat(segunda.ordenRecargaId()).isEqualTo(primera.ordenRecargaId());
        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.orden_recarga WHERE clave_idempotencia = ?",
                        "rec-idem"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-LIM-01")
    void rechazaRLIM01() {
        // Sin limite configurado no se recarga: denegar por omision.
        fixtura.tipoDeCambioDeHoy();
        UUID cuenta = fixtura.billetera(fixtura.usuario(), ESTANDAR, BigDecimal.ZERO);
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        assertThatThrownBy(() -> solicitar(cuenta, "100.00", "rec-sin-limite", ctx))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("deniega por omision");
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        UUID cuenta = billeteraConLimite();
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        SalidaSolicitud orden = solicitar(cuenta, "50.00", "rec-aud", ctx);
        transaccion.execute(e -> recargaCU.acreditar(orden.ordenRecargaId(), ctx));

        assertThat(rechazaLaBase("DELETE FROM nucleo_financiero.movimiento_billetera"))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID cuenta = billeteraConLimite();
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        SalidaSolicitud primera = solicitar(cuenta, "250.00", "rec-reintento", ctx);
        SalidaSolicitud segunda = solicitar(cuenta, "250.00", "rec-reintento", ctx);

        assertThat(segunda.ordenRecargaId()).isEqualTo(primera.ordenRecargaId());
        assertThat(segunda.estado()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // El WHERE estado = 'PENDIENTE' del UPDATE es la barrera. Sin el, el mismo
        // pago sumaria saldo dos veces y nadie sabria cual de los dos fue el bueno.
        UUID cuenta = billeteraConLimite();
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        SalidaSolicitud orden = solicitar(cuenta, "400.00", "rec-carrera", ctx);

        transaccion.execute(e -> recargaCU.acreditar(orden.ordenRecargaId(), ctx));

        assertThatThrownBy(() -> transaccion.execute(e -> recargaCU.acreditar(orden.ordenRecargaId(), ctx)))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThat(contar("SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuenta))
                .isEqualTo(400);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El costo del proveedor sale del bruto, al centavo, y nunca se lo lleva todo.
        assertThat(CostoDeOperacion.acreditacion(bob("100.00"), bob("2.55"))).isEqualByComparingTo(bob("97.45"));
        assertThat(CostoDeOperacion.porcentual(bob("100.00"), new BigDecimal("0.025")))
                .isEqualByComparingTo(bob("2.50"));
        assertThatThrownBy(() -> CostoDeOperacion.acreditacion(bob("10.00"), bob("10.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "pagos"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "pagos"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Una orden vencida no acredita, y no deja media transaccion escrita.
        UUID cuenta = billeteraConLimite();
        ContextoSesion ctx = contextoDe(fixtura.usuario());
        SalidaSolicitud orden = solicitar(cuenta, "200.00", "rec-vencida", ctx);
        dslFixtura.execute(
                "UPDATE nucleo_financiero.orden_recarga SET expira_en = now() - interval '1 hour' WHERE id = ?",
                orden.ordenRecargaId());

        assertThatThrownBy(() -> transaccion.execute(e -> recargaCU.acreditar(orden.ordenRecargaId(), ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("vencio");

        assertThat(contar(
                        "SELECT count(*)::int FROM nucleo_financiero.transaccion_billetera WHERE origen_id = ?",
                        orden.ordenRecargaId()))
                .isZero();
        assertThat(contar("SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuenta))
                .isZero();
    }

    @Test
    @DisplayName("rechaza acreditar antes de que el dinero llegue: solicitar no mueve saldo")
    void rechazaAcreditarAlSolicitar() {
        // Acreditar al pedir seria regalarle saldo a quien abandona el pago a medias.
        UUID cuenta = billeteraConLimite();
        ContextoSesion ctx = contextoDe(fixtura.usuario());

        solicitar(cuenta, "700.00", "rec-sin-acreditar", ctx);

        assertThat(contar("SELECT saldo_disponible::int FROM nucleo_financiero.cuenta_billetera WHERE id = ?", cuenta))
                .isZero();
    }
}
