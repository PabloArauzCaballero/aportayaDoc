package bo.aportaya.cumplimiento.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code catalogo_reporte_regulatorio}, {@code reporte_regulatorio} y
 * {@code envio_regulatorio}.
 *
 * <p>**El plazo sale del catalogo y se guarda al generar**, no se recalcula al
 * consultar: si el catalogo cambia de plazo, los reportes viejos conservan la fecha
 * limite contra la que efectivamente se los juzgo.
 *
 * <p>Y **el reporte en cero existe**: un mes sin operaciones sobre umbral se informa en
 * cero (R-UIF-06). No informar nada y no informar cero son cosas distintas para el
 * regulador — la primera parece un olvido.
 */
@Component
public class ReporteRegulatorioRepositorio {

    public Optional<Catalogo> catalogoPorCodigo(DSLContext dsl, String codigo) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("codigo", String.class),
                        DSL.field("organismo", String.class),
                        DSL.field("periodicidad", String.class),
                        DSL.field("formato", String.class),
                        DSL.field("plazo_dias", Short.class),
                        DSL.field("obligatorio", Boolean.class))
                .from(DSL.table(DSL.name("cumplimiento", "catalogo_reporte_regulatorio")))
                .where(DSL.field("codigo", String.class)
                        .eq(codigo)
                        .and(DSL.field("activo", Boolean.class).isTrue()))
                .fetchOptional(f -> new Catalogo(
                        f.get("id", UUID.class),
                        f.get("codigo", String.class),
                        f.get("organismo", String.class),
                        f.get("periodicidad", String.class),
                        f.get("formato", String.class),
                        f.get("plazo_dias", Short.class).intValue(),
                        f.get("obligatorio", Boolean.class)));
    }

    public Optional<Reporte> reporteDe(DSLContext dsl, UUID catalogoId, String periodo) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("estado", String.class),
                        DSL.field("cantidad_registros", Integer.class),
                        DSL.field("reporte_en_cero", Boolean.class),
                        DSL.field("hash_archivo", String.class),
                        DSL.field("fecha_limite", LocalDate.class),
                        DSL.field("generado_por", UUID.class),
                        DSL.field("aprobado_por", UUID.class))
                .from(DSL.table(DSL.name("cumplimiento", "reporte_regulatorio")))
                .where(DSL.field("catalogo_reporte_id", UUID.class)
                        .eq(catalogoId)
                        .and(DSL.field("periodo", String.class).eq(periodo)))
                .fetchOptional(f -> new Reporte(
                        f.get("id", UUID.class),
                        f.get("estado", String.class),
                        f.get("cantidad_registros", Integer.class),
                        f.get("reporte_en_cero", Boolean.class),
                        f.get("hash_archivo", String.class),
                        f.get("fecha_limite", LocalDate.class),
                        f.get("generado_por", UUID.class),
                        f.get("aprobado_por", UUID.class)));
    }

    public UUID generar(
            DSLContext dsl,
            UUID catalogoId,
            UUID generadoPor,
            String periodo,
            LocalDate fechaCorte,
            LocalDate fechaLimite,
            int cantidad,
            BigDecimal montoTotal,
            String url,
            String hash,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "reporte_regulatorio")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("catalogo_reporte_id", UUID.class), catalogoId)
                .set(DSL.field("generado_por", UUID.class), generadoPor)
                .set(DSL.field("periodo", String.class), periodo)
                .set(DSL.field("fecha_corte", LocalDate.class), fechaCorte)
                .set(DSL.field("estado", String.class), "GENERADO")
                .set(DSL.field("cantidad_registros", Integer.class), cantidad)
                // ck_reporte_en_cero exige que las dos cosas digan lo mismo: el numero y
                // la bandera. Derivarla evita que alguien las deje contradiciendose.
                .set(DSL.field("reporte_en_cero", Boolean.class), cantidad == 0)
                .set(DSL.field("monto_total", BigDecimal.class), montoTotal)
                .set(DSL.field("url_archivo", String.class), url)
                .set(DSL.field("hash_archivo", String.class), hash)
                .set(DSL.field("fecha_limite", LocalDate.class), fechaLimite)
                .set(DSL.field("generado_en", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /** Aprobar es un acto separado de generar: lo exige R-SEG-04 y lo verifica el CU. */
    public boolean aprobar(DSLContext dsl, UUID reporteId, UUID aprobadoPor) {
        return dsl.update(DSL.table(DSL.name("cumplimiento", "reporte_regulatorio")))
                        .set(DSL.field("aprobado_por", UUID.class), aprobadoPor)
                        .set(DSL.field("estado", String.class), "APROBADO")
                        .where(DSL.field("id", UUID.class)
                                .eq(reporteId)
                                .and(DSL.field("estado", String.class).eq("GENERADO")))
                        .execute()
                == 1;
    }

    public UUID registrarEnvio(
            DSLContext dsl,
            UUID reporteId,
            UUID enviadoPor,
            String organismo,
            String canal,
            String estado,
            String numeroConstancia,
            int reintentos,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "envio_regulatorio")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("reporte_regulatorio_id", UUID.class), reporteId)
                .set(DSL.field("enviado_por", UUID.class), enviadoPor)
                .set(DSL.field("organismo", String.class), organismo)
                .set(DSL.field("canal", String.class), canal)
                .set(DSL.field("fecha_envio", OffsetDateTime.class), ahora)
                .set(DSL.field("numero_constancia", String.class), numeroConstancia)
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("reintentos", Short.class), (short) reintentos)
                .execute();
        if ("ACEPTADO".equals(estado)) {
            dsl.update(DSL.table(DSL.name("cumplimiento", "reporte_regulatorio")))
                    .set(DSL.field("estado", String.class), "ENVIADO")
                    .where(DSL.field("id", UUID.class).eq(reporteId))
                    .execute();
        }
        return id;
    }

    /** Los reportes cuya fecha limite paso sin envio aceptado. Es lo que abre hallazgo. */
    public List<Vencido> vencidosSinEnviar(DSLContext dsl, LocalDate corte) {
        var r = DSL.table(DSL.name("cumplimiento", "reporte_regulatorio")).as("r");
        var c = DSL.table(DSL.name("cumplimiento", "catalogo_reporte_regulatorio"))
                .as("c");
        return dsl.select(
                        DSL.field("r.id", UUID.class).as("id"),
                        DSL.field("c.codigo", String.class).as("codigo"),
                        DSL.field("r.periodo", String.class).as("periodo"),
                        DSL.field("r.fecha_limite", LocalDate.class).as("fecha_limite"))
                .from(r)
                .join(c)
                .on(DSL.field("c.id", UUID.class).eq(DSL.field("r.catalogo_reporte_id", UUID.class)))
                .where(DSL.field("r.fecha_limite", LocalDate.class)
                        .lt(corte)
                        .and(DSL.field("r.estado", String.class).ne("ENVIADO")))
                .fetch(f -> new Vencido(
                        f.get("id", UUID.class),
                        f.get("codigo", String.class),
                        f.get("periodo", String.class),
                        f.get("fecha_limite", LocalDate.class)));
    }

    public record Catalogo(
            UUID id,
            String codigo,
            String organismo,
            String periodicidad,
            String formato,
            int plazoDias,
            boolean obligatorio) {}

    public record Reporte(
            UUID id,
            String estado,
            int cantidadRegistros,
            boolean enCero,
            String hashArchivo,
            LocalDate fechaLimite,
            UUID generadoPor,
            UUID aprobadoPor) {}

    public record Vencido(UUID id, String codigo, String periodo, LocalDate fechaLimite) {}
}
