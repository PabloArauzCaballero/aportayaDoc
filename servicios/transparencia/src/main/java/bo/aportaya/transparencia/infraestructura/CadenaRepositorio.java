package bo.aportaya.transparencia.infraestructura;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code bloque_transparencia}, {@code registro_sellado} y {@code verificacion_publica}.
 *
 * <p>El registro sellado es **append-only**: es la evidencia. Y la cadena la encadena la
 * base ({@code tg_bloque_encadenado}, R-REP-04): un salto de numeracion o un hash
 * anterior que no coincide se rechazan al escribir, no al auditar.
 */
@Component
public class CadenaRepositorio {

    /** El ultimo bloque del grupo. Vacio si es el primero. */
    public Optional<Ultimo> ultimoBloque(DSLContext dsl, UUID grupoId) {
        return dsl.select(DSL.field("numero_bloque", Long.class), DSL.field("hash_bloque", String.class))
                .from(DSL.table(DSL.name("transparencia", "bloque_transparencia")))
                .where(DSL.field("grupo_id", UUID.class).eq(grupoId))
                .orderBy(DSL.field("numero_bloque").desc())
                .limit(1)
                .fetchOptional(f -> new Ultimo(f.get("numero_bloque", Long.class), f.get("hash_bloque", String.class)));
    }

    public UUID sellarBloque(
            DSLContext dsl,
            UUID grupoId,
            long numero,
            String hashAnterior,
            String raizMerkle,
            String hashBloque,
            int cantidadEventos,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("transparencia", "bloque_transparencia")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("grupo_id", UUID.class), grupoId)
                .set(DSL.field("numero_bloque", Long.class), numero)
                .set(DSL.field("hash_bloque_anterior", String.class), hashAnterior)
                .set(DSL.field("raiz_merkle", String.class), raizMerkle)
                .set(DSL.field("hash_bloque", String.class), hashBloque)
                .set(DSL.field("cantidad_eventos", Integer.class), cantidadEventos)
                .set(DSL.field("periodo_cubierto_desde", OffsetDateTime.class), desde)
                .set(DSL.field("periodo_cubierto_hasta", OffsetDateTime.class), hasta)
                .set(DSL.field("sellado_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public UUID sellarRegistro(
            DSLContext dsl,
            UUID bloqueId,
            String tipoEntidad,
            UUID entidadId,
            String hashContenido,
            String resumenJson,
            OffsetDateTime ocurridoEn) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("transparencia", "registro_sellado")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("bloque_id", UUID.class), bloqueId)
                .set(DSL.field("tipo_entidad", String.class), tipoEntidad)
                .set(DSL.field("entidad_id", UUID.class), entidadId)
                .set(DSL.field("hash_contenido", String.class), hashContenido)
                .set(DSL.field("resumen_publico", JSONB.class), JSONB.valueOf(resumenJson))
                .set(DSL.field("ocurrido_en", OffsetDateTime.class), ocurridoEn)
                .execute();
        return id;
    }

    /** La cadena entera de un grupo, en orden. Es lo que se verifica. */
    public List<Persistido> cadenaDe(DSLContext dsl, UUID grupoId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("numero_bloque", Long.class),
                        DSL.field("hash_bloque_anterior", String.class),
                        DSL.field("raiz_merkle", String.class),
                        DSL.field("hash_bloque", String.class),
                        DSL.field("periodo_cubierto_desde", OffsetDateTime.class),
                        DSL.field("periodo_cubierto_hasta", OffsetDateTime.class),
                        DSL.field("sellado_en", OffsetDateTime.class))
                .from(DSL.table(DSL.name("transparencia", "bloque_transparencia")))
                .where(DSL.field("grupo_id", UUID.class).eq(grupoId))
                .orderBy(DSL.field("numero_bloque").asc())
                .fetch(f -> new Persistido(
                        f.get("id", UUID.class),
                        f.get("numero_bloque", Long.class),
                        f.get("hash_bloque_anterior", String.class),
                        f.get("raiz_merkle", String.class),
                        f.get("hash_bloque", String.class),
                        f.get("periodo_cubierto_desde", OffsetDateTime.class),
                        f.get("periodo_cubierto_hasta", OffsetDateTime.class),
                        f.get("sellado_en", OffsetDateTime.class)));
    }

    /**
     * Las hojas de **todos** los bloques del grupo, en una sola consulta.
     *
     * <p>Una consulta por bloque seria un N+1 sobre la operacion que mas se llama de
     * este servicio: la verificacion publica la dispara cualquiera, sin sesion.
     */
    public Map<UUID, List<String>> hojasDelGrupo(DSLContext dsl, UUID grupoId) {
        var b = DSL.table(DSL.name("transparencia", "bloque_transparencia")).as("b");
        var r = DSL.table(DSL.name("transparencia", "registro_sellado")).as("r");
        var hojas = new LinkedHashMap<UUID, List<String>>();
        dsl.select(
                        DSL.field("r.bloque_id", UUID.class).as("bloque_id"),
                        DSL.field("r.hash_contenido", String.class).as("hash_contenido"))
                .from(r)
                .join(b)
                .on(DSL.field("b.id", UUID.class).eq(DSL.field("r.bloque_id", UUID.class)))
                .where(DSL.field("b.grupo_id", UUID.class).eq(grupoId))
                .orderBy(DSL.field("r.ocurrido_en").asc(), DSL.field("r.id").asc())
                .forEach(f -> hojas.computeIfAbsent(f.get("bloque_id", UUID.class), k -> new ArrayList<>())
                        .add(f.get("hash_contenido", String.class)));
        return hojas;
    }

    /** Los hashes de contenido de un bloque: las hojas de su arbol de Merkle. */
    public List<String> hojasDe(DSLContext dsl, UUID bloqueId) {
        return dsl.select(DSL.field("hash_contenido", String.class))
                .from(DSL.table(DSL.name("transparencia", "registro_sellado")))
                .where(DSL.field("bloque_id", UUID.class).eq(bloqueId))
                .orderBy(DSL.field("ocurrido_en").asc(), DSL.field("id").asc())
                .fetch(f -> f.get("hash_contenido", String.class));
    }

    /**
     * Registra una consulta publica de verificacion.
     *
     * <p>Se cuenta cuantas veces se consulto: un codigo verificado cien veces por
     * distintas personas es lo que le da valor al certificado.
     */
    public void registrarVerificacion(
            DSLContext dsl,
            String codigo,
            String tipoDocumento,
            UUID referenciaId,
            String hashEsperado,
            String hashRecomputado,
            String resultado,
            OffsetDateTime ahora) {

        var tabla = DSL.table(DSL.name("transparencia", "verificacion_publica"));
        var consultas = DSL.field("consultas", Integer.class);
        // En el DO UPDATE, «consultas» a secas es ambiguo entre la fila que ya esta y
        // la que se intento insertar: PostgreSQL no adivina cual se quiere sumar.
        var consultasDeLaFila = DSL.field(DSL.name("verificacion_publica", "consultas"), Integer.class);
        dsl.insertInto(tabla)
                .set(DSL.field("codigo", String.class), codigo)
                .set(DSL.field("tipo_documento", String.class), tipoDocumento)
                .set(DSL.field("referencia_id", UUID.class), referenciaId)
                .set(DSL.field("hash_esperado", String.class), hashEsperado)
                .set(DSL.field("hash_recomputado", String.class), hashRecomputado)
                .set(DSL.field("resultado", String.class), resultado)
                .set(DSL.field("verificado_en", OffsetDateTime.class), ahora)
                .set(consultas, 1)
                .set(DSL.field("ultima_consulta_en", OffsetDateTime.class), ahora)
                .onConflict(DSL.field("codigo", String.class))
                .doUpdate()
                .set(DSL.field("hash_recomputado", String.class), hashRecomputado)
                .set(DSL.field("resultado", String.class), resultado)
                .set(DSL.field("verificado_en", OffsetDateTime.class), ahora)
                .set(consultas, consultasDeLaFila.plus(1))
                .set(DSL.field("ultima_consulta_en", OffsetDateTime.class), ahora)
                .execute();
    }

    public Optional<Verificacion> verificacionDe(DSLContext dsl, String codigo) {
        return dsl.select(
                        DSL.field("codigo", String.class),
                        DSL.field("resultado", String.class),
                        DSL.field("consultas", Integer.class))
                .from(DSL.table(DSL.name("transparencia", "verificacion_publica")))
                .where(DSL.field("codigo", String.class).eq(codigo))
                .fetchOptional(f -> new Verificacion(
                        f.get("codigo", String.class),
                        f.get("resultado", String.class),
                        f.get("consultas", Integer.class)));
    }

    public record Persistido(
            UUID id,
            long numero,
            String hashAnterior,
            String raizMerkle,
            String hash,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            OffsetDateTime selladoEn) {}

    public record Ultimo(long numero, String hash) {}

    public record Verificacion(String codigo, String resultado, int consultas) {}
}
