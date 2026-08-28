package bo.aportaya.entregas.infraestructura;

import bo.aportaya.entregas.dominio.LiquidacionDeEntrega;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
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
 * {@code entrega_fondo}, {@code deduccion_entrega} y {@code validacion_pre_entrega}.
 *
 * <p>{@code tg_deduccion_recalcula} recalcula los totales de la entrega cada vez que se
 * agrega o quita una deduccion: los importes de la cabecera **no se escriben a mano**.
 * Si se pudieran, un neto y sus deducciones podrian dejar de coincidir sin que nada
 * avise.
 */
@Component
public class EntregaRepositorio {

    public Optional<Entrega> ver(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("entregas", "entrega_fondo")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(this::aEntrega);
    }

    /** Con candado: dos autorizaciones de la misma entrega se ponen en fila. */
    public Optional<Entrega> bloquear(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("entregas", "entrega_fondo")))
                .where(DSL.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOptional(this::aEntrega);
    }

    public UUID crear(
            DSLContext dsl,
            UUID grupoId,
            UUID periodoId,
            UUID turnoId,
            UUID cupoId,
            UUID beneficiarioId,
            Dinero bruto,
            String metodoDesembolso,
            LocalDate fechaProgramada) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("entregas", "entrega_fondo")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("grupo_id", UUID.class), grupoId)
                .set(DSL.field("periodo_id", UUID.class), periodoId)
                .set(DSL.field("turno_id", UUID.class), turnoId)
                .set(DSL.field("cupo_id", UUID.class), cupoId)
                .set(DSL.field("beneficiario_participante_id", UUID.class), beneficiarioId)
                .set(DSL.field("monto_bolsa_bruto", BigDecimal.class), bruto.monto())
                .set(DSL.field("total_deducciones", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("monto_neto_a_entregar", BigDecimal.class), bruto.monto())
                .set(DSL.field("monto_efectivamente_entregado", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("moneda", String.class), bruto.moneda().name())
                .set(DSL.field("estado", String.class), "PROGRAMADA")
                .set(DSL.field("metodo_desembolso", String.class), metodoDesembolso)
                .set(DSL.field("fecha_programada", LocalDate.class), fechaProgramada)
                .set(DSL.field("version", Integer.class), 0)
                .execute();
        return id;
    }

    /** Las deducciones las escribe una a una; el trigger recalcula la cabecera. */
    public UUID agregarDeduccion(DSLContext dsl, UUID entregaId, LiquidacionDeEntrega.Deduccion deduccion) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("entregas", "deduccion_entrega")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("entrega_id", UUID.class), entregaId)
                .set(DSL.field("tipo", String.class), deduccion.tipo())
                .set(DSL.field("descripcion", String.class), deduccion.descripcion())
                .set(DSL.field("monto", BigDecimal.class), deduccion.monto().monto())
                .set(DSL.field("referencia_origen_id", UUID.class), deduccion.referenciaOrigenId())
                .set(DSL.field("es_obligatoria", Boolean.class), deduccion.esObligatoria())
                .execute();
        return id;
    }

    /** Mueve el estado desde el que se espera. Version optimista encima. */
    public boolean cambiarEstado(DSLContext dsl, UUID id, List<String> desde, String hacia, int versionLeida) {
        return dsl.update(DSL.table(DSL.name("entregas", "entrega_fondo")))
                        .set(DSL.field("estado", String.class), hacia)
                        .set(DSL.field("version", Integer.class), versionLeida + 1)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).in(desde))
                                .and(DSL.field("version", Integer.class).eq(versionLeida)))
                        .execute()
                == 1;
    }

    public boolean autorizar(DSLContext dsl, UUID id, UUID autorizadaPor, OffsetDateTime ahora, int versionLeida) {
        return dsl.update(DSL.table(DSL.name("entregas", "entrega_fondo")))
                        .set(DSL.field("estado", String.class), "AUTORIZADA")
                        .set(DSL.field("autorizada_por", UUID.class), autorizadaPor)
                        .set(DSL.field("fecha_autorizacion", OffsetDateTime.class), ahora)
                        .set(DSL.field("version", Integer.class), versionLeida + 1)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).in("PROGRAMADA", "LISTA_PARA_ENTREGA"))
                                .and(DSL.field("version", Integer.class).eq(versionLeida)))
                        .execute()
                == 1;
    }

    public boolean marcarEntregada(
            DSLContext dsl, UUID id, UUID ejecutadaPor, Dinero entregado, OffsetDateTime ahora, int versionLeida) {
        return dsl.update(DSL.table(DSL.name("entregas", "entrega_fondo")))
                        .set(DSL.field("estado", String.class), "ENTREGADA")
                        .set(DSL.field("ejecutada_por", UUID.class), ejecutadaPor)
                        .set(DSL.field("monto_efectivamente_entregado", BigDecimal.class), entregado.monto())
                        .set(DSL.field("fecha_entrega", OffsetDateTime.class), ahora)
                        .set(DSL.field("version", Integer.class), versionLeida + 1)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).in("AUTORIZADA", "EN_PROCESO_DESEMBOLSO"))
                                .and(DSL.field("version", Integer.class).eq(versionLeida)))
                        .execute()
                == 1;
    }

    /** Las validaciones bloqueantes que no pasaron. Sin esto la entrega no sale. */
    public int bloqueantesSinAprobar(DSLContext dsl, UUID entregaId) {
        return dsl.fetchCount(
                DSL.table(DSL.name("entregas", "validacion_pre_entrega")),
                DSL.field("entrega_id", UUID.class)
                        .eq(entregaId)
                        .and(DSL.field("es_bloqueante", Boolean.class).isTrue())
                        .and(DSL.field("resultado", String.class).eq("RECHAZADA")));
    }

    public UUID registrarValidacion(
            DSLContext dsl,
            UUID entregaId,
            UUID reglaId,
            String resultado,
            String esperado,
            String obtenido,
            boolean esBloqueante) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("entregas", "validacion_pre_entrega")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("entrega_id", UUID.class), entregaId)
                .set(DSL.field("regla_id", UUID.class), reglaId)
                .set(DSL.field("resultado", String.class), resultado)
                .set(DSL.field("valor_esperado", String.class), esperado)
                .set(DSL.field("valor_obtenido", String.class), obtenido)
                .set(DSL.field("es_bloqueante", Boolean.class), esBloqueante)
                .execute();
        return id;
    }

    private List<org.jooq.Field<?>> campos() {
        return List.of(
                DSL.field("id", UUID.class),
                DSL.field("grupo_id", UUID.class),
                DSL.field("periodo_id", UUID.class),
                DSL.field("turno_id", UUID.class),
                DSL.field("beneficiario_participante_id", UUID.class),
                DSL.field("cuenta_destino_id", UUID.class),
                DSL.field("monto_bolsa_bruto", BigDecimal.class),
                DSL.field("total_deducciones", BigDecimal.class),
                DSL.field("monto_neto_a_entregar", BigDecimal.class),
                DSL.field("moneda", String.class),
                DSL.field("estado", String.class),
                DSL.field("autorizada_por", UUID.class),
                DSL.field("version", Integer.class));
    }

    private Entrega aEntrega(org.jooq.Record f) {
        Moneda moneda = Moneda.valueOf(f.get("moneda", String.class));
        return new Entrega(
                f.get("id", UUID.class),
                f.get("grupo_id", UUID.class),
                f.get("periodo_id", UUID.class),
                f.get("turno_id", UUID.class),
                f.get("beneficiario_participante_id", UUID.class),
                f.get("cuenta_destino_id", UUID.class),
                Dinero.de(f.get("monto_bolsa_bruto", BigDecimal.class), moneda),
                Dinero.de(f.get("total_deducciones", BigDecimal.class), moneda),
                Dinero.de(f.get("monto_neto_a_entregar", BigDecimal.class), moneda),
                f.get("estado", String.class),
                f.get("autorizada_por", UUID.class),
                f.get("version", Integer.class));
    }

    public record Entrega(
            UUID id,
            UUID grupoId,
            UUID periodoId,
            UUID turnoId,
            UUID beneficiarioId,
            UUID cuentaDestinoId,
            Dinero bruto,
            Dinero totalDeducciones,
            Dinero neto,
            String estado,
            UUID autorizadaPor,
            int version) {}
}
