package bo.aportaya.erp.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code ejercicio_fiscal}, {@code periodo_contable} y {@code cierre_periodo_contable}.
 *
 * <p>**En un periodo cerrado no se asienta nada** (R-CTB-01, lo verifica
 * {@code tg_asiento_periodo_abierto}). Es lo que hace que un balance publicado siga
 * siendo el mismo dentro de un año: sin esa puerta, cualquiera podria agregar un asiento
 * viejo y cambiar un estado financiero que ya se entrego.
 */
@Component
public class PeriodoRepositorio {

    public UUID abrirEjercicio(DSLContext dsl, int anio, LocalDate desde, LocalDate hasta) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "ejercicio_fiscal")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("anio", Short.class), (short) anio)
                .set(DSL.field("fecha_inicio", LocalDate.class), desde)
                .set(DSL.field("fecha_fin", LocalDate.class), hasta)
                .set(DSL.field("estado", String.class), "ABIERTO")
                .execute();
        return id;
    }

    public Optional<Ejercicio> ejercicio(DSLContext dsl, int anio) {
        return dsl.select(
                        DSL.field("id", UUID.class), DSL.field("anio", Short.class), DSL.field("estado", String.class))
                .from(DSL.table(DSL.name("erp", "ejercicio_fiscal")))
                .where(DSL.field("anio", Short.class).eq((short) anio))
                .fetchOptional(f -> new Ejercicio(
                        f.get("id", UUID.class), f.get("anio", Short.class).intValue(), f.get("estado", String.class)));
    }

    public UUID abrirPeriodo(DSLContext dsl, UUID ejercicioId, int mes, LocalDate desde, LocalDate hasta) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "periodo_contable")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("ejercicio_fiscal_id", UUID.class), ejercicioId)
                .set(DSL.field("mes", Short.class), (short) mes)
                .set(DSL.field("fecha_inicio", LocalDate.class), desde)
                .set(DSL.field("fecha_fin", LocalDate.class), hasta)
                .set(DSL.field("estado", String.class), "ABIERTO")
                .execute();
        return id;
    }

    public Optional<Periodo> periodo(DSLContext dsl, UUID ejercicioId, int mes) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("ejercicio_fiscal_id", UUID.class),
                        DSL.field("mes", Short.class),
                        DSL.field("estado", String.class),
                        DSL.field("fecha_inicio", LocalDate.class),
                        DSL.field("fecha_fin", LocalDate.class))
                .from(DSL.table(DSL.name("erp", "periodo_contable")))
                .where(DSL.field("ejercicio_fiscal_id", UUID.class)
                        .eq(ejercicioId)
                        .and(DSL.field("mes", Short.class).eq((short) mes)))
                .fetchOptional(f -> new Periodo(
                        f.get("id", UUID.class),
                        f.get("ejercicio_fiscal_id", UUID.class),
                        f.get("mes", Short.class).intValue(),
                        f.get("estado", String.class),
                        f.get("fecha_inicio", LocalDate.class),
                        f.get("fecha_fin", LocalDate.class)));
    }

    public Optional<Periodo> periodoPorId(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("ejercicio_fiscal_id", UUID.class),
                        DSL.field("mes", Short.class),
                        DSL.field("estado", String.class),
                        DSL.field("fecha_inicio", LocalDate.class),
                        DSL.field("fecha_fin", LocalDate.class))
                .from(DSL.table(DSL.name("erp", "periodo_contable")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Periodo(
                        f.get("id", UUID.class),
                        f.get("ejercicio_fiscal_id", UUID.class),
                        f.get("mes", Short.class).intValue(),
                        f.get("estado", String.class),
                        f.get("fecha_inicio", LocalDate.class),
                        f.get("fecha_fin", LocalDate.class)));
    }

    /**
     * El periodo abierto mas antiguo del ejercicio.
     *
     * <p>Es lo que sostiene que los periodos se cierren en orden: cerrar marzo con
     * febrero abierto dejaria a marzo incluyendo asientos que febrero todavia puede
     * recibir, y el balance de marzo cambiaria despues de publicado.
     */
    public Optional<Periodo> primerPeriodoAbierto(DSLContext dsl, UUID ejercicioId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("ejercicio_fiscal_id", UUID.class),
                        DSL.field("mes", Short.class),
                        DSL.field("estado", String.class),
                        DSL.field("fecha_inicio", LocalDate.class),
                        DSL.field("fecha_fin", LocalDate.class))
                .from(DSL.table(DSL.name("erp", "periodo_contable")))
                .where(DSL.field("ejercicio_fiscal_id", UUID.class)
                        .eq(ejercicioId)
                        .and(DSL.field("estado", String.class).eq("ABIERTO")))
                .orderBy(DSL.field("mes").asc())
                .limit(1)
                .fetchOptional(f -> new Periodo(
                        f.get("id", UUID.class),
                        f.get("ejercicio_fiscal_id", UUID.class),
                        f.get("mes", Short.class).intValue(),
                        f.get("estado", String.class),
                        f.get("fecha_inicio", LocalDate.class),
                        f.get("fecha_fin", LocalDate.class)));
    }

    /** El periodo que contiene una fecha: es lo que dice si esa fecha admite asientos. */
    public Optional<Periodo> periodoDeLaFecha(DSLContext dsl, LocalDate fecha) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("ejercicio_fiscal_id", UUID.class),
                        DSL.field("mes", Short.class),
                        DSL.field("estado", String.class),
                        DSL.field("fecha_inicio", LocalDate.class),
                        DSL.field("fecha_fin", LocalDate.class))
                .from(DSL.table(DSL.name("erp", "periodo_contable")))
                .where(DSL.field("fecha_inicio", LocalDate.class)
                        .le(fecha)
                        .and(DSL.field("fecha_fin", LocalDate.class).ge(fecha)))
                .fetchOptional(f -> new Periodo(
                        f.get("id", UUID.class),
                        f.get("ejercicio_fiscal_id", UUID.class),
                        f.get("mes", Short.class).intValue(),
                        f.get("estado", String.class),
                        f.get("fecha_inicio", LocalDate.class),
                        f.get("fecha_fin", LocalDate.class)));
    }

    /** Los totales del periodo, desde los movimientos. No se guarda un total aparte. */
    public Totales totalesDe(DSLContext dsl, UUID periodoId) {
        // INVARIANTE-11 DECLARADO · el libro contable vive en `nucleo_financiero`.
        //
        // `erp` arma los estados financieros sumando el libro entero de un periodo. Traer
        // esos movimientos por HTTP serian decenas de miles de filas por la red para
        // sumarlas del otro lado, y dentro de la transaccion del cierre: invariante 6.
        //
        // La lectura es SOLO lectura. El invariante 12 —el libro no se parte, solo
        // `nucleo-financiero` lo escribe— se mantiene entero: aca no hay ni un INSERT.
        // Cerrarlo bien pide una vista materializada del lado de erp alimentada por
        // eventos, y eso es un cambio de modelo.
        var a = DSL.table(DSL.name("nucleo_financiero", "asiento_contable")).as("a");
        var m = DSL.table(DSL.name("nucleo_financiero", "movimiento_contable")).as("m");
        var fila = dsl.select(
                        DSL.coalesce(DSL.sum(DSL.field("m.debe", BigDecimal.class)), BigDecimal.ZERO)
                                .as("debe"),
                        DSL.coalesce(DSL.sum(DSL.field("m.haber", BigDecimal.class)), BigDecimal.ZERO)
                                .as("haber"))
                .from(m)
                .join(a)
                .on(DSL.field("a.id", UUID.class).eq(DSL.field("m.asiento_id", UUID.class)))
                .where(DSL.field("a.periodo_contable_id", UUID.class).eq(periodoId))
                .fetchOne();
        return new Totales(fila.get("debe", BigDecimal.class), fila.get("haber", BigDecimal.class));
    }

    /**
     * Cierra el periodo y deja constancia del cuadre.
     *
     * <p>El cierre guarda el debe y el haber **del momento**: si mañana alguien discute
     * el balance, la constancia dice contra que cifras se cerro.
     */
    public UUID cerrar(
            DSLContext dsl,
            UUID periodoId,
            UUID cerradoPor,
            BigDecimal totalDebe,
            BigDecimal totalHaber,
            String observaciones,
            OffsetDateTime ahora) {

        // **Un periodo se cierra una vez y nada mas.** La constancia es append-only
        // (R-AUD-01) y unica por periodo, asi que ni se puede guardar una segunda ni
        // corregir la que hay. Es la restriccion mas fuerte del modulo y tiene sentido:
        // el cierre es el acto que congela un mes, y un cierre que se puede repetir no
        // congela nada.
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("erp", "cierre_periodo_contable")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("periodo_contable_id", UUID.class), periodoId)
                .set(DSL.field("cerrado_en", OffsetDateTime.class), ahora)
                .set(DSL.field("cerrado_por", UUID.class), cerradoPor)
                .set(DSL.field("total_debe", BigDecimal.class), totalDebe)
                .set(DSL.field("total_haber", BigDecimal.class), totalHaber)
                .set(DSL.field("diferencia", BigDecimal.class), totalDebe.subtract(totalHaber))
                .set(DSL.field("observaciones", String.class), observaciones)
                .execute();

        dsl.update(DSL.table(DSL.name("erp", "periodo_contable")))
                .set(DSL.field("estado", String.class), "CERRADO")
                .where(DSL.field("id", UUID.class).eq(periodoId))
                .execute();
        return id;
    }

    public record Ejercicio(UUID id, int anio, String estado) {}

    public record Periodo(UUID id, UUID ejercicioId, int mes, String estado, LocalDate desde, LocalDate hasta) {}

    public record Totales(BigDecimal debe, BigDecimal haber) {}
}
