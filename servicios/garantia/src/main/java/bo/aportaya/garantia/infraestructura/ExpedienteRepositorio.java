package bo.aportaya.garantia.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code registro_incumplimiento}, su historial, sus evidencias y los descargos.
 *
 * <p>El registro y el historial son **append-only**. La columna {@code estado} del
 * registro guarda el estado <b>al detectar</b>; el corriente sale de la ultima fila del
 * historial. Un expediente cuyo estado se puede reescribir no prueba nada, y la persona
 * sancionada no tiene contra que defenderse.
 *
 * <p>La evidencia tambien es inmutable ({@code tg_evidencia_inmutable}): una prueba que
 * se puede editar despues de presentada no es una prueba.
 */
@Component
public class ExpedienteRepositorio {

    public Optional<Expediente> ver(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("garantia", "registro_incumplimiento")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(this::aExpediente);
    }

    /** Con candado: dos gestiones del mismo expediente se ponen en fila. */
    public Optional<Expediente> bloquear(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("garantia", "registro_incumplimiento")))
                .where(DSL.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOptional(this::aExpediente);
    }

    /** El expediente de una obligacion, si ya se abrio (uq_registro_..._obligacion_id). */
    public Optional<Expediente> porObligacion(DSLContext dsl, UUID obligacionId) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("garantia", "registro_incumplimiento")))
                .where(DSL.field("obligacion_id", UUID.class).eq(obligacionId))
                .fetchOptional(this::aExpediente);
    }

    public UUID abrir(
            DSLContext dsl,
            String codigoExpediente,
            UUID usuarioId,
            UUID participanteId,
            UUID grupoId,
            UUID periodoId,
            UUID cupoId,
            UUID obligacionId,
            String tipo,
            String severidad,
            String origenDeteccion,
            Dinero montoInvolucrado,
            int diasMora,
            int numeroReincidencia,
            boolean afectoALaEntrega,
            UUID reportadoPor,
            OffsetDateTime notificadoEn,
            OffsetDateTime fechaLimiteSubsanacion,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "registro_incumplimiento")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("codigo_expediente", String.class), codigoExpediente)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("participante_id", UUID.class), participanteId)
                .set(DSL.field("grupo_id", UUID.class), grupoId)
                .set(DSL.field("periodo_id", UUID.class), periodoId)
                .set(DSL.field("cupo_id", UUID.class), cupoId)
                .set(DSL.field("obligacion_id", UUID.class), obligacionId)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("severidad", String.class), severidad)
                // El estado AL DETECTAR. El corriente vive en el historial: esta
                // columna no se puede mover (append-only).
                .set(DSL.field("estado", String.class), "NOTIFICADO")
                .set(DSL.field("origen_deteccion", String.class), origenDeteccion)
                .set(DSL.field("monto_involucrado", BigDecimal.class), montoInvolucrado.monto())
                .set(DSL.field("monto_recuperado", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("monto_castigado", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("dias_mora_al_detectar", Short.class), (short) diasMora)
                .set(DSL.field("dias_mora_actuales", Short.class), (short) diasMora)
                .set(DSL.field("es_reincidencia", Boolean.class), numeroReincidencia > 1)
                .set(DSL.field("numero_reincidencia", Short.class), (short) numeroReincidencia)
                .set(DSL.field("afecto_a_la_entrega", Boolean.class), afectoALaEntrega)
                .set(DSL.field("detectado_en", OffsetDateTime.class), ahora)
                .set(DSL.field("reportado_por", UUID.class), reportadoPor)
                // La notificacion y su plazo entran AL ABRIR: la tabla es append-only y
                // despues no se pueden escribir. Declarar el incumplimiento y
                // comunicarselo a la persona es un solo acto — enterarse despues de que
                // el plazo empezo a correr es no tener plazo. Ver H-1 del informe.
                .set(DSL.field("notificado_en", OffsetDateTime.class), notificadoEn)
                .set(DSL.field("fecha_limite_subsanacion", OffsetDateTime.class), fechaLimiteSubsanacion)
                .set(DSL.field("version", Integer.class), 0)
                .execute();
        return id;
    }

    /**
     * El estado corriente: la ultima fila del historial, o el de deteccion si no hay.
     *
     * <p>No se lee {@code registro.estado} porque esa columna no se puede mover — es el
     * estado al detectar, y leerla daria un expediente eternamente DETECTADO.
     */
    public String estadoCorriente(DSLContext dsl, UUID registroId) {
        return dsl.select(DSL.field("estado_nuevo", String.class))
                .from(DSL.table(DSL.name("garantia", "historial_estado_incumplimiento")))
                .where(DSL.field("registro_id", UUID.class).eq(registroId))
                .orderBy(DSL.field("fecha_hora").desc(), DSL.field("id").desc())
                .limit(1)
                .fetchOptional(f -> f.get("estado_nuevo", String.class))
                .orElseGet(() ->
                        ver(dsl, registroId).map(Expediente::estadoAlDetectar).orElse(null));
    }

    /** Cada transicion es una fila con su motivo. El historial ES la maquina de estados. */
    public UUID registrarTransicion(
            DSLContext dsl,
            UUID registroId,
            String estadoAnterior,
            String estadoNuevo,
            String motivo,
            Dinero montoAsociado,
            UUID ejecutadoPor,
            boolean esAutomatico,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "historial_estado_incumplimiento")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("registro_id", UUID.class), registroId)
                .set(DSL.field("estado_anterior", String.class), estadoAnterior)
                .set(DSL.field("estado_nuevo", String.class), estadoNuevo)
                .set(DSL.field("motivo", String.class), motivo)
                .set(
                        DSL.field("monto_asociado", BigDecimal.class),
                        montoAsociado == null ? null : montoAsociado.monto())
                .set(DSL.field("ejecutado_por", UUID.class), ejecutadoPor)
                .set(DSL.field("es_automatico", Boolean.class), esAutomatico)
                .set(DSL.field("fecha_hora", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public UUID agregarEvidencia(
            DSLContext dsl,
            UUID registroId,
            String tipo,
            String descripcion,
            String urlArchivo,
            String hashArchivo,
            String contenidoJson,
            UUID aportadaPor,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "evidencia_incumplimiento")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("registro_id", UUID.class), registroId)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("url_archivo", String.class), urlArchivo)
                .set(DSL.field("hash_archivo", String.class), hashArchivo)
                .set(
                        DSL.field("contenido_estructurado", JSONB.class),
                        contenidoJson == null ? null : JSONB.valueOf(contenidoJson))
                .set(DSL.field("aportada_por", UUID.class), aportadaPor)
                .set(DSL.field("fecha_hora", OffsetDateTime.class), ahora)
                .set(DSL.field("es_inmutable", Boolean.class), true)
                .execute();
        return id;
    }

    public Optional<UUID> descargoDe(DSLContext dsl, UUID registroId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("garantia", "descargo_participante")))
                .where(DSL.field("registro_id", UUID.class).eq(registroId))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID presentarDescargo(
            DSLContext dsl,
            UUID registroId,
            UUID participanteId,
            String argumento,
            String evidenciasJson,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "descargo_participante")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("registro_id", UUID.class), registroId)
                .set(DSL.field("participante_id", UUID.class), participanteId)
                .set(DSL.field("argumento", String.class), argumento)
                .set(DSL.field("evidencias", JSONB.class), JSONB.valueOf(evidenciasJson))
                .set(DSL.field("estado", String.class), "PRESENTADO")
                .set(DSL.field("presentado_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public boolean resolverDescargo(
            DSLContext dsl, UUID id, String estado, UUID resueltoPor, String resolucion, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("garantia", "descargo_participante")))
                        .set(DSL.field("estado", String.class), estado)
                        .set(DSL.field("resuelto_por", UUID.class), resueltoPor)
                        .set(DSL.field("resolucion", String.class), resolucion)
                        .set(DSL.field("resuelto_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).in("PRESENTADO", "EN_ANALISIS")))
                        .execute()
                == 1;
    }

    /** Cuantos expedientes previos tiene el usuario: define la reincidencia. */
    public int expedientesPreviosDe(DSLContext dsl, UUID usuarioId) {
        return dsl.fetchCount(
                DSL.table(DSL.name("garantia", "registro_incumplimiento")),
                DSL.field("usuario_id", UUID.class).eq(usuarioId));
    }

    private List<org.jooq.Field<?>> campos() {
        return List.of(
                DSL.field("id", UUID.class),
                DSL.field("codigo_expediente", String.class),
                DSL.field("usuario_id", UUID.class),
                DSL.field("participante_id", UUID.class),
                DSL.field("grupo_id", UUID.class),
                DSL.field("periodo_id", UUID.class),
                DSL.field("obligacion_id", UUID.class),
                DSL.field("tipo", String.class),
                DSL.field("severidad", String.class),
                DSL.field("estado", String.class),
                DSL.field("monto_involucrado", BigDecimal.class),
                DSL.field("dias_mora_al_detectar", Short.class),
                DSL.field("numero_reincidencia", Short.class),
                DSL.field("notificado_en", OffsetDateTime.class),
                DSL.field("fecha_limite_subsanacion", OffsetDateTime.class));
    }

    private Expediente aExpediente(org.jooq.Record f) {
        return new Expediente(
                f.get("id", UUID.class),
                f.get("codigo_expediente", String.class),
                f.get("usuario_id", UUID.class),
                f.get("participante_id", UUID.class),
                f.get("grupo_id", UUID.class),
                f.get("periodo_id", UUID.class),
                f.get("obligacion_id", UUID.class),
                f.get("tipo", String.class),
                f.get("severidad", String.class),
                f.get("estado", String.class),
                Dinero.de(f.get("monto_involucrado", BigDecimal.class), Moneda.BOB),
                f.get("dias_mora_al_detectar", Short.class),
                f.get("numero_reincidencia", Short.class),
                f.get("notificado_en", OffsetDateTime.class),
                f.get("fecha_limite_subsanacion", OffsetDateTime.class));
    }

    public record Expediente(
            UUID id,
            String codigoExpediente,
            UUID usuarioId,
            UUID participanteId,
            UUID grupoId,
            UUID periodoId,
            UUID obligacionId,
            String tipo,
            String severidad,
            /** El estado AL DETECTAR. El corriente sale del historial. */
            String estadoAlDetectar,
            Dinero montoInvolucrado,
            int diasMoraAlDetectar,
            int numeroReincidencia,
            OffsetDateTime notificadoEn,
            OffsetDateTime fechaLimiteSubsanacion) {}
}
