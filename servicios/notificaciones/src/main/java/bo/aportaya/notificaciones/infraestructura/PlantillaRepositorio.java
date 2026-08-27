package bo.aportaya.notificaciones.infraestructura;

import bo.aportaya.notificaciones.dominio.Canal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code plantilla_mensaje} y su {@code version_plantilla} vigente.
 *
 * <p>Solo devuelve plantillas **aprobadas y activas**. Improvisar el texto de un
 * mensaje que sale a nombre de una entidad financiera es exactamente lo que la
 * aprobacion previa existe para impedir.
 */
@Component
public class PlantillaRepositorio {

    public Optional<Version> vigentePara(
            DSLContext dsl, String codigo, Canal canal, String idioma, OffsetDateTime ahora) {
        Record fila = dsl.select(
                        DSL.field("v.id", UUID.class).as("version_id"),
                        DSL.field("v.cuerpo", String.class).as("cuerpo"),
                        DSL.field("v.asunto", String.class).as("asunto"),
                        DSL.field("p.id", UUID.class).as("plantilla_id"))
                .from(DSL.table(DSL.name("notificaciones", "version_plantilla")).as("v"))
                .join(DSL.table(DSL.name("notificaciones", "plantilla_mensaje")).as("p"))
                .on(DSL.field("v.plantilla_id", UUID.class).eq(DSL.field("p.id", UUID.class)))
                .where(DSL.field("p.codigo").eq(codigo))
                .and(DSL.field("p.canal").eq(canal.name()))
                .and(DSL.field("p.activa", Boolean.class).isTrue())
                .and(DSL.field("p.estado_aprobacion").eq("APROBADA"))
                .and(DSL.field("v.idioma").eq(idioma))
                .and(DSL.field("v.vigente_desde", OffsetDateTime.class).le(ahora))
                .and(DSL.field("v.vigente_hasta")
                        .isNull()
                        .or(DSL.field("v.vigente_hasta", OffsetDateTime.class).gt(ahora)))
                .orderBy(DSL.field("v.version").desc())
                .limit(1)
                .fetchOne();

        return Optional.ofNullable(fila)
                .map(f -> new Version(
                        f.get("version_id", UUID.class),
                        f.get("plantilla_id", UUID.class),
                        f.get("cuerpo", String.class),
                        f.get("asunto", String.class)));
    }

    public record Version(UUID id, UUID plantillaId, String cuerpo, String asunto) {}
}
