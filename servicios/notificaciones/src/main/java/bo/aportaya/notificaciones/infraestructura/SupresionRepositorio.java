package bo.aportaya.notificaciones.infraestructura;

import bo.aportaya.notificaciones.dominio.Canal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code lista_supresion}: a quien NO se le escribe, y por que canal.
 *
 * <p>La supresion **nunca se fuerza**. Un aviso obligatorio tampoco la pisa: si
 * alguien pidio que no le escriban por WhatsApp, el aviso obligatorio sale por otro
 * canal, no por ese. Forzarlo convertiria un pedido explicito en una sugerencia.
 */
@Component
public class SupresionRepositorio {

    public boolean estaSuprimido(DSLContext dsl, String identificador, Canal canal, String categoria) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("notificaciones", "lista_supresion")),
                        DSL.field("identificador").eq(identificador),
                        DSL.field("canal").eq(canal.name()),
                        DSL.field("categoria").eq(categoria),
                        DSL.field("activa", Boolean.class).isTrue())
                > 0;
    }

    public UUID suprimir(
            DSLContext dsl,
            String identificador,
            Canal canal,
            String motivo,
            String categoria,
            boolean permanente,
            OffsetDateTime momento) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("notificaciones", "lista_supresion")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("identificador", String.class), identificador)
                .set(DSL.field("canal", String.class), canal.name())
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("categoria", String.class), categoria)
                .set(DSL.field("activa", Boolean.class), true)
                .set(DSL.field("permanente", Boolean.class), permanente)
                .set(DSL.field("agregado_en", OffsetDateTime.class), momento)
                .onConflictDoNothing()
                .execute();
        return id;
    }
}
