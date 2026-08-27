package bo.aportaya.cumplimiento;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;

/**
 * Lo que las pruebas de incidentes de seguridad necesitan encontrar puesto.
 *
 * <p>Escribe con un {@code DSLContext} que <b>no</b> pasa por la transaccion del caso de
 * uso: si escribiera dentro de la misma, la prueba comprobaria que el caso de uso ve lo
 * que ella misma acaba de poner, y no lo que hay en la base.
 */
class FixturaDeIncidentes {

    private final DSLContext dsl;

    FixturaDeIncidentes(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * La designacion de responsable de seguridad de la informacion.
     *
     * <p>Sin esto CU-55 rechaza, y esta bien que rechace: es la precondicion 2 del caso
     * de uso. Que la fixtura tenga que ponerla explicitamente es la prueba de que el
     * caso de uso no la asume.
     */
    UUID designarResponsableDeSeguridad(UUID usuarioId) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.designacion_regulatoria
                    (id, usuario_id, cargo, tipo, fecha_designacion, activo)
                VALUES (?, ?, 'RESPONSABLE_SEGURIDAD_INFORMACION', 'TITULAR', ?, true)
                """,
                id,
                usuarioId,
                LocalDate.now().minusYears(1));
        return id;
    }

    /** Deja sin efecto toda designacion de seguridad, para probar que sin ella se rechaza. */
    void bajarResponsablesDeSeguridad() {
        dsl.execute(
                """
                UPDATE cumplimiento.designacion_regulatoria SET activo = false
                 WHERE cargo = 'RESPONSABLE_SEGURIDAD_INFORMACION'
                """);
    }

    /** Un contrato con un proveedor critico, para el criterio del tercero. */
    UUID contratoTercero(String razonSocial) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.contrato_tercero
                    (id, razon_social, servicio_contratado, es_critico, accede_a_datos_personales,
                     pais_procesamiento, nivel_riesgo, clausula_confidencialidad, clausula_auditoria,
                     clausula_continuidad, acuerdo_nivel_servicio, comunicado_al_organismo,
                     vigente_desde, estado)
                VALUES (?, ?, 'Procesamiento de pagos', true, true, 'BO', 'CRITICO',
                        true, true, true, '{}'::jsonb, true, ?, 'VIGENTE')
                """,
                id,
                razonSocial,
                LocalDate.now().minusYears(1));
        return id;
    }

    /**
     * Un activo del inventario. {@code contieneDatosPersonales} es lo que despues le
     * gana a lo que declare quien reporta el incidente.
     */
    UUID activo(String codigo, boolean contieneDatosPersonales, Optional<UUID> contratoTerceroId) {
        UUID id = UUID.randomUUID();
        dsl.execute(
                """
                INSERT INTO cumplimiento.activo_informacion
                    (id, contrato_tercero_id, codigo, nombre, tipo, clasificacion,
                     contiene_datos_personales, contiene_datos_sensibles, criticidad,
                     ubicacion, exige_cifrado, ultima_revision)
                VALUES (?, ?, ?, 'Activo de prueba', 'BASE_DATOS', 'CONFIDENCIAL',
                        ?, false, 'ALTA', 'nube', true, ?)
                """,
                id,
                contratoTerceroId.orElse(null),
                codigo,
                contieneDatosPersonales,
                LocalDate.now().minusMonths(1));
        return id;
    }

    OffsetDateTime plazoDeReporte(UUID incidenteId) {
        return dsl.fetchOne("SELECT plazo_reporte FROM cumplimiento.incidente_seguridad WHERE id = ?", incidenteId)
                .get(0, OffsetDateTime.class);
    }

    Optional<OffsetDateTime> reportadoEn(UUID incidenteId) {
        return Optional.ofNullable(dsl.fetchOne(
                        "SELECT reportado_al_organismo_en FROM cumplimiento.incidente_seguridad WHERE id = ?",
                        incidenteId)
                .get(0, OffsetDateTime.class));
    }

    Optional<OffsetDateTime> notificadoEn(UUID incidenteId) {
        return Optional.ofNullable(dsl.fetchOne(
                        "SELECT notificado_a_titulares_en FROM cumplimiento.incidente_seguridad WHERE id = ?",
                        incidenteId)
                .get(0, OffsetDateTime.class));
    }
}
