package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.tarifas.aplicacion.CU30CotizarComision.EntradaCotizacion;
import bo.aportaya.tarifas.aplicacion.CU31DevengarComision.EntradaCobro;
import bo.aportaya.tarifas.aplicacion.CU31DevengarComision.EntradaDevengo;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-31 · las pruebas de RECHAZO, una por restriccion citada. */
class CU31RechazosTest extends BaseDeTarifas {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private String codigoCorto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(
            String codigoTarifario,
            String hecho,
            UUID tarifarioId,
            UUID conceptoId,
            UUID usuario,
            ContextoSesion ctx) {}

    private Caso caso() {
        String codigoTarifario = "TAR-" + codigoCorto();
        String hechoCodigo = "ENTREGA-" + codigoCorto();
        UUID tarifario = fixtura.tarifarioVigente(codigoTarifario);
        UUID hecho = fixtura.hechoGenerador(hechoCodigo);
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + codigoCorto(), "0.01", "BANCARIO");
        UUID concepto = fixtura.conceptoPorcentual(
                tarifario,
                hecho,
                redondeo,
                fixtura.cuentaDeIngreso(),
                "COM-SERV",
                "0.0030",
                "10.00",
                "50.00",
                false,
                false);
        fixtura.activar(tarifario);
        UUID usuario = fixtura.usuario();
        return new Caso(codigoTarifario, hechoCodigo, tarifario, concepto, usuario, contextoDe(usuario));
    }

    private UUID devengar(Caso c, String claveCotizacion, String claveDevengo) {
        UUID cotizacion = transaccion
                .execute(t -> cotizacionCU.cotizar(
                        new EntradaCotizacion(
                                claveCotizacion,
                                c.codigoTarifario(),
                                c.hecho(),
                                "ENTREGA_FONDO",
                                UUID.randomUUID(),
                                bob("6000.00"),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()),
                        c.ctx()))
                .cotizacionId();
        return transaccion
                .execute(t -> devengoCU.devengar(
                        new EntradaDevengo(
                                claveDevengo,
                                cotizacion,
                                c.tarifarioId(),
                                "ENTREGA_FONDO",
                                UUID.randomUUID(),
                                c.usuario(),
                                Optional.empty(),
                                bob("0.00"),
                                false),
                        c.ctx()))
                .devengoId();
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El devengo es append-only: los montos de un ingreso ya reconocido no se
        // editan. Corregirlos en el lugar borraria la prueba de cuanto se gano.
        Caso c = caso();
        UUID devengoId = devengar(c, "cot-aud", "dev-aud");

        assertThat(rechazaLaBase("UPDATE tarifas.devengo_comision SET monto_comision = 1.00 WHERE id = '%s'"
                        .formatted(devengoId)))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // Sin cuenta de ingreso mapeada no hay asiento posible, y sin asiento el
        // ingreso no cuadra contra nada. Se rechaza antes de escribir.
        String codigoTarifario = "TAR-" + codigoCorto();
        UUID tarifario = fixtura.tarifarioVigente(codigoTarifario);
        String hechoCodigo = "ENTREGA-" + codigoCorto();
        UUID hecho = fixtura.hechoGenerador(hechoCodigo);
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + codigoCorto(), "0.01", "BANCARIO");
        fixtura.conceptoPorcentual(tarifario, hecho, redondeo, null, "SIN-CUENTA", "0.0030", null, null, false, false);
        fixtura.activar(tarifario);
        UUID usuario = fixtura.usuario();
        ContextoSesion ctx = contextoDe(usuario);
        UUID cotizacion = transaccion
                .execute(t -> cotizacionCU.cotizar(
                        new EntradaCotizacion(
                                "cot-sc",
                                codigoTarifario,
                                hechoCodigo,
                                "ENTREGA_FONDO",
                                UUID.randomUUID(),
                                bob("6000.00"),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()),
                        ctx))
                .cotizacionId();

        assertThatThrownBy(() -> transaccion.execute(t -> devengoCU.devengar(
                        new EntradaDevengo(
                                "dev-sc",
                                cotizacion,
                                tarifario,
                                "ENTREGA_FONDO",
                                UUID.randomUUID(),
                                usuario,
                                Optional.empty(),
                                bob("0.00"),
                                false),
                        ctx)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("cuenta de ingreso");
        assertThat(contar("SELECT count(*)::int FROM tarifas.devengo_comision WHERE clave_idempotencia = ?", "dev-sc"))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-TAR-04")
    void rechazaRTAR04() {
        // Un devengo por hecho y concepto. Dos devengos del mismo hecho cobran dos
        // veces la misma comision.
        Caso c = caso();
        UUID referencia = UUID.randomUUID();
        String insertar =
                """
                INSERT INTO tarifas.devengo_comision
                    (id, concepto_tarifa_id, tarifario_id, usuario_obligado_id, referencia_tipo,
                     referencia_id, monto_base, monto_comision, monto_descuento, monto_impuesto,
                     monto_total, moneda, estado, fecha_devengo, periodo_contable, clave_idempotencia)
                VALUES (gen_random_uuid(), '%s', '%s', '%s', 'PAGO', '%s', 100, 1, 0, 0, 1, 'BOB',
                        'DEVENGADO', now(), '2026-08', '%s')
                """;
        dsl.execute(insertar.formatted(c.conceptoId(), c.tarifarioId(), c.usuario(), referencia, "clave-a"));

        assertThat(rechazaLaBase(
                        insertar.formatted(c.conceptoId(), c.tarifarioId(), c.usuario(), referencia, "clave-b")))
                .contains("uq_devengo_hecho");
    }

    @Test
    @DisplayName("rechaza por R-TAR-05")
    void rechazaRTAR05() {
        // La clave de idempotencia es unica por grupo: el mismo evento reprocesado no
        // devenga dos veces, y la BASE lo sostiene aunque la aplicacion se equivoque.
        Caso c = caso();
        String insertar =
                """
                INSERT INTO tarifas.devengo_comision
                    (id, concepto_tarifa_id, tarifario_id, usuario_obligado_id, referencia_tipo,
                     referencia_id, monto_base, monto_comision, monto_descuento, monto_impuesto,
                     monto_total, moneda, estado, fecha_devengo, periodo_contable, clave_idempotencia)
                VALUES (gen_random_uuid(), '%s', '%s', '%s', 'PAGO', gen_random_uuid(), 100, 1, 0, 0, 1,
                        'BOB', 'DEVENGADO', now(), '2026-08', 'clave-repetida')
                """;
        dsl.execute(insertar.formatted(c.conceptoId(), c.tarifarioId(), c.usuario()));

        assertThat(rechazaLaBase(insertar.formatted(c.conceptoId(), c.tarifarioId(), c.usuario())))
                .contains("uq_devengo_idem");
    }

    @Test
    @DisplayName("rechaza por R-TAR-06")
    void rechazaRTAR06() {
        // Una deduccion de entrega respalda UN solo cargo. Dos cargos sobre la misma
        // deduccion cobran dos veces del mismo descuento.
        Caso c = caso();
        UUID devengoId = devengar(c, "cot-ded", "dev-ded");
        UUID deduccion = fixtura.deduccionDeEntrega(fixtura.grupo());
        String insertar =
                """
                INSERT INTO tarifas.cargo_comision
                    (id, devengo_id, deduccion_entrega_id, forma_cobro, monto_cobrado, moneda,
                     estado, intentos, cobrado_en)
                VALUES (gen_random_uuid(), '%s', '%s', 'DEDUCCION_DE_ENTREGA', 18.00, 'BOB',
                        'COBRADO', 1, now())
                """;
        dsl.execute(insertar.formatted(devengoId, deduccion));

        assertThat(rechazaLaBase(insertar.formatted(devengoId, deduccion))).contains("uq_cargo_deduccion");
    }

    @Test
    @DisplayName("rechaza por R-TAR-11")
    void rechazaRTAR11() {
        // No se devuelve mas de lo cobrado, y lo impide la BASE
        // (tg_devolucion_maxima), no solo la aplicacion.
        Caso c = caso();
        UUID devengoId = devengar(c, "cot-dev", "dev-dev");
        transaccion.execute(
                t -> devengoCU.anotarCobro(new EntradaCobro(devengoId, "DEDUCCION_DE_ENTREGA", true, null), c.ctx()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO tarifas.devolucion_comision
                            (id, devengo_id, autorizada_por, motivo, detalle, monto_devuelto, forma,
                             estado, solicitada_en, ejecutada_en)
                        VALUES (gen_random_uuid(), '%s', '%s', 'ERROR_DE_TARIFA', 'de mas', 25.00,
                                'ABONO_BILLETERA', 'EJECUTADA', now(), now())
                        """
                                .formatted(devengoId, c.usuario())))
                .contains("R-TAR-11");
    }
}
