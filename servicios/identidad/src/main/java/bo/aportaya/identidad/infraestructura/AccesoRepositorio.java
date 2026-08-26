package bo.aportaya.identidad.infraestructura;

import static bo.aportaya.identidad.generado.Tables.BLOQUEO_CUENTA;
import static bo.aportaya.identidad.generado.Tables.DISPOSITIVO;
import static bo.aportaya.identidad.generado.Tables.FACTOR_MFA;
import static bo.aportaya.identidad.generado.Tables.INTENTO_AUTENTICACION;
import static bo.aportaya.identidad.generado.Tables.SESION;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** Escrituras del ingreso: intento, dispositivo, sesion y bloqueo. */
@Component
public class AccesoRepositorio {

    /**
     * El intento se escribe ANTES de conocer el resultado, y por eso lleva su propio
     * campo de exito que se completa al cerrar: si se escribiera al final, un fallo
     * que revierte se llevaria consigo la evidencia del fallo.
     */
    public UUID registrarIntento(
            DSLContext dsl,
            Optional<UUID> usuarioId,
            String identificadorUsado,
            String ip,
            String agente,
            String huella,
            OffsetDateTime ahora) {
        // Con columns()/values() y no con set(): `inet` obliga a un casteo explicito,
        // y un Field<Object> vuelve ambiguo el set(campo, valor) de jOOQ.
        return dsl.insertInto(
                        INTENTO_AUTENTICACION,
                        INTENTO_AUTENTICACION.USUARIO_ID,
                        INTENTO_AUTENTICACION.IDENTIFICADOR_USADO,
                        INTENTO_AUTENTICACION.FECHA_HORA,
                        INTENTO_AUTENTICACION.EXITOSO,
                        INTENTO_AUTENTICACION.IP_ORIGEN,
                        INTENTO_AUTENTICACION.AGENTE_USUARIO,
                        INTENTO_AUTENTICACION.HUELLA_DISPOSITIVO,
                        INTENTO_AUTENTICACION.PUNTAJE_RIESGO)
                .values(
                        DSL.val(usuarioId.orElse(null), UUID.class),
                        DSL.val(identificadorUsado),
                        DSL.val(ahora),
                        DSL.val(false),
                        comoInet(ip),
                        DSL.val(agente),
                        DSL.val(huella),
                        DSL.val(java.math.BigDecimal.ZERO))
                .returning(INTENTO_AUTENTICACION.ID)
                .fetchOne(INTENTO_AUTENTICACION.ID);
    }

    public void cerrarIntento(DSLContext dsl, UUID intentoId, boolean exitoso, String motivoFallo) {
        dsl.update(INTENTO_AUTENTICACION)
                .set(INTENTO_AUTENTICACION.EXITOSO, exitoso)
                .set(INTENTO_AUTENTICACION.MOTIVO_FALLO, motivoFallo)
                .where(INTENTO_AUTENTICACION.ID.eq(intentoId))
                .execute();
    }

    public int fallidosConsecutivos(DSLContext dsl, UUID usuarioId, OffsetDateTime desde) {
        return dsl.fetchCount(
                INTENTO_AUTENTICACION,
                INTENTO_AUTENTICACION
                        .USUARIO_ID
                        .eq(usuarioId)
                        .and(INTENTO_AUTENTICACION.EXITOSO.isFalse())
                        .and(INTENTO_AUTENTICACION.FECHA_HORA.ge(desde)));
    }

    /** El dispositivo nuevo nace con {@code es_confiable = false}. Sin excepciones. */
    public Dispositivo dispositivoPorHuella(
            DSLContext dsl, UUID usuarioId, String huella, String plataforma, OffsetDateTime ahora) {
        var existente = dsl.select(DISPOSITIVO.ID, DISPOSITIVO.ES_CONFIABLE)
                .from(DISPOSITIVO)
                .where(DISPOSITIVO.USUARIO_ID.eq(usuarioId))
                .and(DISPOSITIVO.HUELLA.eq(huella))
                .and(DISPOSITIVO.REVOCADO_EN.isNull())
                .fetchOne();

        if (existente != null) {
            dsl.update(DISPOSITIVO)
                    .set(DISPOSITIVO.ULTIMO_USO_EN, ahora)
                    .where(DISPOSITIVO.ID.eq(existente.value1()))
                    .execute();
            return new Dispositivo(existente.value1(), Boolean.TRUE.equals(existente.value2()));
        }

        UUID nuevo = dsl.insertInto(DISPOSITIVO)
                .set(DISPOSITIVO.USUARIO_ID, usuarioId)
                .set(DISPOSITIVO.HUELLA, huella)
                .set(DISPOSITIVO.PLATAFORMA, plataforma)
                .set(DISPOSITIVO.MODELO, "desconocido")
                .set(DISPOSITIVO.VERSION_APP, "desconocida")
                .set(DISPOSITIVO.ES_CONFIABLE, false)
                .set(DISPOSITIVO.ULTIMO_USO_EN, ahora)
                .returning(DISPOSITIVO.ID)
                .fetchOne(DISPOSITIVO.ID);
        return new Dispositivo(nuevo, false);
    }

    public Optional<String> factorActivo(DSLContext dsl, UUID usuarioId) {
        return dsl.select(FACTOR_MFA.TIPO)
                .from(FACTOR_MFA)
                .where(FACTOR_MFA.USUARIO_ID.eq(usuarioId))
                .and(FACTOR_MFA.ACTIVO.isTrue())
                .and(FACTOR_MFA.CONFIRMADO_EN.isNotNull())
                .orderBy(FACTOR_MFA.ES_PRINCIPAL.desc())
                .limit(1)
                .fetchOptional(FACTOR_MFA.TIPO);
    }

    public SesionAbierta abrirSesion(
            DSLContext dsl, UUID usuarioId, UUID dispositivoId, String ip, Duration vigencia, OffsetDateTime ahora) {
        OffsetDateTime expira = ahora.plus(vigencia);
        UUID id = dsl.insertInto(
                        SESION,
                        SESION.USUARIO_ID,
                        SESION.DISPOSITIVO_ID,
                        SESION.INICIADA_EN,
                        SESION.ULTIMA_ACTIVIDAD_EN,
                        SESION.EXPIRA_EN,
                        SESION.IP_ORIGEN)
                .values(
                        DSL.val(usuarioId),
                        DSL.val(dispositivoId),
                        DSL.val(ahora),
                        DSL.val(ahora),
                        DSL.val(expira),
                        comoInet(ip))
                .returning(SESION.ID)
                .fetchOne(SESION.ID);
        return new SesionAbierta(id, expira);
    }

    public void bloquear(DSLContext dsl, UUID usuarioId, Duration duracion, OffsetDateTime ahora) {
        dsl.insertInto(BLOQUEO_CUENTA)
                .set(BLOQUEO_CUENTA.USUARIO_ID, usuarioId)
                .set(BLOQUEO_CUENTA.MOTIVO, "INTENTOS_FALLIDOS")
                .set(BLOQUEO_CUENTA.BLOQUEADA_EN, ahora)
                .set(BLOQUEO_CUENTA.DESBLOQUEA_EN, ahora.plus(duracion))
                .execute();
    }

    /**
     * {@code inet} no es una cadena para PostgreSQL: sin el casteo explicito el
     * driver manda varchar y el motor lo rechaza. jOOQ mapea el tipo a {@code Object},
     * asi que el casteo tiene que estar en el SQL.
     */
    private static Field<Object> comoInet(String ip) {
        return DSL.field("cast({0} as inet)", Object.class, DSL.val(ip));
    }

    public record Dispositivo(UUID id, boolean confiable) {}

    public record SesionAbierta(UUID id, OffsetDateTime expiraEn) {}
}
