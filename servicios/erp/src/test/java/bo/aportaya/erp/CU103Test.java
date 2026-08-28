package bo.aportaya.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.erp.aplicacion.CU100AbrirCerrarPeriodo.EntradaCierre;
import bo.aportaya.erp.aplicacion.CU103FacturaDeProveedor.EntradaFactura;
import bo.aportaya.erp.aplicacion.CU103FacturaDeProveedor.EntradaPago;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-103 · Registrar y pagar una factura de proveedor. */
class CU103Test extends EscenarioDeFactura {

    @Test
    @DisplayName(
            "Dada una factura_proveedor aprobada con saldo_pendiente > 0 · Cuando Tesorería registra un pago_a_proveedor por el saldo total · Entonces la factura pasa a estado PAGADA y queda su asiento contable enlazado")
    void criterio1() {
        var registrada = transaccion.execute(t -> facturaCU.registrar(factura("5000.00", enPeriodo(1)), aprobador));

        var pagada = transaccion.execute(t -> facturaCU.pagar(
                new EntradaPago(
                        registrada.facturaId(),
                        new BigDecimal("5000.00"),
                        "BOB",
                        "TRANSFERENCIA",
                        tesoreria.usuarioId()),
                tesoreria));

        assertThat(pagada.estado()).isEqualTo("PAGADA");
        assertThat(pagada.saldoPendiente()).isEqualByComparingTo("0.00");
        // HUECO: `factura_proveedor` es append-only, asi que su columna `estado` se
        // queda en APROBADA para siempre y `monto_pagado` en cero. El estado real se
        // deriva de la suma de sus pagos, que es donde esta el hecho.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.factura_proveedor f
                         WHERE f.id = ?
                           AND f.monto = (SELECT COALESCE(SUM(p.monto), 0) FROM erp.pago_a_proveedor p
                                           WHERE p.factura_proveedor_id = f.id)
                        """,
                        registrada.facturaId()))
                .isEqualTo(1);
        // HUECO: el asiento contable lo escribe nucleo-financiero (invariante 12); aca
        // se pide por evento y la columna `asiento_contable_id` la completa ese servicio.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.pago_a_proveedor_registrado' AND payload->>'facturaId' = ?
                        """,
                        registrada.facturaId().toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una factura aprobada por la usuaria Ana · Cuando Ana intenta autorizar también el pago de esa misma factura · Entonces el sistema devuelve MISMO_APROBADOR_Y_PAGADOR")
    void criterio2() {
        var registrada = transaccion.execute(t -> facturaCU.registrar(factura("5000.00", enPeriodo(1)), aprobador));

        // R-CTB-05. Una sola persona que aprueba y paga puede inventarse un proveedor,
        // aprobarle una factura y transferirse la plata.
        assertThatThrownBy(() -> transaccion.execute(t -> facturaCU.pagar(
                        new EntradaPago(
                                registrada.facturaId(),
                                new BigDecimal("5000.00"),
                                "BOB",
                                "TRANSFERENCIA",
                                aprobador.usuarioId()),
                        aprobador)))
                .hasMessageContaining("no puede ademas autorizar su pago");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.pago_a_proveedor WHERE factura_proveedor_id = ?",
                        registrada.facturaId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un período contable cerrado · Cuando se intenta registrar una factura con fecha de emisión dentro de ese período · Entonces el sistema devuelve PERIODO_CERRADO")
    void criterio3() {
        UUID enero = dsl.fetchOne(
                        "SELECT id FROM erp.periodo_contable WHERE ejercicio_fiscal_id = ? AND mes = 1", ejercicioId)
                .get(0, UUID.class);
        transaccion.execute(t -> periodoCU.cerrarPeriodo(new EntradaCierre(enero, "Cierre"), aprobador));

        // Una factura con fecha dentro de un periodo cerrado cambiaria un resultado ya
        // publicado.
        assertThatThrownBy(() ->
                        transaccion.execute(t -> facturaCU.registrar(factura("5000.00", enPeriodo(1)), aprobador)))
                .hasMessageContaining("esta cerrado");
        assertThat(contar("SELECT count(*)::int FROM erp.factura_proveedor WHERE tercero_comercial_id = ?", proveedor))
                .isZero();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        var entrada = factura("5000.00", enPeriodo(1));
        transaccion.execute(t -> facturaCU.registrar(entrada, aprobador));

        // uq_factura_proveedor_numero: la misma factura cargada dos veces se paga dos
        // veces.
        assertThatThrownBy(() -> transaccion.execute(t -> facturaCU.registrar(entrada, aprobador)))
                .hasMessageContaining("ya tiene registrada la factura");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.factura_proveedor WHERE tercero_comercial_id = ? AND numero_factura = ?",
                        proveedor,
                        entrada.numeroFactura()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        var registrada = transaccion.execute(t -> facturaCU.registrar(factura("5000.00", enPeriodo(1)), aprobador));
        var pago = new EntradaPago(
                registrada.facturaId(), new BigDecimal("5000.00"), "BOB", "TRANSFERENCIA", tesoreria.usuarioId());

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> facturaCU.pagar(pago, tesoreria));
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

        // El FOR UPDATE es lo que impide que los dos lean el mismo monto pagado y los
        // dos pasen: sin el, el error sale del sistema como dos transferencias.
        assertThat(errores).hasSize(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.pago_a_proveedor WHERE factura_proveedor_id = ?",
                        registrada.facturaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        var registrada = transaccion.execute(t -> facturaCU.registrar(factura("5000.00", enPeriodo(1)), aprobador));

        transaccion.execute(t -> facturaCU.pagar(
                new EntradaPago(
                        registrada.facturaId(),
                        new BigDecimal("2000.00"),
                        "BOB",
                        "TRANSFERENCIA",
                        tesoreria.usuarioId()),
                tesoreria));
        var segundo = transaccion.execute(t -> facturaCU.pagar(
                new EntradaPago(
                        registrada.facturaId(),
                        new BigDecimal("3000.00"),
                        "BOB",
                        "TRANSFERENCIA",
                        tesoreria.usuarioId()),
                tesoreria));

        // Lo pagado mas el saldo iguala el monto, al centavo. Si no cuadrara, la
        // diferencia la absorberia el proveedor sin enterarse.
        assertThat(segundo.montoPagado().add(segundo.saldoPendiente())).isEqualByComparingTo(segundo.monto());
        var suma = dsl.fetchOne(
                        "SELECT COALESCE(SUM(monto), 0) FROM erp.pago_a_proveedor WHERE factura_proveedor_id = ?",
                        registrada.facturaId())
                .get(0, BigDecimal.class);
        assertThat(suma).isEqualByComparingTo(segundo.montoPagado());
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        var registrada = transaccion.execute(t -> facturaCU.registrar(factura("5000.00", enPeriodo(1)), aprobador));
        var pago = new EntradaPago(
                registrada.facturaId(), new BigDecimal("5000.00"), "BOB", "TRANSFERENCIA", tesoreria.usuarioId());

        transaccion.execute(t -> facturaCU.pagar(pago, tesoreria));
        // El segundo pago del total llega tarde: la factura ya no tiene saldo, y pagarla
        // de nuevo seria transferir dos veces.
        assertThatThrownBy(() -> transaccion.execute(t -> facturaCU.pagar(pago, tesoreria)))
                .hasMessageContaining("excede el saldo");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.pago_a_proveedor WHERE factura_proveedor_id = ?",
                        registrada.facturaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: vencimiento anterior a la emision.
        LocalDate emision = enPeriodo(1);
        assertThatThrownBy(() -> transaccion.execute(t -> facturaCU.registrar(
                        new EntradaFactura(
                                proveedor,
                                null,
                                null,
                                "F-MAL-" + anio,
                                emision,
                                emision.minusDays(1),
                                new BigDecimal("100.00"),
                                "BOB",
                                null,
                                aprobador.usuarioId()),
                        aprobador)))
                .hasMessageContaining("vencer antes de emitirse");

        var registrada = transaccion.execute(t -> facturaCU.registrar(factura("5000.00", emision), aprobador));

        // Paso fallido: pagar una factura sin aprobar. La factura nace REGISTRADA
        // cuando no trae aprobador, y sin aprobacion no se paga.
        var sinAprobar = transaccion.execute(t -> facturaCU.registrar(factura("400.00", emision, null), aprobador));
        assertThatThrownBy(() -> transaccion.execute(t -> facturaCU.pagar(
                        new EntradaPago(
                                sinAprobar.facturaId(),
                                new BigDecimal("100.00"),
                                "BOB",
                                "TRANSFERENCIA",
                                tesoreria.usuarioId()),
                        tesoreria)))
                .hasMessageContaining("no esta aprobada");

        // Paso fallido: pagar mas que el saldo.
        assertThatThrownBy(() -> transaccion.execute(t -> facturaCU.pagar(
                        new EntradaPago(
                                registrada.facturaId(),
                                new BigDecimal("9000.00"),
                                "BOB",
                                "TRANSFERENCIA",
                                tesoreria.usuarioId()),
                        tesoreria)))
                .hasMessageContaining("excede el saldo");
        assertThat(contar(
                        "SELECT count(*)::int FROM erp.pago_a_proveedor WHERE factura_proveedor_id = ?",
                        registrada.facturaId()))
                .isZero();

        // Con el monto correcto y otro autorizante, el mismo camino cierra.
        var pagada = transaccion.execute(t -> facturaCU.pagar(
                new EntradaPago(
                        registrada.facturaId(),
                        new BigDecimal("5000.00"),
                        "BOB",
                        "TRANSFERENCIA",
                        tesoreria.usuarioId()),
                tesoreria));
        assertThat(pagada.estado()).isEqualTo("PAGADA");
    }
}
