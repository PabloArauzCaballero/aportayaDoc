package bo.aportaya.nucleofinanciero.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code transferencia_p2p} y la politica que la habilita. */
@Component
public class TransferenciaRepositorio {

    public UUID registrar(
            DSLContext dsl,
            UUID transaccionId,
            UUID origenId,
            UUID destinoId,
            Optional<UUID> grupoId,
            Optional<UUID> obligacionId,
            Dinero monto,
            String concepto,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("nucleo_financiero", "transferencia_p2p")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("transaccion_id", UUID.class), transaccionId)
                .set(DSL.field("cuenta_billetera_origen_id", UUID.class), origenId)
                .set(DSL.field("cuenta_billetera_destino_id", UUID.class), destinoId)
                .set(DSL.field("grupo_id", UUID.class), grupoId.orElse(null))
                .set(DSL.field("obligacion_id", UUID.class), obligacionId.orElse(null))
                .set(DSL.field("monto", BigDecimal.class), monto.monto())
                .set(DSL.field("moneda", String.class), monto.moneda().name())
                .set(DSL.field("concepto", String.class), concepto)
                .set(DSL.field("estado", String.class), "EJECUTADA")
                .set(DSL.field("ejecutada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /**
     * ¿La politica de esa billetera admite transferencias?
     *
     * <p>Sin politica se responde **false**, no true: una cuenta sin politica no tiene
     * quien haya autorizado nada, y denegar por omision es lo que corresponde
     * (invariante 9).
     */
    public boolean permiteP2P(DSLContext dsl, UUID cuentaId) {
        Boolean permite = dsl.select(
                        DSL.field("p.permite_transferencia_p2p", Boolean.class).as("permite"))
                .from(DSL.table(DSL.name("nucleo_financiero", "cuenta_billetera"))
                        .as("c"))
                .join(DSL.table(DSL.name("nucleo_financiero", "politica_billetera"))
                        .as("p"))
                .on(DSL.field("c.politica_billetera_id", UUID.class).eq(DSL.field("p.id", UUID.class)))
                .where(DSL.field("c.id", UUID.class).eq(cuentaId))
                .fetchOne(DSL.field("permite", Boolean.class));
        return Boolean.TRUE.equals(permite);
    }
}
