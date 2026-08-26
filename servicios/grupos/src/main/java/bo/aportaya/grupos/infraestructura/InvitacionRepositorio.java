package bo.aportaya.grupos.infraestructura;

import static bo.aportaya.grupos.generado.Tables.CUPO;
import static bo.aportaya.grupos.generado.Tables.INVITACION;
import static bo.aportaya.grupos.generado.Tables.PARTICIPANTE;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Component;

/** Invitaciones a un grupo, con su token de un solo uso. */
@Component
public class InvitacionRepositorio {

    public boolean hayCuposLibres(DSLContext dsl, UUID grupoId) {
        return dsl.fetchCount(CUPO, CUPO.GRUPO_ID.eq(grupoId).and(CUPO.ESTADO.eq("LIBRE"))) > 0;
    }

    public boolean emisorHabilitado(DSLContext dsl, UUID grupoId, UUID emisorId) {
        return dsl.fetchExists(dsl.selectFrom(PARTICIPANTE)
                .where(PARTICIPANTE.GRUPO_ID.eq(grupoId))
                .and(PARTICIPANTE.USUARIO_ID.eq(emisorId))
                .and(PARTICIPANTE.ESTADO.eq("ACTIVO")));
    }

    public UUID crear(
            DSLContext dsl,
            UUID grupoId,
            String telefono,
            String nombreSugerido,
            UUID emisorId,
            UUID tokenId,
            String canal,
            OffsetDateTime enviadaEn,
            OffsetDateTime expiraEn) {
        return dsl.insertInto(INVITACION)
                .set(INVITACION.GRUPO_ID, grupoId)
                .set(INVITACION.TELEFONO_INVITADO, telefono)
                .set(INVITACION.NOMBRE_SUGERIDO, nombreSugerido)
                .set(INVITACION.EMISOR_ID, emisorId)
                .set(INVITACION.TOKEN_ID, tokenId)
                .set(INVITACION.CANAL, canal)
                .set(INVITACION.ESTADO, "ENVIADA")
                .set(INVITACION.ENVIOS_REALIZADOS, (short) 1)
                .set(INVITACION.FECHA_ENVIO, enviadaEn)
                .set(INVITACION.FECHA_EXPIRACION, expiraEn)
                .returning(INVITACION.ID)
                .fetchOne(INVITACION.ID);
    }

    public Optional<Invitacion> porId(DSLContext dsl, UUID invitacionId) {
        Record fila = dsl.select(
                        INVITACION.ESTADO,
                        INVITACION.ENVIOS_REALIZADOS,
                        INVITACION.GRUPO_ID,
                        INVITACION.FECHA_EXPIRACION)
                .from(INVITACION)
                .where(INVITACION.ID.eq(invitacionId))
                .fetchOne();
        return fila == null
                ? Optional.empty()
                : Optional.of(new Invitacion(
                        fila.get(INVITACION.ESTADO),
                        fila.get(INVITACION.ENVIOS_REALIZADOS),
                        fila.get(INVITACION.GRUPO_ID),
                        fila.get(INVITACION.FECHA_EXPIRACION)));
    }

    /**
     * Acepta la invitacion, y solo si sigue {@code ENVIADA}.
     *
     * <p>El {@code WHERE estado = 'ENVIADA'} es lo que hace el token de un solo uso:
     * la segunda vez actualiza cero filas. Comprobarlo antes con un {@code SELECT}
     * dejaria pasar dos aceptaciones simultaneas.
     */
    public int aceptar(DSLContext dsl, UUID invitacionId, OffsetDateTime ahora) {
        return dsl.update(INVITACION)
                .set(INVITACION.ESTADO, "ACEPTADA")
                .set(INVITACION.FECHA_RESPUESTA, ahora)
                .where(INVITACION.ID.eq(invitacionId))
                .and(INVITACION.ESTADO.eq("ENVIADA"))
                .execute();
    }

    public int reenviar(DSLContext dsl, UUID invitacionId) {
        return dsl.update(INVITACION)
                .set(INVITACION.ENVIOS_REALIZADOS, INVITACION.ENVIOS_REALIZADOS.plus((short) 1))
                .where(INVITACION.ID.eq(invitacionId))
                .execute();
    }

    // `yaEsParticipante` NO vive aca a proposito: resolver un telefono exige leer
    // identidad.usuario, y este servicio no lee el esquema de otro (invariante 11).
    // La respuesta llega resuelta desde afuera, como el resto de lo que no es suyo.

    public record Invitacion(String estado, short envios, UUID grupoId, OffsetDateTime expiraEn) {}
}
