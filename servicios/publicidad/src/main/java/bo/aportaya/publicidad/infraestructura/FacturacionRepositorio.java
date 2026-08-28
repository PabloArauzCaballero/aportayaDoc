package bo.aportaya.publicidad.infraestructura;

import bo.aportaya.publicidad.dominio.ConsumoDelPeriodo;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code factura_publicidad} y la suma del consumo que la origina. */
@Component
public class FacturacionRepositorio {

    private static final String ESQUEMA = "publicidad";

    /**
     * Lo que la cuenta consumio en el mes, separado por origen.
     *
     * <p>El periodo llega como {@code YYYY-MM} y se compara contra la marca de tiempo
     * del hecho. Se usa {@code to_char} y no un rango calculado en Java porque la
     * frontera del mes la tiene que decidir la base, que es la que sabe en que zona
     * estan guardadas esas marcas.
     */
    public ConsumoDelPeriodo consumo(DSLContext dsl, UUID cuentaId, String periodo) {
        BigDecimal impresiones = dsl.resultQuery(
                        """
                        SELECT COALESCE(SUM(i.costo), 0) AS total
                          FROM publicidad.impresion_anuncio i
                          JOIN publicidad.anuncio a ON a.id = i.anuncio_id
                          JOIN publicidad.conjunto_anuncios cj ON cj.id = a.conjunto_anuncios_id
                          JOIN publicidad.campana_publicitaria c ON c.id = cj.campana_publicitaria_id
                         WHERE c.cuenta_publicitaria_id = ?
                           AND to_char(i.mostrada_en, 'YYYY-MM') = ?
                        """,
                        cuentaId,
                        periodo)
                .fetchOne(f -> f.get("total", BigDecimal.class));

        BigDecimal clics = dsl.resultQuery(
                        """
                        SELECT COALESCE(SUM(cl.costo), 0) AS total
                          FROM publicidad.clic_anuncio cl
                          JOIN publicidad.impresion_anuncio i ON i.id = cl.impresion_id
                          JOIN publicidad.anuncio a ON a.id = i.anuncio_id
                          JOIN publicidad.conjunto_anuncios cj ON cj.id = a.conjunto_anuncios_id
                          JOIN publicidad.campana_publicitaria c ON c.id = cj.campana_publicitaria_id
                         WHERE c.cuenta_publicitaria_id = ?
                           AND to_char(cl.clic_en, 'YYYY-MM') = ?
                        """,
                        cuentaId,
                        periodo)
                .fetchOne(f -> f.get("total", BigDecimal.class));

        return new ConsumoDelPeriodo(impresiones, clics);
    }

    public Optional<UUID> facturaDe(DSLContext dsl, UUID cuentaId, String periodo) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name(ESQUEMA, "factura_publicidad")))
                .where(DSL.field("cuenta_publicitaria_id", UUID.class).eq(cuentaId))
                .and(DSL.field("periodo", String.class).eq(periodo))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    /**
     * La factura del periodo, **completa desde el insert**.
     *
     * <p>{@code factura_publicidad} es append-only (R-AUD-01): su estado no se puede
     * mover despues, y {@code factura_electronica_id} y {@code cuenta_por_cobrar_id} no
     * se pueden completar mas tarde. Los tres datos son del alta o no son.
     */
    public UUID emitir(
            DSLContext dsl,
            UUID cuentaId,
            String periodo,
            BigDecimal montoTotal,
            String moneda,
            UUID facturaElectronicaId,
            UUID cuentaPorCobrarId,
            String estado,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name(ESQUEMA, "factura_publicidad")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("cuenta_publicitaria_id", UUID.class), cuentaId)
                .set(DSL.field("periodo", String.class), periodo)
                .set(DSL.field("monto_total", BigDecimal.class), montoTotal)
                .set(DSL.field("moneda", String.class), moneda)
                .set(DSL.field("factura_electronica_id", UUID.class), facturaElectronicaId)
                .set(DSL.field("cuenta_por_cobrar_id", UUID.class), cuentaPorCobrarId)
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("generada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }
}
