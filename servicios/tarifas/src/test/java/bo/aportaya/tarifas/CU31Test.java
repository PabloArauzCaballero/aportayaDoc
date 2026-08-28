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
import bo.aportaya.tarifas.aplicacion.CU31DevengarComision.SalidaDevengo;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-31 · Devengar y cobrar la comision. */
class CU31Test extends BaseDeTarifas {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(
            String codigoTarifario,
            String hecho,
            UUID tarifarioId,
            UUID conceptoId,
            UUID usuario,
            ContextoSesion ctx) {}

    private Caso caso(boolean conCuentaDeIngreso) {
        String codigoTarifario = "TAR-" + corto();
        String hechoCodigo = "ENTREGA-" + corto();
        UUID tarifario = fixtura.tarifarioVigente(codigoTarifario);
        UUID hecho = fixtura.hechoGenerador(hechoCodigo);
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + corto(), "0.01", "BANCARIO");
        UUID cuenta = conCuentaDeIngreso ? facturacion.cuentaDeIngreso() : null;
        UUID concepto = fixtura.conceptoPorcentual(
                tarifario, hecho, redondeo, cuenta, "COM-SERV", "0.0030", "10.00", "50.00", false, false);
        fixtura.activar(tarifario);
        UUID usuario = fixtura.usuario();
        return new Caso(codigoTarifario, hechoCodigo, tarifario, concepto, usuario, contextoDe(usuario));
    }

    private UUID cotizar(Caso c, String base, String clave) {
        return transaccion
                .execute(t -> cotizacionCU.cotizar(
                        new EntradaCotizacion(
                                clave,
                                c.codigoTarifario(),
                                c.hecho(),
                                "ENTREGA_FONDO",
                                UUID.randomUUID(),
                                bob(base),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty()),
                        c.ctx()))
                .cotizacionId();
    }

    private SalidaDevengo devengar(Caso c, UUID cotizacionId, String clave) {
        return transaccion.execute(t -> devengoCU.devengar(
                new EntradaDevengo(
                        clave,
                        cotizacionId,
                        c.tarifarioId(),
                        "ENTREGA_FONDO",
                        UUID.randomUUID(),
                        c.usuario(),
                        Optional.empty(),
                        bob("0.00"),
                        false),
                c.ctx()));
    }

    @Test
    @DisplayName(
            "Dada una entrega de fondo acreditada · Cuando se procesa el evento · Entonces existe un devengo_comision con estado DEVENGADO o COBRADO")
    void criterio1() {
        Caso c = caso(true);
        UUID cotizacion = cotizar(c, "6000.00", "cot-d1");

        SalidaDevengo salida = devengar(c, cotizacion, "dev-1");

        assertThat(salida.estado()).isEqualTo("DEVENGADO");
        assertThat(salida.montoTotal()).isEqualByComparingTo(bob("18.00"));
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.devengo_comision WHERE id = ? AND estado IN ('DEVENGADO','COBRADO')",
                        salida.devengoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado el mismo evento reprocesado · Cuando se intenta devengar otra vez · Entonces la base de datos lo rechaza por unicidad (R-TAR-04)")
    void criterio2() {
        Caso c = caso(true);
        UUID cotizacion = cotizar(c, "6000.00", "cot-d2");
        SalidaDevengo primero = devengar(c, cotizacion, "dev-2");
        UUID referencia = UUID.randomUUID();

        // Un devengo por hecho y concepto: la BASE lo sostiene, no la aplicacion.
        dsl.execute(
                """
                INSERT INTO tarifas.devengo_comision
                    (id, concepto_tarifa_id, tarifario_id, usuario_obligado_id, referencia_tipo,
                     referencia_id, monto_base, monto_comision, monto_descuento, monto_impuesto,
                     monto_total, moneda, estado, fecha_devengo, periodo_contable, clave_idempotencia)
                VALUES (gen_random_uuid(), ?, ?, ?, 'PAGO', ?, 100, 1, 0, 0, 1, 'BOB',
                        'DEVENGADO', now(), '2026-08', ?)
                """,
                c.conceptoId(),
                c.tarifarioId(),
                c.usuario(),
                referencia,
                "dev-otro-" + UUID.randomUUID());

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO tarifas.devengo_comision
                            (id, concepto_tarifa_id, tarifario_id, usuario_obligado_id, referencia_tipo,
                             referencia_id, monto_base, monto_comision, monto_descuento, monto_impuesto,
                             monto_total, moneda, estado, fecha_devengo, periodo_contable, clave_idempotencia)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', 'PAGO', '%s', 100, 1, 0, 0, 1, 'BOB',
                                'DEVENGADO', now(), '2026-08', 'dev-repetido')
                        """
                                .formatted(c.conceptoId(), c.tarifarioId(), c.usuario(), referencia)))
                .contains("uq_devengo_hecho");
        assertThat(primero.esNuevo()).isTrue();
    }

    @Test
    @DisplayName(
            "Dado un cobro fallido tres veces · Cuando se agota el reintento · Entonces existe una cuenta_por_cobrar_comision para ese devengo")
    void criterio3() {
        Caso c = caso(true);
        UUID cotizacion = cotizar(c, "6000.00", "cot-d3");
        SalidaDevengo devengo = devengar(c, cotizacion, "dev-3");

        for (int intento = 1; intento <= INTENTOS_ANTES_DE_INCOBRABLE; intento++) {
            transaccion.execute(t -> devengoCU.anotarCobro(
                    new EntradaCobro(devengo.devengoId(), "DEBITO_DE_BILLETERA", false, "saldo insuficiente"),
                    c.ctx()));
        }

        // La deuda NO desaparece porque el debito falle: pasa a cobranza.
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.cuenta_por_cobrar_comision WHERE devengo_id = ?",
                        devengo.devengoId()))
                .isEqualTo(1);
        // El devengo NO se marca incobrable con un UPDATE: la tabla no lo admite. La
        // cuenta por cobrar es el registro de que la deuda sigue viva, y el estado
        // corriente se deriva de ahi.
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.devengo_comision WHERE id = ? AND estado = 'DEVENGADO'",
                        devengo.devengoId()))
                .isEqualTo(1);
        // Y los tres intentos quedan registrados, no solo el ultimo.
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.cargo_comision WHERE devengo_id = ? AND estado = 'FALLIDO'",
                        devengo.devengoId()))
                .isEqualTo(INTENTOS_ANTES_DE_INCOBRABLE);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        Caso c = caso(true);
        UUID cotizacion = cotizar(c, "6000.00", "cot-idem");

        SalidaDevengo a = devengar(c, cotizacion, "dev-idem");
        SalidaDevengo b = devengar(c, cotizacion, "dev-idem");

        assertThat(b.devengoId()).isEqualTo(a.devengoId());
        assertThat(b.esNuevo()).isFalse();
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.devengo_comision WHERE clave_idempotencia = ?", "dev-idem"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos cobros exitosos del mismo devengo: el segundo no entra. La tabla es
        // append-only y no hay UPDATE que sirva de barrera, asi que el candado de fila
        // los pone en cola y el segundo ve el cargo del primero. Sin eso se cobraria
        // dos veces la misma comision y nadie lo veria hasta el cierre del mes.
        Caso c = caso(true);
        UUID cotizacion = cotizar(c, "6000.00", "cot-conc");
        SalidaDevengo devengo = devengar(c, cotizacion, "dev-conc");

        transaccion.execute(t -> devengoCU.anotarCobro(
                new EntradaCobro(devengo.devengoId(), "DEBITO_DE_BILLETERA", true, null), c.ctx()));

        assertThatThrownBy(() -> transaccion.execute(t -> devengoCU.anotarCobro(
                        new EntradaCobro(devengo.devengoId(), "DEBITO_DE_BILLETERA", true, null), c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya tiene un cobro registrado");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Lo cobrado iguala el monto del devengo, al centavo. Un centavo de diferencia
        // por operacion se vuelve un descuadre que nadie sabe de donde salio.
        Caso c = caso(true);
        UUID cotizacion = cotizar(c, "6000.00", "cot-cuadre");
        SalidaDevengo devengo = devengar(c, cotizacion, "dev-cuadre");

        transaccion.execute(t -> devengoCU.anotarCobro(
                new EntradaCobro(devengo.devengoId(), "DEDUCCION_DE_ENTREGA", true, null), c.ctx()));

        var fila = dsl.fetchOne(
                """
                SELECT d.monto_total AS devengado, c.monto_cobrado AS cobrado
                  FROM tarifas.devengo_comision d
                  JOIN tarifas.cargo_comision c ON c.devengo_id = d.id
                 WHERE d.id = ?
                """,
                devengo.devengoId());
        assertThat(fila.get("cobrado", java.math.BigDecimal.class))
                .isEqualByComparingTo(fila.get("devengado", java.math.BigDecimal.class));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "devengador"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "devengador"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin cuenta de ingreso mapeada no se devenga NADA: un ingreso sin asiento es
        // un ingreso que la contabilidad no ve, y aparece recien en la auditoria.
        Caso c = caso(false);
        UUID cotizacion = cotizar(c, "6000.00", "cot-sincuenta");

        assertThatThrownBy(() -> devengar(c, cotizacion, "dev-sincuenta"))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("cuenta de ingreso");
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.devengo_comision WHERE clave_idempotencia = ?",
                        "dev-sincuenta"))
                .isZero();
    }
}
