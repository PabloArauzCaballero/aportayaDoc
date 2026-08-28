package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/** CU-42 · Detectar umbral y registrar ROG. */
class CU42Test extends BaseDeCumplimiento {

    private UUID usuario;
    private UUID cuenta;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        usuario = fixtura.usuario();
        cuenta = uif.cuentaBilletera(usuario, "USD");
        ctx = ContextoSesion.de(
                usuario, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    private EntradaUmbral operacion(UUID umbralId, String concepto, String monto, String tipo, BigDecimal acumulado) {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID tx = uif.transaccionConUmbralApagado(umbralId, cuenta, monto, "USD", tipo, ahora);
        return new EntradaUmbral(
                usuario,
                tx,
                concepto,
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
                null,
                null);
    }

    @Test
    @DisplayName(
            "Dado un retiro en efectivo en moneda extranjera · Cuando se ejecuta · Entonces existe un registro_operacion_relevante con formulario ROG-01 sin importar el monto")
    void criterio1() {
        // ROG-01 tiene umbral CERO: la norma manda informarlo por su tipo, no por su
        // monto. Un retiro de un dolar entra igual que uno de diez mil.
        UUID umbral = uif.umbral("ROG-01", "EFECTIVO", false, "0.00", null);

        var salida = transaccion.execute(
                t -> rogCU.registrar(operacion(umbral, "EFECTIVO", "1.00", "RETIRO", BigDecimal.ZERO), ctx));

        assertThat(salida.registros()).hasSize(1);
        assertThat(salida.registros().get(0).formulario()).isEqualTo("ROG-01");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante
                         WHERE usuario_id = ? AND formulario = 'ROG-01'
                        """,
                        usuario))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dadas transferencias desde billetera que acumulan USD 1.000 en 3 días · Cuando ocurre la que alcanza el umbral · Entonces existe un registro con formulario ROG-03 y es_acumulada = true")
    void criterio2() {
        UUID umbral = uif.umbral("ROG-03", "TRANSFERENCIA_BILLETERA", true, "1000.00", 3);

        var salida = transaccion.execute(t -> rogCU.registrar(
                operacion(umbral, "TRANSFERENCIA_BILLETERA", "200.00", "TRANSFERENCIA_P2P", new BigDecimal("850.00")),
                ctx));

        assertThat(salida.registros()).hasSize(1);
        assertThat(salida.registros().get(0).formulario()).isEqualTo("ROG-03");
        assertThat(salida.registros().get(0).esAcumulada()).isTrue();
        assertThat(salida.registros().get(0).montoAcumuladoUsd()).isEqualByComparingTo("1050.00");
    }

    @Test
    @DisplayName(
            "Dada una operación que dispara PCC-01 y ROG-03 · Cuando se procesa · Entonces existen dos registros distintos")
    void criterio3() {
        // Dos obligaciones con articulos, plazos y formatos distintos. Fusionarlas
        // ahorraria una fila y perderia que son dos cosas.
        UUID rog = uif.umbral("ROG-03", "CARGA_BILLETERA", false, "1000.00", null);
        UUID pcc = uif.umbral("PCC-01", "CARGA_BILLETERA", true, "1000.00", 3);
        var entrada = operacion(pcc, "CARGA_BILLETERA", "1500.00", "RECARGA", BigDecimal.ZERO);
        uif.activarUmbral(rog);

        var conDeclaracion = new EntradaUmbral(
                entrada.usuarioId(),
                entrada.transaccionId(),
                entrada.concepto(),
                entrada.monto(),
                entrada.moneda(),
                entrada.tipoDeCambio(),
                entrada.acumuladoEnVentana(),
                entrada.inicioDeVentanaId(),
                entrada.ventanaDesde(),
                entrada.fecha(),
                entrada.ocurridaEn(),
                false,
                null,
                "SALARIO",
                "Compra de electrodomesticos");

        transaccion.execute(t -> pccCU.registrar(conDeclaracion, ctx));
        transaccion.execute(t -> rogCU.registrar(entrada, ctx));

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante WHERE transaccion_id = ?",
                        entrada.transaccionId()))
                .isEqualTo(2);
        assertThat(contar(
                        """
                        SELECT count(DISTINCT formulario)::int FROM cumplimiento.registro_operacion_relevante
                         WHERE transaccion_id = ?
                        """,
                        entrada.transaccionId()))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID umbral = uif.umbral("ROG-01", "EFECTIVO", false, "0.00", null);
        var entrada = operacion(umbral, "EFECTIVO", "500.00", "RETIRO", BigDecimal.ZERO);

        var a = transaccion.execute(t -> rogCU.registrar(entrada, ctx));
        var b = transaccion.execute(t -> rogCU.registrar(entrada, ctx));

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
        UUID umbral = uif.umbral("ROG-01", "EFECTIVO", false, "0.00", null);
        var entrada = operacion(umbral, "EFECTIVO", "500.00", "RETIRO", BigDecimal.ZERO);

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> rogCU.registrar(entrada, ctx));
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
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        UUID umbral = uif.umbral("ROG-01", "EFECTIVO", false, "0.00", null);
        var entrada = operacion(umbral, "EFECTIVO", "500.00", "RETIRO", BigDecimal.ZERO);
        transaccion.execute(t -> rogCU.registrar(entrada, ctx));

        // En USD el equivalente es el mismo monto y el tipo de cambio es 1: si no
        // cuadrara, el reporte diria otra cifra que la operacion.
        var fila = dsl.fetchOne(
                """
                SELECT monto, monto_equivalente_usd, tipo_cambio_aplicado
                  FROM cumplimiento.registro_operacion_relevante WHERE transaccion_id = ?
                """,
                entrada.transaccionId());
        assertThat(fila.get("monto_equivalente_usd", BigDecimal.class))
                .isEqualByComparingTo(fila.get("monto", BigDecimal.class));
        assertThat(fila.get("tipo_cambio_aplicado", BigDecimal.class)).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID umbral = uif.umbral("ROG-01", "EFECTIVO", false, "0.00", null);
        var entrada = operacion(umbral, "EFECTIVO", "500.00", "RETIRO", BigDecimal.ZERO);

        transaccion.execute(t -> rogCU.registrar(entrada, ctx));
        transaccion.execute(t -> rogCU.registrar(entrada, ctx));
        transaccion.execute(t -> rogCU.registrar(entrada, ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.uif_operacion_general' AND payload->>'usuarioId' = ?
                        """,
                        usuario.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        UUID umbral = uif.umbral("ROG-01", "EFECTIVO", false, "0.00", null);
        var base = operacion(umbral, "EFECTIVO", "500.00", "RETIRO", BigDecimal.ZERO);

        // Paso fallido: moneda distinta de dolares sin cotizacion.
        assertThatThrownBy(() -> transaccion.execute(t -> rogCU.registrar(
                        new EntradaUmbral(
                                usuario,
                                base.transaccionId(),
                                "EFECTIVO",
                                new BigDecimal("500.00"),
                                "BOB",
                                null,
                                BigDecimal.ZERO,
                                null,
                                base.ventanaDesde(),
                                base.fecha(),
                                base.ocurridaEn(),
                                false,
                                null,
                                null,
                                null),
                        ctx)))
                .hasMessageContaining("tipo de cambio");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante WHERE transaccion_id = ?",
                        base.transaccionId()))
                .isZero();

        // Operativa propia: sin titular no hay a quien atribuirle la operacion, y a
        // diferencia del PCC-01 el ROG no falla, simplemente no registra.
        var sinTitular = transaccion.execute(t -> rogCU.registrar(
                new EntradaUmbral(
                        null,
                        base.transaccionId(),
                        "EFECTIVO",
                        new BigDecimal("500.00"),
                        "USD",
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        null,
                        base.ventanaDesde(),
                        base.fecha(),
                        base.ocurridaEn(),
                        false,
                        null,
                        null,
                        null),
                ctx));
        assertThat(sinTitular.registros()).isEmpty();

        // Con todo en orden, el mismo camino cierra.
        var buena = transaccion.execute(t -> rogCU.registrar(base, ctx));
        assertThat(buena.registros()).hasSize(1);
    }
}
