package bo.aportaya.aportes.infraestructura;

import bo.aportaya.aportes.dominio.SaldoDeLaObligacion.Estado;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code obligacion_aporte}: lo que cada participante debe en cada periodo. */
@Component
public class ObligacionRepositorio {

    /**
     * La obligacion, **bloqueada**.
     *
     * <p>Dos pagos simultaneos sobre la misma obligacion sin bloqueo leen el mismo
     * `monto_pagado` y los dos escriben el suyo: el segundo pisa al primero y la
     * persona paga dos veces por una sola cuota registrada.
     */
    public Optional<Obligacion> bloquear(DSLContext dsl, UUID obligacionId) {
        return leer(dsl, obligacionId, true);
    }

    public Optional<Obligacion> ver(DSLContext dsl, UUID obligacionId) {
        return leer(dsl, obligacionId, false);
    }

    private Optional<Obligacion> leer(DSLContext dsl, UUID obligacionId, boolean bloqueando) {
        var consulta = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("grupo_id", UUID.class),
                        DSL.field("periodo_id", UUID.class),
                        DSL.field("cupo_id", UUID.class),
                        DSL.field("participante_id", UUID.class),
                        DSL.field("politica_mora_id", UUID.class),
                        DSL.field("tipo", String.class),
                        DSL.field("monto_esperado", BigDecimal.class),
                        DSL.field("monto_pagado", BigDecimal.class),
                        DSL.field("monto_condonado", BigDecimal.class),
                        DSL.field("monto_cubierto_garantia", BigDecimal.class),
                        DSL.field("moneda", String.class),
                        DSL.field("estado", String.class),
                        DSL.field("fecha_vencimiento", LocalDate.class),
                        DSL.field("fecha_fin_gracia", LocalDate.class),
                        DSL.field("version", Integer.class))
                .from(DSL.table(DSL.name("aportes", "obligacion_aporte")))
                .where(DSL.field("id", UUID.class).eq(obligacionId));
        Record fila = bloqueando ? consulta.forUpdate().fetchOne() : consulta.fetchOne();

        return Optional.ofNullable(fila).map(f -> {
            Moneda moneda = Moneda.valueOf(f.get("moneda", String.class));
            return new Obligacion(
                    f.get("id", UUID.class),
                    f.get("grupo_id", UUID.class),
                    f.get("periodo_id", UUID.class),
                    f.get("cupo_id", UUID.class),
                    f.get("participante_id", UUID.class),
                    Optional.ofNullable(f.get("politica_mora_id", UUID.class)),
                    f.get("tipo", String.class),
                    new Estado(
                            Dinero.de(f.get("monto_esperado", BigDecimal.class), moneda),
                            Dinero.de(f.get("monto_pagado", BigDecimal.class), moneda),
                            Dinero.de(f.get("monto_condonado", BigDecimal.class), moneda),
                            Dinero.de(f.get("monto_cubierto_garantia", BigDecimal.class), moneda)),
                    f.get("estado", String.class),
                    f.get("fecha_vencimiento", LocalDate.class),
                    f.get("fecha_fin_gracia", LocalDate.class),
                    f.get("version", Integer.class));
        });
    }

    /** Suma lo pagado y actualiza el estado. La version optimista evita el pisado. */
    public boolean acreditar(
            DSLContext dsl,
            UUID obligacionId,
            Dinero monto,
            String nuevoEstado,
            int versionEsperada,
            OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("aportes", "obligacion_aporte")))
                        .set(
                                DSL.field("monto_pagado", BigDecimal.class),
                                DSL.field("monto_pagado", BigDecimal.class).plus(monto.monto()))
                        .set(DSL.field("estado", String.class), nuevoEstado)
                        .set(DSL.field("fecha_pago_efectivo", LocalDate.class), ahora.toLocalDate())
                        .set(DSL.field("version", Integer.class), versionEsperada + 1)
                        .where(DSL.field("id", UUID.class).eq(obligacionId))
                        .and(DSL.field("version", Integer.class).eq(versionEsperada))
                        .execute()
                > 0;
    }

    /** Devuelve lo reembolsado: la obligacion vuelve a estar pendiente por ese importe. */
    public void revertirPago(DSLContext dsl, UUID obligacionId, Dinero monto, String nuevoEstado) {
        dsl.update(DSL.table(DSL.name("aportes", "obligacion_aporte")))
                .set(
                        DSL.field("monto_pagado", BigDecimal.class),
                        DSL.field("monto_pagado", BigDecimal.class).minus(monto.monto()))
                .set(DSL.field("estado", String.class), nuevoEstado)
                .set(
                        DSL.field("version", Integer.class),
                        DSL.field("version", Integer.class).plus(1))
                .where(DSL.field("id", UUID.class).eq(obligacionId))
                .execute();
    }

    /** Crea la obligacion de recargo, encadenada a la que la origino. */
    public UUID crearRecargo(DSLContext dsl, Obligacion origen, Dinero recargo, int diasDeMora, LocalDate vencimiento) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("aportes", "obligacion_aporte")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("grupo_id", UUID.class), origen.grupoId())
                .set(DSL.field("periodo_id", UUID.class), origen.periodoId())
                .set(DSL.field("cupo_id", UUID.class), origen.cupoId())
                .set(DSL.field("participante_id", UUID.class), origen.participanteId())
                .set(DSL.field("obligacion_origen_id", UUID.class), origen.id())
                .set(DSL.field("tipo", String.class), "RECARGO_MORA")
                .set(DSL.field("monto_esperado", BigDecimal.class), recargo.monto())
                .set(DSL.field("moneda", String.class), recargo.moneda().name())
                .set(DSL.field("monto_pagado", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("monto_recargo", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("monto_condonado", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("monto_cubierto_garantia", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("estado", String.class), "PENDIENTE")
                .set(DSL.field("fecha_vencimiento", LocalDate.class), vencimiento)
                .set(DSL.field("fecha_fin_gracia", LocalDate.class), vencimiento)
                .set(DSL.field("dias_mora", Short.class), (short) diasDeMora)
                .set(DSL.field("version", Integer.class), 0)
                .execute();
        return id;
    }

    /** ¿Ya se le genero recargo a esa obligacion? Uno por origen, no uno por corrida. */
    public boolean yaTieneRecargo(DSLContext dsl, UUID origenId) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("aportes", "obligacion_aporte")),
                        DSL.field("obligacion_origen_id", UUID.class).eq(origenId),
                        DSL.field("tipo").eq("RECARGO_MORA"))
                > 0;
    }

    /** La politica de mora del grupo, o la de plataforma si el grupo no tiene. */
    public Optional<PoliticaFila> politicaDe(DSLContext dsl, Optional<UUID> grupoId) {
        var condicion = grupoId.map(g -> DSL.field("grupo_id", UUID.class)
                        .eq(g)
                        .or(DSL.field("grupo_id").isNull()))
                .orElse(DSL.field("grupo_id").isNull());
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("dias_gracia", Short.class),
                        DSL.field("tipo_recargo", String.class),
                        DSL.field("valor_recargo", BigDecimal.class),
                        DSL.field("tope_recargo", BigDecimal.class),
                        DSL.field("dias_para_mora_grave", Short.class),
                        DSL.field("dias_para_incumplimiento", Short.class))
                .from(DSL.table(DSL.name("aportes", "politica_mora")))
                .where(condicion)
                .orderBy(DSL.field("grupo_id").desc().nullsLast())
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new PoliticaFila(
                        f.get("id", UUID.class),
                        f.get("dias_gracia", Short.class),
                        f.get("tipo_recargo", String.class),
                        f.get("valor_recargo", BigDecimal.class),
                        f.get("tope_recargo", BigDecimal.class),
                        f.get("dias_para_mora_grave", Short.class),
                        f.get("dias_para_incumplimiento", Short.class)));
    }

    /** Las vencidas que todavia no tienen recargo: lo que el trabajo diario mira. */
    public List<UUID> vencidasSinRecargo(DSLContext dsl, LocalDate hoy) {
        return dsl.fetch(
                        """
                        SELECT o.id FROM aportes.obligacion_aporte o
                         WHERE o.tipo = 'APORTE_PERIODICO'
                           AND o.estado IN ('PENDIENTE', 'PARCIAL', 'EN_MORA')
                           AND o.fecha_fin_gracia < ?
                           AND NOT EXISTS (SELECT 1 FROM aportes.obligacion_aporte r
                                            WHERE r.obligacion_origen_id = o.id AND r.tipo = 'RECARGO_MORA')
                        """,
                        hoy)
                .map(f -> f.get(0, UUID.class));
    }

    public record Obligacion(
            UUID id,
            UUID grupoId,
            UUID periodoId,
            UUID cupoId,
            UUID participanteId,
            Optional<UUID> politicaMoraId,
            String tipo,
            Estado saldo,
            String estado,
            LocalDate vencimiento,
            LocalDate finDeGracia,
            int version) {}

    public record PoliticaFila(
            UUID id,
            short diasGracia,
            String tipoRecargo,
            BigDecimal valorRecargo,
            BigDecimal topeRecargo,
            short diasParaMoraGrave,
            short diasParaIncumplimiento) {}
}
