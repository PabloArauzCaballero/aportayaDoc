package bo.aportaya.nucleofinanciero.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code cierre_diario} y los asientos que lo bloquean. */
@Component
public class CierreDiarioRepositorio {

    public Optional<Cierre> delDia(DSLContext dsl, LocalDate fecha) {
        Record fila = dsl.select(DSL.field("id", UUID.class), DSL.field("cuadrado", Boolean.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "cierre_diario")))
                .where(DSL.field("fecha", LocalDate.class).eq(fecha))
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Cierre(f.get("id", UUID.class), f.get("cuadrado", Boolean.class)));
    }

    /**
     * Los asientos del dia que siguen en borrador.
     *
     * <p>Un asiento sin confirmar significa que la contabilidad del dia todavia se
     * esta escribiendo, y firmar un cierre sobre libros abiertos es firmar un numero
     * que va a cambiar.
     */
    public int asientosSinConfirmar(DSLContext dsl, LocalDate fecha) {
        Integer cuantos = (Integer) dsl.fetchOne(
                        """
                        SELECT count(*)::int FROM nucleo_financiero.asiento_contable
                         WHERE fecha::date = ? AND estado = 'BORRADOR'
                        """,
                        fecha)
                .get(0);
        return cuantos == null ? 0 : cuantos;
    }

    /** Cuantas cuentas activas hay: el cierre tiene que dejar un saldo diario por cada una. */
    public java.util.List<UUID> cuentasActivas(DSLContext dsl) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "cuenta_billetera")))
                .where(DSL.field("estado").eq("ACTIVA"))
                .fetch(f -> f.get("id", UUID.class));
    }

    public UUID registrar(
            DSLContext dsl,
            LocalDate fecha,
            BigDecimal totalRecaudado,
            BigDecimal totalConciliado,
            BigDecimal totalExcepciones,
            int cantidadPagos,
            boolean cuadrado,
            UUID cerradoPor,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("nucleo_financiero", "cierre_diario")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("fecha", LocalDate.class), fecha)
                .set(DSL.field("total_recaudado", BigDecimal.class), totalRecaudado)
                .set(DSL.field("total_conciliado", BigDecimal.class), totalConciliado)
                .set(DSL.field("total_excepciones", BigDecimal.class), totalExcepciones)
                .set(DSL.field("cantidad_pagos", Integer.class), cantidadPagos)
                .set(DSL.field("cuadrado", Boolean.class), cuadrado)
                .set(DSL.field("cerrado_por", UUID.class), cerradoPor)
                .set(DSL.field("cerrado_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public record Cierre(UUID id, boolean cuadrado) {}
}
