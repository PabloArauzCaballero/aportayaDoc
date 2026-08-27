package bo.aportaya.nucleofinanciero.infraestructura;

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

/**
 * {@code orden_retiro} y el instrumento de destino.
 *
 * <p>La base tiene tres guardias sobre esta tabla y ninguna se replica en Java: el
 * trigger del instrumento (R-BIL-09), el del encaje (R-BIL-11b) y los CHECK de MFA y
 * doble aprobacion. Duplicarlas crearia dos verdades; lo que se hace es adelantar el
 * rechazo para dar un mensaje util y dejar que la base tenga la ultima palabra.
 */
@Component
public class OrdenRetiroRepositorio {

    public UUID crear(
            DSLContext dsl,
            UUID cuentaId,
            UUID instrumentoDestinoId,
            UUID retencionId,
            UUID solicitadaPor,
            Dinero solicitado,
            Dinero costo,
            Dinero neto,
            boolean mfaVerificado,
            boolean requiereDobleAprobacion,
            Optional<OffsetDateTime> enfriamientoHasta,
            String claveIdempotencia,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("nucleo_financiero", "orden_retiro")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("cuenta_billetera_id", UUID.class), cuentaId)
                .set(DSL.field("instrumento_destino_id", UUID.class), instrumentoDestinoId)
                .set(DSL.field("retencion_id", UUID.class), retencionId)
                .set(DSL.field("solicitada_por", UUID.class), solicitadaPor)
                .set(DSL.field("monto_solicitado", BigDecimal.class), solicitado.monto())
                .set(DSL.field("costo_retiro", BigDecimal.class), costo.monto())
                .set(DSL.field("monto_neto", BigDecimal.class), neto.monto())
                .set(DSL.field("moneda", String.class), solicitado.moneda().name())
                .set(DSL.field("estado", String.class), "PENDIENTE")
                .set(DSL.field("mfa_verificado", Boolean.class), mfaVerificado)
                .set(DSL.field("requiere_doble_aprobacion", Boolean.class), requiereDobleAprobacion)
                .set(DSL.field("ventana_enfriamiento_hasta", OffsetDateTime.class), enfriamientoHasta.orElse(null))
                .set(DSL.field("clave_idempotencia", String.class), claveIdempotencia)
                .set(DSL.field("solicitada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public Optional<Orden> ver(DSLContext dsl, UUID ordenId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("cuenta_billetera_id", UUID.class),
                        DSL.field("retencion_id", UUID.class),
                        DSL.field("monto_solicitado", BigDecimal.class),
                        DSL.field("monto_neto", BigDecimal.class),
                        DSL.field("moneda", String.class),
                        DSL.field("estado", String.class),
                        DSL.field("solicitada_por", UUID.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "orden_retiro")))
                .where(DSL.field("id", UUID.class).eq(ordenId))
                .fetchOne();
        return Optional.ofNullable(fila).map(f -> {
            Moneda moneda = Moneda.valueOf(f.get("moneda", String.class));
            return new Orden(
                    f.get("id", UUID.class),
                    f.get("cuenta_billetera_id", UUID.class),
                    Optional.ofNullable(f.get("retencion_id", UUID.class)),
                    Dinero.de(f.get("monto_solicitado", BigDecimal.class), moneda),
                    Dinero.de(f.get("monto_neto", BigDecimal.class), moneda),
                    f.get("estado", String.class),
                    f.get("solicitada_por", UUID.class));
        });
    }

    public Optional<UUID> porClaveIdempotencia(DSLContext dsl, String clave) {
        return Optional.ofNullable(dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "orden_retiro")))
                .where(DSL.field("clave_idempotencia").eq(clave))
                .fetchOne(DSL.field("id", UUID.class)));
    }

    /** Transicion condicionada al estado previo: la carrera la decide el UPDATE. */
    public boolean pasarA(DSLContext dsl, UUID ordenId, String desde, String hacia, OffsetDateTime pagadaEn) {
        var paso = dsl.update(DSL.table(DSL.name("nucleo_financiero", "orden_retiro")))
                .set(DSL.field("estado", String.class), hacia)
                .set(DSL.field("pagada_en", OffsetDateTime.class), pagadaEn);
        return paso.where(DSL.field("id", UUID.class).eq(ordenId))
                        .and(DSL.field("estado").eq(desde))
                        .execute()
                > 0;
    }

    /** El instrumento de destino, con lo que las condiciones duras necesitan mirar. */
    public Optional<Instrumento> instrumento(DSLContext dsl, UUID instrumentoId) {
        Record fila = dsl.select(
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("estado_verificacion", String.class),
                        DSL.field("titular_coincide", Boolean.class),
                        DSL.field("bloqueado_hasta", OffsetDateTime.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "instrumento_fondeo")))
                .where(DSL.field("id", UUID.class).eq(instrumentoId))
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Instrumento(
                        f.get("usuario_id", UUID.class),
                        "VERIFICADO".equals(f.get("estado_verificacion", String.class)),
                        f.get("titular_coincide", Boolean.class),
                        Optional.ofNullable(f.get("bloqueado_hasta", OffsetDateTime.class))));
    }

    /** ¿Hay saldo inmovilizado por oficio sobre esa cuenta? */
    public boolean hayBloqueoDeAutoridad(DSLContext dsl, UUID cuentaId) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("nucleo_financiero", "bloqueo_saldo")),
                        DSL.field("cuenta_billetera_id", UUID.class).eq(cuentaId),
                        DSL.field("estado").eq("VIGENTE"))
                > 0;
    }

    /**
     * R-BIL-11b · ¿la custodia cumple encaje?
     *
     * <p>Se mira la ULTIMA conciliacion de cada cuenta de custodia de la moneda. Con el
     * encaje roto no salen retiros nuevos: seguir pagando es el escenario clasico de la
     * corrida — cobran los primeros que llegan y no queda para los demas.
     */
    public boolean encajeCumplido(DSLContext dsl, String moneda) {
        Integer incumplen = (Integer) dsl.fetchOne(
                        """
                        SELECT count(*)::int FROM (
                          SELECT DISTINCT ON (c.cuenta_custodia_id) c.cumple_encaje
                            FROM nucleo_financiero.conciliacion_custodia c
                            JOIN nucleo_financiero.cuenta_custodia cc ON cc.id = c.cuenta_custodia_id
                           WHERE cc.moneda = ?
                           ORDER BY c.cuenta_custodia_id, c.fecha DESC
                        ) ultimas WHERE NOT cumple_encaje
                        """,
                        moneda)
                .get(0);
        return incumplen == null || incumplen == 0;
    }

    public record Orden(
            UUID id,
            UUID cuentaId,
            Optional<UUID> retencionId,
            Dinero solicitado,
            Dinero neto,
            String estado,
            UUID solicitadaPor) {}

    public record Instrumento(
            UUID usuarioId, boolean verificado, boolean titularCoincide, Optional<OffsetDateTime> bloqueadoHasta) {}
}
