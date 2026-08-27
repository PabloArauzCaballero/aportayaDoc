package bo.aportaya.auditoria;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/**
 * Los datos que las pruebas del circuito de reportes necesitan encontrar puestos.
 *
 * <p>Aparte de {@link FixturaDeAuditoria} porque son otro grupo de tablas y otro caso de
 * uso. Juntas pasaban el limite de tamano, y ese limite no es una regla de estilo: un
 * archivo de fixturas que crece sin partirse termina siendo el lugar donde todos tocan y
 * nadie entiende.
 *
 * <p>Escribe con un {@code DSLContext} que <b>no</b> pasa por la transaccion del caso de
 * uso: si escribiera dentro de la misma, la prueba comprobaria que el caso de uso ve lo
 * que ella misma acaba de poner, y no lo que hay en la base.
 */
class FixturaDeReportes {

    private static final org.jooq.Name DEFINICION = DSL.name("auditoria", "definicion_reporte");
    private static final org.jooq.Name EJECUCION = DSL.name("auditoria", "ejecucion_reporte");
    private static final org.jooq.Name EXPORTACION = DSL.name("auditoria", "exportacion_reporte");

    private final DSLContext dsl;

    FixturaDeReportes(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Una definicion de reporte activa.
     *
     * <p>La {@code consulta_base} es de verdad y se corre: una prueba con una consulta de
     * mentira comprobaria que el caso de uso guarda bien un resultado inventado, que es
     * justo lo que no interesa. Los marcadores {@code :nombre} son los que liga el
     * ejecutor.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    UUID definicionDeReporte(
            String nombre, String permiso, boolean sensible, String consulta, String parametrosEsperados) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DEFINICION))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("tipo", String.class),
                        DSL.field("nombre", String.class),
                        DSL.field("descripcion", String.class),
                        DSL.field("consulta_base", String.class),
                        DSL.field("parametros_esperados", org.jooq.JSONB.class),
                        DSL.field("columnas", org.jooq.JSONB.class),
                        DSL.field("permiso_requerido", String.class),
                        DSL.field("contiene_datos_sensibles", Boolean.class),
                        DSL.field("cache_minutos", Integer.class),
                        DSL.field("activa", Boolean.class))
                .values(
                        id,
                        "HISTORICO_DE_PAGOS",
                        nombre,
                        "Definicion de prueba para el circuito de reportes.",
                        consulta,
                        org.jooq.JSONB.valueOf(parametrosEsperados),
                        org.jooq.JSONB.valueOf("[\"columna\"]"),
                        permiso,
                        sensible,
                        0,
                        true)
                .execute();
        return id;
    }

    String estadoDeLaEjecucion(UUID ejecucionId) {
        return dsl.select(DSL.field("estado", String.class))
                .from(DSL.table(EJECUCION))
                .where(DSL.field("id").eq(ejecucionId))
                .fetchOne(0, String.class);
    }

    /** Las ejecuciones que dejo una definicion, con su estado y su motivo. */
    java.util.List<String> ejecucionesDe(UUID definicionId) {
        return dsl.select(DSL.field("estado", String.class), DSL.field("mensaje_error", String.class))
                .from(DSL.table(EJECUCION))
                .where(DSL.field("definicion_id").eq(definicionId))
                .fetch(fila -> fila.get(0, String.class) + ":" + String.valueOf(fila.get(1, String.class)));
    }

    /** La exportacion de una ejecucion: cifrado, caducidad y descargas. */
    org.jooq.Record exportacionDe(UUID ejecucionId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("esta_cifrado", Boolean.class),
                        DSL.field("expira_en", OffsetDateTime.class),
                        DSL.field("descargas", Integer.class))
                .from(DSL.table(EXPORTACION))
                .where(DSL.field("ejecucion_id").eq(ejecucionId))
                .fetchOne();
    }

    /** Adelanta el vencimiento de una exportacion para probar la caducidad sin esperar. */
    void vencerExportacion(UUID exportacionId) {
        dsl.update(DSL.table(EXPORTACION))
                .set(
                        DSL.field("expira_en", OffsetDateTime.class),
                        OffsetDateTime.now().minusDays(1))
                .where(DSL.field("id").eq(exportacionId))
                .execute();
    }

    void agotarDescargas(UUID exportacionId, int tope) {
        dsl.update(DSL.table(EXPORTACION))
                .set(DSL.field("descargas", Integer.class), tope)
                .where(DSL.field("id").eq(exportacionId))
                .execute();
    }

    int accesosRegistradosDe(UUID consultor) {
        Number cuantos = (Number) dsl.selectCount()
                .from(DSL.table(DSL.name("comun", "registro_acceso_datos")))
                .where(DSL.field("usuario_consultor_id").eq(consultor))
                .fetchOne(0);
        return cuantos.intValue();
    }
}
