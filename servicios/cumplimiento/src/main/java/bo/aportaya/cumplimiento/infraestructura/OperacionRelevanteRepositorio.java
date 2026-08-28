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
 * {@code umbral_reporte_uif} (catalogo) y {@code registro_operacion_relevante}.
 *
 * <p>El umbral es **dato con vigencia y cita normativa** (R-UIF-01), no una constante:
 * el dia que la UIF lo mueva, la fila nueva cierra la vieja y las operaciones pasadas
 * conservan el {@code umbral_aplicado_usd} con el que se juzgaron. Sin eso, revisar un
 * registro de hace dos años daria un resultado distinto del que se reporto.
 */
@Component
public class OperacionRelevanteRepositorio {

    /** Los umbrales vigentes para un concepto a una fecha. Sin umbral, no hay registro. */
    public List<Umbral> umbralesVigentes(DSLContext dsl, String concepto, LocalDate fecha) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("formulario", String.class),
                        DSL.field("inciso", String.class),
                        DSL.field("concepto_operacion", String.class),
                        DSL.field("es_acumulado", Boolean.class),
                        DSL.field("umbral_usd", BigDecimal.class),
                        DSL.field("ventana_dias_calendario", Short.class),
                        DSL.field("exige_declaracion_origen_destino", Boolean.class),
                        DSL.field("base_normativa", String.class))
                .from(DSL.table(DSL.name("catalogo", "umbral_reporte_uif")))
                .where(DSL.field("activo", Boolean.class)
                        .isTrue()
                        .and(DSL.field("concepto_operacion", String.class).eq(concepto))
                        .and(DSL.field("vigente_desde", LocalDate.class).le(fecha))
                        .and(DSL.field("vigente_hasta", LocalDate.class)
                                .isNull()
                                .or(DSL.field("vigente_hasta", LocalDate.class).ge(fecha))))
                .orderBy(DSL.field("formulario").asc(), DSL.field("inciso").asc())
                .fetch(f -> new Umbral(
                        f.get("id", UUID.class),
                        f.get("formulario", String.class),
                        f.get("inciso", String.class),
                        f.get("concepto_operacion", String.class),
                        f.get("es_acumulado", Boolean.class),
                        f.get("umbral_usd", BigDecimal.class),
                        f.get("ventana_dias_calendario", Short.class) == null
                                ? null
                                : f.get("ventana_dias_calendario", Short.class).intValue(),
                        f.get("exige_declaracion_origen_destino", Boolean.class),
                        f.get("base_normativa", String.class)));
    }

    /** El registro de esa transaccion y ese umbral, si ya existe (R-UIF-13). */
    public Optional<UUID> registroDe(DSLContext dsl, UUID transaccionId, UUID umbralId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("cumplimiento", "registro_operacion_relevante")))
                .where(DSL.field("transaccion_id", UUID.class)
                        .eq(transaccionId)
                        .and(DSL.field("umbral_reporte_id", UUID.class).eq(umbralId)))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    /**
     * Alta del registro, **siempre completo**.
     *
     * <p>{@code ck_operelev_declaracion} exige que un PCC-01 no exento traiga origen y
     * destino, o su motivo de exencion. Y la tabla es append-only (R-AUD-01), asi que
     * tampoco se puede completar despues. Entonces la fila nace entera o no nace: no hay
     * estado intermedio que la boveda admita.
     */
    public UUID registrar(DSLContext dsl, Registro r) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "registro_operacion_relevante")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), r.usuarioId())
                .set(DSL.field("transaccion_id", UUID.class), r.transaccionId())
                .set(DSL.field("umbral_reporte_id", UUID.class), r.umbralId())
                .set(DSL.field("operacion_inicio_ventana_id", UUID.class), r.inicioDeVentanaId())
                .set(DSL.field("formulario", String.class), r.formulario())
                .set(DSL.field("concepto_operacion", String.class), r.concepto())
                .set(DSL.field("es_acumulada", Boolean.class), r.esAcumulada())
                .set(DSL.field("ventana_desde", LocalDate.class), r.ventanaDesde())
                .set(DSL.field("ventana_hasta", LocalDate.class), r.ventanaHasta())
                .set(DSL.field("monto", BigDecimal.class), r.monto())
                .set(DSL.field("moneda", String.class), r.moneda())
                .set(DSL.field("monto_acumulado_ventana", BigDecimal.class), r.acumulado())
                .set(DSL.field("tipo_cambio_aplicado", BigDecimal.class), r.tipoDeCambio())
                .set(DSL.field("monto_equivalente_usd", BigDecimal.class), r.montoUsd())
                .set(DSL.field("umbral_aplicado_usd", BigDecimal.class), r.umbralUsd())
                .set(DSL.field("exento", Boolean.class), r.exento())
                .set(DSL.field("motivo_exencion", String.class), r.motivo())
                .set(DSL.field("origen_declarado", String.class), r.origenDeclarado())
                .set(DSL.field("destino_declarado", String.class), r.destinoDeclarado())
                .set(DSL.field("periodo_remision", String.class), r.periodo())
                .set(DSL.field("fecha_operacion", OffsetDateTime.class), r.fechaOperacion())
                .execute();
        return id;
    }

    /**
     * Los registros de un periodo y un formulario.
     *
     * <p>**No se filtra por `reporte_regulatorio_id IS NULL`, y no por descuido:** la
     * tabla es append-only (R-AUD-01), asi que esa columna no se puede escribir nunca
     * despues del alta y queda siempre en nulo. Lo que impide reportar dos veces el
     * mismo periodo es {@code uq_reporte_catalogo_periodo}, no una marca en el registro.
     * Queda declarado como hueco del carril.
     */
    public List<Pendiente> pendientesDelPeriodo(DSLContext dsl, String periodo, String formulario) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("formulario", String.class),
                        DSL.field("monto_equivalente_usd", BigDecimal.class))
                .from(DSL.table(DSL.name("cumplimiento", "registro_operacion_relevante")))
                .where(DSL.field("periodo_remision", String.class)
                        .eq(periodo)
                        .and(DSL.field("formulario", String.class).like(formulario)))
                .orderBy(DSL.field("fecha_operacion").asc())
                .fetch(f -> new Pendiente(
                        f.get("id", UUID.class),
                        f.get("formulario", String.class),
                        f.get("monto_equivalente_usd", BigDecimal.class)));
    }

    public UUID declararOrigen(
            DSLContext dsl,
            UUID usuarioId,
            UUID transaccionId,
            BigDecimal monto,
            String moneda,
            String origen,
            String descripcion,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "declaracion_origen_fondos")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("transaccion_id", UUID.class), transaccionId)
                .set(DSL.field("monto", BigDecimal.class), monto)
                .set(DSL.field("moneda", String.class), moneda)
                .set(DSL.field("origen", String.class), origen)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("estado", String.class), "DECLARADA")
                .set(DSL.field("declarada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public record Umbral(
            UUID id,
            String formulario,
            String inciso,
            String concepto,
            boolean esAcumulado,
            BigDecimal umbralUsd,
            Integer ventanaDias,
            boolean exigeDeclaracion,
            String baseNormativa) {}

    public record Registro(
            UUID usuarioId,
            UUID transaccionId,
            UUID umbralId,
            UUID inicioDeVentanaId,
            String formulario,
            String concepto,
            boolean esAcumulada,
            LocalDate ventanaDesde,
            LocalDate ventanaHasta,
            BigDecimal monto,
            String moneda,
            BigDecimal acumulado,
            BigDecimal tipoDeCambio,
            BigDecimal montoUsd,
            BigDecimal umbralUsd,
            boolean exento,
            String motivo,
            String origenDeclarado,
            String destinoDeclarado,
            String periodo,
            OffsetDateTime fechaOperacion) {}

    public record Pendiente(UUID id, String formulario, BigDecimal montoUsd) {}
}
