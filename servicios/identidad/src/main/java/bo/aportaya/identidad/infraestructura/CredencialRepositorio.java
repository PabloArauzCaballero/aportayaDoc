package bo.aportaya.identidad.infraestructura;

import static bo.aportaya.identidad.generado.Tables.CREDENCIAL_ACCESO;
import static bo.aportaya.identidad.generado.Tables.DISPOSITIVO;
import static bo.aportaya.identidad.generado.Tables.HISTORIAL_CREDENCIAL;
import static bo.aportaya.identidad.generado.Tables.SESION;
import static bo.aportaya.identidad.generado.Tables.SOLICITUD_BAJA;
import static bo.aportaya.identidad.generado.Tables.USUARIO;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/** Credenciales, su historial, y lo que cuelga de ellas. */
@Component
public class CredencialRepositorio {

    /** Los hashes anteriores, del mas reciente al mas viejo. */
    public List<String> historialDe(DSLContext dsl, UUID usuarioId) {
        return dsl.select(HISTORIAL_CREDENCIAL.HASH_CONTRASENA)
                .from(HISTORIAL_CREDENCIAL)
                .where(HISTORIAL_CREDENCIAL.USUARIO_ID.eq(usuarioId))
                .orderBy(HISTORIAL_CREDENCIAL.REEMPLAZADA_EN.desc())
                .fetch(HISTORIAL_CREDENCIAL.HASH_CONTRASENA);
    }

    /** La clave anterior pasa al historial: probar que no se reutilizo exige guardarla. */
    public void archivar(DSLContext dsl, UUID usuarioId, String hashAnterior, OffsetDateTime ahora) {
        dsl.insertInto(HISTORIAL_CREDENCIAL)
                .set(HISTORIAL_CREDENCIAL.USUARIO_ID, usuarioId)
                .set(HISTORIAL_CREDENCIAL.HASH_CONTRASENA, hashAnterior)
                .set(HISTORIAL_CREDENCIAL.REEMPLAZADA_EN, ahora)
                .execute();
    }

    public void reemplazar(DSLContext dsl, UUID usuarioId, String hashNuevo, OffsetDateTime ahora) {
        dsl.update(CREDENCIAL_ACCESO)
                .set(CREDENCIAL_ACCESO.HASH_CONTRASENA, hashNuevo)
                .set(CREDENCIAL_ACCESO.CAMBIADA_EN, ahora)
                .set(CREDENCIAL_ACCESO.REQUIERE_CAMBIO, false)
                .where(CREDENCIAL_ACCESO.USUARIO_ID.eq(usuarioId))
                .execute();
    }

    public int cerrarSesiones(
            DSLContext dsl, UUID usuarioId, Optional<UUID> exceptoEsta, String motivo, OffsetDateTime ahora) {
        var condicion = SESION.USUARIO_ID.eq(usuarioId).and(SESION.REVOCADA_EN.isNull());
        if (exceptoEsta.isPresent()) {
            condicion = condicion.and(SESION.ID.ne(exceptoEsta.get()));
        }
        return dsl.update(SESION)
                .set(SESION.REVOCADA_EN, ahora)
                .set(SESION.MOTIVO_REVOCACION, motivo)
                .where(condicion)
                .execute();
    }

    /** R-SEG-11: ningun dispositivo del operador queda confiable tras el restablecimiento. */
    public int quitarConfianza(DSLContext dsl, UUID usuarioId) {
        return dsl.update(DISPOSITIVO)
                .set(DISPOSITIVO.ES_CONFIABLE, false)
                .where(DISPOSITIVO.USUARIO_ID.eq(usuarioId))
                .execute();
    }

    public UUID solicitarBaja(DSLContext dsl, UUID usuarioId, String motivo, boolean bloqueada, OffsetDateTime ahora) {
        return dsl.insertInto(SOLICITUD_BAJA)
                .set(SOLICITUD_BAJA.USUARIO_ID, usuarioId)
                .set(SOLICITUD_BAJA.MOTIVO, motivo)
                .set(SOLICITUD_BAJA.SOLICITADA_EN, ahora)
                .set(SOLICITUD_BAJA.BLOQUEADA_POR_OBLIGACIONES, bloqueada)
                .returning(SOLICITUD_BAJA.ID)
                .fetchOne(SOLICITUD_BAJA.ID);
    }

    public Optional<String> telefonoDe(DSLContext dsl, UUID usuarioId) {
        return dsl.select(USUARIO.TELEFONO_E164)
                .from(USUARIO)
                .where(USUARIO.ID.eq(usuarioId))
                .fetchOptional(USUARIO.TELEFONO_E164);
    }
}
