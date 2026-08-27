package bo.aportaya.auditoria;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

/**
 * Los datos que las pruebas de auditoria necesitan encontrar puestos.
 *
 * <p>Escribe con un {@code DSLContext} que **no** pasa por la transaccion del caso de
 * uso: si la fixtura escribiera dentro de la misma transaccion, la prueba comprobaria
 * que el caso de uso ve lo que ella misma acaba de poner, y no lo que hay en la base.
 */
class FixturaDeAuditoria {

    private static final org.jooq.Name DEFINICION = DSL.name("auditoria", "definicion_indicador");
    private static final org.jooq.Name KPI = DSL.name("auditoria", "indicador_kpi");
    private static final org.jooq.Name RETENCION = DSL.name("auditoria", "politica_retencion");
    private static final org.jooq.Name ANONIMIZACION = DSL.name("auditoria", "proceso_anonimizacion");
    private static final org.jooq.Name EVENTOS = DSL.name("auditoria", "evento_dominio");

    private final DSLContext dsl;

    FixturaDeAuditoria(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * No hay `limpiar()`: {@code indicador_kpi} es append-only y la base rechaza el
     * DELETE (`R-AUD-01`). Cada prueba usa **su propio periodo**, que es como se
     * trabaja de verdad contra una tabla sellada — y de paso comprueba que el caso de
     * uso filtra por periodo en vez de depender de una tabla vacia.
     */
    static String periodoDe(String prueba) {
        return prueba;
    }

    /** Intenta pisar el valor de un indicador ya publicado. **Tiene que fallar.** */
    void pisarElValor(String codigo) {
        dsl.update(DSL.table(KPI))
                .set(DSL.field("valor", BigDecimal.class), new BigDecimal("1.0000"))
                .where(DSL.field("codigo").eq(codigo))
                .execute();
    }

    /** Intenta borrar un indicador publicado. **Tiene que fallar.** */
    void borrarIndicador(String codigo) {
        dsl.deleteFrom(DSL.table(KPI)).where(DSL.field("codigo").eq(codigo)).execute();
    }

    /**
     * Intenta dejar un indicador sin su definicion. **Tiene que fallar**: es la prueba
     * de que la garantia vive en el motor y no en la aplicacion.
     */
    void desligarDeSuDefinicion() {
        dsl.update(DSL.table(KPI))
                .set(DSL.field("definicion_indicador_id", UUID.class), (UUID) null)
                .execute();
    }

    private static final java.util.concurrent.atomic.AtomicInteger SECUENCIA =
            new java.util.concurrent.atomic.AtomicInteger(70_000_000);

    /**
     * Un titular. La clave foranea a `identidad.usuario` cruza esquemas y la base la
     * verifica igual: una solicitud sobre alguien que no existe no entra, que es
     * exactamente lo que se quiere de un expediente de datos personales.
     */
    UUID usuario() {
        return usuarioConId(UUID.randomUUID());
    }

    /**
     * El mismo titular, con un identificador elegido. Lo necesita el OPERADOR de las
     * pruebas: `ejecucion_reporte.solicitado_por` tiene clave foranea a
     * `identidad.usuario`, asi que un operador inventado no puede pedir un reporte — y
     * esta bien que no pueda, porque «quien saco que» tiene que apuntar a alguien.
     */
    UUID usuarioConId(UUID id) {
        if (existeUsuario(id)) {
            return id;
        }
        dsl.execute(
                """
                INSERT INTO identidad.usuario
                    (id, codigo_publico, nombres, apellidos, telefono_e164, fecha_nacimiento,
                     estado, nivel_kyc, idioma, zona_horaria, fecha_registro)
                VALUES (?, ?, 'Auditoria', 'Prueba', ?, DATE '1990-01-01', 'ACTIVO', 'BASICO',
                        'es', 'America/La_Paz', now())
                """,
                id,
                "AUD-" + id.toString().substring(0, 8),
                "+591" + SECUENCIA.incrementAndGet());
        return id;
    }

    private boolean existeUsuario(UUID id) {
        Number cuantos = (Number) dsl.fetchOne("SELECT count(*) FROM identidad.usuario WHERE id = ?", id)
                .get(0);
        return cuantos.intValue() > 0;
    }

    /** Una politica de retencion vigente, que es catalogo y no constante. */
    void politicaDeRetencion(String entidad, int mesesActiva, int mesesHistorica, String baseLegal) {
        dsl.insertInto(DSL.table(RETENCION))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("entidad", String.class),
                        DSL.field("meses_retencion_activa", Integer.class),
                        DSL.field("meses_retencion_historica", Integer.class),
                        DSL.field("accion_al_vencer", String.class),
                        DSL.field("base_legal", String.class),
                        DSL.field("vigente_desde", java.time.LocalDate.class))
                .values(
                        UUID.randomUUID(),
                        entidad,
                        mesesActiva,
                        mesesHistorica,
                        "ANONIMIZAR",
                        baseLegal,
                        java.time.LocalDate.now().minusYears(1))
                .execute();
    }

    String estadoDelProceso(UUID procesoId) {
        return dsl.select(DSL.field("estado", String.class))
                .from(DSL.table(ANONIMIZACION))
                .where(DSL.field("id").eq(procesoId))
                .fetchOne(0, String.class);
    }

    boolean hayEventoDeTipo(String tipo) {
        Number cuantos = (Number) dsl.selectCount()
                .from(DSL.table(EVENTOS))
                .where(DSL.field("tipo").eq(tipo))
                .fetchOne(0);
        return cuantos.intValue() > 0;
    }

    UUID definicion(String codigo, String familia, String sentido, int minimoCasos, String version) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DEFINICION))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("codigo", String.class),
                        DSL.field("version", String.class),
                        DSL.field("familia", String.class),
                        DSL.field("dueno_familia", String.class),
                        DSL.field("sentido_meta", String.class),
                        DSL.field("formula", String.class),
                        DSL.field("fuente", String.class),
                        DSL.field("minimo_casos", Integer.class),
                        DSL.field("vigente_desde", OffsetDateTime.class))
                .values(
                        id,
                        codigo,
                        version,
                        familia,
                        "Gerencia de " + familia.toLowerCase(java.util.Locale.ROOT),
                        sentido,
                        "definicion de prueba",
                        "fixtura",
                        minimoCasos,
                        OffsetDateTime.now().minusYears(1))
                .execute();
        return id;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    void indicador(
            UUID definicionId,
            String codigo,
            String periodo,
            String valor,
            String meta,
            Integer casos,
            boolean provisorio) {
        dsl.insertInto(DSL.table(KPI))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("definicion_indicador_id", UUID.class),
                        DSL.field("codigo", String.class),
                        DSL.field("nombre", String.class),
                        DSL.field("valor", BigDecimal.class),
                        DSL.field("unidad", String.class),
                        DSL.field("dimension", String.class),
                        DSL.field("periodo", String.class),
                        DSL.field("meta", BigDecimal.class),
                        DSL.field("provisorio", Boolean.class),
                        DSL.field("casos", Integer.class),
                        DSL.field("calculado_en", OffsetDateTime.class))
                .values(
                        UUID.randomUUID(),
                        definicionId,
                        codigo,
                        codigo.toLowerCase(java.util.Locale.ROOT).replace('_', ' '),
                        new BigDecimal(valor),
                        "PORCENTAJE",
                        "GLOBAL",
                        periodo,
                        meta == null ? null : new BigDecimal(meta),
                        provisorio,
                        casos,
                        OffsetDateTime.now())
                .execute();
    }
}
