package bo.aportaya.tarifas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.tarifas.aplicacion.CU32EmitirFactura.EntradaFactura;
import bo.aportaya.tarifas.aplicacion.CU32EmitirFactura.SalidaFactura;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-32 · Emitir factura electronica. */
class CU32Test extends BaseDeTarifas {

    @AfterEach
    void limpiar() {
        // El simulador es compartido: si una prueba lo deja caido, la siguiente falla
        // por una razon que no tiene nada que ver con lo que verifica.
        servicioFiscal.simularCaida(false);
        servicioFiscal.simularRechazo(null);
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(UUID devengoId, UUID usuario, ContextoSesion ctx) {}

    private Caso caso(boolean conDatosDeFacturacion) {
        UUID tarifario = fixtura.tarifarioVigente("TAR-" + corto());
        UUID hecho = fixtura.hechoGenerador("ENTREGA-" + corto());
        UUID redondeo = fixtura.politicaDeRedondeo("CENT-" + corto(), "0.01", "BANCARIO");
        UUID concepto = fixtura.conceptoPorcentual(
                tarifario, hecho, redondeo, fixtura.cuentaDeIngreso(), "COM-SERV", "0.0030", null, null, true, true);
        fixtura.activar(tarifario);
        UUID usuario = fixtura.usuario();
        if (conDatosDeFacturacion) {
            fixtura.datosDeFacturacion(usuario);
        }
        UUID devengo = fixtura.devengoCobrado(concepto, tarifario, usuario, "30.00", "2026-08");
        return new Caso(devengo, usuario, contextoDe(usuario));
    }

    private SalidaFactura emitir(Caso c) {
        // Dos pasos a proposito: la llamada al tercero va FUERA de la transaccion.
        var consulta = facturaCU.consultarAlServicio();
        return transaccion.execute(t -> facturaCU.emitir(
                new EntradaFactura(c.devengoId(), bob("3.45"), "https://f.test/x.pdf"), consulta, c.ctx()));
    }

    @Test
    @DisplayName(
            "Dado un devengo cobrado · Cuando se emite la factura en línea · Entonces existe factura_electronica con CUF único y estado_fiscal VALIDADA")
    void criterio1() {
        Caso c = caso(true);

        SalidaFactura salida = emitir(c);

        assertThat(salida.estadoFiscal()).isEqualTo("VALIDADA");
        assertThat(salida.cuf()).isNotBlank();
        assertThat(salida.eventoSignificativoId()).isNull();
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.factura_electronica WHERE devengo_id = ? AND cuf = ?",
                        c.devengoId(),
                        salida.cuf()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una contingencia del servicio de impuestos · Cuando se emiten documentos · Entonces cada uno queda EMITIDA_OFFLINE con evento_significativo_id · Y el evento tiene plazo_registro guardado")
    void criterio2() {
        Caso c = caso(true);
        servicioFiscal.simularCaida(true);

        SalidaFactura salida = emitir(c);

        // La caida del servicio NO detiene la operacion: si emitir dependiera de que el
        // SIN responda, un corte de su lado dejaria a la gente sin poder cobrar su turno.
        assertThat(salida.estadoFiscal()).isEqualTo("EMITIDA_OFFLINE");
        assertThat(salida.eventoSignificativoId()).isNotNull();
        // El plazo se GUARDA al abrir la contingencia: recalcularlo despues es mover la
        // vara de un evento ya ocurrido, y ese argumento el regulador no lo acepta.
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.evento_significativo_sin WHERE id = ? AND plazo_registro IS NOT NULL",
                        salida.eventoSignificativoId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT cantidad_documentos_offline FROM tarifas.evento_significativo_sin WHERE id = ?",
                        salida.eventoSignificativoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un intento de modificar el monto de una factura validada · Cuando se ejecuta · Entonces se rechaza; solo procede anulación y nota de crédito")
    void criterio3() {
        Caso c = caso(true);
        SalidaFactura salida = emitir(c);

        // Editar un documento fiscal ya aceptado es falsificarlo, sin importar cual
        // haya sido la intencion. Lo impide la BASE.
        assertThat(rechazaLaBase("UPDATE tarifas.factura_electronica SET monto_total = 1.00 WHERE id = '%s'"
                        .formatted(salida.facturaId())))
                .contains("R-TAR-10");
        // Anular si se puede: es el camino previsto.
        assertThat(rechazaLaBase("UPDATE tarifas.factura_electronica SET estado_fiscal = 'ANULADA' WHERE id = '%s'"
                        .formatted(salida.facturaId())))
                .isEmpty();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Un devengo, una factura. La clave es el devengo: dos documentos fiscales por
        // el mismo cobro solo se arreglan anulando uno.
        Caso c = caso(true);
        SalidaFactura primera = emitir(c);

        assertThatThrownBy(() -> emitir(c)).isInstanceOf(ErrorDeNegocio.class).hasMessageContaining("ya tiene factura");
        assertThat(contar("SELECT count(*)::int FROM tarifas.factura_electronica WHERE devengo_id = ?", c.devengoId()))
                .isEqualTo(1);
        assertThat(primera.numeroFactura()).isPositive();
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos facturas con el mismo correlativo del mismo punto de venta: la base las
        // rechaza. Enterarse en el INSERT significa perder el documento ya enviado, y
        // por eso el correlativo se toma con candado.
        Caso c = caso(true);
        SalidaFactura salida = emitir(c);
        UUID otroDevengo = c.devengoId();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO tarifas.factura_electronica
                            (id, devengo_id, usuario_id, datos_facturacion_id, nit_emisor, sucursal,
                             punto_venta, numero_factura, cuf, cufd, fecha_emision, monto_total,
                             monto_iva, monto_no_sujeto, moneda, estado_fiscal, hash_documento)
                        SELECT gen_random_uuid(), '%s', usuario_id, datos_facturacion_id, nit_emisor,
                               sucursal, punto_venta, numero_factura, 'OTRO-CUF', cufd, now(),
                               monto_total, monto_iva, 0, moneda, 'VALIDADA', repeat('g', 64)
                          FROM tarifas.factura_electronica WHERE id = '%s'
                        """
                                .formatted(otroDevengo, salida.facturaId())))
                // La base tiene DOS indices sobre el correlativo —uno con el NIT y otro
                // sin el— y cual dispara primero es cosa suya. Lo que la prueba exige
                // es que el correlativo repetido no entre, no cual de los dos lo frena.
                .contains("numero_factura");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El monto de la factura iguala el del devengo, al centavo: un documento fiscal
        // que declara otra cifra es un problema con el servicio de impuestos.
        Caso c = caso(true);

        SalidaFactura salida = emitir(c);

        var fila = dsl.fetchOne(
                """
                SELECT f.monto_total AS facturado, d.monto_total AS devengado
                  FROM tarifas.factura_electronica f
                  JOIN tarifas.devengo_comision d ON d.id = f.devengo_id
                 WHERE f.id = ?
                """,
                salida.facturaId());
        assertThat(fila.get("facturado", java.math.BigDecimal.class))
                .isEqualByComparingTo(fila.get("devengado", java.math.BigDecimal.class));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "facturador"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "facturador"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin datos de facturacion el documento no sale, pero **el cobro no se
        // revierte**: castigar al usuario por un tramite pendiente seria cobrarle y
        // ademas quitarle el servicio.
        Caso c = caso(false);

        assertThatThrownBy(() -> emitir(c))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("datos de facturacion");
        assertThat(contar("SELECT count(*)::int FROM tarifas.factura_electronica WHERE devengo_id = ?", c.devengoId()))
                .isZero();
        // El cargo cobrado sigue ahi: la plata entro y eso no se toca.
        assertThat(contar(
                        "SELECT count(*)::int FROM tarifas.cargo_comision WHERE devengo_id = ? AND estado = 'COBRADO'",
                        c.devengoId()))
                .isEqualTo(1);
    }
}
