package bo.aportaya.cumplimiento.infraestructura;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** La base de perdidas y su remediacion. Sin logica: la decision es de los atomos. */
@Component
public class RiesgoOperativoRepositorio {

    private static final org.jooq.Name EVENTO = DSL.name("cumplimiento", "evento_riesgo_operativo");
    private static final org.jooq.Name PLAN = DSL.name("cumplimiento", "plan_accion_riesgo");
    private static final org.jooq.Name HALLAZGO = DSL.name("cumplimiento", "hallazgo_auditoria");

    /** Un plan que vencio sin cierre, con lo necesario para abrir el hallazgo. */
    public record PlanVencido(UUID id, UUID responsableId, String descripcion, LocalDate fechaCompromiso) {}

    /**
     * Registra el evento y devuelve la perdida neta <b>que calculo el motor</b>.
     *
     * <p>{@code perdida_neta} es una columna GENERATED: se lee de vuelta en vez de
     * confiar en la resta de la aplicacion. Si algun dia las dos dejaran de coincidir,
     * la que vale es la de la base — y esta forma de escribir hace imposible que la
     * respuesta diga otra cosa que la fila (`R-RIS-02`).
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public Registrado registrar(
            DSLContext dsl,
            String codigo,
            UUID registradoPor,
            String categoria,
            String factor,
            String lineaNegocio,
            String descripcion,
            OffsetDateTime ocurrencia,
            OffsetDateTime deteccion,
            BigDecimal perdidaBruta,
            BigDecimal recuperacion,
            String moneda) {
        UUID id = UUID.randomUUID();
        Record fila = dsl.insertInto(DSL.table(EVENTO))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("codigo", String.class),
                        DSL.field("registrado_por", UUID.class),
                        DSL.field("categoria_evento", String.class),
                        DSL.field("factor_riesgo", String.class),
                        DSL.field("linea_negocio", String.class),
                        DSL.field("descripcion", String.class),
                        DSL.field("fecha_ocurrencia", OffsetDateTime.class),
                        DSL.field("fecha_deteccion", OffsetDateTime.class),
                        DSL.field("perdida_bruta", BigDecimal.class),
                        DSL.field("recuperacion", BigDecimal.class),
                        DSL.field("moneda", String.class),
                        DSL.field("estado", String.class))
                .values(
                        id,
                        codigo,
                        registradoPor,
                        categoria,
                        factor,
                        lineaNegocio,
                        descripcion,
                        ocurrencia,
                        deteccion,
                        perdidaBruta,
                        recuperacion,
                        moneda,
                        "REGISTRADO")
                .returning(DSL.field("id", UUID.class), DSL.field("perdida_neta", BigDecimal.class))
                .fetchOne();
        return new Registrado(id, fila.get("perdida_neta", BigDecimal.class));
    }

    public record Registrado(UUID id, BigDecimal perdidaNeta) {}

    /**
     * El plan de accion que cierra el circulo.
     *
     * <p>Un evento sin responsable y sin fecha no es gestion de riesgo: es una lista de
     * cosas que salieron mal.
     */
    public UUID planificar(
            DSLContext dsl, UUID eventoId, UUID responsableId, String descripcion, LocalDate fechaCompromiso) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(PLAN))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("evento_riesgo_id", UUID.class),
                        DSL.field("responsable_id", UUID.class),
                        DSL.field("descripcion", String.class),
                        DSL.field("fecha_compromiso", LocalDate.class),
                        DSL.field("avance_porcentaje", BigDecimal.class),
                        DSL.field("estado", String.class))
                .values(id, eventoId, responsableId, descripcion, fechaCompromiso, BigDecimal.ZERO, "PENDIENTE")
                .execute();
        return id;
    }

    /**
     * Los planes cuyo compromiso ya paso y siguen abiertos.
     *
     * <p>Se toman <b>para actualizar y salteando los tomados</b>: el control corre en
     * varias replicas y sin esto dos abririan el mismo hallazgo. {@code skipLocked} en
     * vez de esperar, porque un control diario que se bloquea a si mismo no corre.
     */
    public List<PlanVencido> planesVencidos(DSLContext dsl, LocalDate hoy, int tope) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("responsable_id", UUID.class),
                        DSL.field("descripcion", String.class),
                        DSL.field("fecha_compromiso", LocalDate.class))
                .from(DSL.table(PLAN))
                .where(DSL.field("fecha_compromiso", LocalDate.class).lt(hoy))
                .and(DSL.field("estado", String.class).in("PENDIENTE", "EN_CURSO"))
                .orderBy(DSL.field("fecha_compromiso"))
                .limit(tope)
                .forUpdate()
                .skipLocked()
                .fetch(fila -> new PlanVencido(
                        fila.get("id", UUID.class),
                        fila.get("responsable_id", UUID.class),
                        fila.get("descripcion", String.class),
                        fila.get("fecha_compromiso", LocalDate.class)));
    }

    public void marcarVencido(DSLContext dsl, UUID planId) {
        dsl.update(DSL.table(PLAN))
                .set(DSL.field("estado", String.class), "VENCIDO")
                .where(DSL.field("id").eq(planId))
                .execute();
    }

    /**
     * Abre el hallazgo del plan vencido.
     *
     * <p>El plazo de regularizacion se persiste al crear, no se recalcula al consultar
     * (invariante 8): si se recalculara, bastaria declarar un feriado despues para que
     * el vencimiento se moviera solo.
     */
    public UUID abrirHallazgo(
            DSLContext dsl,
            String codigo,
            UUID responsableId,
            String descripcion,
            String severidad,
            LocalDate hoy,
            LocalDate plazo) {
        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(HALLAZGO))
                .columns(
                        DSL.field("id", UUID.class),
                        DSL.field("codigo", String.class),
                        DSL.field("responsable_id", UUID.class),
                        DSL.field("origen", String.class),
                        DSL.field("descripcion", String.class),
                        DSL.field("severidad", String.class),
                        DSL.field("proceso", String.class),
                        DSL.field("fecha_identificacion", LocalDate.class),
                        DSL.field("plazo_regularizacion", LocalDate.class),
                        DSL.field("estado", String.class))
                .values(
                        id,
                        codigo,
                        responsableId,
                        // No es una auditoria la que lo encontro: lo encontro el propio
                        // control. AUTOEVALUACION es lo que dice la verdad, y es de las
                        // cuatro que admite `ck_hallazgo_auditoria_origen`.
                        "AUTOEVALUACION",
                        descripcion,
                        severidad,
                        "Gestion de riesgo operativo",
                        hoy,
                        plazo,
                        "ABIERTO")
                .execute();
        return id;
    }

    /** Si ya hay un hallazgo con ese codigo, no se abre otro. */
    public Optional<UUID> hallazgoConCodigo(DSLContext dsl, String codigo) {
        return Optional.ofNullable(dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(HALLAZGO))
                .where(DSL.field("codigo", String.class).eq(codigo))
                .fetchOne(0, UUID.class));
    }

    /**
     * El codigo del hallazgo se DERIVA de lo que lo origino — un plan vencido (CU-54) o
     * un incidente sin reportar (CU-55).
     *
     * <p>Es la clave de idempotencia de los controles diarios: derivada del hecho y no
     * de la ejecucion, de modo que correr el control dos veces no abre dos hallazgos por
     * la misma causa. Un {@code UUID.randomUUID()} aca convertiria cada reintento en un
     * expediente nuevo, y el listado de hallazgos abiertos crecería solo.
     */
    public static String codigoDerivadoDe(UUID origenId) {
        return "AUTO-" + origenId.toString().substring(0, 13).toUpperCase(java.util.Locale.ROOT);
    }
}
