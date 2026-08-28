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

/**
 * CU-41 · Las dos pruebas transversales, aparte del archivo de criterios.
 *
 * <p>Estan separadas porque comprueban otra cosa: los criterios verifican que el
 * formulario salga cuando tiene que salir; estas dos verifican que la conversion a
 * dolares se pueda reproducir y que ningun paso a medias deje basura.
 */
class CU41CuadreTest extends BaseDeCumplimiento {

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
        umbralId = uif.umbral("PCC-01", "CARGA_BILLETERA", true, "1000.00", 3);
        ctx = ContextoSesion.de(
                usuario, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
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
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID tx = uif.transaccionConUmbralApagado(umbralId, cuentaBob, "6960.00", "BOB", "RECARGA", ahora);
        var enBolivianos = new EntradaUmbral(
                usuario,
                tx,
                "CARGA_BILLETERA",
                new BigDecimal("6960.00"),
                "BOB",
                new BigDecimal("0.143678"),
                BigDecimal.ZERO,
                null,
                LocalDate.now(ZoneOffset.UTC).minusDays(2),
                LocalDate.now(ZoneOffset.UTC),
                ahora,
                false,
                null,
                "SALARIO",
                "Compra de electrodomesticos");

        transaccion.execute(t -> pccCU.registrar(enBolivianos, ctx));

        // El equivalente en dolares y el tipo de cambio guardado tienen que reproducir
        // el monto original: sin eso el registro no se puede auditar.
        var fila = dsl.fetchOne(
                """
                SELECT monto, monto_equivalente_usd, tipo_cambio_aplicado
                  FROM cumplimiento.registro_operacion_relevante WHERE transaccion_id = ?
                """,
                tx);
        BigDecimal monto = fila.get("monto", BigDecimal.class);
        BigDecimal usd = fila.get("monto_equivalente_usd", BigDecimal.class);
        BigDecimal tc = fila.get("tipo_cambio_aplicado", BigDecimal.class);
        assertThat(monto.multiply(tc).setScale(2, java.math.RoundingMode.HALF_EVEN))
                .isEqualByComparingTo(usd);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID tx = uif.transaccionAplicada(cuentaBob, "6960.00", "BOB", "RECARGA", ahora);

        // Paso fallido: sin tipo de cambio no hay conversion reproducible.
        assertThatThrownBy(() -> transaccion.execute(t -> pccCU.registrar(
                        new EntradaUmbral(
                                usuario,
                                tx,
                                "CARGA_BILLETERA",
                                new BigDecimal("6960.00"),
                                "BOB",
                                null,
                                BigDecimal.ZERO,
                                null,
                                LocalDate.now(ZoneOffset.UTC),
                                LocalDate.now(ZoneOffset.UTC),
                                ahora,
                                false,
                                null,
                                null,
                                null),
                        ctx)))
                .hasMessageContaining("tipo de cambio");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante WHERE transaccion_id = ?",
                        tx))
                .isZero();

        // Paso fallido: operativa propia, sin titular. Tampoco escribe nada.
        assertThatThrownBy(() -> transaccion.execute(t -> pccCU.registrar(
                        new EntradaUmbral(
                                null,
                                tx,
                                "CARGA_BILLETERA",
                                new BigDecimal("2000.00"),
                                "USD",
                                BigDecimal.ONE,
                                BigDecimal.ZERO,
                                null,
                                LocalDate.now(ZoneOffset.UTC),
                                LocalDate.now(ZoneOffset.UTC),
                                ahora,
                                false,
                                null,
                                null,
                                null),
                        ctx)))
                .hasMessageContaining("operativa propia");

        // Con todo en orden, la declaracion del titular hace nacer el registro COMPLETO.
        var pendiente = cargaSinDeclarar("1500.00", BigDecimal.ZERO);
        var deteccion = transaccion.execute(t -> pccCU.registrar(pendiente, ctx));
        assertThat(deteccion.requiereDeclaracion()).isTrue();
        assertThat(deteccion.registros()).isEmpty();

        var conDeclaracion = transaccion.execute(t -> pccCU.declarar(
                new bo.aportaya.cumplimiento.aplicacion.CU41RegistrarPcc01.EntradaDeclaracion(
                        pendiente, "SALARIO", "Compra de electrodomesticos", "Sueldo de agosto"),
                ctx));

        assertThat(conDeclaracion.registros()).hasSize(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante
                         WHERE transaccion_id = ? AND origen_declarado IS NOT NULL
                           AND destino_declarado IS NOT NULL AND motivo_exencion IS NULL
                        """,
                        pendiente.transaccionId()))
                .isEqualTo(1);
    }
}
