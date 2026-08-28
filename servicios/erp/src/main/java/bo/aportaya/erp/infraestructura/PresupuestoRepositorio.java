package bo.aportaya.erp.infraestructura;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code centro_costo}, {@code presupuesto}, {@code partida_presupuestaria} y
 * {@code estado_financiero_generado}.
 *
 * <p>**Un presupuesto por centro de costo y ejercicio** (R-CTB-03). Dos presupuestos
 * vigentes del mismo centro dejarian a cada area eligiendo cual mirar, y el control de
 * ejecucion perderia sentido.
 */
@Component
public class PresupuestoRepositorio {

    public UUID crearCentro(DSLContext dsl, String codigo, String nombre, String tipo) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "centro_costo")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("codigo", String.class), codigo)
                .set(DSL.field("nombre", String.class), nombre)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("activo", Boolean.class), true)
                .execute();
        return id;
    }

    public UUID crearPresupuesto(DSLContext dsl, UUID centroId, UUID ejercicioId, String nombre) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "presupuesto")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("centro_costo_id", UUID.class), centroId)
                .set(DSL.field("ejercicio_fiscal_id", UUID.class), ejercicioId)
                .set(DSL.field("nombre", String.class), nombre)
                .set(DSL.field("estado", String.class), "BORRADOR")
                .execute();
        return id;
    }

    public UUID agregarPartida(
            DSLContext dsl, UUID presupuestoId, UUID cuentaId, UUID periodoId, BigDecimal monto, String moneda) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "partida_presupuestaria")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("presupuesto_id", UUID.class), presupuestoId)
                .set(DSL.field("cuenta_contable_id", UUID.class), cuentaId)
                .set(DSL.field("periodo_contable_id", UUID.class), periodoId)
                .set(DSL.field("monto_presupuestado", BigDecimal.class), monto)
                .set(DSL.field("moneda", String.class), moneda)
                .set(DSL.field("monto_ejecutado", BigDecimal.class), BigDecimal.ZERO)
                .execute();
        return id;
    }

    /** Aprobar exige firma y fecha: {@code ck_presupuesto_aprobacion} no admite menos. */
    public boolean aprobar(DSLContext dsl, UUID presupuestoId, UUID aprobadoPor, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("erp", "presupuesto")))
                        .set(DSL.field("estado", String.class), "APROBADO")
                        .set(DSL.field("aprobado_por", UUID.class), aprobadoPor)
                        .set(DSL.field("aprobado_en", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(presupuestoId)
                                .and(DSL.field("estado", String.class).eq("BORRADOR")))
                        .execute()
                == 1;
    }

    public Optional<String> estadoDelPresupuesto(DSLContext dsl, UUID presupuestoId) {
        return dsl.select(DSL.field("estado", String.class))
                .from(DSL.table(DSL.name("erp", "presupuesto")))
                .where(DSL.field("id", UUID.class).eq(presupuestoId))
                .fetchOptional(f -> f.get("estado", String.class));
    }

    /**
     * Suma lo ejecutado en la partida de esa cuenta y ese periodo.
     *
     * <p>Devuelve false si no hay partida: **gastar en una cuenta sin presupuestar no se
     * bloquea**, se registra sin partida. Bloquearlo pararia la operacion por un olvido
     * del area de planificacion, y el control de ejecucion existe para informar, no para
     * frenar el negocio.
     */
    public boolean ejecutar(DSLContext dsl, UUID cuentaId, UUID periodoId, BigDecimal monto) {
        return dsl.update(DSL.table(DSL.name("erp", "partida_presupuestaria")))
                        .set(
                                DSL.field("monto_ejecutado", BigDecimal.class),
                                DSL.field("monto_ejecutado", BigDecimal.class).plus(monto))
                        .where(DSL.field("cuenta_contable_id", UUID.class)
                                .eq(cuentaId)
                                .and(DSL.field("periodo_contable_id", UUID.class)
                                        .eq(periodoId)))
                        .execute()
                > 0;
    }

    public List<Partida> partidas(DSLContext dsl, UUID presupuestoId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("cuenta_contable_id", UUID.class),
                        DSL.field("periodo_contable_id", UUID.class),
                        DSL.field("monto_presupuestado", BigDecimal.class),
                        DSL.field("monto_ejecutado", BigDecimal.class))
                .from(DSL.table(DSL.name("erp", "partida_presupuestaria")))
                .where(DSL.field("presupuesto_id", UUID.class).eq(presupuestoId))
                .fetch(f -> new Partida(
                        f.get("id", UUID.class),
                        f.get("cuenta_contable_id", UUID.class),
                        f.get("periodo_contable_id", UUID.class),
                        f.get("monto_presupuestado", BigDecimal.class),
                        f.get("monto_ejecutado", BigDecimal.class)));
    }

    /**
     * Los saldos por cuenta de un periodo, desde los movimientos contables.
     *
     * <p>**El signo lo da la naturaleza de la cuenta**, no la resta cruda. Una cuenta
     * deudora suma por el debe y una acreedora por el haber; calcularlas todas igual
     * dejaria pasivo y patrimonio en negativo y la ecuacion contable no cerraria nunca —
     * que es exactamente el error que hace que un balance parezca roto cuando no lo esta.
     */
    public List<Saldo> saldosDelPeriodo(DSLContext dsl, UUID periodoId) {
        var a = DSL.table(DSL.name("nucleo_financiero", "asiento_contable")).as("a");
        var m = DSL.table(DSL.name("nucleo_financiero", "movimiento_contable")).as("m");
        var c = DSL.table(DSL.name("nucleo_financiero", "cuenta_contable")).as("c");
        return dsl.select(
                        DSL.field("c.codigo", String.class).as("codigo"),
                        DSL.field("c.tipo", String.class).as("tipo"),
                        DSL.sum(DSL.when(
                                                DSL.field("c.naturaleza", String.class)
                                                        .eq("DEUDORA"),
                                                DSL.field("m.debe", BigDecimal.class)
                                                        .minus(DSL.field("m.haber", BigDecimal.class)))
                                        .otherwise(DSL.field("m.haber", BigDecimal.class)
                                                .minus(DSL.field("m.debe", BigDecimal.class))))
                                .as("saldo"))
                .from(m)
                .join(a)
                .on(DSL.field("a.id", UUID.class).eq(DSL.field("m.asiento_id", UUID.class)))
                .join(c)
                .on(DSL.field("c.id", UUID.class).eq(DSL.field("m.cuenta_id", UUID.class)))
                .where(DSL.field("a.periodo_contable_id", UUID.class).eq(periodoId))
                .groupBy(DSL.field("c.codigo"), DSL.field("c.tipo"), DSL.field("c.naturaleza"))
                .fetch(f -> new Saldo(
                        f.get("codigo", String.class), f.get("tipo", String.class), f.get("saldo", BigDecimal.class)));
    }

    public Optional<UUID> estadoDe(DSLContext dsl, UUID periodoId, String tipo) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("erp", "estado_financiero_generado")))
                .where(DSL.field("periodo_contable_id", UUID.class)
                        .eq(periodoId)
                        .and(DSL.field("tipo", String.class).eq(tipo)))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID guardarEstado(
            DSLContext dsl,
            UUID periodoId,
            String tipo,
            UUID generadoPor,
            String datosJson,
            String hash,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "estado_financiero_generado")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("periodo_contable_id", UUID.class), periodoId)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("generado_en", OffsetDateTime.class), ahora)
                .set(DSL.field("generado_por", UUID.class), generadoPor)
                .set(DSL.field("datos", org.jooq.JSONB.class), org.jooq.JSONB.valueOf(datosJson))
                .set(DSL.field("hash_contenido", String.class), hash)
                .execute();
        return id;
    }

    /** Las lineas de una plantilla de asiento, en su orden. */
    public List<LineaPlantilla> lineasDePlantilla(DSLContext dsl, UUID plantillaId) {
        var l = DSL.table(DSL.name("erp", "linea_plantilla_asiento")).as("l");
        var c = DSL.table(DSL.name("nucleo_financiero", "cuenta_contable")).as("c");
        return dsl.select(
                        DSL.field("l.orden", Short.class).as("orden"),
                        DSL.field("l.cuenta_contable_id", UUID.class).as("cuenta"),
                        DSL.field("l.tipo_movimiento", String.class).as("tipo"),
                        DSL.field("c.es_cuenta_de_movimiento", Boolean.class).as("movimiento"))
                .from(l)
                .join(c)
                .on(DSL.field("c.id", UUID.class).eq(DSL.field("l.cuenta_contable_id", UUID.class)))
                .where(DSL.field("l.plantilla_id", UUID.class).eq(plantillaId))
                .orderBy(DSL.field("l.orden").asc())
                .fetch(f -> new LineaPlantilla(
                        f.get("orden", Short.class).intValue(),
                        f.get("cuenta", UUID.class),
                        f.get("tipo", String.class),
                        f.get("movimiento", Boolean.class)));
    }

    /**
     * @param esCuentaDeMovimiento si es falso, la plantilla apunta a una sumarizadora y
     *     el asiento se rechazaria: mejor decirlo al aplicarla que al escribirla
     */
    public record LineaPlantilla(int orden, UUID cuentaId, String tipoMovimiento, boolean esCuentaDeMovimiento) {}

    public record Partida(UUID id, UUID cuentaId, UUID periodoId, BigDecimal presupuestado, BigDecimal ejecutado) {}

    public record Saldo(String codigo, String tipo, BigDecimal saldo) {}
}
