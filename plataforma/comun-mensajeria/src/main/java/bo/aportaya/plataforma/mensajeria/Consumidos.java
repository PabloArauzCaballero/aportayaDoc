package bo.aportaya.plataforma.mensajeria;

import java.util.Objects;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/**
 * Todo consumidor es idempotente, sin excepcion, porque el transporte es **al menos
 * una vez**.
 *
 * <p>La marca se toma con {@code INSERT ... ON CONFLICT DO NOTHING} y no con un
 * {@code SELECT} previo: entre dos replicas consumiendo el mismo mensaje, los dos
 * {@code SELECT} dicen «no esta» y las dos siguen. Solo la clave unica decide.
 */
public final class Consumidos {

    private final String esquema;

    public Consumidos(String esquema) {
        this.esquema = Objects.requireNonNull(esquema, "esquema");
    }

    /** {@code true} si es la primera vez; {@code false} si ya se proceso. */
    public boolean registrar(DSLContext dsl, UUID idEvento, String consumidor) {
        return dsl.insertInto(DSL.table(DSL.name(esquema, "evento_consumido")))
                        .columns(DSL.field("id_evento"), DSL.field("consumidor"), DSL.field("consumido_en"))
                        .values(DSL.val(idEvento), DSL.val(consumidor), DSL.field("now()"))
                        .onConflictDoNothing()
                        .execute()
                == 1;
    }
}
