package bo.aportaya.cumplimiento.infraestructura;

import bo.aportaya.cumplimiento.dominio.ClasificacionPep.BeneficiarioFinal;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** Escribe {@code declaracion_pep} y {@code beneficiario_final}. */
@Component
public class DeclaracionPepRepositorio {

    public UUID declarar(
            DSLContext dsl,
            UUID usuarioId,
            boolean esPep,
            Optional<String> tipoPep,
            Optional<String> cargo,
            Optional<String> institucion,
            OffsetDateTime momento) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("cumplimiento", "declaracion_pep")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("es_pep", Boolean.class), esPep)
                .set(DSL.field("tipo_pep", String.class), tipoPep.orElse(null))
                .set(DSL.field("cargo", String.class), cargo.orElse(null))
                .set(DSL.field("institucion", String.class), institucion.orElse(null))
                .set(DSL.field("declarada_en", OffsetDateTime.class), momento)
                .execute();
        return id;
    }

    /**
     * Los beneficiarios finales del titular.
     *
     * <p>Se borran y se reinsertan porque la estructura de control es una foto
     * completa, no una lista incremental: si alguien dejo de ser beneficiario, su
     * fila tiene que desaparecer, y un INSERT por encima la dejaria viva.
     */
    public void reemplazarBeneficiarios(DSLContext dsl, UUID usuarioId, List<BeneficiarioFinal> beneficiarios) {
        dsl.deleteFrom(DSL.table(DSL.name("cumplimiento", "beneficiario_final")))
                .where(DSL.field("usuario_id", UUID.class).eq(usuarioId))
                .execute();

        for (BeneficiarioFinal beneficiario : beneficiarios) {
            dsl.insertInto(DSL.table(DSL.name("cumplimiento", "beneficiario_final")))
                    .set(DSL.field("id", UUID.class), UUID.randomUUID())
                    .set(DSL.field("usuario_id", UUID.class), usuarioId)
                    .set(DSL.field("nombre", String.class), beneficiario.nombre())
                    .set(DSL.field("documento", String.class), beneficiario.documento())
                    // El porcentaje no se inventa: sin dato declarado va en cero, que
                    // es verificable, en vez de un numero verosimil que nadie dijo.
                    .set(DSL.field("porcentaje_participacion", BigDecimal.class), BigDecimal.ZERO)
                    .set(DSL.field("tipo_control", String.class), "PARTICIPACION_ACCIONARIA")
                    .execute();
        }
    }

    /** ¿Hay alguna declaracion PEP vigente para esa persona? Lo mismo que mira R-UIF-10. */
    public boolean esPepVigente(DSLContext dsl, UUID usuarioId) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("cumplimiento", "declaracion_pep")),
                        DSL.field("usuario_id", UUID.class).eq(usuarioId),
                        DSL.field("es_pep", Boolean.class).isTrue(),
                        DSL.field("hasta")
                                .isNull()
                                .or(DSL.field("hasta", java.time.LocalDate.class)
                                        .ge(java.time.LocalDate.now())))
                > 0;
    }
}
