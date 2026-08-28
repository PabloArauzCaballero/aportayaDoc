package bo.aportaya.nucleofinanciero.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code saldo_diario_billetera} y {@code certificado_saldo}. */
@Component
public class ExtractoRepositorio {

    public Optional<Cierre> cierreDelDia(DSLContext dsl, UUID cuentaId, LocalDate fecha) {
        Record fila = dsl.select(
                        DSL.field("saldo_disponible", BigDecimal.class),
                        DSL.field("saldo_retenido", BigDecimal.class),
                        DSL.field("cantidad_movimientos", Integer.class),
                        DSL.field("hash_registro", String.class))
                .from(DSL.table(DSL.name("nucleo_financiero", "saldo_diario_billetera")))
                .where(DSL.field("cuenta_billetera_id", UUID.class).eq(cuentaId))
                .and(DSL.field("fecha", LocalDate.class).eq(fecha))
                .fetchOne();
        return Optional.ofNullable(fila)
                .map(f -> new Cierre(
                        Dinero.de(f.get("saldo_disponible", BigDecimal.class), Moneda.BOB),
                        Dinero.de(f.get("saldo_retenido", BigDecimal.class), Moneda.BOB),
                        f.get("cantidad_movimientos", Integer.class),
                        f.get("hash_registro", String.class)));
    }

    /**
     * El saldo recalculado desde el libro hasta esa fecha.
     *
     * <p>Se recalcula a proposito en vez de leer la columna: comparar el cierre contra
     * si mismo no detectaria nada. Lo que este metodo busca es justamente la
     * diferencia entre lo que se cerro y lo que el libro dice hoy.
     */
    public Dinero saldoCalculadoHasta(DSLContext dsl, UUID cuentaId, LocalDate hasta) {
        BigDecimal total = (BigDecimal) dsl.fetchOne(
                        """
                        SELECT COALESCE(SUM(CASE WHEN m.sentido = 'CREDITO' THEN m.monto ELSE -m.monto END), 0)
                          FROM nucleo_financiero.movimiento_billetera m
                         WHERE m.cuenta_billetera_id = ?
                           AND m.registrado_en < (CAST(? AS date) + 1)
                        """,
                        cuentaId,
                        hasta)
                .get(0);
        // El retenido no se resta aca: el cierre guarda disponible y retenido por
        // separado, y se comparan por separado.
        BigDecimal retenido = (BigDecimal) dsl.fetchOne(
                        """
                        SELECT COALESCE(SUM(monto), 0) FROM nucleo_financiero.retencion_saldo
                         WHERE cuenta_billetera_id = ? AND estado = 'VIGENTE'
                        """,
                        cuentaId)
                .get(0);
        return Dinero.de(total.subtract(retenido), Moneda.BOB);
    }

    public int contarMovimientos(DSLContext dsl, UUID cuentaId, LocalDate desde, LocalDate hasta) {
        Integer cuantos = (Integer) dsl.fetchOne(
                        """
                        SELECT count(*)::int FROM nucleo_financiero.movimiento_billetera
                         WHERE cuenta_billetera_id = ?
                           AND registrado_en >= CAST(? AS date)
                           AND registrado_en < (CAST(? AS date) + 1)
                        """,
                        cuentaId,
                        desde,
                        hasta)
                .get(0);
        return cuantos == null ? 0 : cuantos;
    }

    /** Folio correlativo por año: es lo que un tercero cita al verificar. */
    public String siguienteFolio(DSLContext dsl, int anio) {
        Integer emitidos = (Integer) dsl.fetchOne(
                        "SELECT count(*)::int FROM nucleo_financiero.certificado_saldo WHERE folio LIKE ?", anio + "-%")
                .get(0);
        return "%d-%06d".formatted(anio, (emitidos == null ? 0 : emitidos) + 1);
    }

    public UUID emitirCertificado(
            DSLContext dsl,
            UUID cuentaId,
            UUID solicitadoPor,
            String folio,
            String motivo,
            Dinero saldo,
            LocalDate fechaCorte,
            String hash,
            String url,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("nucleo_financiero", "certificado_saldo")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("cuenta_billetera_id", UUID.class), cuentaId)
                .set(DSL.field("solicitado_por", UUID.class), solicitadoPor)
                .set(DSL.field("folio", String.class), folio)
                .set(DSL.field("motivo", String.class), motivo)
                .set(DSL.field("saldo_certificado", BigDecimal.class), saldo.monto())
                .set(DSL.field("fecha_corte", LocalDate.class), fechaCorte)
                .set(DSL.field("hash_documento", String.class), hash)
                .set(DSL.field("url_documento", String.class), url)
                .set(DSL.field("emitido_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public boolean coincideFolioYHash(DSLContext dsl, String folio, String hash) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("nucleo_financiero", "certificado_saldo")),
                        DSL.field("folio").eq(folio),
                        DSL.field("hash_documento").eq(hash))
                > 0;
    }

    public record Cierre(Dinero disponible, Dinero retenido, int movimientos, String hash) {}
}
