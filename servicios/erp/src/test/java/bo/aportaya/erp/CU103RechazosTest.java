package bo.aportaya.erp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.erp.aplicacion.CU103FacturaDeProveedor.EntradaPago;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-103 · Lo que la base y el caso de uso rechazan. */
class CU103RechazosTest extends EscenarioDeFactura {

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        var registrada = transaccion.execute(t -> facturaCU.registrar(
                factura("F-A01-" + anio, "1200.00", enPeriodo(1), aprobador.usuarioId()), aprobador));

        // La factura es append-only: ni su monto ni su aprobacion se reescriben, y no
        // se borra. Una factura editable no prueba nada de lo que se pago.
        assertThat(rechazaLaBase("UPDATE erp.factura_proveedor SET monto = 1 WHERE id = ?", registrada.facturaId()))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM erp.factura_proveedor WHERE id = ?", registrada.facturaId()))
                .contains("R-AUD-01");

        var pago = transaccion.execute(t -> facturaCU.pagar(
                new EntradaPago(
                        registrada.facturaId(),
                        new BigDecimal("400.00"),
                        "BOB",
                        "TRANSFERENCIA",
                        tesoreria.usuarioId()),
                tesoreria));
        assertThat(pago.montoPagado()).isEqualByComparingTo("400.00");
        assertThat(rechazaLaBase(
                        "DELETE FROM erp.pago_a_proveedor WHERE factura_proveedor_id = ?", registrada.facturaId()))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        var registrada = transaccion.execute(t -> facturaCU.registrar(
                factura("F-A05-" + anio, "800.00", enPeriodo(1), aprobador.usuarioId()), aprobador));
        transaccion.execute(t -> facturaCU.pagar(
                new EntradaPago(
                        registrada.facturaId(),
                        new BigDecimal("800.00"),
                        "BOB",
                        "TRANSFERENCIA",
                        tesoreria.usuarioId()),
                tesoreria));

        // Alta y pago dejan su evento en la misma transaccion que la fila: el rastro no
        // depende de que despues alguien se acuerde de escribirlo.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.evento_dominio
                         WHERE tipo = 'erp.factura_proveedor_registrada' AND agregado_id = ?
                            OR tipo = 'erp.pago_a_proveedor_registrado' AND payload->>'facturaId' = ?
                        """,
                        registrada.facturaId(),
                        registrada.facturaId().toString()))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("rechaza por R-AUD-06")
    void rechazaRAUD06() {
        var registrada = transaccion.execute(t -> facturaCU.registrar(
                factura("F-A06-" + anio, "500.00", enPeriodo(1), aprobador.usuarioId()), aprobador));
        transaccion.execute(t -> facturaCU.pagar(
                new EntradaPago(
                        registrada.facturaId(),
                        new BigDecimal("500.00"),
                        "BOB",
                        "TRANSFERENCIA",
                        tesoreria.usuarioId()),
                tesoreria));

        // Quien aprobo y quien autorizo quedan escritos, y son personas distintas.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM erp.factura_proveedor f
                          JOIN erp.pago_a_proveedor p ON p.factura_proveedor_id = f.id
                         WHERE f.id = ? AND f.aprobada_por IS NOT NULL
                           AND p.autorizado_por IS NOT NULL AND p.autorizado_por <> f.aprobada_por
                        """,
                        registrada.facturaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-CTB-01")
    void rechazaRCTB01() {
        // Una factura fechada en un periodo cerrado no entra: el mes ya se cuadro y
        // firmo, y meterle un gasto despues invalida esa firma.
        LocalDate enero = enPeriodo(1);
        UUID periodoEnero = idDelPeriodo(1);
        transaccion.execute(t -> periodoCU.cerrarPeriodo(
                new bo.aportaya.erp.aplicacion.CU100AbrirCerrarPeriodo.EntradaCierre(periodoEnero, "Cierre"),
                aprobador));

        assertThatThrownBy(() -> transaccion.execute(t -> facturaCU.registrar(
                        factura("F-C01-" + anio, "300.00", enero, aprobador.usuarioId()), aprobador)))
                .satisfies(e -> assertThat(raizDe(e)).contains("periodo 1 de la fecha de emision esta cerrado"));
    }

    @Test
    @DisplayName("rechaza por R-CTB-04")
    void rechazaRCTB04() {
        String numero = "F-C04-" + anio;
        transaccion.execute(
                t -> facturaCU.registrar(factura(numero, "1000.00", enPeriodo(1), aprobador.usuarioId()), aprobador));

        // Un proveedor no emite dos veces el mismo numero de factura; si aparece dos
        // veces, una de las dos es un pago repetido esperando ocurrir.
        assertThatThrownBy(() -> transaccion.execute(t -> facturaCU.registrar(
                        factura(numero, "1000.00", enPeriodo(1), aprobador.usuarioId()), aprobador)))
                .satisfies(e -> assertThat(raizDe(e)).contains("ya tiene registrada la factura"));

        // El vencimiento no puede ser anterior a la emision.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO erp.factura_proveedor
                            (tercero_comercial_id, numero_factura, fecha_emision, fecha_vencimiento,
                             monto, moneda, monto_pagado, estado, aprobada_por)
                        VALUES (?, ?, current_date, current_date - 5, 100, 'BOB', 0, 'APROBADA', ?)
                        """,
                        proveedor,
                        "F-C04B-" + anio,
                        aprobador.usuarioId()))
                .contains("ck_factura_proveedor_vencimiento");

        // Y el saldo nunca queda negativo: no se paga mas de lo facturado.
        var registrada = transaccion.execute(t -> facturaCU.registrar(
                factura("F-C04C-" + anio, "600.00", enPeriodo(1), aprobador.usuarioId()), aprobador));
        assertThatThrownBy(() -> transaccion.execute(t -> facturaCU.pagar(
                        new EntradaPago(
                                registrada.facturaId(),
                                new BigDecimal("700.00"),
                                "BOB",
                                "TRANSFERENCIA",
                                tesoreria.usuarioId()),
                        tesoreria)))
                .satisfies(e -> assertThat(raizDe(e)).contains("excede el saldo pendiente"));
    }

    @Test
    @DisplayName("rechaza por R-CTB-05")
    void rechazaRCTB05() {
        var registrada = transaccion.execute(t -> facturaCU.registrar(
                factura("F-C05-" + anio, "1500.00", enPeriodo(1), aprobador.usuarioId()), aprobador));

        // Cuatro ojos sobre el egreso: quien aprobo la factura no autoriza su pago. Con
        // uno solo, aprobar y pagarse a si mismo es un unico movimiento de raton.
        assertThatThrownBy(() -> transaccion.execute(t -> facturaCU.pagar(
                        new EntradaPago(
                                registrada.facturaId(),
                                new BigDecimal("1500.00"),
                                "BOB",
                                "TRANSFERENCIA",
                                aprobador.usuarioId()),
                        aprobador)))
                .satisfies(e -> assertThat(raizDe(e)).contains("R-CTB-05"));
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // La misma segregacion, vista desde la base y no desde el caso de uso: el
        // trigger la sostiene aunque alguien escriba el pago por fuera.
        var registrada = transaccion.execute(t -> facturaCU.registrar(
                factura("F-S04-" + anio, "2000.00", enPeriodo(1), aprobador.usuarioId()), aprobador));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO erp.pago_a_proveedor
                            (factura_proveedor_id, monto, moneda, forma_pago, fecha_pago, autorizado_por)
                        VALUES (?, 2000, 'BOB', 'TRANSFERENCIA', current_date, ?)
                        """,
                        registrada.facturaId(),
                        aprobador.usuarioId()))
                .contains("R-CTB-05");
    }
}
