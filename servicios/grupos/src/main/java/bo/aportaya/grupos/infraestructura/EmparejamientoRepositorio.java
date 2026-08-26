package bo.aportaya.grupos.infraestructura;

import static bo.aportaya.grupos.generado.Tables.CRITERIO_EMPAREJAMIENTO;
import static bo.aportaya.grupos.generado.Tables.CUPO;
import static bo.aportaya.grupos.generado.Tables.PROPUESTA_GRUPO;
import static bo.aportaya.grupos.generado.Tables.PROPUESTA_POSTULACION;
import static bo.aportaya.grupos.generado.Tables.SOLICITUD_INGRESO;

import bo.aportaya.grupos.dominio.CriterioDeEmparejamiento;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

/** Postulaciones, propuestas de grupo y el criterio vigente. */
@Component
public class EmparejamientoRepositorio {

    /** El criterio VIGENTE: el mas reciente cuya vigencia ya empezo. */
    public Optional<CriterioDeEmparejamiento> criterioVigente(DSLContext dsl, OffsetDateTime ahora) {
        Record fila = dsl.selectFrom(CRITERIO_EMPAREJAMIENTO)
                .where(CRITERIO_EMPAREJAMIENTO.VIGENTE_DESDE.le(ahora))
                .orderBy(CRITERIO_EMPAREJAMIENTO.VIGENTE_DESDE.desc())
                .limit(1)
                .fetchOne();
        return fila == null
                ? Optional.empty()
                : Optional.of(new CriterioDeEmparejamiento(
                        fila.get(CRITERIO_EMPAREJAMIENTO.PESO_REPUTACION),
                        fila.get(CRITERIO_EMPAREJAMIENTO.PESO_MONTO),
                        fila.get(CRITERIO_EMPAREJAMIENTO.PESO_GEOGRAFIA),
                        fila.get(CRITERIO_EMPAREJAMIENTO.PESO_HISTORIAL_COMUN),
                        fila.get(CRITERIO_EMPAREJAMIENTO.REPUTACION_MINIMA).intValue(),
                        fila.get(CRITERIO_EMPAREJAMIENTO.MAX_MOROSOS_POR_GRUPO).intValue()));
    }

    public boolean hayCuposLibres(DSLContext dsl, UUID grupoId) {
        return dsl.fetchCount(CUPO, CUPO.GRUPO_ID.eq(grupoId).and(CUPO.ESTADO.eq("LIBRE"))) > 0;
    }

    public boolean yaPostulo(DSLContext dsl, UUID grupoId, UUID usuarioId) {
        return dsl.fetchExists(dsl.selectFrom(SOLICITUD_INGRESO)
                .where(SOLICITUD_INGRESO.GRUPO_ID.eq(grupoId))
                .and(SOLICITUD_INGRESO.USUARIO_ID.eq(usuarioId))
                .and(SOLICITUD_INGRESO.ESTADO.eq("PENDIENTE")));
    }

    public UUID postular(
            DSLContext dsl,
            UUID grupoId,
            UUID usuarioId,
            short cupos,
            String mensaje,
            BigDecimal puntaje,
            OffsetDateTime ahora) {
        return dsl.insertInto(SOLICITUD_INGRESO)
                .set(SOLICITUD_INGRESO.GRUPO_ID, grupoId)
                .set(SOLICITUD_INGRESO.USUARIO_ID, usuarioId)
                .set(SOLICITUD_INGRESO.CUPOS_SOLICITADOS, cupos)
                .set(SOLICITUD_INGRESO.MENSAJE, mensaje)
                .set(SOLICITUD_INGRESO.ESTADO, "PENDIENTE")
                .set(SOLICITUD_INGRESO.PUNTAJE_COMPATIBILIDAD, puntaje)
                .set(SOLICITUD_INGRESO.FECHA_SOLICITUD, ahora)
                .returning(SOLICITUD_INGRESO.ID)
                .fetchOne(SOLICITUD_INGRESO.ID);
    }

    public Optional<Propuesta> propuesta(DSLContext dsl, UUID propuestaId) {
        Record fila = dsl.select(
                        PROPUESTA_GRUPO.ESTADO,
                        PROPUESTA_GRUPO.EXPIRA_EN,
                        PROPUESTA_GRUPO.ACEPTACIONES_RECIBIDAS,
                        PROPUESTA_GRUPO.GRUPO_MATERIALIZADO_ID)
                .from(PROPUESTA_GRUPO)
                .where(PROPUESTA_GRUPO.ID.eq(propuestaId))
                .fetchOne();
        return fila == null
                ? Optional.empty()
                : Optional.of(new Propuesta(
                        fila.get(PROPUESTA_GRUPO.ESTADO),
                        fila.get(PROPUESTA_GRUPO.EXPIRA_EN),
                        fila.get(PROPUESTA_GRUPO.ACEPTACIONES_RECIBIDAS),
                        Optional.ofNullable(fila.get(PROPUESTA_GRUPO.GRUPO_MATERIALIZADO_ID))));
    }

    /** Responder a la propuesta, y solo si nadie respondio antes por esa postulacion. */
    public int responder(DSLContext dsl, UUID propuestaId, UUID postulacionId, boolean acepto, OffsetDateTime ahora) {
        return dsl.update(PROPUESTA_POSTULACION)
                .set(PROPUESTA_POSTULACION.ACEPTO, acepto)
                .set(PROPUESTA_POSTULACION.RESPONDIDO_EN, ahora)
                .where(PROPUESTA_POSTULACION.PROPUESTA_ID.eq(propuestaId))
                .and(PROPUESTA_POSTULACION.POSTULACION_ID.eq(postulacionId))
                .and(PROPUESTA_POSTULACION.RESPONDIDO_EN.isNull())
                .execute();
    }

    public int contarAceptaciones(DSLContext dsl, UUID propuestaId) {
        return dsl.fetchCount(
                PROPUESTA_POSTULACION,
                PROPUESTA_POSTULACION.PROPUESTA_ID.eq(propuestaId).and(PROPUESTA_POSTULACION.ACEPTO.isTrue()));
    }

    public void materializar(DSLContext dsl, UUID propuestaId, UUID grupoId, int aceptaciones) {
        dsl.update(PROPUESTA_GRUPO)
                .set(PROPUESTA_GRUPO.ESTADO, "CONFIRMADA")
                .set(PROPUESTA_GRUPO.GRUPO_MATERIALIZADO_ID, grupoId)
                .set(PROPUESTA_GRUPO.ACEPTACIONES_RECIBIDAS, (short) aceptaciones)
                .where(PROPUESTA_GRUPO.ID.eq(propuestaId))
                .execute();
    }

    public void anotarAceptacion(DSLContext dsl, UUID propuestaId, int aceptaciones) {
        dsl.update(PROPUESTA_GRUPO)
                .set(PROPUESTA_GRUPO.ACEPTACIONES_RECIBIDAS, (short) aceptaciones)
                .where(PROPUESTA_GRUPO.ID.eq(propuestaId))
                .execute();
    }

    public record Propuesta(
            String estado, OffsetDateTime expiraEn, short aceptaciones, Optional<UUID> grupoMaterializado) {}
}
