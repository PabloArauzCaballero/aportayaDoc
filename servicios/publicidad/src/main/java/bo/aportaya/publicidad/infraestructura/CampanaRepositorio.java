package bo.aportaya.publicidad.infraestructura;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code campana_publicitaria}, {@code conjunto_anuncios} y sus catalogos. */
@Component
public class CampanaRepositorio {

    private static final String ESQUEMA = "publicidad";

    public UUID crear(
            DSLContext dsl,
            UUID cuentaPublicitariaId,
            String nombre,
            String objetivo,
            BigDecimal presupuestoTotal,
            String moneda,
            OffsetDateTime inicio,
            OffsetDateTime fin) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name(ESQUEMA, "campana_publicitaria")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("cuenta_publicitaria_id", UUID.class), cuentaPublicitariaId)
                .set(DSL.field("nombre", String.class), nombre)
                .set(DSL.field("objetivo", String.class), objetivo)
                .set(DSL.field("presupuesto_total", BigDecimal.class), presupuestoTotal)
                .set(DSL.field("presupuesto_consumido", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("moneda", String.class), moneda)
                .set(DSL.field("fecha_inicio", OffsetDateTime.class), inicio)
                .set(DSL.field("fecha_fin", OffsetDateTime.class), fin)
                .set(DSL.field("estado", String.class), "EN_REVISION")
                .execute();
        return id;
    }

    public UUID agregarConjunto(
            DSLContext dsl,
            UUID campanaId,
            UUID segmentoId,
            UUID espacioId,
            String nombre,
            BigDecimal presupuestoDiario,
            String moneda,
            BigDecimal pujaMaxima,
            String modeloPuja) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name(ESQUEMA, "conjunto_anuncios")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("campana_publicitaria_id", UUID.class), campanaId)
                .set(DSL.field("segmento_audiencia_id", UUID.class), segmentoId)
                .set(DSL.field("espacio_publicitario_id", UUID.class), espacioId)
                .set(DSL.field("nombre", String.class), nombre)
                .set(DSL.field("presupuesto_diario", BigDecimal.class), presupuestoDiario)
                .set(DSL.field("moneda", String.class), moneda)
                .set(DSL.field("puja_maxima", BigDecimal.class), pujaMaxima)
                .set(DSL.field("modelo_puja", String.class), modeloPuja)
                .set(DSL.field("estado", String.class), "PAUSADO")
                .execute();
        return id;
    }

    public Optional<Campana> bloqueada(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("cuenta_publicitaria_id", UUID.class),
                        DSL.field("presupuesto_total", BigDecimal.class),
                        DSL.field("presupuesto_consumido", BigDecimal.class),
                        DSL.field("moneda", String.class),
                        DSL.field("estado", String.class),
                        DSL.field("aprobada_por", UUID.class))
                .from(DSL.table(DSL.name(ESQUEMA, "campana_publicitaria")))
                .where(DSL.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOptional(f -> new Campana(
                        f.get("id", UUID.class),
                        f.get("cuenta_publicitaria_id", UUID.class),
                        f.get("presupuesto_total", BigDecimal.class),
                        f.get("presupuesto_consumido", BigDecimal.class),
                        f.get("moneda", String.class),
                        f.get("estado", String.class),
                        f.get("aprobada_por", UUID.class)));
    }

    /** Aprueba y deja entrar en entrega a sus conjuntos. */
    public boolean aprobar(DSLContext dsl, UUID campanaId, UUID aprobadaPor) {
        int filas = dsl.update(DSL.table(DSL.name(ESQUEMA, "campana_publicitaria")))
                .set(DSL.field("estado", String.class), "ACTIVA")
                .set(DSL.field("aprobada_por", UUID.class), aprobadaPor)
                .where(DSL.field("id", UUID.class).eq(campanaId))
                .and(DSL.field("estado", String.class).eq("EN_REVISION"))
                .execute();
        if (filas == 1) {
            dsl.update(DSL.table(DSL.name(ESQUEMA, "conjunto_anuncios")))
                    .set(DSL.field("estado", String.class), "ACTIVO")
                    .where(DSL.field("campana_publicitaria_id", UUID.class).eq(campanaId))
                    .execute();
        }
        return filas == 1;
    }

    /** Rechaza en revision. El motivo queda en el evento: la tabla no tiene columna. */
    public boolean rechazar(DSLContext dsl, UUID campanaId) {
        return dsl.update(DSL.table(DSL.name(ESQUEMA, "campana_publicitaria")))
                        .set(DSL.field("estado", String.class), "RECHAZADA")
                        .where(DSL.field("id", UUID.class).eq(campanaId))
                        .and(DSL.field("estado", String.class).eq("EN_REVISION"))
                        .execute()
                == 1;
    }

    public List<UUID> conjuntosDe(DSLContext dsl, UUID campanaId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name(ESQUEMA, "conjunto_anuncios")))
                .where(DSL.field("campana_publicitaria_id", UUID.class).eq(campanaId))
                .fetch(f -> f.get("id", UUID.class));
    }

    public Optional<Espacio> espacio(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("codigo", String.class),
                        DSL.field("capacidad_maxima_simultanea", Short.class),
                        DSL.field("activo", Boolean.class))
                .from(DSL.table(DSL.name(ESQUEMA, "espacio_publicitario")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Espacio(
                        f.get("id", UUID.class),
                        f.get("codigo", String.class),
                        f.get("capacidad_maxima_simultanea", Short.class),
                        f.get("activo", Boolean.class)));
    }

    public boolean existeSegmento(DSLContext dsl, UUID id) {
        return dsl.fetchExists(DSL.selectOne()
                .from(DSL.table(DSL.name(ESQUEMA, "segmento_audiencia")))
                .where(DSL.field("id", UUID.class).eq(id)));
    }

    /** Suma al consumido de la campana. Lo acota {@code ck_campana_pub_consumo}. */
    public void sumarConsumo(DSLContext dsl, UUID campanaId, BigDecimal monto) {
        dsl.update(DSL.table(DSL.name(ESQUEMA, "campana_publicitaria")))
                .set(
                        DSL.field("presupuesto_consumido", BigDecimal.class),
                        DSL.field("presupuesto_consumido", BigDecimal.class).plus(monto))
                .where(DSL.field("id", UUID.class).eq(campanaId))
                .execute();
    }

    /** Deja el conjunto sin entregar mas hoy. */
    public void agotarConjunto(DSLContext dsl, UUID conjuntoId) {
        dsl.update(DSL.table(DSL.name(ESQUEMA, "conjunto_anuncios")))
                .set(DSL.field("estado", String.class), "AGOTADO")
                .where(DSL.field("id", UUID.class).eq(conjuntoId))
                .and(DSL.field("estado", String.class).eq("ACTIVO"))
                .execute();
    }

    /** Detiene toda la campana: se acabo el presupuesto total, no el del dia. */
    public void finalizar(DSLContext dsl, UUID campanaId) {
        dsl.update(DSL.table(DSL.name(ESQUEMA, "campana_publicitaria")))
                .set(DSL.field("estado", String.class), "FINALIZADA")
                .where(DSL.field("id", UUID.class).eq(campanaId))
                .execute();
        dsl.update(DSL.table(DSL.name(ESQUEMA, "conjunto_anuncios")))
                .set(DSL.field("estado", String.class), "FINALIZADO")
                .where(DSL.field("campana_publicitaria_id", UUID.class).eq(campanaId))
                .execute();
    }

    public record Campana(
            UUID id,
            UUID cuentaPublicitariaId,
            BigDecimal presupuestoTotal,
            BigDecimal presupuestoConsumido,
            String moneda,
            String estado,
            UUID aprobadaPor) {}

    public record Espacio(UUID id, String codigo, Short capacidadMaximaSimultanea, Boolean activo) {}
}
