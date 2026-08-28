package bo.aportaya.nucleofinanciero.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code conciliacion_custodia} y los cierres diarios contra los que compara. */
@Component
public class ConciliacionRepositorio {

    public boolean existeDelDia(DSLContext dsl, UUID cuentaCustodiaId, LocalDate fecha) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("nucleo_financiero", "conciliacion_custodia")),
                        DSL.field("cuenta_custodia_id", UUID.class).eq(cuentaCustodiaId),
                        DSL.field("fecha", LocalDate.class).eq(fecha))
                > 0;
    }

    /** ¿Se cerraron los saldos de ese dia? Sin ellos no hay contra que comparar. */
    public boolean haySaldosDelDia(DSLContext dsl, LocalDate fecha) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("nucleo_financiero", "saldo_diario_billetera")),
                        DSL.field("fecha", LocalDate.class).eq(fecha))
                > 0;
    }

    /**
     * Registra la conciliacion.
     *
     * <p>{@code diferencia} y {@code ratio_cobertura} son columnas GENERATED: las
     * calcula la base a partir de los saldos. No se envian — y esta bien que sea asi,
     * porque el numero que decide si hay encaje no puede depender de que la aplicacion
     * lo calcule igual que la consulta que despues lo audita.
     */
    public UUID registrar(
            DSLContext dsl,
            UUID cuentaCustodiaId,
            LocalDate fecha,
            Dinero emitido,
            Dinero custodia,
            Dinero enTransito,
            boolean cumpleEncaje,
            UUID ejecutadaPor,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("nucleo_financiero", "conciliacion_custodia")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("cuenta_custodia_id", UUID.class), cuentaCustodiaId)
                .set(DSL.field("ejecutada_por", UUID.class), ejecutadaPor)
                .set(DSL.field("fecha", LocalDate.class), fecha)
                .set(DSL.field("saldo_dinero_electronico", BigDecimal.class), emitido.monto())
                .set(DSL.field("saldo_custodia", BigDecimal.class), custodia.monto())
                .set(DSL.field("saldo_en_transito", BigDecimal.class), enTransito.monto())
                .set(DSL.field("cumple_encaje", Boolean.class), cumpleEncaje)
                .set(DSL.field("estado", String.class), cumpleEncaje ? "CUADRADA" : "DESCUADRADA")
                .set(DSL.field("ejecutada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /** Cierra el saldo del dia de una cuenta: es lo que el extracto y la conciliacion leen. */
    public UUID cerrarSaldoDelDia(
            DSLContext dsl,
            UUID cuentaId,
            LocalDate fecha,
            Dinero disponible,
            Dinero retenido,
            int movimientos,
            String hash,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("nucleo_financiero", "saldo_diario_billetera")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("cuenta_billetera_id", UUID.class), cuentaId)
                .set(DSL.field("fecha", LocalDate.class), fecha)
                .set(DSL.field("saldo_disponible", BigDecimal.class), disponible.monto())
                .set(DSL.field("saldo_retenido", BigDecimal.class), retenido.monto())
                .set(DSL.field("cantidad_movimientos", Integer.class), movimientos)
                .set(DSL.field("hash_registro", String.class), hash)
                .set(DSL.field("cerrado_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }
}
