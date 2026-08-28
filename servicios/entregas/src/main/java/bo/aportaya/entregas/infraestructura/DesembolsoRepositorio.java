package bo.aportaya.entregas.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code orden_desembolso} y {@code intento_desembolso}.
 *
 * <p>Una orden viva por entrega (R-DES-01) y ninguna a cuenta sin verificar (R-DES-02,
 * {@code tg_orden_desembolso_cuenta_verificada}). Las dos las sostiene la base: enviar
 * dos ordenes de la misma entrega es pagar dos veces, y no se descubre hasta que
 * alguien concilia el extracto.
 */
@Component
public class DesembolsoRepositorio {

    public Optional<Orden> porClave(DSLContext dsl, UUID entregaId, String clave) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("entregas", "orden_desembolso")))
                .where(DSL.field("entrega_id", UUID.class)
                        .eq(entregaId)
                        .and(DSL.field("clave_idempotencia", String.class).eq(clave)))
                .fetchOptional(this::aOrden);
    }

    public Optional<Orden> ver(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("entregas", "orden_desembolso")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(this::aOrden);
    }

    /** Con candado: dos corridas del motor no procesan la misma orden a la vez. */
    public Optional<Orden> bloquear(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("entregas", "orden_desembolso")))
                .where(DSL.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOptional(this::aOrden);
    }

    public UUID emitir(
            DSLContext dsl,
            UUID entregaId,
            UUID proveedorId,
            UUID cuentaDestinoId,
            Dinero monto,
            String glosa,
            String clave) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("entregas", "orden_desembolso")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("entrega_id", UUID.class), entregaId)
                .set(DSL.field("proveedor_id", UUID.class), proveedorId)
                .set(DSL.field("cuenta_destino_id", UUID.class), cuentaDestinoId)
                .set(DSL.field("monto", BigDecimal.class), monto.monto())
                .set(DSL.field("moneda", String.class), monto.moneda().name())
                .set(DSL.field("estado", String.class), "CREADA")
                .set(DSL.field("glosa", String.class), glosa)
                .set(DSL.field("clave_idempotencia", String.class), clave)
                .execute();
        return id;
    }

    public boolean cambiarEstado(DSLContext dsl, UUID id, List<String> desde, String hacia) {
        return dsl.update(DSL.table(DSL.name("entregas", "orden_desembolso")))
                        .set(DSL.field("estado", String.class), hacia)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).in(desde)))
                        .execute()
                == 1;
    }

    public boolean acreditar(DSLContext dsl, UUID id, String referenciaProveedor, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("entregas", "orden_desembolso")))
                        .set(DSL.field("estado", String.class), "ACREDITADA")
                        .set(DSL.field("referencia_proveedor", String.class), referenciaProveedor)
                        .set(DSL.field("acreditada_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class)
                                        .in("CREADA", "ENVIADA_A_PROVEEDOR", "EN_PROCESO")))
                        .execute()
                == 1;
    }

    /**
     * Cierra el intento que estaba en curso con lo que contesto el proveedor.
     *
     * <p>Un intento es **una fila**: la que se abre al enviar la orden es la misma que
     * se cierra al recibir la respuesta. Agregar otra al contestar convertiria un
     * intento en dos, y el conteo que decide si se reintenta dejaria de significar algo.
     */
    public Optional<Integer> cerrarIntentoPendiente(
            DSLContext dsl,
            UUID ordenId,
            OffsetDateTime finalizado,
            String resultado,
            String codigoError,
            String mensajeProveedor,
            OffsetDateTime reintentableEn) {

        var pendiente = dsl.select(DSL.field("id", UUID.class), DSL.field("numero_intento", Short.class))
                .from(DSL.table(DSL.name("entregas", "intento_desembolso")))
                .where(DSL.field("orden_desembolso_id", UUID.class)
                        .eq(ordenId)
                        .and(DSL.field("resultado", String.class).eq("PENDIENTE")))
                .orderBy(DSL.field("numero_intento").desc())
                .limit(1)
                .fetchOptional();
        if (pendiente.isEmpty()) {
            return Optional.empty();
        }
        dsl.update(DSL.table(DSL.name("entregas", "intento_desembolso")))
                .set(DSL.field("finalizado_en", OffsetDateTime.class), finalizado)
                .set(DSL.field("resultado", String.class), resultado)
                .set(DSL.field("codigo_error", String.class), codigoError)
                .set(DSL.field("mensaje_proveedor", String.class), mensajeProveedor)
                .set(DSL.field("reintentable_en", OffsetDateTime.class), reintentableEn)
                .where(DSL.field("id", UUID.class).eq(pendiente.get().get("id", UUID.class)))
                .execute();
        return Optional.of((int) pendiente.get().get("numero_intento", Short.class));
    }

    public int intentosDe(DSLContext dsl, UUID ordenId) {
        return dsl.fetchCount(
                DSL.table(DSL.name("entregas", "intento_desembolso")),
                DSL.field("orden_desembolso_id", UUID.class).eq(ordenId));
    }

    public UUID registrarIntento(
            DSLContext dsl,
            UUID ordenId,
            int numero,
            OffsetDateTime iniciado,
            OffsetDateTime finalizado,
            String resultado,
            String codigoError,
            String mensajeProveedor,
            OffsetDateTime reintentableEn) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("entregas", "intento_desembolso")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("orden_desembolso_id", UUID.class), ordenId)
                .set(DSL.field("numero_intento", Short.class), (short) numero)
                .set(DSL.field("iniciado_en", OffsetDateTime.class), iniciado)
                .set(DSL.field("finalizado_en", OffsetDateTime.class), finalizado)
                .set(DSL.field("resultado", String.class), resultado)
                .set(DSL.field("codigo_error", String.class), codigoError)
                .set(DSL.field("mensaje_proveedor", String.class), mensajeProveedor)
                .set(DSL.field("reintentable_en", OffsetDateTime.class), reintentableEn)
                .execute();
        return id;
    }

    private List<org.jooq.Field<?>> campos() {
        return List.of(
                DSL.field("id", UUID.class),
                DSL.field("entrega_id", UUID.class),
                DSL.field("proveedor_id", UUID.class),
                DSL.field("cuenta_destino_id", UUID.class),
                DSL.field("monto", BigDecimal.class),
                DSL.field("moneda", String.class),
                DSL.field("estado", String.class),
                DSL.field("clave_idempotencia", String.class));
    }

    private Orden aOrden(org.jooq.Record f) {
        return new Orden(
                f.get("id", UUID.class),
                f.get("entrega_id", UUID.class),
                f.get("proveedor_id", UUID.class),
                f.get("cuenta_destino_id", UUID.class),
                Dinero.de(f.get("monto", BigDecimal.class), Moneda.valueOf(f.get("moneda", String.class))),
                f.get("estado", String.class),
                f.get("clave_idempotencia", String.class));
    }

    public record Orden(
            UUID id,
            UUID entregaId,
            UUID proveedorId,
            UUID cuentaDestinoId,
            Dinero monto,
            String estado,
            String claveIdempotencia) {}
}
