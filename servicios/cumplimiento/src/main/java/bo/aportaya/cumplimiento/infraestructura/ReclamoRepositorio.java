package bo.aportaya.cumplimiento.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code punto_reclamo}, {@code reclamo_cliente} e {@code instancia_reclamo}.
 *
 * <p>El plazo se **guarda al ingresar** (R-CON-01) y la fecha de conservacion tambien:
 * diez años, y la base lo verifica ({@code ck_reclamo_conservacion}). Las dos cosas
 * protegen lo mismo — que el cliente pueda comprobar despues cual era el plazo
 * prometido y que el expediente siga estando cuando lo reclame.
 */
@Component
public class ReclamoRepositorio {

    public Optional<Punto> puntoPorCodigo(DSLContext dsl, String codigo) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("tipo", String.class),
                        DSL.field("activo", Boolean.class))
                .from(DSL.table(DSL.name("cumplimiento", "punto_reclamo")))
                .where(DSL.field("codigo", String.class).eq(codigo))
                .fetchOptional(f -> new Punto(
                        f.get("id", UUID.class), f.get("tipo", String.class), f.get("activo", Boolean.class)));
    }

    public UUID ingresar(
            DSLContext dsl,
            String codigo,
            UUID usuarioId,
            UUID puntoId,
            String categoria,
            String producto,
            BigDecimal montoReclamado,
            String descripcion,
            String canal,
            int diasHabiles,
            OffsetDateTime ingreso,
            OffsetDateTime plazoRespuesta,
            LocalDate conservarHasta) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "reclamo_cliente")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("codigo", String.class), codigo)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("punto_reclamo_id", UUID.class), puntoId)
                .set(DSL.field("categoria", String.class), categoria)
                .set(DSL.field("producto", String.class), producto)
                .set(DSL.field("monto_reclamado", BigDecimal.class), montoReclamado)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("canal_ingreso", String.class), canal)
                .set(DSL.field("estado", String.class), "INGRESADO")
                .set(DSL.field("fecha_ingreso", OffsetDateTime.class), ingreso)
                .set(DSL.field("dias_habiles_plazo", Short.class), (short) diasHabiles)
                .set(DSL.field("plazo_respuesta", OffsetDateTime.class), plazoRespuesta)
                .set(DSL.field("conservar_hasta", LocalDate.class), conservarHasta)
                .execute();
        return id;
    }

    public Optional<Reclamo> porId(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("estado", String.class),
                        DSL.field("resultado", String.class),
                        DSL.field("monto_reclamado", BigDecimal.class),
                        DSL.field("fecha_ingreso", OffsetDateTime.class),
                        DSL.field("plazo_respuesta", OffsetDateTime.class),
                        DSL.field("plazo_prorrogado_hasta", OffsetDateTime.class),
                        DSL.field("fecha_respuesta", OffsetDateTime.class))
                .from(DSL.table(DSL.name("cumplimiento", "reclamo_cliente")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Reclamo(
                        f.get("id", UUID.class),
                        f.get("usuario_id", UUID.class),
                        f.get("estado", String.class),
                        f.get("resultado", String.class),
                        f.get("monto_reclamado", BigDecimal.class),
                        f.get("fecha_ingreso", OffsetDateTime.class),
                        f.get("plazo_respuesta", OffsetDateTime.class),
                        f.get("plazo_prorrogado_hasta", OffsetDateTime.class),
                        f.get("fecha_respuesta", OffsetDateTime.class)));
    }

    public boolean prorrogar(
            DSLContext dsl,
            UUID reclamoId,
            OffsetDateTime hasta,
            OffsetDateTime comunicadaAlCliente,
            OffsetDateTime comunicadaAlOrganismo,
            String justificacion) {

        return dsl.update(DSL.table(DSL.name("cumplimiento", "reclamo_cliente")))
                        .set(DSL.field("plazo_prorrogado_hasta", OffsetDateTime.class), hasta)
                        .set(DSL.field("prorroga_comunicada_al_cliente_en", OffsetDateTime.class), comunicadaAlCliente)
                        .set(
                                DSL.field("prorroga_comunicada_al_organismo_en", OffsetDateTime.class),
                                comunicadaAlOrganismo)
                        .set(DSL.field("justificacion_prorroga", String.class), justificacion)
                        .set(DSL.field("estado", String.class), "EN_ANALISIS")
                        .where(DSL.field("id", UUID.class)
                                .eq(reclamoId)
                                .and(DSL.field("plazo_prorrogado_hasta", OffsetDateTime.class)
                                        .isNull()))
                        .execute()
                == 1;
    }

    /**
     * Cierra el reclamo con su resultado.
     *
     * <p>{@code devolucionId} no es opcional cuando el resultado es FAVORABLE y habia
     * monto: {@code ck_reclamo_reparacion} lo rechaza (R-CON-04). Darle la razon a
     * alguien y no devolverle la plata es darsela de mentira.
     */
    public boolean cerrar(
            DSLContext dsl,
            UUID reclamoId,
            String resultado,
            String respuesta,
            UUID devolucionId,
            UUID responsableId,
            OffsetDateTime ahora) {

        return dsl.update(DSL.table(DSL.name("cumplimiento", "reclamo_cliente")))
                        .set(DSL.field("estado", String.class), "CERRADO")
                        .set(DSL.field("resultado", String.class), resultado)
                        .set(DSL.field("respuesta", String.class), respuesta)
                        .set(DSL.field("devolucion_comision_id", UUID.class), devolucionId)
                        .set(DSL.field("responsable_id", UUID.class), responsableId)
                        .set(DSL.field("fecha_respuesta", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(reclamoId)
                                .and(DSL.field("estado", String.class).ne("CERRADO")))
                        .execute()
                == 1;
    }

    // ---------------------------------------------------------------- segunda instancia

    public Optional<UUID> instanciaAbierta(DSLContext dsl, UUID reclamoId, String instancia) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("cumplimiento", "instancia_reclamo")))
                .where(DSL.field("reclamo_id", UUID.class)
                        .eq(reclamoId)
                        .and(DSL.field("instancia", String.class).eq(instancia))
                        .and(DSL.field("estado", String.class).in("PRESENTADA", "EN_TRAMITE")))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID elevar(
            DSLContext dsl, UUID reclamoId, String instancia, String numeroExpediente, OffsetDateTime fechaElevacion) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "instancia_reclamo")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("reclamo_id", UUID.class), reclamoId)
                .set(DSL.field("instancia", String.class), instancia)
                .set(DSL.field("fecha_elevacion", OffsetDateTime.class), fechaElevacion)
                .set(DSL.field("numero_expediente", String.class), numeroExpediente)
                .set(DSL.field("estado", String.class), "PRESENTADA")
                .execute();
        dsl.update(DSL.table(DSL.name("cumplimiento", "reclamo_cliente")))
                .set(DSL.field("estado", String.class), "ELEVADO")
                .where(DSL.field("id", UUID.class).eq(reclamoId))
                .execute();
        return id;
    }

    public boolean resolverInstancia(
            DSLContext dsl, UUID instanciaId, String resolucion, BigDecimal montoResarcido, OffsetDateTime ahora) {

        return dsl.update(DSL.table(DSL.name("cumplimiento", "instancia_reclamo")))
                        .set(DSL.field("estado", String.class), "RESUELTA")
                        .set(DSL.field("resolucion", String.class), resolucion)
                        .set(DSL.field("monto_resarcido", BigDecimal.class), montoResarcido)
                        .set(DSL.field("fecha_resolucion", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(instanciaId)
                                .and(DSL.field("estado", String.class).in("PRESENTADA", "EN_TRAMITE")))
                        .execute()
                == 1;
    }

    /** Reclamos cuyo plazo vencio sin respuesta: lo que el control diario escala. */
    public List<Reclamo> vencidosSinResponder(DSLContext dsl, OffsetDateTime corte) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("estado", String.class),
                        DSL.field("resultado", String.class),
                        DSL.field("monto_reclamado", BigDecimal.class),
                        DSL.field("fecha_ingreso", OffsetDateTime.class),
                        DSL.field("plazo_respuesta", OffsetDateTime.class),
                        DSL.field("plazo_prorrogado_hasta", OffsetDateTime.class),
                        DSL.field("fecha_respuesta", OffsetDateTime.class))
                .from(DSL.table(DSL.name("cumplimiento", "reclamo_cliente")))
                .where(DSL.field("fecha_respuesta", OffsetDateTime.class)
                        .isNull()
                        .and(DSL.coalesce(
                                        DSL.field("plazo_prorrogado_hasta", OffsetDateTime.class),
                                        DSL.field("plazo_respuesta", OffsetDateTime.class))
                                .lt(corte)))
                .fetch(f -> new Reclamo(
                        f.get("id", UUID.class),
                        f.get("usuario_id", UUID.class),
                        f.get("estado", String.class),
                        f.get("resultado", String.class),
                        f.get("monto_reclamado", BigDecimal.class),
                        f.get("fecha_ingreso", OffsetDateTime.class),
                        f.get("plazo_respuesta", OffsetDateTime.class),
                        f.get("plazo_prorrogado_hasta", OffsetDateTime.class),
                        f.get("fecha_respuesta", OffsetDateTime.class)));
    }

    public record Punto(UUID id, String tipo, boolean activo) {}

    public record Reclamo(
            UUID id,
            UUID usuarioId,
            String estado,
            String resultado,
            BigDecimal montoReclamado,
            OffsetDateTime fechaIngreso,
            OffsetDateTime plazoRespuesta,
            OffsetDateTime plazoProrrogadoHasta,
            OffsetDateTime fechaRespuesta) {}
}
