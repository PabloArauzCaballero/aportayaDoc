package bo.aportaya.publicidad.infraestructura;

import bo.aportaya.publicidad.dominio.SubastaDelEspacio.Candidato;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code anuncio}, {@code impresion_anuncio}, {@code clic_anuncio} y {@code conversion_anuncio}. */
@Component
public class EntregaRepositorio {

    private static final String ESQUEMA = "publicidad";

    public UUID programarAnuncio(DSLContext dsl, UUID conjuntoId, UUID piezaId) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name(ESQUEMA, "anuncio")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("conjunto_anuncios_id", UUID.class), conjuntoId)
                .set(DSL.field("pieza_creativa_id", UUID.class), piezaId)
                .set(DSL.field("estado", String.class), "PROGRAMADO")
                .execute();
        return id;
    }

    /**
     * Los anuncios que compiten por un espacio, con lo que su conjunto ya gasto hoy.
     *
     * <p>Van **dos consultas y no una**, y la razon es la que hace que este caso de uso
     * no sobregaste. Bajo READ COMMITTED, {@code FOR UPDATE} vuelve a leer la fila
     * bloqueada cuando el candado se libera, pero el resto de la consulta se queda con
     * la foto del comienzo: la suma de impresiones seguiria siendo la de antes de que
     * la otra transaccion cometiera. Bloquear primero y sumar despues, en una sentencia
     * nueva, es lo que hace que la segunda entrega vea lo que gasto la primera.
     *
     * <p>El gasto del dia no se guarda en una columna a proposito: un contador seria un
     * candado sobre el conjunto entero, y aca las entregas son concurrentes por
     * definicion.
     */
    public List<Candidato> candidatos(DSLContext dsl, UUID espacioId, LocalDate dia) {
        var enCarrera = dsl.resultQuery(
                        """
                        SELECT a.id            AS anuncio_id,
                               cj.id           AS conjunto_id,
                               cj.modelo_puja  AS modelo_puja,
                               cj.puja_maxima  AS puja_maxima,
                               cj.presupuesto_diario AS presupuesto_diario
                          FROM publicidad.anuncio a
                          JOIN publicidad.conjunto_anuncios cj ON cj.id = a.conjunto_anuncios_id
                          JOIN publicidad.campana_publicitaria c ON c.id = cj.campana_publicitaria_id
                         WHERE cj.espacio_publicitario_id = ?
                           AND cj.estado = 'ACTIVO'
                           AND c.estado = 'ACTIVA'
                           AND a.estado IN ('PROGRAMADO', 'EN_ENTREGA')
                         ORDER BY cj.puja_maxima DESC, a.id
                         FOR UPDATE OF cj
                        """,
                        espacioId)
                .fetch();

        return enCarrera.map(f -> new Candidato(
                f.get("anuncio_id", UUID.class),
                f.get("conjunto_id", UUID.class),
                f.get("modelo_puja", String.class),
                f.get("puja_maxima", BigDecimal.class),
                f.get("presupuesto_diario", BigDecimal.class),
                gastadoHoy(dsl, f.get("conjunto_id", UUID.class), dia)));
    }

    /** Lo que un conjunto gasto hoy: la suma de sus impresiones y sus clics. */
    private BigDecimal gastadoHoy(DSLContext dsl, UUID conjuntoId, LocalDate dia) {
        return dsl.resultQuery(
                        """
                        SELECT COALESCE(SUM(i.costo), 0) + COALESCE(SUM(cl.costo), 0) AS gastado
                          FROM publicidad.impresion_anuncio i
                          JOIN publicidad.anuncio a ON a.id = i.anuncio_id
                          LEFT JOIN publicidad.clic_anuncio cl ON cl.impresion_id = i.id
                         WHERE a.conjunto_anuncios_id = ?
                           AND i.mostrada_en >= ?::date
                           AND i.mostrada_en <  ?::date + 1
                        """,
                        conjuntoId,
                        dia.toString(),
                        dia.toString())
                .fetchOne(f -> f.get("gastado", BigDecimal.class));
    }

    /** Cuantos anuncios estan ocupando el espacio ahora mismo. */
    public int enEntrega(DSLContext dsl, UUID espacioId) {
        return dsl.fetchCount(DSL.selectOne()
                .from(DSL.table(DSL.name(ESQUEMA, "anuncio")).as("a"))
                .join(DSL.table(DSL.name(ESQUEMA, "conjunto_anuncios")).as("cj"))
                .on(DSL.field("cj.id", UUID.class).eq(DSL.field("a.conjunto_anuncios_id", UUID.class)))
                .where(DSL.field("cj.espacio_publicitario_id", UUID.class).eq(espacioId))
                .and(DSL.field("a.estado", String.class).eq("EN_ENTREGA")));
    }

    public void marcarEnEntrega(DSLContext dsl, UUID anuncioId, OffsetDateTime ahora) {
        dsl.update(DSL.table(DSL.name(ESQUEMA, "anuncio")))
                .set(DSL.field("estado", String.class), "EN_ENTREGA")
                .set(DSL.field("iniciado_en", OffsetDateTime.class), ahora)
                .where(DSL.field("id", UUID.class).eq(anuncioId))
                .and(DSL.field("estado", String.class).eq("PROGRAMADO"))
                .execute();
    }

    public UUID registrarImpresion(
            DSLContext dsl, UUID anuncioId, UUID usuarioId, BigDecimal costo, String moneda, OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name(ESQUEMA, "impresion_anuncio")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("anuncio_id", UUID.class), anuncioId)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("mostrada_en", OffsetDateTime.class), ahora)
                .set(DSL.field("costo", BigDecimal.class), costo)
                .set(DSL.field("moneda", String.class), moneda)
                .execute();
        return id;
    }

    public UUID registrarClic(
            DSLContext dsl, UUID impresionId, UUID usuarioId, BigDecimal costo, String moneda, OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name(ESQUEMA, "clic_anuncio")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("impresion_id", UUID.class), impresionId)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("clic_en", OffsetDateTime.class), ahora)
                .set(DSL.field("costo", BigDecimal.class), costo)
                .set(DSL.field("moneda", String.class), moneda)
                .execute();
        return id;
    }

    public UUID registrarConversion(
            DSLContext dsl, UUID clicId, UUID impresionId, String tipo, UUID referenciaId, OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name(ESQUEMA, "conversion_anuncio")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("clic_id", UUID.class), clicId)
                .set(DSL.field("impresion_id", UUID.class), impresionId)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("referencia_id", UUID.class), referenciaId)
                .set(DSL.field("ocurrida_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /** La impresion y el conjunto del que colgo: el clic se cobra con la puja de ese conjunto. */
    public Optional<Origen> origenDeLaImpresion(DSLContext dsl, UUID impresionId) {
        return dsl.select(
                        DSL.field("i.id", UUID.class),
                        DSL.field("cj.id", UUID.class).as("conjunto_id"),
                        DSL.field("c.id", UUID.class).as("campana_id"),
                        DSL.field("c.cuenta_publicitaria_id", UUID.class).as("cuenta_id"),
                        DSL.field("cj.modelo_puja", String.class),
                        DSL.field("cj.puja_maxima", BigDecimal.class),
                        DSL.field("cj.moneda", String.class))
                .from(DSL.table(DSL.name(ESQUEMA, "impresion_anuncio")).as("i"))
                .join(DSL.table(DSL.name(ESQUEMA, "anuncio")).as("a"))
                .on(DSL.field("a.id", UUID.class).eq(DSL.field("i.anuncio_id", UUID.class)))
                .join(DSL.table(DSL.name(ESQUEMA, "conjunto_anuncios")).as("cj"))
                .on(DSL.field("cj.id", UUID.class).eq(DSL.field("a.conjunto_anuncios_id", UUID.class)))
                .join(DSL.table(DSL.name(ESQUEMA, "campana_publicitaria")).as("c"))
                .on(DSL.field("c.id", UUID.class).eq(DSL.field("cj.campana_publicitaria_id", UUID.class)))
                .where(DSL.field("i.id", UUID.class).eq(impresionId))
                .fetchOptional(f -> new Origen(
                        f.get("i.id", UUID.class),
                        f.get("conjunto_id", UUID.class),
                        f.get("campana_id", UUID.class),
                        f.get("cuenta_id", UUID.class),
                        f.get("cj.modelo_puja", String.class),
                        f.get("cj.puja_maxima", BigDecimal.class),
                        f.get("cj.moneda", String.class)));
    }

    /** La campana y la cuenta a la que se le carga lo que entrega un conjunto. */
    public Optional<Origen> origenDelConjunto(DSLContext dsl, UUID conjuntoId) {
        return dsl.select(
                        DSL.field("cj.id", UUID.class),
                        DSL.field("c.id", UUID.class).as("campana_id"),
                        DSL.field("c.cuenta_publicitaria_id", UUID.class).as("cuenta_id"),
                        DSL.field("cj.modelo_puja", String.class),
                        DSL.field("cj.puja_maxima", BigDecimal.class),
                        DSL.field("cj.moneda", String.class))
                .from(DSL.table(DSL.name(ESQUEMA, "conjunto_anuncios")).as("cj"))
                .join(DSL.table(DSL.name(ESQUEMA, "campana_publicitaria")).as("c"))
                .on(DSL.field("c.id", UUID.class).eq(DSL.field("cj.campana_publicitaria_id", UUID.class)))
                .where(DSL.field("cj.id", UUID.class).eq(conjuntoId))
                .fetchOptional(f -> new Origen(
                        null,
                        f.get("cj.id", UUID.class),
                        f.get("campana_id", UUID.class),
                        f.get("cuenta_id", UUID.class),
                        f.get("cj.modelo_puja", String.class),
                        f.get("cj.puja_maxima", BigDecimal.class),
                        f.get("cj.moneda", String.class)));
    }

    public record Origen(
            UUID impresionId,
            UUID conjuntoId,
            UUID campanaId,
            UUID cuentaPublicitariaId,
            String modeloPuja,
            BigDecimal pujaMaxima,
            String moneda) {}
}
