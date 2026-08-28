package bo.aportaya.tarifas.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code factura_electronica}, {@code datos_facturacion} y {@code evento_significativo_sin}.
 *
 * <p>Una factura validada **no se modifica** ({@code tg_factura_inmutable}, R-TAR-10):
 * se anula y se emite nota de credito. Editar un documento fiscal ya aceptado es
 * falsificarlo, sin importar cual haya sido la intencion.
 */
@Component
public class FacturaRepositorio {

    /** Los datos de facturacion predeterminados del usuario, si los cargo. */
    public Optional<DatosFacturacion> datosDe(DSLContext dsl, UUID usuarioId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("tipo_documento", String.class),
                        DSL.field("numero_documento", String.class),
                        DSL.field("razon_social", String.class),
                        DSL.field("email_envio", String.class))
                .from(DSL.table(DSL.name("tarifas", "datos_facturacion")))
                .where(DSL.field("usuario_id", UUID.class)
                        .eq(usuarioId)
                        .and(DSL.field("es_predeterminado", Boolean.class).isTrue()))
                .limit(1)
                .fetchOptional(f -> new DatosFacturacion(
                        f.get("id", UUID.class),
                        f.get("tipo_documento", String.class),
                        f.get("numero_documento", String.class),
                        f.get("razon_social", String.class),
                        f.get("email_envio", String.class)));
    }

    public Optional<UUID> facturaDe(DSLContext dsl, UUID devengoId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("tarifas", "factura_electronica")))
                .where(DSL.field("devengo_id", UUID.class).eq(devengoId))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    /**
     * El siguiente correlativo del punto de venta.
     *
     * <p>Se serializa con un **candado de aviso de transaccion** sobre el punto de
     * venta, no con {@code FOR UPDATE}: Postgres no admite bloquear filas en una
     * consulta con agregados, y aunque lo admitiera no hay fila que bloquear cuando el
     * punto de venta todavia no emitio ninguna factura — que es justo el momento en
     * que dos procesos concurrentes elegirian el numero 1 los dos.
     *
     * <p>El candado se libera solo al terminar la transaccion. Dos facturas con el
     * mismo numero es lo que {@code uq_factura_correlativo} rechaza, y enterarse en el
     * INSERT significa perder el documento que ya se envio al servicio de impuestos.
     */
    public long siguienteCorrelativo(DSLContext dsl, String nitEmisor, int sucursal, int puntoVenta) {
        dsl.execute("SELECT pg_advisory_xact_lock(?, ?)", claveDeCandado(nitEmisor), sucursal * 1000 + puntoVenta);
        Long maximo = dsl.fetchOne(
                        """
                        SELECT COALESCE(MAX(numero_factura), 0) AS ultimo
                          FROM tarifas.factura_electronica
                         WHERE nit_emisor = ? AND sucursal = ? AND punto_venta = ?
                        """,
                        nitEmisor,
                        (short) sucursal,
                        (short) puntoVenta)
                .get("ultimo", Long.class);
        return maximo + 1;
    }

    /** Un entero estable por emisor: el candado tiene que ser el mismo entre corridas. */
    private int claveDeCandado(String nitEmisor) {
        return nitEmisor.hashCode();
    }

    public UUID emitir(
            DSLContext dsl,
            UUID devengoId,
            UUID usuarioId,
            UUID datosFacturacionId,
            String nitEmisor,
            int sucursal,
            int puntoVenta,
            long numeroFactura,
            String cuf,
            String cufd,
            Dinero total,
            Dinero iva,
            String estadoFiscal,
            UUID eventoSignificativoId,
            String hashDocumento,
            String urlPdf,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("tarifas", "factura_electronica")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("devengo_id", UUID.class), devengoId)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("datos_facturacion_id", UUID.class), datosFacturacionId)
                .set(DSL.field("evento_significativo_id", UUID.class), eventoSignificativoId)
                .set(DSL.field("nit_emisor", String.class), nitEmisor)
                .set(DSL.field("sucursal", Short.class), (short) sucursal)
                .set(DSL.field("punto_venta", Short.class), (short) puntoVenta)
                .set(DSL.field("numero_factura", Long.class), numeroFactura)
                .set(DSL.field("cuf", String.class), cuf)
                .set(DSL.field("cufd", String.class), cufd)
                .set(DSL.field("fecha_emision", OffsetDateTime.class), ahora)
                .set(DSL.field("monto_total", BigDecimal.class), total.monto())
                .set(DSL.field("monto_iva", BigDecimal.class), iva.monto())
                .set(DSL.field("monto_no_sujeto", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("moneda", String.class), total.moneda().name())
                .set(DSL.field("estado_fiscal", String.class), estadoFiscal)
                .set(DSL.field("hash_documento", String.class), hashDocumento)
                .set(DSL.field("url_pdf", String.class), urlPdf)
                .execute();
        return id;
    }

    /** La contingencia abierta del punto de venta, si hay una. */
    public Optional<Contingencia> contingenciaAbierta(DSLContext dsl, int sucursal, int puntoVenta) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("fecha_inicio", OffsetDateTime.class),
                        DSL.field("plazo_registro", OffsetDateTime.class),
                        DSL.field("cantidad_documentos_offline", Integer.class))
                .from(DSL.table(DSL.name("tarifas", "evento_significativo_sin")))
                .where(DSL.field("sucursal", Short.class)
                        .eq((short) sucursal)
                        .and(DSL.field("punto_venta", Short.class).eq((short) puntoVenta))
                        .and(DSL.field("estado", String.class).eq("ABIERTO")))
                .fetchOptional(f -> new Contingencia(
                        f.get("id", UUID.class),
                        f.get("fecha_inicio", OffsetDateTime.class),
                        f.get("plazo_registro", OffsetDateTime.class),
                        f.get("cantidad_documentos_offline", Integer.class)));
    }

    public UUID abrirContingencia(
            DSLContext dsl,
            UUID registradoPor,
            String codigoEvento,
            String descripcion,
            int sucursal,
            int puntoVenta,
            String cufd,
            OffsetDateTime inicio,
            OffsetDateTime plazoRegistro) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("tarifas", "evento_significativo_sin")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("registrado_por", UUID.class), registradoPor)
                .set(DSL.field("codigo_evento", String.class), codigoEvento)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("sucursal", Short.class), (short) sucursal)
                .set(DSL.field("punto_venta", Short.class), (short) puntoVenta)
                .set(DSL.field("cufd_evento", String.class), cufd)
                .set(DSL.field("fecha_inicio", OffsetDateTime.class), inicio)
                .set(DSL.field("cantidad_documentos_offline", Integer.class), 0)
                .set(DSL.field("plazo_registro", OffsetDateTime.class), plazoRegistro)
                .set(DSL.field("estado", String.class), "ABIERTO")
                .execute();
        return id;
    }

    public void contarDocumentoOffline(DSLContext dsl, UUID contingenciaId) {
        dsl.execute(
                """
                UPDATE tarifas.evento_significativo_sin
                   SET cantidad_documentos_offline = cantidad_documentos_offline + 1
                 WHERE id = ?
                """,
                contingenciaId);
    }

    public record DatosFacturacion(
            UUID id, String tipoDocumento, String numeroDocumento, String razonSocial, String emailEnvio) {}

    public record Contingencia(UUID id, OffsetDateTime inicio, OffsetDateTime plazoRegistro, int documentosOffline) {}
}
