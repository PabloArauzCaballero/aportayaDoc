package bo.aportaya.plataforma.web.idempotencia;

import bo.aportaya.plataforma.dominio.ClaveIdempotencia;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;

/**
 * Reintentar tiene que ser seguro. La clave se valida ANTES de escribir.
 *
 * <p>El orden importa y es el error mas comun de esta capa: validar despues del
 * {@code INSERT} significa que el segundo intento ya escribio antes de descubrir que
 * era el segundo.
 *
 * <p>La deteccion no se apoya en un {@code SELECT} previo sino en la clave unica de
 * la tabla: entre dos peticiones simultaneas, el {@code SELECT} de las dos dice «no
 * existe» y las dos escriben. Solo el indice unico decide.
 */
public final class Idempotencia {

    private static final Duration VIGENCIA = Duration.ofDays(1);

    private final String esquema;

    public Idempotencia(String esquema) {
        this.esquema = Objects.requireNonNull(esquema, "esquema");
    }

    /**
     * Reserva la clave. Si ya estaba, lanza {@link OperacionRepetida} con la
     * respuesta original.
     */
    public void exigirNueva(
            DSLContext dsl, ContextoSesion ctx, String operacion, ClaveIdempotencia clave, String hashSolicitud) {
        var tabla = DSL.table(DSL.name(esquema, "respuesta_idempotente"));

        Record previa = dsl.select(DSL.field("cuerpo_respuesta"), DSL.field("codigo_http"))
                .from(tabla)
                .where(DSL.field("clave_idempotencia").eq(clave.valor()))
                .and(DSL.field("operacion").eq(operacion))
                .fetchAny();

        if (previa != null) {
            throw new OperacionRepetida(((Number) previa.get(1)).intValue(), String.valueOf(previa.get(0)));
        }

        int filas = dsl.insertInto(tabla)
                .columns(
                        DSL.field("id"),
                        DSL.field("usuario_id"),
                        DSL.field("operacion"),
                        DSL.field("clave_idempotencia"),
                        DSL.field("hash_solicitud"),
                        DSL.field("codigo_http"),
                        DSL.field("cuerpo_respuesta"),
                        DSL.field("registrada_en"),
                        DSL.field("expira_en"))
                .values(
                        DSL.field("gen_random_uuid()"),
                        DSL.val(ctx.usuarioId()),
                        DSL.val(operacion),
                        DSL.val(clave.valor()),
                        DSL.val(hashSolicitud),
                        DSL.val((short) 202),
                        DSL.val(JSONB.valueOf("{}")),
                        DSL.field("now()"),
                        DSL.val(OffsetDateTime.now().plus(VIGENCIA)))
                .onConflictDoNothing()
                .execute();

        if (filas == 0) {
            // Otra peticion gano la carrera entre el SELECT y este INSERT.
            throw new ErrorDeNegocio(
                    bo.aportaya.plataforma.dominio.CodigoError.de(0, 0), "Esa operacion ya se esta procesando.");
        }
    }

    /** Guarda la respuesta real, para que el reintento devuelva exactamente eso. */
    public void guardarRespuesta(DSLContext dsl, ClaveIdempotencia clave, int codigoHttp, String cuerpoJson) {
        dsl.update(DSL.table(DSL.name(esquema, "respuesta_idempotente")))
                .set(DSL.field("codigo_http"), DSL.val((short) codigoHttp))
                .set(DSL.field("cuerpo_respuesta"), DSL.val(JSONB.valueOf(cuerpoJson)))
                .where(DSL.field("clave_idempotencia").eq(clave.valor()))
                .execute();
    }
}
