package bo.aportaya.nucleofinanciero.infraestructura;

import static bo.aportaya.nucleofinanciero.generado.Tables.ASIENTO_CONTABLE;
import static bo.aportaya.nucleofinanciero.generado.Tables.MOVIMIENTO_CONTABLE;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * Alta del asiento y sus movimientos. Append-only: no hay, ni habrá, un método que
 * actualice una fila de estas dos tablas — la base lo rechazaría (R-AUD-01).
 */
@Component
public class AsientoRepositorio {

    public record AsientoCreado(UUID id, long numero) {}

    public record AsientoExistente(UUID id, String origenTipo, UUID origenId, UUID grupoId, String estado) {}

    public record MovimientoExistente(UUID cuentaId, BigDecimal debe, BigDecimal haber) {}

    /**
     * {@code periodo_contable_id} queda NULL: {@code periodo_contable} es del módulo 13
     * (ERP), que todavía no existe, y {@code tg_asiento_periodo_abierto} acepta
     * explícitamente un asiento sin período ("anteriores a M13"). Cuando el ERP exista,
     * el período entra por acá y el trigger pasa a exigirlo abierto (R-CTB-01).
     */
    public AsientoCreado crear(
            DSLContext dsl,
            OffsetDateTime fecha,
            String glosa,
            String origenTipo,
            UUID origenId,
            Optional<UUID> grupoId,
            Optional<UUID> registradoPor,
            Optional<UUID> asientoReversaId) {
        // R-AUD-11: el estado y el enlace de reversa son la misma decisión, y la base
        // rechaza que se contradigan. Se derivan de un solo dato para que no puedan.
        String estado = asientoReversaId.isPresent() ? "REVERSADO" : "CONFIRMADO";
        return dsl.insertInto(ASIENTO_CONTABLE)
                .set(ASIENTO_CONTABLE.FECHA, fecha)
                .set(ASIENTO_CONTABLE.GLOSA, glosa)
                .set(ASIENTO_CONTABLE.ORIGEN_TIPO, origenTipo)
                .set(ASIENTO_CONTABLE.ORIGEN_ID, origenId)
                .set(ASIENTO_CONTABLE.GRUPO_ID, grupoId.orElse(null))
                .set(ASIENTO_CONTABLE.ESTADO, estado)
                .set(ASIENTO_CONTABLE.REGISTRADO_POR, registradoPor.orElse(null))
                .set(ASIENTO_CONTABLE.ASIENTO_REVERSA_ID, asientoReversaId.orElse(null))
                .returning(ASIENTO_CONTABLE.ID, ASIENTO_CONTABLE.NUMERO)
                .fetchOne(r -> new AsientoCreado(r.getId(), r.getNumero()));
    }

    public void agregarMovimiento(
            DSLContext dsl, UUID asientoId, UUID cuentaId, BigDecimal debe, BigDecimal haber, String descripcion) {
        dsl.insertInto(MOVIMIENTO_CONTABLE)
                .set(MOVIMIENTO_CONTABLE.ASIENTO_ID, asientoId)
                .set(MOVIMIENTO_CONTABLE.CUENTA_ID, cuentaId)
                .set(MOVIMIENTO_CONTABLE.DEBE, debe)
                .set(MOVIMIENTO_CONTABLE.HABER, haber)
                .set(MOVIMIENTO_CONTABLE.DESCRIPCION, descripcion)
                .execute();
    }

    public Optional<AsientoExistente> porId(DSLContext dsl, UUID id) {
        return dsl.select(
                        ASIENTO_CONTABLE.ID,
                        ASIENTO_CONTABLE.ORIGEN_TIPO,
                        ASIENTO_CONTABLE.ORIGEN_ID,
                        ASIENTO_CONTABLE.GRUPO_ID,
                        ASIENTO_CONTABLE.ESTADO)
                .from(ASIENTO_CONTABLE)
                .where(ASIENTO_CONTABLE.ID.eq(id))
                .fetchOptional(r -> new AsientoExistente(r.value1(), r.value2(), r.value3(), r.value4(), r.value5()));
    }

    public List<MovimientoExistente> movimientosDe(DSLContext dsl, UUID asientoId) {
        return dsl.select(MOVIMIENTO_CONTABLE.CUENTA_ID, MOVIMIENTO_CONTABLE.DEBE, MOVIMIENTO_CONTABLE.HABER)
                .from(MOVIMIENTO_CONTABLE)
                .where(MOVIMIENTO_CONTABLE.ASIENTO_ID.eq(asientoId))
                .fetch(r -> new MovimientoExistente(r.value1(), r.value2(), r.value3()));
    }
}
