package bo.aportaya.nucleofinanciero.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code solicitud_cierre_billetera} y el cierre de la cuenta. */
@Component
public class CierreRepositorio {

    public UUID solicitar(
            DSLContext dsl,
            UUID cuentaId,
            UUID solicitadaPor,
            String motivo,
            Dinero saldoAlSolicitar,
            String destinoSaldo,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("nucleo_financiero", "solicitud_cierre_billetera")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("cuenta_billetera_id", UUID.class), cuentaId)
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("saldo_al_solicitar", BigDecimal.class), saldoAlSolicitar.monto())
                .set(DSL.field("destino_saldo", String.class), destinoSaldo)
                .set(DSL.field("estado", String.class), "SOLICITADA")
                .set(DSL.field("solicitada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /**
     * ¿Ya hay una solicitud para esa cuenta?
     *
     * <p>`uq_solicitud_cierre_billetera_cuenta_billetera_id` admite UNA por cuenta,
     * para siempre — no una abierta a la vez. Es mas estricto de lo que parece y esta
     * bien: cerrar una billetera es un acto unico, y dos solicitudes sobre la misma
     * cuenta serian dos historias del mismo cierre.
     */
    public Optional<UUID> solicitudDe(DSLContext dsl, UUID cuentaId) {
        return Optional.ofNullable(dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "solicitud_cierre_billetera")))
                .where(DSL.field("cuenta_billetera_id", UUID.class).eq(cuentaId))
                .fetchOne(DSL.field("id", UUID.class)));
    }

    public Optional<UUID> cuentaDe(DSLContext dsl, UUID solicitudId) {
        return Optional.ofNullable(dsl.select(DSL.field("cuenta_billetera_id", UUID.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "solicitud_cierre_billetera")))
                .where(DSL.field("id", UUID.class).eq(solicitudId))
                .fetchOne(DSL.field("cuenta_billetera_id", UUID.class)));
    }

    public boolean marcarEjecutada(DSLContext dsl, UUID solicitudId, UUID aprobadaPor, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("nucleo_financiero", "solicitud_cierre_billetera")))
                        .set(DSL.field("estado", String.class), "EJECUTADA")
                        .set(DSL.field("aprobada_por", UUID.class), aprobadaPor)
                        .set(DSL.field("ejecutada_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class).eq(solicitudId))
                        .and(DSL.field("estado").in("SOLICITADA", "APROBADA"))
                        .execute()
                > 0;
    }

    /**
     * Cierra la cuenta.
     *
     * <p>Es el unico UPDATE que este servicio hace sobre {@code cuenta_billetera}
     * fuera del recalculo por trigger, y toca solo el estado y la fecha: **nunca el
     * saldo**, que se deriva del libro.
     */
    public void cerrarCuenta(DSLContext dsl, UUID cuentaId, OffsetDateTime ahora) {
        dsl.update(DSL.table(DSL.name("nucleo_financiero", "cuenta_billetera")))
                .set(DSL.field("estado", String.class), "CERRADA")
                .set(DSL.field("fecha_cierre", OffsetDateTime.class), ahora)
                .where(DSL.field("id", UUID.class).eq(cuentaId))
                .execute();
    }

    public boolean hayRetencionVigente(DSLContext dsl, UUID cuentaId) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("nucleo_financiero", "retencion_saldo")),
                        DSL.field("cuenta_billetera_id", UUID.class).eq(cuentaId),
                        DSL.field("estado").eq("VIGENTE"))
                > 0;
    }
}
