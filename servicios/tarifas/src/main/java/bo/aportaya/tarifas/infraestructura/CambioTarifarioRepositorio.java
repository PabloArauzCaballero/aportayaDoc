package bo.aportaya.tarifas.infraestructura;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code catalogo.tarifario}, {@code cambio_tarifario} y {@code simulacion_tarifa}.
 *
 * <p>Un tarifario vigente **no se edita** ({@code tg_concepto_tarifa_inmutable}): se
 * publica la version siguiente. Poder decir que se cobraba en una fecha pasada es la
 * unica forma de responder un reclamo de hace seis meses.
 */
@Component
public class CambioTarifarioRepositorio {

    public Optional<Tarifario> ver(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("codigo", String.class),
                        DSL.field("version", Short.class),
                        DSL.field("estado", String.class),
                        DSL.field("moneda_base", String.class),
                        DSL.field("dias_preaviso", Short.class),
                        DSL.field("vigente_desde", OffsetDateTime.class))
                .from(DSL.table(DSL.name("catalogo", "tarifario")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(f -> new Tarifario(
                        f.get("id", UUID.class),
                        f.get("codigo", String.class),
                        f.get("version", Short.class),
                        f.get("estado", String.class),
                        f.get("moneda_base", String.class),
                        f.get("dias_preaviso", Short.class),
                        f.get("vigente_desde", OffsetDateTime.class)));
    }

    /** Clona el tarifario como version N+1 en BORRADOR. */
    public UUID clonar(DSLContext dsl, Tarifario base, String nombre, int diasPreaviso, OffsetDateTime desde) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("catalogo", "tarifario")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("codigo", String.class), base.codigo())
                .set(DSL.field("version", Short.class), (short) (base.version() + 1))
                .set(DSL.field("nombre", String.class), nombre)
                .set(DSL.field("estado", String.class), "BORRADOR")
                .set(DSL.field("moneda_base", String.class), base.monedaBase())
                .set(DSL.field("vigente_desde", OffsetDateTime.class), desde)
                .set(DSL.field("dias_preaviso", Short.class), (short) diasPreaviso)
                .set(DSL.field("tarifario_anterior_id", UUID.class), base.id())
                .execute();
        return id;
    }

    /**
     * Publica el tarifario: sin {@code publicado_en}, {@code url_publicacion} y
     * {@code hash_documento} la base no lo deja pasar a VIGENTE
     * ({@code ck_tarifario_publicado}). Un tarifario vigente que nadie publico es
     * exactamente lo que la transparencia exige que no pase.
     */
    public void publicar(
            DSLContext dsl, UUID id, String url, String hash, UUID aprobadoPor, String acta, OffsetDateTime ahora) {
        dsl.update(DSL.table(DSL.name("catalogo", "tarifario")))
                .set(DSL.field("publicado_en", OffsetDateTime.class), ahora)
                .set(DSL.field("url_publicacion", String.class), url)
                .set(DSL.field("hash_documento", String.class), hash)
                .set(DSL.field("aprobado_por", UUID.class), aprobadoPor)
                .set(DSL.field("acta_aprobacion", String.class), acta)
                .where(DSL.field("id", UUID.class).eq(id))
                .execute();
    }

    public boolean cambiarEstado(DSLContext dsl, UUID id, String desde, String hacia) {
        return dsl.update(DSL.table(DSL.name("catalogo", "tarifario")))
                        .set(DSL.field("estado", String.class), hacia)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).eq(desde)))
                        .execute()
                == 1;
    }

    /** Cierra la vigencia del anterior: sin esto, el EXCLUDE de R-TAR-01 rechaza el nuevo. */
    public void sustituir(DSLContext dsl, UUID id, OffsetDateTime hasta) {
        dsl.update(DSL.table(DSL.name("catalogo", "tarifario")))
                .set(DSL.field("estado", String.class), "SUSTITUIDO")
                .set(DSL.field("vigente_hasta", OffsetDateTime.class), hasta)
                .where(DSL.field("id", UUID.class).eq(id))
                .execute();
    }

    public UUID registrarCambio(
            DSLContext dsl,
            UUID anteriorId,
            UUID nuevoId,
            UUID aprobadoPor,
            String tipoCambio,
            boolean requierePreaviso,
            int diasPreaviso,
            boolean permiteRescision) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("tarifas", "cambio_tarifario")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("tarifario_anterior_id", UUID.class), anteriorId)
                .set(DSL.field("tarifario_nuevo_id", UUID.class), nuevoId)
                .set(DSL.field("aprobado_por", UUID.class), aprobadoPor)
                .set(DSL.field("tipo_cambio", String.class), tipoCambio)
                .set(DSL.field("requiere_preaviso", Boolean.class), requierePreaviso)
                .set(DSL.field("dias_preaviso", Short.class), (short) diasPreaviso)
                .set(DSL.field("usuarios_notificados", Integer.class), 0)
                .set(DSL.field("permite_rescision_sin_costo", Boolean.class), permiteRescision)
                .execute();
        return id;
    }

    /** Anota el aviso: la fecha se GUARDA, y es contra ella que corre el preaviso. */
    public void anotarAviso(DSLContext dsl, UUID cambioId, String canal, int notificados, OffsetDateTime ahora) {
        dsl.update(DSL.table(DSL.name("tarifas", "cambio_tarifario")))
                .set(DSL.field("fecha_aviso", OffsetDateTime.class), ahora)
                .set(DSL.field("canal_aviso", String.class), canal)
                .set(DSL.field("usuarios_notificados", Integer.class), notificados)
                .set(DSL.field("publicado_en", OffsetDateTime.class), ahora)
                .where(DSL.field("id", UUID.class).eq(cambioId))
                .execute();
    }

    public Optional<Cambio> cambioDe(DSLContext dsl, UUID tarifarioNuevoId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("tipo_cambio", String.class),
                        DSL.field("requiere_preaviso", Boolean.class),
                        DSL.field("dias_preaviso", Short.class),
                        DSL.field("fecha_aviso", OffsetDateTime.class))
                .from(DSL.table(DSL.name("tarifas", "cambio_tarifario")))
                .where(DSL.field("tarifario_nuevo_id", UUID.class).eq(tarifarioNuevoId))
                .fetchOptional(f -> new Cambio(
                        f.get("id", UUID.class),
                        f.get("tipo_cambio", String.class),
                        f.get("requiere_preaviso", Boolean.class),
                        f.get("dias_preaviso", Short.class),
                        f.get("fecha_aviso", OffsetDateTime.class)));
    }

    public UUID guardarSimulacion(
            DSLContext dsl,
            UUID tarifarioId,
            UUID ejecutadaPor,
            String escenario,
            String resultado,
            BigDecimal deltaIngreso,
            int usuariosImpactados,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("tarifas", "simulacion_tarifa")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("tarifario_id", UUID.class), tarifarioId)
                .set(DSL.field("ejecutada_por", UUID.class), ejecutadaPor)
                .set(DSL.field("escenario", JSONB.class), JSONB.valueOf(escenario))
                .set(DSL.field("resultado", JSONB.class), JSONB.valueOf(resultado))
                .set(DSL.field("delta_ingreso_estimado", BigDecimal.class), deltaIngreso)
                .set(DSL.field("usuarios_impactados", Integer.class), usuariosImpactados)
                .set(DSL.field("ejecutada_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    public record Tarifario(
            UUID id,
            String codigo,
            short version,
            String estado,
            String monedaBase,
            short diasPreaviso,
            OffsetDateTime vigenteDesde) {}

    public record Cambio(
            UUID id, String tipoCambio, boolean requierePreaviso, short diasPreaviso, OffsetDateTime fechaAviso) {}
}
