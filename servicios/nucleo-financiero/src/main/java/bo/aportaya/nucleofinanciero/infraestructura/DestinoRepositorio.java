package bo.aportaya.nucleofinanciero.infraestructura;

import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/**
 * De quien es la billetera, y cual es la del grupo.
 *
 * <p>Las dos consultas viven en el esquema de este servicio, y por eso estan aca y no
 * detras de un puerto: {@code cuenta_billetera} tiene {@code usuario_id} y
 * {@code grupo_id}, asi que traducir una persona o un grupo a su cuenta es una lectura
 * propia. Lo unico ajeno es el alias, y eso si se pregunta.
 *
 * <p>Solo cuentas {@code ACTIVA}: acreditar en una cerrada deja plata que nadie puede
 * sacar, y es mejor rechazar la transferencia que descubrirlo despues.
 */
@Repository
public class DestinoRepositorio {

    public Optional<UUID> cuentaDeUsuario(DSLContext dsl, UUID usuarioId) {
        return unaCuenta(dsl, "usuario_id", usuarioId, "USUARIO");
    }

    public Optional<UUID> cuentaDeGrupo(DSLContext dsl, UUID grupoId) {
        return unaCuenta(dsl, "grupo_id", grupoId, "GRUPO");
    }

    private Optional<UUID> unaCuenta(DSLContext dsl, String columna, UUID valor, String tipo) {
        return Optional.ofNullable(dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "cuenta_billetera")))
                .where(DSL.field(DSL.name(columna), UUID.class).eq(valor))
                .and(DSL.field("tipo", String.class).eq(tipo))
                .and(DSL.field("estado", String.class).eq("ACTIVA"))
                .fetchOne(DSL.field("id", UUID.class)));
    }
}
