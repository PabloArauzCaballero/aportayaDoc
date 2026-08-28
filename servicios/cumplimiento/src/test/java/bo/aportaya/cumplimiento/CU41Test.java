package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.cumplimiento.aplicacion.CU41RegistrarPcc01.EntradaUmbral;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-41 · Detectar umbral y registrar el formulario PCC-01. */
class CU41Test extends BaseDeCumplimiento {

    private UUID usuario;
    private UUID cuenta;
    private UUID cuentaBob;
    private UUID umbralId;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        usuario = fixtura.usuario();
        cuenta = uif.cuentaBilletera(usuario, "USD");
        cuentaBob = uif.cuentaBilletera(usuario, "BOB");
        uif.tipoDeCambio("BOB", "0.143678");
        // El umbral de la semilla: carga de billetera, USD 1.000 acumulados en 3 dias.
        umbralId = uif.umbral("PCC-01", "CARGA_BILLETERA", true, "1000.00", 3);
        ctx = ContextoSesion.de(
                usuario, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    /** Una carga con la declaracion del titular ya tomada: el registro nace completo. */
    private EntradaUmbral carga(String monto, BigDecimal acumulado) {
        return carga(monto, acumulado, "SALARIO", "Compra de electrodomesticos");
    }

    /** Una carga sin declaracion: solo se detecta el umbral y se la pide. */
    private EntradaUmbral cargaSinDeclarar(String monto, BigDecimal acumulado) {
        return carga(monto, acumulado, null, null);
    }

    private EntradaUmbral carga(String monto, BigDecimal acumulado, String origen, String destino) {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID tx = uif.transaccionConUmbralApagado(umbralId, cuenta, monto, "USD", "RECARGA", ahora);
        return new EntradaUmbral(
                usuario,
                tx,
                "CARGA_BILLETERA",
                new BigDecimal(monto),
                "USD",
                BigDecimal.ONE,
                acumulado,
                null,
                LocalDate.now(ZoneOffset.UTC).minusDays(2),
                LocalDate.now(ZoneOffset.UTC),
                ahora,
                false,
                null,
                origen,
                destino);
    }

    @Test
    @DisplayName(
            "Dado el umbral de carga de billetera acumulada de USD 1.000 en 3 días · Y un usuario que carga USD 400, USD 300 y USD 350 en tres días consecutivos · Cuando se acredita la tercera carga · Entonces existe un registro_operacion_relevante con formulario PCC-01 · Y monto_acumulado_ventana es USD 1.050 · Y se declara origen y destino solo de la tercera operación")
    void criterio1() {
        // Las dos primeras no alcanzan: 400 y 700 acumulados quedan bajo los 1.000.
        var primera = transaccion.execute(t -> pccCU.registrar(carga("400.00", BigDecimal.ZERO), ctx));
        var segunda = transaccion.execute(t -> pccCU.registrar(carga("300.00", new BigDecimal("400.00")), ctx));
        assertThat(primera.registros()).isEmpty();
        assertThat(segunda.registros()).isEmpty();

        // La tercera lleva el acumulado a 1.050 y dispara el formulario.
        var tercera = transaccion.execute(t -> pccCU.registrar(carga("350.00", new BigDecimal("700.00")), ctx));

        assertThat(tercera.registros()).hasSize(1);
        assertThat(tercera.registros().get(0).formulario()).isEqualTo("PCC-01");
        assertThat(tercera.registros().get(0).montoAcumuladoUsd()).isEqualByComparingTo("1050.00");
        // Con la declaracion ya tomada el registro nace completo y no queda pendiente.
        assertThat(tercera.requiereDeclaracion()).isFalse();
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante
                         WHERE usuario_id = ? AND origen_declarado IS NOT NULL
                        """,
                        usuario))
                .isEqualTo(1);
        // Solo una fila: la declaracion se pide por la operacion que alcanza el umbral,
        // no por las tres.
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante WHERE usuario_id = ?",
                        usuario))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado que ya se alcanzó el umbral · Cuando ocurre la operación siguiente · Entonces esa operación inicia una ventana nueva")
    void criterio2() {
        transaccion.execute(t -> pccCU.registrar(carga("1000.00", BigDecimal.ZERO), ctx));

        // R-UIF-03: la ventana reinicia. El acumulado que llega es el de la ventana
        // nueva —cero—, no el arrastre de la anterior.
        var siguiente = transaccion.execute(t -> pccCU.registrar(carga("100.00", BigDecimal.ZERO), ctx));

        assertThat(siguiente.registros()).isEmpty();
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante WHERE usuario_id = ?",
                        usuario))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un pago de servicios básicos · Cuando alcanza el umbral · Entonces el registro queda con exento = true y su motivo")
    void criterio3() {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID tx = uif.transaccionConUmbralApagado(umbralId, cuenta, "1200.00", "USD", "RECARGA", ahora);
        var exenta = new EntradaUmbral(
                usuario,
                tx,
                "CARGA_BILLETERA",
                new BigDecimal("1200.00"),
                "USD",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                null,
                LocalDate.now(ZoneOffset.UTC).minusDays(2),
                LocalDate.now(ZoneOffset.UTC),
                ahora,
                true,
                "Pago de servicios basicos",
                null,
                null);

        var salida = transaccion.execute(t -> pccCU.registrar(exenta, ctx));

        assertThat(salida.registros()).hasSize(1);
        // Queda registrada IGUAL: no registrarla seria no poder demostrar despues por
        // que no se reporto.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante
                         WHERE usuario_id = ? AND exento = true AND motivo_exencion = 'Pago de servicios basicos'
                        """,
                        usuario))
                .isEqualTo(1);
        assertThat(salida.requiereDeclaracion()).isFalse();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        var entrada = carga("1500.00", BigDecimal.ZERO);

        var a = transaccion.execute(t -> pccCU.registrar(entrada, ctx));
        var b = transaccion.execute(t -> pccCU.registrar(entrada, ctx));

        // R-UIF-13: un registro por transaccion y umbral. La red duplica; el reporte a
        // la UIF no puede duplicarse con ella.
        assertThat(b.registros().get(0).registroId())
                .isEqualTo(a.registros().get(0).registroId());
        assertThat(b.registros().get(0).esNuevo()).isFalse();
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante WHERE transaccion_id = ?",
                        entrada.transaccionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        var entrada = carga("1500.00", BigDecimal.ZERO);

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> pccCU.registrar(entrada, ctx));
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

        assertThat(errores).hasSizeLessThanOrEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante WHERE transaccion_id = ?",
                        entrada.transaccionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        var entrada = carga("1500.00", BigDecimal.ZERO);
        transaccion.execute(t -> pccCU.registrar(entrada, ctx));
        transaccion.execute(t -> pccCU.registrar(entrada, ctx));
        transaccion.execute(t -> pccCU.registrar(entrada, ctx));

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante WHERE transaccion_id = ?",
                        entrada.transaccionId()))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.uif_umbral_alcanzado' AND payload->>'usuarioId' = ?
                        """,
                        usuario.toString()))
                .isEqualTo(1);
    }
}
