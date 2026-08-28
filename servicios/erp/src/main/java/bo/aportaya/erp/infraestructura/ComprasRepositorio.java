package bo.aportaya.erp.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code tercero_comercial}, {@code orden_compra}, {@code factura_proveedor} y
 * {@code pago_a_proveedor}.
 *
 * <p>**Quien aprueba una factura no autoriza su pago** (R-CTB-05, lo verifica
 * {@code fn_ctb_segregacion_pago}). Es el control mas viejo de la contabilidad y sigue
 * siendo el que mas fraude interno evita: una sola persona que aprueba y paga puede
 * inventarse un proveedor.
 *
 * <p>Y **una factura por proveedor y numero** ({@code uq_factura_proveedor_numero}): la
 * misma factura cargada dos veces se paga dos veces.
 */
@Component
public class ComprasRepositorio {

    public UUID altaDeTercero(
            DSLContext dsl,
            String tipo,
            String razonSocial,
            String numeroDocumento,
            String email,
            UUID cuentaContableId) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "tercero_comercial")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("razon_social", String.class), razonSocial)
                .set(DSL.field("numero_documento", String.class), numeroDocumento)
                .set(DSL.field("email", String.class), email)
                .set(DSL.field("cuenta_contable_id", UUID.class), cuentaContableId)
                .set(DSL.field("estado", String.class), "ACTIVO")
                .execute();
        return id;
    }

    public Optional<Tercero> terceroPorDocumento(DSLContext dsl, String numeroDocumento) {
        return dsl.select(
                        DSL.field("id", UUID.class), DSL.field("tipo", String.class), DSL.field("estado", String.class))
                .from(DSL.table(DSL.name("erp", "tercero_comercial")))
                .where(DSL.field("numero_documento", String.class).eq(numeroDocumento))
                .fetchOptional(f -> new Tercero(
                        f.get("id", UUID.class), f.get("tipo", String.class), f.get("estado", String.class)));
    }

    public Optional<Tercero> terceroPorId(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class), DSL.field("tipo", String.class), DSL.field("estado", String.class))
                .from(DSL.table(DSL.name("erp", "tercero_comercial")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Tercero(
                        f.get("id", UUID.class), f.get("tipo", String.class), f.get("estado", String.class)));
    }

    public UUID crearOrden(
            DSLContext dsl,
            UUID terceroId,
            UUID centroCostoId,
            String numero,
            String descripcion,
            BigDecimal monto,
            String moneda) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "orden_compra")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("tercero_comercial_id", UUID.class), terceroId)
                .set(DSL.field("centro_costo_id", UUID.class), centroCostoId)
                .set(DSL.field("numero", String.class), numero)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("monto_total", BigDecimal.class), monto)
                .set(DSL.field("moneda", String.class), moneda)
                // Nace en BORRADOR: aprobar es un acto separado, con firma.
                .set(DSL.field("estado", String.class), "BORRADOR")
                .execute();
        return id;
    }

    public boolean aprobarOrden(DSLContext dsl, UUID ordenId, UUID aprobadaPor) {
        return dsl.update(DSL.table(DSL.name("erp", "orden_compra")))
                        .set(DSL.field("estado", String.class), "APROBADA")
                        .set(DSL.field("aprobada_por", UUID.class), aprobadaPor)
                        .where(DSL.field("id", UUID.class)
                                .eq(ordenId)
                                .and(DSL.field("estado", String.class).eq("BORRADOR")))
                        .execute()
                == 1;
    }

    public Optional<Orden> orden(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("tercero_comercial_id", UUID.class),
                        DSL.field("monto_total", BigDecimal.class),
                        DSL.field("estado", String.class),
                        DSL.field("aprobada_por", UUID.class))
                .from(DSL.table(DSL.name("erp", "orden_compra")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Orden(
                        f.get("id", UUID.class),
                        f.get("tercero_comercial_id", UUID.class),
                        f.get("monto_total", BigDecimal.class),
                        f.get("estado", String.class),
                        f.get("aprobada_por", UUID.class)));
    }

    /**
     * Alta de la factura, **ya con su aprobacion si la tiene**.
     *
     * <p>{@code factura_proveedor} es append-only (R-AUD-01): {@code aprobada_por} no se
     * puede completar despues, asi que aprobar no es un UPDATE posterior sino un dato
     * del alta. Y {@code ck_factura_proveedor_aprobacion} lo exige en el mismo sentido:
     * una fila APROBADA sin aprobador no se puede guardar. Queda declarado como hueco
     * del carril.
     */
    public UUID registrarFactura(
            DSLContext dsl,
            UUID terceroId,
            UUID ordenId,
            UUID centroCostoId,
            String numeroFactura,
            LocalDate emision,
            LocalDate vencimiento,
            BigDecimal monto,
            String moneda,
            UUID aprobadaPor) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "factura_proveedor")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("tercero_comercial_id", UUID.class), terceroId)
                .set(DSL.field("orden_compra_id", UUID.class), ordenId)
                .set(DSL.field("centro_costo_id", UUID.class), centroCostoId)
                .set(DSL.field("numero_factura", String.class), numeroFactura)
                .set(DSL.field("fecha_emision", LocalDate.class), emision)
                .set(DSL.field("fecha_vencimiento", LocalDate.class), vencimiento)
                .set(DSL.field("monto", BigDecimal.class), monto)
                .set(DSL.field("moneda", String.class), moneda)
                // `monto_pagado` queda en cero PARA SIEMPRE: la tabla es append-only.
                // Lo pagado se deriva de `pago_a_proveedor`, que es donde realmente
                // esta el hecho.
                .set(DSL.field("monto_pagado", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("estado", String.class), aprobadaPor == null ? "REGISTRADA" : "APROBADA")
                .set(DSL.field("aprobada_por", UUID.class), aprobadaPor)
                .execute();
        return id;
    }

    /**
     * La factura con bloqueo de fila y **lo pagado derivado de sus pagos**.
     *
     * <p>{@code monto_pagado} no sirve: la tabla es append-only y esa columna se queda
     * en cero para siempre. Lo pagado es la suma de {@code pago_a_proveedor}, que es
     * donde esta el hecho.
     *
     * <p>{@code FOR UPDATE} sobre la factura —que es una lectura, no una mutacion, asi
     * que el append-only la admite— porque el saldo se decide leyendo esa suma: sin
     * bloquear, dos pagos simultaneos leen el mismo total y **los dos pasan**. El error
     * saldria del sistema como dos transferencias antes de que nadie lo note.
     */
    public Optional<Factura> facturaBloqueada(DSLContext dsl, UUID id) {
        var factura = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("tercero_comercial_id", UUID.class),
                        DSL.field("monto", BigDecimal.class),
                        DSL.field("estado", String.class),
                        DSL.field("aprobada_por", UUID.class))
                .from(DSL.table(DSL.name("erp", "factura_proveedor")))
                .where(DSL.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOne();
        if (factura == null) {
            return Optional.empty();
        }
        BigDecimal pagado = dsl.select(DSL.coalesce(DSL.sum(DSL.field("monto", BigDecimal.class)), BigDecimal.ZERO)
                        .as("pagado"))
                .from(DSL.table(DSL.name("erp", "pago_a_proveedor")))
                .where(DSL.field("factura_proveedor_id", UUID.class).eq(id))
                .fetchOne(f -> f.get("pagado", BigDecimal.class));

        return Optional.of(new Factura(
                factura.get("id", UUID.class),
                factura.get("tercero_comercial_id", UUID.class),
                factura.get("monto", BigDecimal.class),
                pagado,
                factura.get("estado", String.class),
                factura.get("aprobada_por", UUID.class)));
    }

    public UUID pagar(
            DSLContext dsl,
            UUID facturaId,
            BigDecimal monto,
            String moneda,
            String formaPago,
            UUID autorizadoPor,
            BigDecimal pagadoNuevo,
            BigDecimal montoFactura,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "pago_a_proveedor")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("factura_proveedor_id", UUID.class), facturaId)
                .set(DSL.field("monto", BigDecimal.class), monto)
                .set(DSL.field("moneda", String.class), moneda)
                .set(DSL.field("fecha_pago", OffsetDateTime.class), ahora)
                .set(DSL.field("forma_pago", String.class), formaPago)
                .set(DSL.field("autorizado_por", UUID.class), autorizadoPor)
                .execute();

        // La factura NO se toca: es append-only. Su estado se deriva de la suma de sus
        // pagos, y esa suma es la que manda.
        return id;
    }

    public record Tercero(UUID id, String tipo, String estado) {}

    public record Orden(UUID id, UUID terceroId, BigDecimal montoTotal, String estado, UUID aprobadaPor) {}

    public record Factura(
            UUID id, UUID terceroId, BigDecimal monto, BigDecimal montoPagado, String estado, UUID aprobadaPor) {}
}
