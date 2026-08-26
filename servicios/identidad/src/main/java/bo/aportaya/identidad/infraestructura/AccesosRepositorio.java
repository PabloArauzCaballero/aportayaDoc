package bo.aportaya.identidad.infraestructura;

import static bo.aportaya.identidad.generado.Tables.ASIGNACION_ROL;
import static bo.aportaya.identidad.generado.Tables.PERMISO;
import static bo.aportaya.identidad.generado.Tables.ROL;
import static bo.aportaya.identidad.generado.Tables.ROL_PERMISO;
import static bo.aportaya.identidad.generado.Tables.SESION;

import bo.aportaya.identidad.dominio.PermisosEfectivos.AsignacionVigente;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

/** Roles, permisos y asignaciones. Sin logica: la decision es de los atomos. */
@Component
public class AccesosRepositorio {

    public Optional<String> ambitoDelRol(DSLContext dsl, UUID rolId) {
        return dsl.select(ROL.AMBITO).from(ROL).where(ROL.ID.eq(rolId)).fetchOptional(ROL.AMBITO);
    }

    public Set<String> permisosDelRol(DSLContext dsl, UUID rolId) {
        return new TreeSet<>(dsl.select(PERMISO.CODIGO)
                .from(ROL_PERMISO)
                .join(PERMISO)
                .on(PERMISO.ID.eq(ROL_PERMISO.PERMISO_ID))
                .where(ROL_PERMISO.ROL_ID.eq(rolId))
                .fetch(PERMISO.CODIGO));
    }

    /** Las asignaciones del usuario con la ventana y los permisos de cada rol. */
    public List<AsignacionVigente> asignacionesDe(DSLContext dsl, UUID usuarioId) {
        List<AsignacionVigente> asignaciones = new ArrayList<>();
        for (Record fila : dsl.select(
                        ASIGNACION_ROL.ROL_ID,
                        ASIGNACION_ROL.OTORGADA_EN,
                        ASIGNACION_ROL.VIGENTE_HASTA,
                        ASIGNACION_ROL.REVOCADA_EN)
                .from(ASIGNACION_ROL)
                .where(ASIGNACION_ROL.USUARIO_ID.eq(usuarioId))
                .fetch()) {
            asignaciones.add(new AsignacionVigente(
                    fila.get(ASIGNACION_ROL.OTORGADA_EN),
                    Optional.ofNullable(fila.get(ASIGNACION_ROL.VIGENTE_HASTA)),
                    Optional.ofNullable(fila.get(ASIGNACION_ROL.REVOCADA_EN)),
                    permisosDelRol(dsl, fila.get(ASIGNACION_ROL.ROL_ID))));
        }
        return asignaciones;
    }

    public UUID asignar(
            DSLContext dsl,
            UUID usuarioId,
            UUID rolId,
            String ambito,
            Optional<UUID> ambitoId,
            UUID otorgadaPor,
            Optional<OffsetDateTime> vigenteHasta,
            OffsetDateTime ahora) {
        return dsl.insertInto(ASIGNACION_ROL)
                .set(ASIGNACION_ROL.USUARIO_ID, usuarioId)
                .set(ASIGNACION_ROL.ROL_ID, rolId)
                .set(ASIGNACION_ROL.AMBITO, ambito)
                .set(ASIGNACION_ROL.AMBITO_ID, ambitoId.orElse(null))
                .set(ASIGNACION_ROL.OTORGADA_POR, otorgadaPor)
                .set(ASIGNACION_ROL.OTORGADA_EN, ahora)
                .set(ASIGNACION_ROL.VIGENTE_HASTA, vigenteHasta.orElse(null))
                .returning(ASIGNACION_ROL.ID)
                .fetchOne(ASIGNACION_ROL.ID);
    }

    /** Revocar escribe; **la fila no se borra** ({@code R-AUD-01}). */
    public int revocar(DSLContext dsl, UUID asignacionId, String motivo, OffsetDateTime ahora) {
        return dsl.update(ASIGNACION_ROL)
                .set(ASIGNACION_ROL.REVOCADA_EN, ahora)
                .set(ASIGNACION_ROL.MOTIVO_REVOCACION, motivo)
                .where(ASIGNACION_ROL.ID.eq(asignacionId))
                .and(ASIGNACION_ROL.REVOCADA_EN.isNull())
                .execute();
    }

    public Optional<UUID> rolDe(DSLContext dsl, UUID asignacionId) {
        return dsl.select(ASIGNACION_ROL.ROL_ID)
                .from(ASIGNACION_ROL)
                .where(ASIGNACION_ROL.ID.eq(asignacionId))
                .fetchOptional(ASIGNACION_ROL.ROL_ID);
    }

    public Optional<UUID> titularDe(DSLContext dsl, UUID asignacionId) {
        return dsl.select(ASIGNACION_ROL.USUARIO_ID)
                .from(ASIGNACION_ROL)
                .where(ASIGNACION_ROL.ID.eq(asignacionId))
                .fetchOptional(ASIGNACION_ROL.USUARIO_ID);
    }

    /**
     * Revocar un permiso y dejar viva la sesion que lo usaba no es revocar nada.
     */
    public int cerrarSesionesDe(DSLContext dsl, UUID usuarioId, String motivo, OffsetDateTime ahora) {
        return dsl.update(SESION)
                .set(SESION.REVOCADA_EN, ahora)
                .set(SESION.MOTIVO_REVOCACION, motivo)
                .where(SESION.USUARIO_ID.eq(usuarioId))
                .and(SESION.REVOCADA_EN.isNull())
                .execute();
    }

    /** Cuantos usuarios distintos conservan el permiso, sin contar una asignacion dada. */
    public int cuantosConservan(DSLContext dsl, String codigoDePermiso, UUID asignacionExcluida) {
        return dsl.selectCount()
                .from(ASIGNACION_ROL)
                .join(ROL_PERMISO)
                .on(ROL_PERMISO.ROL_ID.eq(ASIGNACION_ROL.ROL_ID))
                .join(PERMISO)
                .on(PERMISO.ID.eq(ROL_PERMISO.PERMISO_ID))
                .where(PERMISO.CODIGO.eq(codigoDePermiso))
                .and(ASIGNACION_ROL.REVOCADA_EN.isNull())
                .and(ASIGNACION_ROL.ID.ne(asignacionExcluida))
                .fetchOne(0, int.class);
    }
}
