package bo.aportaya.aportes.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code pago}, {@code reembolso} y {@code disputa_pago}. */
@Component
public class PagoRepositorio {

    public UUID registrar(
            DSLContext dsl,
            UUID obligacionId,
            Optional<UUID> proveedorId,
            Dinero monto,
            Dinero comision,
            Dinero neto,
            String canal,
            String referenciaProveedor,
            String claveIdempotencia,
            boolean esManual,
            Optional<UUID> registradoPor,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("aportes", "pago")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("obligacion_id", UUID.class), obligacionId)
                .set(DSL.field("proveedor_id", UUID.class), proveedorId.orElse(null))
                .set(DSL.field("monto", BigDecimal.class), monto.monto())
                .set(DSL.field("moneda", String.class), monto.moneda().name())
                .set(DSL.field("monto_comision_proveedor", BigDecimal.class), comision.monto())
                .set(DSL.field("monto_neto_acreditado", BigDecimal.class), neto.monto())
                .set(DSL.field("canal", String.class), canal)
                .set(DSL.field("estado", String.class), "ACREDITADO")
                .set(DSL.field("fecha_hora_pago", OffsetDateTime.class), ahora)
                .set(DSL.field("fecha_hora_acreditacion", OffsetDateTime.class), ahora)
                .set(DSL.field("referencia_proveedor", String.class), referenciaProveedor)
                .set(DSL.field("registrado_por", UUID.class), registradoPor.orElse(null))
                .set(DSL.field("es_manual", Boolean.class), esManual)
                .set(DSL.field("clave_idempotencia", String.class), claveIdempotencia)
                .execute();
        return id;
    }

    public Optional<UUID> porClaveIdempotencia(DSLContext dsl, String clave) {
        return Optional.ofNullable(dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("aportes", "pago")))
                .where(DSL.field("clave_idempotencia").eq(clave))
                .fetchOne(DSL.field("id", UUID.class)));
    }

    public Optional<Pago> ver(DSLContext dsl, UUID pagoId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("obligacion_id", UUID.class),
                        DSL.field("proveedor_id", UUID.class),
                        DSL.field("monto", BigDecimal.class),
                        DSL.field("moneda", String.class),
                        DSL.field("estado", String.class))
                .from(DSL.table(DSL.name("aportes", "pago")))
                .where(DSL.field("id", UUID.class).eq(pagoId))
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Pago(
                        f.get("id", UUID.class),
                        f.get("obligacion_id", UUID.class),
                        Optional.ofNullable(f.get("proveedor_id", UUID.class)),
                        Dinero.de(f.get("monto", BigDecimal.class), Moneda.valueOf(f.get("moneda", String.class))),
                        f.get("estado", String.class)));
    }

    /** Lo ya reembolsado de un pago: es contra lo que se compara el nuevo pedido. */
    public Dinero reembolsadoDe(DSLContext dsl, UUID pagoId, Moneda moneda) {
        BigDecimal total = (BigDecimal) dsl.fetchOne(
                        """
                        SELECT COALESCE(SUM(monto), 0) FROM aportes.reembolso
                         WHERE pago_id = ? AND estado <> 'RECHAZADO'
                        """,
                        pagoId)
                .get(0);
        return Dinero.de(total, moneda);
    }

    public UUID solicitarReembolso(
            DSLContext dsl, UUID pagoId, Dinero monto, String motivo, UUID solicitadoPor, OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("aportes", "reembolso")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("pago_id", UUID.class), pagoId)
                .set(DSL.field("monto", BigDecimal.class), monto.monto())
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("estado", String.class), "SOLICITADO")
                .set(DSL.field("solicitado_por", UUID.class), solicitadoPor)
                .set(DSL.field("fecha_solicitud", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /**
     * Aprueba y ejecuta. El {@code WHERE estado = 'SOLICITADO'} decide la carrera: dos
     * aprobaciones simultaneas no devuelven la plata dos veces.
     */
    public boolean ejecutarReembolso(DSLContext dsl, UUID reembolsoId, UUID aprobadoPor, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("aportes", "reembolso")))
                        .set(DSL.field("estado", String.class), "EJECUTADO")
                        .set(DSL.field("aprobado_por", UUID.class), aprobadoPor)
                        .set(DSL.field("fecha_ejecucion", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class).eq(reembolsoId))
                        .and(DSL.field("estado").eq("SOLICITADO"))
                        .execute()
                > 0;
    }

    public Optional<Reembolso> verReembolso(DSLContext dsl, UUID reembolsoId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("pago_id", UUID.class),
                        DSL.field("monto", BigDecimal.class),
                        DSL.field("estado", String.class),
                        DSL.field("solicitado_por", UUID.class))
                .from(DSL.table(DSL.name("aportes", "reembolso")))
                .where(DSL.field("id", UUID.class).eq(reembolsoId))
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Reembolso(
                        f.get("id", UUID.class),
                        f.get("pago_id", UUID.class),
                        Dinero.de(f.get("monto", BigDecimal.class), Moneda.BOB),
                        f.get("estado", String.class),
                        f.get("solicitado_por", UUID.class)));
    }

    /** Una disputa abierta por pago: la segunda del proveedor es un reenvio. */
    public boolean hayDisputaAbierta(DSLContext dsl, UUID pagoId) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("aportes", "disputa_pago")),
                        DSL.field("pago_id", UUID.class).eq(pagoId),
                        DSL.field("estado").ne("RESUELTA"))
                > 0;
    }

    /**
     * Abre la disputa con su plazo **guardado**, no calculado al consultar.
     *
     * <p>Invariante 8: el plazo que corre es el que se fijo al recibirla. Recalcularlo
     * al mirar el tablero lo moveria cada vez que cambie la politica, y el proveedor
     * no acepta ese argumento.
     */
    public UUID abrirDisputa(
            DSLContext dsl,
            UUID pagoId,
            String tipo,
            String descripcion,
            Dinero montoDisputado,
            String evidenciasJson,
            OffsetDateTime abiertaEn,
            OffsetDateTime fechaLimite) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("aportes", "disputa_pago")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("pago_id", UUID.class), pagoId)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("monto_disputado", BigDecimal.class), montoDisputado.monto())
                .set(DSL.field("estado", String.class), "ABIERTA")
                .set(DSL.field("evidencias", org.jooq.JSONB.class), org.jooq.JSONB.valueOf(evidenciasJson))
                .set(DSL.field("abierta_en", OffsetDateTime.class), abiertaEn)
                .set(DSL.field("fecha_limite_respuesta", OffsetDateTime.class), fechaLimite)
                .execute();
        return id;
    }

    public record Pago(UUID id, UUID obligacionId, Optional<UUID> proveedorId, Dinero monto, String estado) {}

    public record Reembolso(UUID id, UUID pagoId, Dinero monto, String estado, UUID solicitadoPor) {}
}
