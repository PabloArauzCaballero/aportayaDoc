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

/** CU-41 · Lo que la base y el caso de uso rechazan. */
class CU41RechazosTest extends BaseDeCumplimiento {

    private UUID usuario;
    private UUID cuenta;
    private UUID umbralId;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        usuario = fixtura.usuario();
        cuenta = uif.cuentaBilletera(usuario, "USD");
        umbralId = uif.umbral("PCC-01", "CARGA_BILLETERA", true, "1000.00", 3);
        ctx = ContextoSesion.de(
                usuario, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    private EntradaUmbral carga(String monto, String origen, String destino) {
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID tx = uif.transaccionConUmbralApagado(umbralId, cuenta, monto, "USD", "RECARGA", ahora);
        return new EntradaUmbral(
                usuario,
                tx,
                "CARGA_BILLETERA",
                new BigDecimal(monto),
                "USD",
                BigDecimal.ONE,
                BigDecimal.ZERO,
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
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // `registro_operacion_relevante` es append-only, y de ahi sale el hueco mas
        // grande de este carril: la fila no se puede completar despues, asi que nace
        // entera o no nace.
        var salida = transaccion.execute(
                t -> pccCU.registrar(carga("1500.00", "SALARIO", "Compra de electrodomesticos"), ctx));
        UUID registroId = salida.registros().get(0).registroId();

        assertThat(rechazaLaBase(
                        "UPDATE cumplimiento.registro_operacion_relevante SET origen_declarado = 'OTRO' WHERE id = ?",
                        registroId))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM cumplimiento.registro_operacion_relevante WHERE id = ?", registroId))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-UIF-01")
    void rechazaRUIF01() {
        // Un umbral sin base normativa no se puede defender ante nadie: la cita es lo
        // que convierte una cifra en una obligacion.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO catalogo.umbral_reporte_uif
                            (formulario, inciso, concepto_operacion, es_acumulado, umbral_usd,
                             ventana_dias_calendario, exige_declaracion_origen_destino, reinicia_tras_superar,
                             base_normativa, vigente_desde, activo)
                        VALUES ('PCC-01', 'z', 'GIRO', false, 1000, NULL, true, true, '   ', current_date, false)
                        """))
                .contains("ck_umbral_base_normativa");

        // Y un umbral acumulado sin ventana no se puede medir: «acumulado en cuanto».
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO catalogo.umbral_reporte_uif
                            (formulario, inciso, concepto_operacion, es_acumulado, umbral_usd,
                             ventana_dias_calendario, exige_declaracion_origen_destino, reinicia_tras_superar,
                             base_normativa, vigente_desde, activo)
                        VALUES ('PCC-01', 'z', 'GIRO', true, 1000, NULL, true, true, 'Art. 52', current_date, false)
                        """))
                .contains("ck_umbral_ventana");
    }

    @Test
    @DisplayName("rechaza por R-UIF-02")
    void rechazaRUIF02() {
        // **El motor de la boveda no puede escribir un PCC-01.**
        // `fn_uif_registrar_operacion` inserta con exento=false y sin origen ni destino,
        // y `ck_operelev_declaracion` rechaza exactamente esa fila. Como el disparador
        // es diferido sobre `movimiento_billetera`, aborta la recarga entera. Es el
        // hueco H-1 del carril, y aca queda demostrado con el rechazo en la mano.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.registro_operacion_relevante
                            (usuario_id, transaccion_id, umbral_reporte_id, formulario, concepto_operacion,
                             es_acumulada, monto, moneda, tipo_cambio_aplicado, umbral_aplicado_usd,
                             exento, periodo_remision, fecha_operacion)
                        SELECT ?, t.id, ?, 'PCC-01', 'CARGA_BILLETERA', false, 1500.00, 'USD', 1, 1000.00,
                               false, to_char(now(), 'YYYY-MM'), now()
                          FROM nucleo_financiero.transaccion_billetera t LIMIT 1
                        """,
                        usuario,
                        umbralId))
                .contains("ck_operelev_declaracion");

        // Lo que si entra es la fila COMPLETA, que es como la escribe este caso de uso.
        var salida = transaccion.execute(
                t -> pccCU.registrar(carga("1500.00", "SALARIO", "Compra de electrodomesticos"), ctx));
        assertThat(salida.registros()).hasSize(1);
    }

    @Test
    @DisplayName("rechaza por R-UIF-03")
    void rechazaRUIF03() {
        // Un registro acumulado sin ventana no dice de que periodo habla.
        var salida = transaccion.execute(
                t -> pccCU.registrar(carga("1500.00", "SALARIO", "Compra de electrodomesticos"), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante
                         WHERE id = ? AND es_acumulada = true AND ventana_desde IS NOT NULL
                           AND ventana_hasta IS NOT NULL
                        """,
                        salida.registros().get(0).registroId()))
                .isEqualTo(1);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.registro_operacion_relevante
                            (usuario_id, transaccion_id, umbral_reporte_id, formulario, concepto_operacion,
                             es_acumulada, monto, moneda, tipo_cambio_aplicado, umbral_aplicado_usd,
                             exento, motivo_exencion, periodo_remision, fecha_operacion)
                        SELECT ?, t.id, ?, 'PCC-01', 'CARGA_BILLETERA', true, 1500.00, 'USD', 1, 1000.00,
                               true, 'Exenta', to_char(now(), 'YYYY-MM'), now()
                          FROM nucleo_financiero.transaccion_billetera t LIMIT 1
                        """,
                        usuario,
                        umbralId))
                .contains("ck_operelev_ventana");
    }

    @Test
    @DisplayName("rechaza por R-UIF-04")
    void rechazaRUIF04() {
        // Sin tipo de cambio no hay conversion reproducible: el mismo registro releido
        // el año que viene daria otro monto.
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);
        UUID cuentaBob = uif.cuentaBilletera(usuario, "BOB");
        uif.tipoDeCambio("BOB", "0.143678");
        UUID tx = uif.transaccionConUmbralApagado(umbralId, cuentaBob, "6960.00", "BOB", "RECARGA", ahora);

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
                                "SALARIO",
                                "Compra"),
                        ctx)))
                .hasMessageContaining("tipo de cambio");

        // Y la base tampoco admite una fila en moneda extranjera con tipo de cambio cero.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.registro_operacion_relevante
                            (usuario_id, transaccion_id, umbral_reporte_id, formulario, concepto_operacion,
                             es_acumulada, monto, moneda, tipo_cambio_aplicado, umbral_aplicado_usd,
                             exento, motivo_exencion, periodo_remision, fecha_operacion)
                        SELECT ?, t.id, ?, 'ROG-03', 'ELECTRONICA', false, 1500.00, 'BOB', 0, 1000.00,
                               true, 'Exenta', to_char(now(), 'YYYY-MM'), now()
                          FROM nucleo_financiero.transaccion_billetera t LIMIT 1
                        """,
                        usuario,
                        umbralId))
                .contains("ck_operelev_tipo_cambio");
    }

    @Test
    @DisplayName("rechaza por R-UIF-05")
    void rechazaRUIF05() {
        // Cada registro pertenece a un periodo de remision, con formato fijo: sin el,
        // el envio mensual no sabria que llevar.
        var salida = transaccion.execute(
                t -> pccCU.registrar(carga("1500.00", "SALARIO", "Compra de electrodomesticos"), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.registro_operacion_relevante
                         WHERE id = ? AND periodo_remision ~ '^\\d{4}-\\d{2}$'
                        """,
                        salida.registros().get(0).registroId()))
                .isEqualTo(1);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.registro_operacion_relevante
                            (usuario_id, transaccion_id, umbral_reporte_id, formulario, concepto_operacion,
                             es_acumulada, monto, moneda, tipo_cambio_aplicado, umbral_aplicado_usd,
                             exento, motivo_exencion, periodo_remision, fecha_operacion)
                        SELECT ?, t.id, ?, 'ROG-03', 'ELECTRONICA', false, 1500.00, 'USD', 1, 1000.00,
                               true, 'Exenta', 'agosto ', now()
                          FROM nucleo_financiero.transaccion_billetera t LIMIT 1
                        """,
                        usuario,
                        umbralId))
                .contains("ck_operelev_periodo");
    }
}
