package bo.aportaya.cumplimiento.infraestructura;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** El expediente del incidente, el activo afectado y quien responde por el. */
@Component
public class IncidenteSeguridadRepositorio {

    private static final org.jooq.Name INCIDENTE = DSL.name("cumplimiento", "incidente_seguridad");
    private static final org.jooq.Name ACTIVO = DSL.name("cumplimiento", "activo_informacion");
    private static final org.jooq.Name DESIGNACION = DSL.name("cumplimiento", "designacion_regulatoria");

    /** El cargo que la norma exige tener designado para gestionar un incidente. */
    private static final String CARGO_EXIGIDO = "RESPONSABLE_SEGURIDAD_INFORMACION";

    /**
     * El activo afectado, con lo que decide el resto: si contiene datos personales y por
     * que contrato de tercero pasa.
     */
    public record Activo(
            UUID id, String clasificacion, boolean contieneDatosPersonales, Optional<UUID> contratoTerceroId) {}

    /** Un incidente cuyo plazo de reporte vencio sin haberse reportado. */
    public record IncidenteVencido(UUID id, String codigo, String severidad, OffsetDateTime plazoReporte) {}

    /**
     * Quien responde por seguridad de la informacion, hoy.
     *
     * <p>Vacio significa <b>no se puede gestionar el incidente</b>. No es un detalle
     * burocratico: sin responsable designado y activo, no hay a quien le corran los
     * plazos, y el expediente que se abriria no tendria firma.
     */
    public Optional<UUID> responsableDeSeguridad(DSLContext dsl, LocalDate hoy) {
        return Optional.ofNullable(dsl.select(DSL.field("usuario_id", UUID.class))
                .from(DSL.table(DESIGNACION))
                .where(DSL.field("cargo", String.class).eq(CARGO_EXIGIDO))
                .and(DSL.field("activo", Boolean.class).isTrue())
                .and(DSL.field("fecha_baja", LocalDate.class)
                        .isNull()
                        .or(DSL.field("fecha_baja", LocalDate.class).gt(hoy)))
                // El titular manda sobre el suplente si los dos estan activos: es quien
                // firma ante el organismo.
                .orderBy(DSL.field("tipo", String.class).desc())
                .limit(1)
                .fetchOne(0, UUID.class));
    }

    public Optional<Activo> activo(DSLContext dsl, UUID activoId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("clasificacion", String.class),
                        DSL.field("contiene_datos_personales", Boolean.class),
                        DSL.field("contrato_tercero_id", UUID.class))
                .from(DSL.table(ACTIVO))
                .where(DSL.field("id").eq(activoId))
                .fetchOne();
        return fila == null
                ? Optional.empty()
                : Optional.of(new Activo(
                        fila.get("id", UUID.class),
                        fila.get("clasificacion", String.class),
                        Boolean.TRUE.equals(fila.get("contiene_datos_personales", Boolean.class)),
                        Optional.ofNullable(fila.get("contrato_tercero_id", UUID.class))));
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public UUID abrir(
            DSLContext dsl,
            String codigo,
            UUID activoId,
            UUID responsableId,
            String tipo,
            String severidad,
            boolean datosPersonales,
            int usuariosAfectados,
            OffsetDateTime detectadoEn,
            OffsetDateTime plazoReporte) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(INCIDENTE))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("codigo", String.class),
                        DSL.field("activo_informacion_id", UUID.class),
                        DSL.field("responsable_id", UUID.class),
                        DSL.field("tipo", String.class),
                        DSL.field("severidad", String.class),
                        DSL.field("datos_personales_afectados", Boolean.class),
                        DSL.field("usuarios_afectados", Integer.class),
                        DSL.field("detectado_en", OffsetDateTime.class),
                        DSL.field("plazo_reporte", OffsetDateTime.class),
                        DSL.field("estado", String.class))
                .values(
                        id,
                        codigo,
                        activoId,
                        responsableId,
                        tipo,
                        severidad,
                        datosPersonales,
                        usuariosAfectados,
                        detectadoEn,
                        plazoReporte,
                        "DETECTADO")
                .execute();
        return id;
    }

    /** Enlaza el incidente con la perdida que dejo (CU-54). */
    public void enlazarConEventoDeRiesgo(DSLContext dsl, UUID incidenteId, UUID eventoRiesgoId) {
        dsl.update(DSL.table(INCIDENTE))
                .set(DSL.field("evento_riesgo_id", UUID.class), eventoRiesgoId)
                .where(DSL.field("id").eq(incidenteId))
                .execute();
    }

    public void marcarReportado(DSLContext dsl, UUID incidenteId, OffsetDateTime cuando) {
        dsl.update(DSL.table(INCIDENTE))
                .set(DSL.field("reportado_al_organismo_en", OffsetDateTime.class), cuando)
                .where(DSL.field("id").eq(incidenteId))
                .execute();
    }

    public void marcarNotificado(DSLContext dsl, UUID incidenteId, OffsetDateTime cuando) {
        dsl.update(DSL.table(INCIDENTE))
                .set(DSL.field("notificado_a_titulares_en", OffsetDateTime.class), cuando)
                .where(DSL.field("id").eq(incidenteId))
                .execute();
    }

    public Optional<Estado> estado(DSLContext dsl, UUID incidenteId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("codigo", String.class),
                        DSL.field("datos_personales_afectados", Boolean.class),
                        DSL.field("plazo_reporte", OffsetDateTime.class),
                        DSL.field("reportado_al_organismo_en", OffsetDateTime.class),
                        DSL.field("notificado_a_titulares_en", OffsetDateTime.class),
                        DSL.field("estado", String.class))
                .from(DSL.table(INCIDENTE))
                .where(DSL.field("id").eq(incidenteId))
                .forUpdate()
                .fetchOne();
        return fila == null
                ? Optional.empty()
                : Optional.of(new Estado(
                        fila.get("id", UUID.class),
                        fila.get("codigo", String.class),
                        Boolean.TRUE.equals(fila.get("datos_personales_afectados", Boolean.class)),
                        fila.get("plazo_reporte", OffsetDateTime.class),
                        Optional.ofNullable(fila.get("reportado_al_organismo_en", OffsetDateTime.class)),
                        Optional.ofNullable(fila.get("notificado_a_titulares_en", OffsetDateTime.class)),
                        fila.get("estado", String.class)));
    }

    public record Estado(
            UUID id,
            String codigo,
            boolean datosPersonalesAfectados,
            OffsetDateTime plazoReporte,
            Optional<OffsetDateTime> reportadoEn,
            Optional<OffsetDateTime> notificadoEn,
            String estado) {}

    /**
     * Los incidentes cuyo plazo vencio sin reportar.
     *
     * <p>Igual que los planes de CU-54: {@code FOR UPDATE SKIP LOCKED}, para que dos
     * replicas del control se repartan el trabajo en vez de pelear por las mismas filas.
     */
    public List<IncidenteVencido> vencidosSinReportar(DSLContext dsl, OffsetDateTime ahora, int tope) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("codigo", String.class),
                        DSL.field("severidad", String.class),
                        DSL.field("plazo_reporte", OffsetDateTime.class))
                .from(DSL.table(INCIDENTE))
                .where(DSL.field("plazo_reporte", OffsetDateTime.class).lt(ahora))
                .and(DSL.field("reportado_al_organismo_en").isNull())
                .and(DSL.field("estado", String.class).ne("CERRADO"))
                .orderBy(DSL.field("plazo_reporte"))
                .limit(tope)
                .forUpdate()
                .skipLocked()
                .fetch(fila -> new IncidenteVencido(
                        fila.get("id", UUID.class),
                        fila.get("codigo", String.class),
                        fila.get("severidad", String.class),
                        fila.get("plazo_reporte", OffsetDateTime.class)));
    }
}
