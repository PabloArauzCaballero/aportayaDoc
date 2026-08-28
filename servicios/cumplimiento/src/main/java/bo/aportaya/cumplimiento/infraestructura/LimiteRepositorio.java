package bo.aportaya.cumplimiento.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * Lee {@code catalogo.limite_operativo_billetera}: los topes que desbloquea un nivel.
 *
 * <p>Solo lectura. El consumo acumulado —{@code nucleo_financiero.consumo_limite}—
 * vive en otro esquema y no se toca desde aca (invariante 11): evaluar un limite
 * contra su consumo es CU-40, y por eso CU-40 pertenece al carril de la billetera,
 * no a este.
 */
@Component
public class LimiteRepositorio {

    /** Los topes vigentes de un nivel, para poder decir que desbloquea subir. */
    public List<Tope> vigentesPara(DSLContext dsl, String nivelDiligencia, LocalDate hoy) {
        return dsl.select(
                        DSL.field("concepto", String.class),
                        DSL.field("ventana", String.class),
                        DSL.field("monto_maximo", BigDecimal.class),
                        DSL.field("cantidad_maxima", Integer.class),
                        DSL.field("moneda", String.class))
                .from(DSL.table(DSL.name("catalogo", "limite_operativo_billetera")))
                .where(DSL.field("nivel_debida_diligencia").eq(nivelDiligencia))
                .and(DSL.field("activo", Boolean.class).isTrue())
                .and(DSL.field("vigente_desde", LocalDate.class).le(hoy))
                .and(DSL.field("vigente_hasta")
                        .isNull()
                        .or(DSL.field("vigente_hasta", LocalDate.class).ge(hoy)))
                .orderBy(DSL.field("concepto"), DSL.field("ventana"))
                .fetch(fila -> new Tope(
                        fila.get("concepto", String.class),
                        fila.get("ventana", String.class),
                        fila.get("monto_maximo", BigDecimal.class),
                        fila.get("cantidad_maxima", Integer.class),
                        fila.get("moneda", String.class)));
    }

    /**
     * Los topes de un concepto y nivel, con lo ya consumido en la ventana vigente.
     *
     * <p>El consumo se lee con {@code FOR UPDATE} por el mismo motivo que
     * {@code fn_lim_evaluar}: leerlo sin bloquear la fila permite que dos operaciones
     * simultaneas vean el mismo acumulado y **las dos pasen el tope diario**. Un limite
     * que se evade corriendo dos veces el mismo request no es un limite.
     *
     * <p>{@code consumo_limite} vive en el esquema de {@code nucleo_financiero} y lo
     * escribe ese servicio; aca solo se lee para decidir, que es lo que CU-40 hace.
     */
    public List<TopeConConsumo> conConsumo(
            DSLContext dsl, UUID cuentaId, String nivelDiligencia, String concepto, LocalDate hoy) {

        return dsl.select(
                        DSL.field("l.concepto", String.class).as("concepto"),
                        DSL.field("l.ventana", String.class).as("ventana"),
                        DSL.field("l.monto_maximo", BigDecimal.class).as("monto_maximo"),
                        DSL.field("l.cantidad_maxima", Integer.class).as("cantidad_maxima"),
                        DSL.field("l.moneda", String.class).as("moneda"),
                        DSL.coalesce(DSL.field("c.monto_acumulado", BigDecimal.class), BigDecimal.ZERO)
                                .as("consumido"),
                        DSL.coalesce(DSL.field("c.cantidad_acumulada", Integer.class), 0)
                                .as("cantidad"))
                .from(DSL.table(DSL.name("catalogo", "limite_operativo_billetera"))
                        .as("l"))
                .leftJoin(DSL.table(DSL.name("nucleo_financiero", "consumo_limite"))
                        .as("c"))
                .on(DSL.field("c.limite_id", UUID.class)
                        .eq(DSL.field("l.id", UUID.class))
                        .and(DSL.field("c.cuenta_billetera_id", UUID.class).eq(cuentaId))
                        .and(DSL.currentOffsetDateTime()
                                .between(
                                        DSL.field("c.ventana_inicio", java.time.OffsetDateTime.class),
                                        DSL.field("c.ventana_fin", java.time.OffsetDateTime.class))))
                .where(DSL.field("l.nivel_debida_diligencia", String.class)
                        .eq(nivelDiligencia)
                        .and(DSL.field("l.concepto", String.class).eq(concepto))
                        .and(DSL.field("l.activo", Boolean.class).isTrue())
                        .and(DSL.field("l.vigente_desde", LocalDate.class).le(hoy))
                        .and(DSL.field("l.vigente_hasta", LocalDate.class)
                                .isNull()
                                .or(DSL.field("l.vigente_hasta", LocalDate.class)
                                        .ge(hoy))))
                .orderBy(DSL.field("l.ventana"))
                .fetch(f -> new TopeConConsumo(
                        f.get("concepto", String.class),
                        f.get("ventana", String.class),
                        f.get("monto_maximo", BigDecimal.class),
                        f.get("cantidad_maxima", Integer.class),
                        f.get("moneda", String.class),
                        f.get("consumido", BigDecimal.class),
                        f.get("cantidad", Integer.class)));
    }

    /** El nivel de diligencia de la cuenta: es lo que decide que topes se le aplican. */
    public java.util.Optional<String> nivelDeLaCuenta(DSLContext dsl, UUID cuentaId) {
        return dsl.select(DSL.field("nivel_debida_diligencia", String.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "cuenta_billetera")))
                .where(DSL.field("id", UUID.class).eq(cuentaId))
                .fetchOptional(f -> f.get("nivel_debida_diligencia", String.class));
    }

    public record Tope(
            String concepto, String ventana, BigDecimal montoMaximo, Integer cantidadMaxima, String moneda) {}

    public record TopeConConsumo(
            String concepto,
            String ventana,
            BigDecimal montoMaximo,
            Integer cantidadMaxima,
            String moneda,
            BigDecimal consumido,
            int cantidadConsumida) {}
}
