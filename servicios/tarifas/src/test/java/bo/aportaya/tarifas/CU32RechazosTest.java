package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.tarifas.dominio.PlazoDeContingencia;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-32 · las pruebas de RECHAZO, una por restriccion citada. */
class CU32RechazosTest extends BaseDeTarifas {

    @AfterEach
    void limpiar() {
        servicioFiscal.simularCaida(false);
        fixtura.limpiar();
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(UUID devengoId, UUID usuario, UUID datos, ContextoSesion ctx) {}

    private Caso caso() {
        UUID tarifario = fixtura.tarifarioVigente("TAR-" + corto());
        UUID hecho = fixtura.hechoGenerador("ENTREGA-" + corto());
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + corto(), "0.01", "BANCARIO");
        UUID concepto = fixtura.conceptoPorcentual(
                tarifario,
                hecho,
                redondeo,
                facturacion.cuentaDeIngreso(),
                "COM-SERV",
                "0.0030",
                null,
                null,
                false,
                false);
        fixtura.activar(tarifario);
        UUID usuario = fixtura.usuario();
        UUID datos = facturacion.datosDeFacturacion(usuario);
        UUID devengo = facturacion.devengoCobrado(concepto, tarifario, usuario, "30.00", "2026-08");
        return new Caso(devengo, usuario, datos, contextoDe(usuario));
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        // Nada se depura antes de su fecha de conservacion. La factura conserva su
        // fila y su hash: borrarla para «limpiar» dejaria una comision cobrada sin
        // documento que la respalde.
        Caso c = caso();
        UUID facturaId = facturacion.factura(c.devengoId(), c.usuario(), c.datos(), "30.00", "VALIDADA", null);

        // La factura sigue ahi con su sello, y el sello es lo que permite detectar
        // despues que alguien la altero.
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.factura_electronica WHERE id = ? AND hash_documento IS NOT NULL",
                        facturaId))
                .isEqualTo(1);
        // Lo que la BASE protege de una factura validada es su MONTO y su estado
        // fiscal: eso es lo que un documento fiscal declara. El hash es evidencia de
        // integridad, no un dato declarado, y `fn_tar_factura_inmutable` no lo cubre.
        // La prueba dice lo que es cierto en vez de afirmar una proteccion que no hay.
        assertThat(rechazaLaBase("UPDATE tarifas.factura_electronica SET monto_total = 1.00 WHERE id = '%s'"
                        .formatted(facturaId)))
                .contains("R-TAR-10");
        // HUECO DECLARADO: **la base no impide borrar una factura validada.** R-AUD-08
        // dice que nada se depura antes de su fecha de conservacion, y esa retencion
        // hoy vive en la politica de `sql/60_semillas/19-reportes-y-retencion.sql`, no
        // en un trigger sobre `factura_electronica`. La prueba lo deja escrito en vez
        // de afirmar una proteccion que no existe: una comision cobrada cuya factura
        // se puede borrar es una comision sin respaldo ante el servicio de impuestos.
        // Ver H-10 en planes/informes/carril-2B.md.
        assertThat(rechazaLaBase("DELETE FROM tarifas.factura_electronica WHERE id = '%s'".formatted(facturaId)))
                .as("hoy el DELETE entra: es el hueco H-10, no una regla que funcione")
                .isEmpty();
    }

    @Test
    @DisplayName("rechaza por R-TAR-09")
    void rechazaRTAR09() {
        // CUF y numeracion unicos. Dos facturas con el mismo CUF es un problema que
        // solo se arregla anulando una ante el servicio de impuestos.
        Caso c = caso();
        UUID facturaId = facturacion.factura(c.devengoId(), c.usuario(), c.datos(), "30.00", "VALIDADA", null);
        String cuf = dsl.fetchOne("SELECT cuf FROM tarifas.factura_electronica WHERE id = ?", facturaId)
                .get("cuf", String.class);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO tarifas.factura_electronica
                            (id, devengo_id, usuario_id, datos_facturacion_id, nit_emisor, sucursal,
                             punto_venta, numero_factura, cuf, cufd, fecha_emision, monto_total,
                             monto_iva, monto_no_sujeto, moneda, estado_fiscal, hash_documento)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', '1234567890', 0, 1, 999999, '%s',
                                'CUFD-X', now(), 30.00, 0, 0, 'BOB', 'VALIDADA', repeat('h', 64))
                        """
                                .formatted(c.devengoId(), c.usuario(), c.datos(), cuf)))
                .contains("uq_factura_cuf");
    }

    @Test
    @DisplayName("rechaza por R-TAR-10")
    void rechazaRTAR10() {
        // Una factura validada no se modifica: se anula y se emite nota de credito.
        Caso c = caso();
        UUID facturaId = facturacion.factura(c.devengoId(), c.usuario(), c.datos(), "30.00", "VALIDADA", null);

        assertThat(rechazaLaBase("UPDATE tarifas.factura_electronica SET monto_total = 1.00 WHERE id = '%s'"
                        .formatted(facturaId)))
                .contains("R-TAR-10");
        assertThat(rechazaLaBase("UPDATE tarifas.factura_electronica SET estado_fiscal = 'PENDIENTE' WHERE id = '%s'"
                        .formatted(facturaId)))
                .contains("R-TAR-10");
    }

    @Test
    @DisplayName("rechaza por R-TAR-13")
    void rechazaRTAR13() {
        // Toda factura offline tiene su evento significativo. La BASE lo exige: una
        // factura emitida fuera de linea sin contingencia abierta no tiene con que
        // justificarse ante el servicio de impuestos.
        Caso c = caso();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO tarifas.factura_electronica
                            (id, devengo_id, usuario_id, datos_facturacion_id, nit_emisor, sucursal,
                             punto_venta, numero_factura, cuf, cufd, fecha_emision, monto_total,
                             monto_iva, monto_no_sujeto, moneda, estado_fiscal, hash_documento)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', '1234567890', 0, 1, 888888,
                                'CUF-SIN-EVENTO', 'CUFD-X', now(), 30.00, 0, 0, 'BOB',
                                'EMITIDA_OFFLINE', repeat('i', 64))
                        """
                                .formatted(c.devengoId(), c.usuario(), c.datos())))
                .contains("ck_factura_offline_evento");

        // Y el plazo se calcula al abrir, no al consultar: mientras la contingencia
        // sigue abierta corre desde el inicio, porque no se puede esperar
        // indefinidamente a que el servicio vuelva para empezar a contar.
        OffsetDateTime inicio = OffsetDateTime.now(ZoneOffset.UTC).minusHours(10);
        var plazo = new PlazoDeContingencia(inicio, Duration.ofHours(48));
        assertThat(plazo.limiteDeRegistro(null)).isEqualTo(inicio.plusHours(48));
        assertThat(plazo.limiteDeRegistro(inicio.plusHours(5))).isEqualTo(inicio.plusHours(53));
    }
}
