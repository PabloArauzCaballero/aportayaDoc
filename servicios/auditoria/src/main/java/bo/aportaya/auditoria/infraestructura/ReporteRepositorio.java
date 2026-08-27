package bo.aportaya.auditoria.infraestructura;

import bo.aportaya.auditoria.dominio.ParametrosDeReporte;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** Lee y escribe el circuito de reportes de {@code auditoria}. */
@Component
public class ReporteRepositorio {

    private static final org.jooq.Name DEFINICION = DSL.name("auditoria", "definicion_reporte");
    private static final org.jooq.Name EJECUCION = DSL.name("auditoria", "ejecucion_reporte");
    private static final org.jooq.Name EXPORTACION = DSL.name("auditoria", "exportacion_reporte");

    /**
     * Vive en {@code comun}, no en {@code auditoria}: el registro de acceso a datos es
     * de todos los servicios, no de este. `svc_auditoria` tiene INSERT y nada mas —
     * ni siquiera puede leer lo que escribio, y esa es la idea.
     */
    private static final org.jooq.Name ACCESO = DSL.name("comun", "registro_acceso_datos");

    public record Definicion(
            UUID id,
            String nombre,
            String permisoRequerido,
            boolean contieneDatosSensibles,
            boolean activa,
            String consultaBase,
            List<ParametrosDeReporte.Esperado> esperados) {}

    public record Exportacion(UUID id, UUID ejecucionId, boolean estaCifrado, int descargas, OffsetDateTime expiraEn) {}

    public Optional<Definicion> definicion(DSLContext dsl, UUID definicionId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("nombre", String.class),
                        DSL.field("permiso_requerido", String.class),
                        DSL.field("contiene_datos_sensibles", Boolean.class),
                        DSL.field("activa", Boolean.class),
                        DSL.field("consulta_base", String.class))
                .from(DSL.table(DEFINICION))
                .where(DSL.field("id").eq(definicionId))
                .fetchOne();

        if (fila == null) {
            return Optional.empty();
        }
        return Optional.of(new Definicion(
                fila.get("id", UUID.class),
                fila.get("nombre", String.class),
                fila.get("permiso_requerido", String.class),
                Boolean.TRUE.equals(fila.get("contiene_datos_sensibles", Boolean.class)),
                Boolean.TRUE.equals(fila.get("activa", Boolean.class)),
                fila.get("consulta_base", String.class),
                esperadosDe(dsl, definicionId)));
    }

    /**
     * Los parametros declarados, leidos por el motor y no por una expresion regular.
     *
     * <p>La primera version parseaba el JSONB a mano y funcionaba en las pruebas de
     * escritorio; contra la base fallaba siempre, porque <b>{@code jsonb} no conserva el
     * orden de las claves</b>: las reordena por longitud y despues alfabeticamente, de
     * modo que {@code {"nombre":…,"tipo":…}} vuelve como {@code {"tipo":…,"nombre":…}}.
     * Un patron que espera un orden lee cero parametros y la lista blanca queda vacia —
     * es decir, deja de rechazar. La falla mas peligrosa posible en esta clase, y
     * silenciosa.
     *
     * <p>{@code jsonb_to_recordset} no tiene ese problema: pide los campos por nombre y
     * de paso los convierte al tipo correcto.
     */
    private List<ParametrosDeReporte.Esperado> esperadosDe(DSLContext dsl, UUID definicionId) {
        return dsl.fetch(
                        """
                        SELECT p.nombre, p.tipo, coalesce(p.obligatorio, false) AS obligatorio
                          FROM auditoria.definicion_reporte d,
                               LATERAL jsonb_to_recordset(d.parametros_esperados)
                                    AS p(nombre text, tipo text, obligatorio boolean)
                         WHERE d.id = ?
                        """,
                        definicionId)
                .map(fila -> new ParametrosDeReporte.Esperado(
                        fila.get("nombre", String.class),
                        fila.get("tipo", String.class),
                        Boolean.TRUE.equals(fila.get("obligatorio", Boolean.class))));
    }

    public UUID abrirEjecucion(
            DSLContext dsl,
            UUID definicionId,
            UUID solicitadoPor,
            Map<String, String> parametros,
            OffsetDateTime ahora) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(EJECUCION))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("definicion_id", UUID.class),
                        DSL.field("solicitado_por", UUID.class),
                        DSL.field("parametros", JSONB.class),
                        DSL.field("estado", String.class),
                        DSL.field("filas_generadas", Integer.class),
                        DSL.field("duracion_ms", Integer.class),
                        DSL.field("iniciada_en", OffsetDateTime.class))
                .values(id, definicionId, solicitadoPor, comoJson(parametros), "EN_COLA", 0, 0, ahora)
                .execute();
        return id;
    }

    public void cerrarEjecucion(
            DSLContext dsl,
            UUID ejecucionId,
            String estado,
            int filas,
            int duracionMs,
            String hash,
            OffsetDateTime ahora) {
        dsl.update(DSL.table(EJECUCION))
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("filas_generadas", Integer.class), filas)
                .set(DSL.field("duracion_ms", Integer.class), duracionMs)
                .set(DSL.field("hash_resultado", String.class), hash)
                .set(DSL.field("finalizada_en", OffsetDateTime.class), ahora)
                .where(DSL.field("id").eq(ejecucionId))
                .execute();
    }

    /**
     * Deja la constancia de una ejecucion que no produjo resultado.
     *
     * <p>Es un INSERT completo y no un UPDATE sobre una fila abierta antes a proposito:
     * se llama desde una transaccion aparte, precisamente porque la de afuera se va a
     * revertir. La fila que hubiera abierto la de afuera no existe.
     */
    public UUID registrarIntentoFallido(
            DSLContext dsl,
            UUID definicionId,
            UUID solicitadoPor,
            Map<String, String> parametros,
            String motivo,
            OffsetDateTime ahora) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(EJECUCION))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("definicion_id", UUID.class),
                        DSL.field("solicitado_por", UUID.class),
                        DSL.field("parametros", JSONB.class),
                        DSL.field("estado", String.class),
                        DSL.field("filas_generadas", Integer.class),
                        DSL.field("duracion_ms", Integer.class),
                        DSL.field("mensaje_error", String.class),
                        DSL.field("iniciada_en", OffsetDateTime.class),
                        DSL.field("finalizada_en", OffsetDateTime.class))
                .values(id, definicionId, solicitadoPor, comoJson(parametros), "FALLIDA", 0, 0, motivo, ahora, ahora)
                .execute();
        return id;
    }

    /**
     * El rastro de `R-SEG-02`. La tabla es append-only y particionada por
     * {@code fecha_hora}: el momento no es adorno, es la clave que decide en que
     * particion cae la fila.
     */
    public void registrarAcceso(
            DSLContext dsl, UUID consultor, UUID afectado, String justificacion, int registros, OffsetDateTime ahora) {
        dsl.insertInto(DSL.table(ACCESO))
                .columns(
                        DSL.field("usuario_consultor_id", UUID.class),
                        DSL.field("usuario_afectado_id", UUID.class),
                        DSL.field("tipo_dato", String.class),
                        DSL.field("operacion", String.class),
                        DSL.field("justificacion", String.class),
                        DSL.field("cantidad_registros", Integer.class),
                        DSL.field("ip_origen", Object.class),
                        DSL.field("fecha_hora", OffsetDateTime.class))
                .values(
                        consultor,
                        afectado,
                        "HISTORIAL_PAGOS",
                        "EXPORTACION",
                        justificacion,
                        registros,
                        DSL.field("inet ?", Object.class, IP_DEL_PROCESO),
                        ahora)
                .execute();
    }

    /**
     * De donde salio el acceso. Sin cabecera confiable —la red interna no es perimetro
     * de confianza— se registra la del proceso, que es un dato cierto, en vez de una
     * cabecera que cualquiera puede escribir.
     */
    private static final String IP_DEL_PROCESO = "127.0.0.1";

    public UUID exportar(
            DSLContext dsl,
            UUID ejecucionId,
            String formato,
            String url,
            String hashArchivo,
            long tamanoBytes,
            boolean cifrado,
            OffsetDateTime expiraEn,
            OffsetDateTime ahora) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(EXPORTACION))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("ejecucion_id", UUID.class),
                        DSL.field("formato", String.class),
                        DSL.field("url_archivo", String.class),
                        DSL.field("hash_archivo", String.class),
                        DSL.field("tamano_bytes", Long.class),
                        DSL.field("esta_cifrado", Boolean.class),
                        DSL.field("version_llave", Integer.class),
                        DSL.field("descargas", Integer.class),
                        DSL.field("expira_en", OffsetDateTime.class),
                        DSL.field("generada_en", OffsetDateTime.class))
                .values(id, ejecucionId, formato, url, hashArchivo, tamanoBytes, cifrado, 1, 0, expiraEn, ahora)
                .execute();
        return id;
    }

    public Optional<Exportacion> exportacion(DSLContext dsl, UUID exportacionId) {
        Record fila = dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("ejecucion_id", UUID.class),
                        DSL.field("esta_cifrado", Boolean.class),
                        DSL.field("descargas", Integer.class),
                        DSL.field("expira_en", OffsetDateTime.class))
                .from(DSL.table(EXPORTACION))
                .where(DSL.field("id").eq(exportacionId))
                // Se toma para actualizar: dos descargas simultaneas contarian una sola
                // y el tope dejaria de ser tope.
                .forUpdate()
                .fetchOne();
        return fila == null
                ? Optional.empty()
                : Optional.of(new Exportacion(
                        fila.get("id", UUID.class),
                        fila.get("ejecucion_id", UUID.class),
                        Boolean.TRUE.equals(fila.get("esta_cifrado", Boolean.class)),
                        fila.get("descargas", Integer.class),
                        fila.get("expira_en", OffsetDateTime.class)));
    }

    public int contarDescarga(DSLContext dsl, UUID exportacionId) {
        return dsl.update(DSL.table(EXPORTACION))
                .set(
                        DSL.field("descargas", Integer.class),
                        DSL.field("descargas", Integer.class).plus(1))
                .where(DSL.field("id").eq(exportacionId))
                .execute();
    }

    private static JSONB comoJson(Map<String, String> valores) {
        String cuerpo = valores.entrySet().stream()
                .map(e -> '"' + escapar(e.getKey()) + "\":\"" + escapar(e.getValue()) + '"')
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return JSONB.valueOf("{" + cuerpo + "}");
    }

    private static String escapar(String valor) {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
