package bo.aportaya.organizador.infraestructura;

import bo.aportaya.organizador.dominio.RequisitosDeHabilitacion;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code solicitud_organizador}, {@code organizador} y {@code requisito_habilitacion}. */
@Component
public class OrganizadorRepositorio {

    /** La postulacion pendiente de un usuario, si tiene una (R-ORG-01). */
    public Optional<Solicitud> solicitudPendiente(DSLContext dsl, UUID usuarioId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("estado", String.class),
                        DSL.field("puntaje_reputacion_al_solicitar", BigDecimal.class))
                .from(DSL.table(DSL.name("organizador", "solicitud_organizador")))
                .where(DSL.field("usuario_id", UUID.class)
                        .eq(usuarioId)
                        .and(DSL.field("estado", String.class).in("PENDIENTE", "EN_REVISION")))
                .fetchOptional(this::aSolicitud);
    }

    public Optional<Solicitud> verSolicitud(DSLContext dsl, UUID id) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("estado", String.class),
                        DSL.field("puntaje_reputacion_al_solicitar", BigDecimal.class))
                .from(DSL.table(DSL.name("organizador", "solicitud_organizador")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(this::aSolicitud);
    }

    public UUID postular(
            DSLContext dsl,
            UUID usuarioId,
            String motivacion,
            String experiencia,
            UUID kycReforzadoId,
            BigDecimal reputacion,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("organizador", "solicitud_organizador")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("motivacion", String.class), motivacion)
                .set(DSL.field("experiencia_declarada", String.class), experiencia)
                .set(DSL.field("kyc_reforzado_id", UUID.class), kycReforzadoId)
                .set(DSL.field("puntaje_reputacion_al_solicitar", BigDecimal.class), reputacion)
                .set(DSL.field("estado", String.class), "PENDIENTE")
                .set(DSL.field("fecha_solicitud", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    /**
     * Resuelve la solicitud, solo desde el estado que se espera.
     *
     * <p>El {@code WHERE estado IN (…)} es la barrera: dos revisores que abrieron la
     * misma solicitud no pueden resolverla los dos, y el segundo se entera.
     */
    public boolean resolver(
            DSLContext dsl, UUID id, String estadoNuevo, UUID revisadaPor, String motivoRechazo, OffsetDateTime ahora) {
        return dsl.update(DSL.table(DSL.name("organizador", "solicitud_organizador")))
                        .set(DSL.field("estado", String.class), estadoNuevo)
                        .set(DSL.field("revisada_por", UUID.class), revisadaPor)
                        .set(DSL.field("motivo_rechazo", String.class), motivoRechazo)
                        .set(DSL.field("fecha_resolucion", OffsetDateTime.class), ahora)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("estado", String.class).in("PENDIENTE", "EN_REVISION")))
                        .execute()
                == 1;
    }

    /** Los requisitos vigentes para un nivel. Son catalogo, no constantes. */
    public List<RequisitosDeHabilitacion.Requisito> requisitosDe(DSLContext dsl, String nivel) {
        return dsl.select(
                        DSL.field("codigo", String.class),
                        DSL.field("tipo", String.class),
                        DSL.field("valor_minimo", BigDecimal.class),
                        DSL.field("es_obligatorio", Boolean.class))
                .from(DSL.table(DSL.name("organizador", "requisito_habilitacion")))
                .where(DSL.field("nivel_requerido", String.class)
                        .eq(nivel)
                        .and(DSL.field("activo", Boolean.class).isTrue()))
                .fetch(f -> new RequisitosDeHabilitacion.Requisito(
                        f.get("codigo", String.class),
                        f.get("tipo", String.class),
                        f.get("valor_minimo", BigDecimal.class),
                        f.get("es_obligatorio", Boolean.class)));
    }

    public Optional<Organizador> porUsuario(DSLContext dsl, UUID usuarioId) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("organizador", "organizador")))
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .fetchOptional(this::aOrganizador);
    }

    public Optional<Organizador> ver(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("organizador", "organizador")))
                .where(DSL.field("id", UUID.class).eq(id))
                .fetchOptional(this::aOrganizador);
    }

    /** Con candado: lo que impide que dos evaluaciones muevan el nivel a la vez. */
    public Optional<Organizador> bloquear(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("organizador", "organizador")))
                .where(DSL.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOptional(this::aOrganizador);
    }

    public UUID crear(
            DSLContext dsl,
            UUID usuarioId,
            String estado,
            String nivel,
            int limiteGrupos,
            BigDecimal limiteMonto,
            OffsetDateTime postulacion) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("organizador", "organizador")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("nivel", String.class), nivel)
                .set(DSL.field("limite_grupos_simultaneos", Short.class), (short) limiteGrupos)
                .set(DSL.field("limite_monto_administrado", BigDecimal.class), limiteMonto)
                .set(DSL.field("grupos_activos", Short.class), (short) 0)
                .set(DSL.field("grupos_historicos", Short.class), (short) 0)
                .set(DSL.field("monto_administrado_actual", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("calificacion_promedio", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("indice_morosidad_cartera", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("fecha_postulacion", OffsetDateTime.class), postulacion)
                .execute();
        return id;
    }

    /** Version optimista: dos cambios que leyeron la misma version no escriben los dos. */
    public boolean cambiarEstado(DSLContext dsl, UUID id, String estadoNuevo, int versionLeida, OffsetDateTime cuando) {
        var actualizacion = dsl.update(DSL.table(DSL.name("organizador", "organizador")))
                .set(DSL.field("estado", String.class), estadoNuevo)
                .set(DSL.field("version", Integer.class), versionLeida + 1);
        if ("HABILITADO".equals(estadoNuevo)) {
            actualizacion = actualizacion.set(DSL.field("fecha_habilitacion", OffsetDateTime.class), cuando);
        }
        if ("SUSPENDIDO".equals(estadoNuevo) || "DESHABILITADO".equals(estadoNuevo)) {
            actualizacion = actualizacion.set(DSL.field("fecha_suspension", OffsetDateTime.class), cuando);
        }
        return actualizacion
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("version", Integer.class).eq(versionLeida)))
                        .execute()
                == 1;
    }

    public boolean cambiarNivel(
            DSLContext dsl, UUID id, String nivelNuevo, int limiteGrupos, BigDecimal limiteMonto, int versionLeida) {
        return dsl.update(DSL.table(DSL.name("organizador", "organizador")))
                        .set(DSL.field("nivel", String.class), nivelNuevo)
                        .set(DSL.field("limite_grupos_simultaneos", Short.class), (short) limiteGrupos)
                        .set(DSL.field("limite_monto_administrado", BigDecimal.class), limiteMonto)
                        .set(DSL.field("version", Integer.class), versionLeida + 1)
                        .where(DSL.field("id", UUID.class)
                                .eq(id)
                                .and(DSL.field("version", Integer.class).eq(versionLeida)))
                        .execute()
                == 1;
    }

    /** Los modulos de capacitacion aprobados y vigentes. */
    public int capacitacionesAprobadas(DSLContext dsl, UUID organizadorId, java.time.LocalDate hoy) {
        return dsl.fetchCount(
                DSL.table(DSL.name("organizador", "capacitacion_organizador")),
                DSL.field("organizador_id", UUID.class)
                        .eq(organizadorId)
                        .and(DSL.field("aprobada", Boolean.class).isTrue())
                        .and(DSL.field("vigente_hasta", java.time.LocalDate.class)
                                .isNull()
                                .or(DSL.field("vigente_hasta", java.time.LocalDate.class)
                                        .ge(hoy))));
    }

    private List<org.jooq.Field<?>> campos() {
        return List.of(
                DSL.field("id", UUID.class),
                DSL.field("usuario_id", UUID.class),
                DSL.field("estado", String.class),
                DSL.field("nivel", String.class),
                DSL.field("limite_grupos_simultaneos", Short.class),
                DSL.field("limite_monto_administrado", BigDecimal.class),
                DSL.field("grupos_activos", Short.class),
                DSL.field("monto_administrado_actual", BigDecimal.class),
                DSL.field("version", Integer.class));
    }

    private Solicitud aSolicitud(Record f) {
        return new Solicitud(
                f.get("id", UUID.class),
                f.get("usuario_id", UUID.class),
                f.get("estado", String.class),
                f.get("puntaje_reputacion_al_solicitar", BigDecimal.class));
    }

    private Organizador aOrganizador(Record f) {
        return new Organizador(
                f.get("id", UUID.class),
                f.get("usuario_id", UUID.class),
                f.get("estado", String.class),
                f.get("nivel", String.class),
                f.get("limite_grupos_simultaneos", Short.class),
                f.get("limite_monto_administrado", BigDecimal.class),
                f.get("grupos_activos", Short.class),
                f.get("monto_administrado_actual", BigDecimal.class),
                f.get("version", Integer.class));
    }

    public record Solicitud(UUID id, UUID usuarioId, String estado, BigDecimal reputacionAlSolicitar) {}

    public record Organizador(
            UUID id,
            UUID usuarioId,
            String estado,
            String nivel,
            int limiteGrupos,
            BigDecimal limiteMonto,
            int gruposActivos,
            BigDecimal montoAdministrado,
            int version) {}
}
