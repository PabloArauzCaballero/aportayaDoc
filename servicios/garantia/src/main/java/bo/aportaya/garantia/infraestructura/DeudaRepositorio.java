package bo.aportaya.garantia.infraestructura;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code deuda_participante}: lo que queda debiendo quien hizo que el fondo pagara.
 *
 * <p>Vive aparte de {@link FondoRepositorio} porque es otra cosa. El fondo es un saldo
 * colectivo que sube con aportes y baja con coberturas; la deuda es una obligacion
 * personal, con acreedor, exigibilidad y prescripcion. Mezclarlas en un repositorio
 * hacia que cada cambio de una hiciera releer la otra.
 *
 * <p>Los plazos —exigibilidad y prescripcion— se guardan al crear la deuda y no se
 * recalculan al consultarla (invariante 8): una deuda que prescribe segun cuando se la
 * mire no prescribe nunca.
 */
@Component
public class DeudaRepositorio {

    /** La deuda que la cobertura deja: el fondo pago, y alguien la debe. */
    public UUID registrarDeuda(
            DSLContext dsl,
            UUID usuarioId,
            UUID participanteId,
            UUID grupoId,
            UUID registroId,
            UUID coberturaId,
            String acreedor,
            Dinero capital,
            LocalDate exigibilidad,
            LocalDate prescripcion) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "deuda_participante")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("usuario_id", UUID.class), usuarioId)
                .set(DSL.field("participante_id", UUID.class), participanteId)
                .set(DSL.field("grupo_id", UUID.class), grupoId)
                .set(DSL.field("registro_id", UUID.class), registroId)
                .set(DSL.field("cobertura_id", UUID.class), coberturaId)
                .set(DSL.field("acreedor", String.class), acreedor)
                .set(DSL.field("capital_original", BigDecimal.class), capital.monto())
                .set(DSL.field("recargos_acumulados", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("total_abonado", BigDecimal.class), BigDecimal.ZERO)
                .set(DSL.field("saldo_actual", BigDecimal.class), capital.monto())
                .set(DSL.field("moneda", String.class), capital.moneda().name())
                .set(DSL.field("estado", String.class), "VIGENTE")
                .set(DSL.field("es_subrogada", Boolean.class), false)
                .set(DSL.field("fecha_exigibilidad", LocalDate.class), exigibilidad)
                .set(DSL.field("fecha_prescripcion", LocalDate.class), prescripcion)
                .set(DSL.field("dias_vencida", Short.class), (short) 0)
                .set(DSL.field("version", Integer.class), 0)
                .execute();
        return id;
    }

    public Optional<Deuda> deudaDe(DSLContext dsl, UUID registroId) {
        return dsl.select(
                        DSL.field("id", UUID.class),
                        DSL.field("usuario_id", UUID.class),
                        DSL.field("saldo_actual", BigDecimal.class),
                        DSL.field("moneda", String.class),
                        DSL.field("estado", String.class),
                        DSL.field("es_subrogada", Boolean.class),
                        DSL.field("version", Integer.class))
                .from(DSL.table(DSL.name("garantia", "deuda_participante")))
                .where(DSL.field("registro_id", UUID.class).eq(registroId))
                .fetchOptional(f -> new Deuda(
                        f.get("id", UUID.class),
                        f.get("usuario_id", UUID.class),
                        Dinero.de(
                                f.get("saldo_actual", BigDecimal.class), Moneda.valueOf(f.get("moneda", String.class))),
                        f.get("estado", String.class),
                        f.get("es_subrogada", Boolean.class),
                        f.get("version", Integer.class)));
    }

    public record Deuda(UUID id, UUID usuarioId, Dinero saldoActual, String estado, boolean esSubrogada, int version) {}
}
