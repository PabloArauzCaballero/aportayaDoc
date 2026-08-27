package bo.aportaya.nucleofinanciero.infraestructura;

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
 * Los limites del catalogo y el consumo de la ventana.
 *
 * <p>El consumo se lee **con {@code FOR UPDATE}**, y no por prudencia: sin el, dos
 * operaciones simultaneas leen el mismo acumulado y las dos pasan el tope. Un limite
 * que se evade corriendo dos veces el mismo pedido no es un limite. Es la misma razon
 * por la que {@code fn_lim_evaluar} lo hace en la base.
 */
@Component
public class LimiteRepositorio {

    /** Los limites vigentes para ese concepto y nivel. Vacio significa denegar. */
    public List<Limite> vigentes(DSLContext dsl, String concepto, String nivelDiligencia, LocalDate hoy) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("ventana", String.class),
                        DSL.field("monto_maximo", BigDecimal.class),
                        DSL.field("cantidad_maxima", Integer.class),
                        DSL.field("moneda", String.class))
                .from(DSL.table(DSL.name("catalogo", "limite_operativo_billetera")))
                .where(DSL.field("concepto").eq(concepto))
                .and(DSL.field("nivel_debida_diligencia").eq(nivelDiligencia))
                .and(DSL.field("activo", Boolean.class).isTrue())
                .and(DSL.field("vigente_desde", LocalDate.class).le(hoy))
                .and(DSL.field("vigente_hasta")
                        .isNull()
                        .or(DSL.field("vigente_hasta", LocalDate.class).ge(hoy)))
                .fetch(f -> new Limite(
                        f.get("id", UUID.class),
                        f.get("ventana", String.class),
                        Optional.ofNullable(f.get("monto_maximo", BigDecimal.class))
                                .map(m -> Dinero.de(m, Moneda.valueOf(f.get("moneda", String.class)))),
                        Optional.ofNullable(f.get("cantidad_maxima", Integer.class)),
                        Moneda.valueOf(f.get("moneda", String.class))));
    }

    /**
     * El acumulado de la ventana, bloqueado.
     *
     * <p>Devuelve cero explicito cuando no hay fila. En la funcion de la base ese
     * mismo detalle esta comentado como trampa: sin inicializar, la comparacion contra
     * NULL da NULL y el limite deja de aplicarse en la primera operacion de la ventana.
     */
    public Consumo acumuladoBloqueado(
            DSLContext dsl, UUID cuentaId, UUID limiteId, OffsetDateTime inicio, Moneda moneda) {
        var fila = dsl.select(
                        DSL.field("monto_acumulado", BigDecimal.class), DSL.field("cantidad_acumulada", Integer.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "consumo_limite")))
                .where(DSL.field("cuenta_billetera_id", UUID.class).eq(cuentaId))
                .and(DSL.field("limite_id", UUID.class).eq(limiteId))
                .and(DSL.field("ventana_inicio", OffsetDateTime.class).eq(inicio))
                .forUpdate()
                .fetchOne();

        if (fila == null) {
            return new Consumo(Dinero.cero(moneda), 0);
        }
        return new Consumo(
                Dinero.de(fila.get("monto_acumulado", BigDecimal.class), moneda),
                fila.get("cantidad_acumulada", Integer.class));
    }

    /**
     * Suma la operacion al acumulado de la ventana.
     *
     * <p>{@code uq_consumo_ventana} (R-LIM-02) garantiza una fila por ventana, asi que
     * el UPSERT es la unica forma correcta: un INSERT ciego chocaria y un UPDATE ciego
     * no crearia la primera.
     */
    public void acumular(
            DSLContext dsl,
            UUID cuentaId,
            UUID limiteId,
            OffsetDateTime inicio,
            OffsetDateTime fin,
            Dinero monto,
            OffsetDateTime ahora) {

        var tabla = DSL.table(DSL.name("nucleo_financiero", "consumo_limite"));
        dsl.insertInto(tabla)
                .set(DSL.field("id", UUID.class), UUID.randomUUID())
                .set(DSL.field("cuenta_billetera_id", UUID.class), cuentaId)
                .set(DSL.field("limite_id", UUID.class), limiteId)
                .set(DSL.field("ventana_inicio", OffsetDateTime.class), inicio)
                .set(DSL.field("ventana_fin", OffsetDateTime.class), fin)
                .set(DSL.field("monto_acumulado", BigDecimal.class), monto.monto())
                .set(DSL.field("cantidad_acumulada", Integer.class), 1)
                .set(DSL.field("actualizado_en", OffsetDateTime.class), ahora)
                .onConflict(
                        DSL.field("cuenta_billetera_id", UUID.class),
                        DSL.field("limite_id", UUID.class),
                        DSL.field("ventana_inicio", OffsetDateTime.class))
                .doUpdate()
                .set(
                        DSL.field("monto_acumulado", BigDecimal.class),
                        DSL.field("consumo_limite.monto_acumulado", BigDecimal.class)
                                .plus(monto.monto()))
                .set(
                        DSL.field("cantidad_acumulada", Integer.class),
                        DSL.field("consumo_limite.cantidad_acumulada", Integer.class)
                                .plus(1))
                .set(DSL.field("actualizado_en", OffsetDateTime.class), ahora)
                .execute();
    }

    /**
     * Descuenta un importe reversado del acumulado.
     *
     * <p>Un movimiento que se reverso **no cuenta contra el limite**: si contara, un
     * error del sistema le comeria el cupo del mes a la persona.
     */
    public void devolver(DSLContext dsl, UUID cuentaId, UUID limiteId, OffsetDateTime inicio, Dinero monto) {
        dsl.update(DSL.table(DSL.name("nucleo_financiero", "consumo_limite")))
                .set(
                        DSL.field("monto_acumulado", BigDecimal.class),
                        DSL.field("monto_acumulado", BigDecimal.class).minus(monto.monto()))
                .set(
                        DSL.field("cantidad_acumulada", Integer.class),
                        DSL.field("cantidad_acumulada", Integer.class).minus(1))
                .where(DSL.field("cuenta_billetera_id", UUID.class).eq(cuentaId))
                .and(DSL.field("limite_id", UUID.class).eq(limiteId))
                .and(DSL.field("ventana_inicio", OffsetDateTime.class).eq(inicio))
                .execute();
    }

    public record Limite(
            UUID id, String ventana, Optional<Dinero> montoMaximo, Optional<Integer> cantidadMaxima, Moneda moneda) {}

    public record Consumo(Dinero monto, int cantidad) {}
}
