package bo.aportaya.identidad.infraestructura;

import static bo.aportaya.identidad.generado.Tables.ASIGNACION_ROL;
import static bo.aportaya.identidad.generado.Tables.BLOQUEO_CUENTA;
import static bo.aportaya.identidad.generado.Tables.CREDENCIAL_ACCESO;
import static bo.aportaya.identidad.generado.Tables.ROL;
import static bo.aportaya.identidad.generado.Tables.USUARIO;

import bo.aportaya.identidad.dominio.PerfilDeAcceso;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * Lecturas de identidad para el ingreso. Sin logica: la decision es de los atomos.
 */
@Component
public class UsuarioRepositorio {

    public Optional<UUID> porTelefono(DSLContext dsl, String telefonoE164) {
        return dsl.select(USUARIO.ID)
                .from(USUARIO)
                .where(USUARIO.TELEFONO_E164.eq(telefonoE164))
                .and(USUARIO.ELIMINADO_EN.isNull())
                .fetchOptional(USUARIO.ID);
    }

    public Optional<String> hashDeCredencial(DSLContext dsl, UUID usuarioId) {
        return dsl.select(CREDENCIAL_ACCESO.HASH_CONTRASENA)
                .from(CREDENCIAL_ACCESO)
                .where(CREDENCIAL_ACCESO.USUARIO_ID.eq(usuarioId))
                .orderBy(CREDENCIAL_ACCESO.CAMBIADA_EN.desc())
                .limit(1)
                .fetchOptional(CREDENCIAL_ACCESO.HASH_CONTRASENA);
    }

    /** Los ambitos de sus roles vigentes: de ahi sale si es operador. */
    public PerfilDeAcceso perfilDe(DSLContext dsl, UUID usuarioId) {
        List<String> ambitos = dsl.select(ROL.AMBITO)
                .from(ASIGNACION_ROL)
                .join(ROL)
                .on(ROL.ID.eq(ASIGNACION_ROL.ROL_ID))
                .where(ASIGNACION_ROL.USUARIO_ID.eq(usuarioId))
                .and(ASIGNACION_ROL.REVOCADA_EN.isNull())
                .fetch(ROL.AMBITO);
        return new PerfilDeAcceso(ambitos);
    }

    public boolean tieneBloqueoVigente(DSLContext dsl, UUID usuarioId, OffsetDateTime ahora) {
        return dsl.fetchExists(dsl.selectFrom(BLOQUEO_CUENTA)
                .where(BLOQUEO_CUENTA.USUARIO_ID.eq(usuarioId))
                .and(BLOQUEO_CUENTA.LIBERADA_EN.isNull())
                .and(BLOQUEO_CUENTA.DESBLOQUEA_EN.isNull().or(BLOQUEO_CUENTA.DESBLOQUEA_EN.gt(ahora))));
    }
}
