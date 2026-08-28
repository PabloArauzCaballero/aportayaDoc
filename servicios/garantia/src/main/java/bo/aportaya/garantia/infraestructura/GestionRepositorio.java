package bo.aportaya.garantia.infraestructura;

import bo.aportaya.garantia.dominio.TopeDelAval;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * El aval, la subrogacion, la lista de restriccion, el reemplazo y la disolucion.
 *
 * <p>Todo lo que pasa **despues** de que el fondo cubrio: quien responde, a quien se le
 * cobra, a quien se restringe, y como termina un grupo que ya no puede seguir.
 */
@Component
public class GestionRepositorio {

    // ---------------------------------------------------------------- aval

    public Optional<Aval> avalVigente(DSLContext dsl, UUID grupoId, UUID participanteAvaladoId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("avalista_usuario_id", UUID.class),
                        DSL.field("monto_maximo_avalado", BigDecimal.class),
                        DSL.field("porcentaje_responsabilidad", BigDecimal.class),
                        DSL.field("estado", String.class))
                .from(DSL.table(DSL.name("garantia", "aval_participante")))
                .where(DSL.field("grupo_id", UUID.class)
                        .eq(grupoId)
                        .and(DSL.field("participante_avalado_id", UUID.class).eq(participanteAvaladoId))
                        .and(DSL.field("estado", String.class).eq("VIGENTE")))
                .fetchOptional(f -> new Aval(
                        f.get("id", UUID.class),
                        f.get("avalista_usuario_id", UUID.class),
                        Dinero.de(f.get("monto_maximo_avalado", BigDecimal.class), Moneda.BOB),
                        f.get("porcentaje_responsabilidad", BigDecimal.class),
                        f.get("estado", String.class)));
    }

    /** Lo ya ejecutado de un aval: el tope se mide contra esto, no contra cero. */
    public Dinero ejecutadoDe(DSLContext dsl, UUID avalId, Moneda moneda) {
        BigDecimal suma = dsl.select(
                        DSL.coalesce(DSL.sum(DSL.field("monto_ejecutado", BigDecimal.class)), BigDecimal.ZERO))
                .from(DSL.table(DSL.name("garantia", "ejecucion_aval")))
                .where(DSL.field("aval_id", UUID.class).eq(avalId))
                .fetchOne(0, BigDecimal.class);
        return Dinero.de(suma, moneda);
    }

    public TopeDelAval topeDe(DSLContext dsl, Aval aval, Moneda moneda) {
        return new TopeDelAval(
                aval.montoMaximoAvalado(), aval.porcentajeResponsabilidad(), ejecutadoDe(dsl, aval.id(), moneda));
    }

    public Optional<UUID> ejecucionDe(DSLContext dsl, UUID avalId, UUID registroId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("garantia", "ejecucion_aval")))
                .where(DSL.field("aval_id", UUID.class)
                        .eq(avalId)
                        .and(DSL.field("registro_id", UUID.class).eq(registroId)))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID ejecutarAval(
            DSLContext dsl,
            UUID avalId,
            UUID registroId,
            UUID deudaId,
            Dinero monto,
            OffsetDateTime notificada,
            OffsetDateTime plazoRespuesta,
            boolean generaDeudaDelAvalista) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "ejecucion_aval")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("aval_id", UUID.class), avalId)
                .set(DSL.field("registro_id", UUID.class), registroId)
                .set(DSL.field("deuda_id", UUID.class), deudaId)
                .set(DSL.field("monto_ejecutado", BigDecimal.class), monto.monto())
                .set(DSL.field("estado", String.class), "NOTIFICADA")
                .set(DSL.field("notificada_en", OffsetDateTime.class), notificada)
                .set(DSL.field("plazo_respuesta", OffsetDateTime.class), plazoRespuesta)
                .set(DSL.field("genera_deuda_del_avalista", Boolean.class), generaDeudaDelAvalista)
                .execute();
        return id;
    }

    /**
     * La subrogacion: quien pago pasa a ser el acreedor.
     *
     * <p>Es lo que impide que el deudor se quede sin deber nada porque otro pago por
     * el. La deuda no desaparece: cambia de acreedor.
     */
    public UUID subrogar(
            DSLContext dsl,
            UUID coberturaId,
            UUID deudaId,
            String acreedorOriginal,
            String acreedorSubrogado,
            Dinero monto,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "subrogacion")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("cobertura_id", UUID.class), coberturaId)
                .set(DSL.field("deuda_id", UUID.class), deudaId)
                .set(DSL.field("acreedor_original", String.class), acreedorOriginal)
                .set(DSL.field("acreedor_subrogado", String.class), acreedorSubrogado)
                .set(DSL.field("monto_subrogado", BigDecimal.class), monto.monto())
                .set(DSL.field("fecha", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public boolean marcarDeudaSubrogada(DSLContext dsl, UUID deudaId, int versionLeida) {
        return dsl.update(DSL.table(DSL.name("garantia", "deuda_participante")))
                        .set(DSL.field("es_subrogada", Boolean.class), true)
                        .set(DSL.field("version", Integer.class), versionLeida + 1)
                        .where(DSL.field("id", UUID.class)
                                .eq(deudaId)
                                .and(DSL.field("version", Integer.class).eq(versionLeida))
                                .and(DSL.field("es_subrogada", Boolean.class).isFalse()))
                        .execute()
                == 1;
    }

    // --------------------------------------------------------- restriccion

    /** La restriccion vigente de un usuario, si tiene (R-GAR-05). */
    /**
     * Lo que el deudor tendria que pagar para salir de la lista.
     *
     * <p>Una restriccion sin salida es una condena. Quien consulta necesita poder
     * decirle a la persona cuanto es, no solo que no puede entrar a un grupo.
     */
    public java.math.BigDecimal deudaViva(DSLContext dsl, UUID usuarioId) {
        var fila = dsl.fetchOne(
                """
                SELECT COALESCE(SUM(d.saldo_actual), 0) AS viva
                  FROM garantia.deuda_participante d
                 WHERE d.usuario_id = ? AND d.estado = 'VIGENTE'
                """,
                usuarioId);
        return fila == null ? java.math.BigDecimal.ZERO : fila.get("viva", java.math.BigDecimal.class);
    }

    public Optional<Restriccion> restriccionVigente(DSLContext dsl, UUID usuarioId, OffsetDateTime momento) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("nivel_restriccion", String.class),
                        DSL.field("incluido_en", OffsetDateTime.class),
                        DSL.field("vigente_hasta", OffsetDateTime.class))
                .from(DSL.table(DSL.name("garantia", "lista_restriccion_interna")))
                .where(DSL.field("usuario_id", UUID.class)
                        .eq(usuarioId)
                        .and(DSL.field("retirado_en", OffsetDateTime.class).isNull())
                        .and(DSL.field("vigente_hasta", OffsetDateTime.class)
                                .isNull()
                                .or(DSL.field("vigente_hasta", OffsetDateTime.class)
                                        .gt(momento))))
                .fetchOptional(f -> new Restriccion(
                        f.get("id", UUID.class),
                        f.get("nivel_restriccion", String.class),
                        f.get("incluido_en", OffsetDateTime.class),
                        f.get("vigente_hasta", OffsetDateTime.class)));
    }

    public UUID restringir(
            DSLContext dsl,
            UUID usuarioId,
            UUID registroOrigenId,
            String motivo,
            String nivel,
            Dinero montoAdeudado,
            OffsetDateTime desde,
            OffsetDateTime hasta) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "lista_restriccion_interna")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("registro_origen_id", UUID.class), registroOrigenId)
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("nivel_restriccion", String.class), nivel)
                .set(DSL.field("monto_adeudado", BigDecimal.class), montoAdeudado.monto())
                .set(DSL.field("incluido_en", OffsetDateTime.class), desde)
                .set(DSL.field("vigente_hasta", OffsetDateTime.class), hasta)
                .execute();
        return id;
    }

    /** El levantamiento **se motiva** (R-GAR-05): sin motivo escrito no se levanta. */
    public boolean levantar(DSLContext dsl, UUID id, UUID retiradoPor, String motivo, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("garantia", "lista_restriccion_interna")))
                        .set(DSL.field("retirado_en", OffsetDateTime.class), ahora)
                        .set(DSL.field("retirado_por", UUID.class), retiradoPor)
                        .set(DSL.field("motivo_retiro", String.class), motivo)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("retirado_en", OffsetDateTime.class)
                                        .isNull()))
                        .execute()
                == 1;
    }

    // ----------------------------------------------------------- reemplazo

    public UUID proponerReemplazo(
            DSLContext dsl,
            UUID grupoId,
            UUID cupoId,
            UUID registroId,
            UUID salienteId,
            UUID entranteId,
            Dinero deudaAsumida,
            Dinero deudaRetenida,
            boolean conservaTurno,
            String estado,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "reemplazo_participante")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("grupo_id", UUID.class), grupoId)
                .set(DSL.field("cupo_id", UUID.class), cupoId)
                .set(DSL.field("registro_id", UUID.class), registroId)
                .set(DSL.field("participante_saliente_id", UUID.class), salienteId)
                .set(DSL.field("participante_entrante_id", UUID.class), entranteId)
                .set(DSL.field("deuda_asumida_por_entrante", BigDecimal.class), deudaAsumida.monto())
                .set(DSL.field("deuda_retenida_por_saliente", BigDecimal.class), deudaRetenida.monto())
                .set(DSL.field("conserva_orden_de_turno", Boolean.class), conservaTurno)
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("fecha", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public boolean cambiarEstadoDeReemplazo(DSLContext dsl, UUID id, String desde, String hacia) {
        return dsl.update(DSL.table(DSL.name("garantia", "reemplazo_participante")))
                        .set(DSL.field("estado", String.class), hacia)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).eq(desde)))
                        .execute()
                == 1;
    }

    // ---------------------------------------------------------- disolucion

    public Optional<UUID> disolucionDe(DSLContext dsl, UUID grupoId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("garantia", "disolucion_anticipada")))
                .where(DSL.field("grupo_id", UUID.class).eq(grupoId))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID iniciarDisolucion(
            DSLContext dsl,
            UUID grupoId,
            String causal,
            String motivo,
            Dinero totalAportado,
            Dinero totalEntregado,
            Dinero saldoADistribuir,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "disolucion_anticipada")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("grupo_id", UUID.class), grupoId)
                .set(DSL.field("causal", String.class), causal)
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("total_aportado_grupo", BigDecimal.class), totalAportado.monto())
                .set(DSL.field("total_entregado", BigDecimal.class), totalEntregado.monto())
                .set(DSL.field("saldo_a_distribuir", BigDecimal.class), saldoADistribuir.monto())
                .set(DSL.field("estado", String.class), "CALCULADA")
                .set(DSL.field("iniciada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /**
     * Cierra la disolucion.
     *
     * <p>{@code tg_disolucion_cuadra} exige que la cuenta del grupo cierre **en cero**
     * (R-GRP-13): un grupo disuelto con saldo es plata de alguien que quedo sin dueno.
     */
    public boolean cerrarDisolucion(DSLContext dsl, UUID id, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("garantia", "disolucion_anticipada")))
                        .set(DSL.field("estado", String.class), "CERRADA")
                        .set(DSL.field("cerrada_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).in("CALCULADA", "EJECUTADA")))
                        .execute()
                == 1;
    }

    public record Aval(
            UUID id,
            UUID avalistaUsuarioId,
            Dinero montoMaximoAvalado,
            BigDecimal porcentajeResponsabilidad,
            String estado) {}

    public record Restriccion(UUID id, String nivel, OffsetDateTime desde, OffsetDateTime hasta) {}
}
