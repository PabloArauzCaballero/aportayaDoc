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
 * {@code retencion_saldo}.
 *
 * <p>Ningun metodo toca {@code cuenta_billetera.saldo_*}: el trigger
 * {@code tg_retencion_sincroniza_saldo} recalcula los dos saldos desde el libro cada
 * vez que una retencion nace o cambia de estado. Escribirlos a mano seria mantener
 * dos verdades sobre el mismo dinero.
 */
@Component
public class RetencionRepositorio {

    public UUID retener(
            DSLContext dsl,
            UUID cuentaId,
            Dinero monto,
            String motivo,
            Optional<UUID> transaccionOrigenId,
            Optional<String> referenciaTipo,
            Optional<UUID> referenciaId,
            Optional<OffsetDateTime> expiraEn,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("nucleo_financiero", "retencion_saldo")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("cuenta_billetera_id", UUID.class), cuentaId)
                .set(DSL.field("transaccion_origen_id", UUID.class), transaccionOrigenId.orElse(null))
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("referencia_tipo", String.class), referenciaTipo.orElse(null))
                .set(DSL.field("referencia_id", UUID.class), referenciaId.orElse(null))
                .set(DSL.field("monto", BigDecimal.class), monto.monto())
                .set(DSL.field("estado", String.class), "VIGENTE")
                .set(DSL.field("expira_en", OffsetDateTime.class), expiraEn.orElse(null))
                .set(DSL.field("creada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public Optional<Retencion> ver(DSLContext dsl, UUID retencionId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("cuenta_billetera_id", UUID.class),
                        DSL.field("monto", BigDecimal.class),
                        DSL.field("motivo", String.class),
                        DSL.field("estado", String.class),
                        DSL.field("expira_en", OffsetDateTime.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "retencion_saldo")))
                .where(DSL.field("id", UUID.class).eq(retencionId))
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Retencion(
                        f.get("id", UUID.class),
                        f.get("cuenta_billetera_id", UUID.class),
                        Dinero.de(f.get("monto", BigDecimal.class), Moneda.BOB),
                        f.get("motivo", String.class),
                        f.get("estado", String.class),
                        Optional.ofNullable(f.get("expira_en", OffsetDateTime.class))));
    }

    /**
     * Cambia el estado, **solo si sigue vigente**.
     *
     * <p>El {@code WHERE estado = 'VIGENTE'} es la barrera de concurrencia: dos
     * liberaciones simultaneas, y la segunda actualiza cero filas. Comprobarlo con un
     * SELECT previo dejaria la ventana entre leer y escribir.
     */
    public boolean cerrar(
            DSLContext dsl, UUID retencionId, String nuevoEstado, Optional<UUID> liberadaPor, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("nucleo_financiero", "retencion_saldo")))
                        .set(DSL.field("estado", String.class), nuevoEstado)
                        .set(DSL.field("liberada_por", UUID.class), liberadaPor.orElse(null))
                        .set(DSL.field("liberada_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class).eq(retencionId))
                        .and(DSL.field("estado").eq("VIGENTE"))
                        .execute()
                > 0;
    }

    /**
     * Las que ya pasaron su fecha: las libera el trabajo programado, no una consulta.
     *
     * <p>Quedan **LIBERADA** y no VENCIDA porque asi lo pide el criterio de aceptacion
     * del caso de uso. El enum de la base admite las dos, pero para la persona el
     * efecto es el mismo —su plata vuelve a estar disponible— y usar el estado que la
     * ficha nombra evita que dos partes del sistema llamen distinto a lo mismo.
     */
    public int vencerLasCaducadas(DSLContext dsl, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("nucleo_financiero", "retencion_saldo")))
                .set(DSL.field("estado", String.class), "LIBERADA")
                .set(DSL.field("liberada_en", OffsetDateTime.class), ahora)
                .where(DSL.field("estado").eq("VIGENTE"))
                .and(DSL.field("expira_en").isNotNull())
                .and(DSL.field("expira_en", OffsetDateTime.class).lt(ahora))
                .execute();
    }

    /** Los dias de vigencia que fija la politica de la cuenta. */
    public int diasDeVigencia(DSLContext dsl, UUID cuentaId) {
        Integer dias = dsl.select(
                        DSL.field("p.dias_vigencia_retencion", Integer.class).as("dias"))
                .from(DSL.table(DSL.name("nucleo_financiero", "cuenta_billetera"))
                        .as("c"))
                .join(DSL.table(DSL.name("nucleo_financiero", "politica_billetera"))
                        .as("p"))
                .on(DSL.field("c.politica_billetera_id", UUID.class).eq(DSL.field("p.id", UUID.class)))
                .where(DSL.field("c.id", UUID.class).eq(cuentaId))
                .fetchOne(DSL.field("dias", Integer.class));
        if (dias == null) {
            // Sin politica no se inventa un plazo: retener sin saber por cuanto es
            // justo lo que R-BIL-08 quiere impedir.
            throw new IllegalStateException("La cuenta no tiene politica de billetera: no hay plazo que aplicar");
        }
        return dias;
    }

    public record Retencion(
            UUID id, UUID cuentaId, Dinero monto, String motivo, String estado, Optional<OffsetDateTime> expiraEn) {}
}
