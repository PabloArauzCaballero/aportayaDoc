package bo.aportaya.garantia.infraestructura;

import bo.aportaya.garantia.dominio.CoberturaAplicable;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@code fondo_garantia}, sus movimientos, las coberturas y las deudas que dejan.
 *
 * <p>{@code movimiento_fondo} es **append-only**: el saldo del fondo se deriva de sus
 * movimientos, y cada uno lleva el saldo resultante. Corregir un movimiento en el lugar
 * borraria la historia de por que el fondo tiene lo que tiene.
 *
 * <p>Una cobertura por expediente y una por obligacion: cubrir dos veces el mismo
 * incumplimiento vacia el fondo por un solo caso.
 */
@Component
public class FondoRepositorio {

    public Optional<Fondo> delGrupo(DSLContext dsl, UUID grupoId) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("garantia", "fondo_garantia")))
                .where(DSL.field("grupo_id", UUID.class).eq(grupoId))
                .fetchOptional(this::aFondo);
    }

    /** Con candado: dos coberturas simultaneas no pueden gastar el mismo saldo. */
    public Optional<Fondo> bloquear(DSLContext dsl, UUID id) {
        return dsl.select(campos())
                .from(DSL.table(DSL.name("garantia", "fondo_garantia")))
                .where(DSL.field("id", UUID.class).eq(id))
                .forUpdate()
                .fetchOptional(this::aFondo);
    }

    /** La politica del fondo. Es catalogo (invariante 10), no constantes del codigo. */
    public Optional<CoberturaAplicable.Politica> politica(DSLContext dsl, UUID politicaId, Moneda moneda) {
        return dsl.select(
                        DSL.field("porcentaje_maximo_cobertura_por_aporte", BigDecimal.class),
                        DSL.field("tope_cobertura_por_participante", BigDecimal.class),
                        DSL.field("tope_cobertura_por_periodo", BigDecimal.class),
                        DSL.field("max_coberturas_por_participante", Short.class),
                        DSL.field("requiere_aprobacion_manual_desde", BigDecimal.class),
                        DSL.field("dias_mora_para_activar", Short.class))
                .from(DSL.table(DSL.name("garantia", "politica_cobertura")))
                .where(DSL.field("id", UUID.class).eq(politicaId))
                .fetchOptional(f -> new CoberturaAplicable.Politica(
                        f.get("porcentaje_maximo_cobertura_por_aporte", BigDecimal.class),
                        Dinero.de(f.get("tope_cobertura_por_participante", BigDecimal.class), moneda),
                        Dinero.de(f.get("tope_cobertura_por_periodo", BigDecimal.class), moneda),
                        f.get("max_coberturas_por_participante", Short.class),
                        Dinero.de(f.get("requiere_aprobacion_manual_desde", BigDecimal.class), moneda),
                        f.get("dias_mora_para_activar", Short.class)));
    }

    /** Lo que este participante ya consumio del fondo, y cuantas veces. */
    public CoberturaAplicable.Consumido consumido(
            DSLContext dsl, UUID fondoId, UUID participanteId, UUID periodoId, Moneda moneda) {
        var fila = dsl.fetchOne(
                """
                SELECT
                  COALESCE(SUM(c.monto_cubierto) FILTER (WHERE r.participante_id = ?), 0) AS por_participante,
                  COALESCE(SUM(c.monto_cubierto) FILTER (WHERE c.periodo_id = ?), 0)      AS por_periodo,
                  count(*) FILTER (WHERE r.participante_id = ?)::int                       AS previas
                  FROM garantia.cobertura_incumplimiento c
                  JOIN garantia.registro_incumplimiento r ON r.id = c.registro_id
                 WHERE c.fondo_id = ? AND c.estado <> 'RECHAZADA'
                """,
                participanteId,
                periodoId,
                participanteId,
                fondoId);
        return new CoberturaAplicable.Consumido(
                Dinero.de(fila.get("por_participante", BigDecimal.class), moneda),
                Dinero.de(fila.get("por_periodo", BigDecimal.class), moneda),
                fila.get("previas", Integer.class));
    }

    public Optional<UUID> coberturaDe(DSLContext dsl, UUID registroId) {
        return dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("garantia", "cobertura_incumplimiento")))
                .where(DSL.field("registro_id", UUID.class).eq(registroId))
                .fetchOptional(f -> f.get("id", UUID.class));
    }

    public UUID registrarCobertura(
            DSLContext dsl,
            UUID fondoId,
            UUID registroId,
            UUID obligacionId,
            UUID periodoId,
            CoberturaAplicable.Resultado calculo,
            String estado,
            UUID aprobadaPor,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "cobertura_incumplimiento")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("fondo_id", UUID.class), fondoId)
                .set(DSL.field("registro_id", UUID.class), registroId)
                .set(DSL.field("obligacion_id", UUID.class), obligacionId)
                .set(DSL.field("periodo_id", UUID.class), periodoId)
                .set(DSL.field("aprobada_por", UUID.class), aprobadaPor)
                .set(
                        DSL.field("monto_solicitado", BigDecimal.class),
                        calculo.montoSolicitado().monto())
                .set(
                        DSL.field("monto_cubierto", BigDecimal.class),
                        calculo.montoCubierto().monto())
                .set(DSL.field("porcentaje_cobertura", BigDecimal.class), calculo.porcentajeCobertura())
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("requirio_aprobacion_manual", Boolean.class), calculo.exigeAprobacionManual())
                .set(DSL.field("solicitada_en", OffsetDateTime.class), ahora)
                .set(DSL.field("aplicada_en", OffsetDateTime.class), "APLICADA".equals(estado) ? ahora : null)
                .execute();
        return id;
    }

    /**
     * Un movimiento del fondo, con el saldo que deja.
     *
     * <p>Es append-only: el saldo se DERIVA de la cadena de movimientos. Guardar el
     * resultante en cada uno es lo que permite reconstruir el fondo en cualquier fecha
     * pasada sin recalcular todo desde el principio.
     */
    public UUID registrarMovimiento(
            DSLContext dsl,
            UUID fondoId,
            String tipo,
            Dinero monto,
            Dinero saldoResultante,
            String referenciaTipo,
            UUID referenciaId,
            String descripcion,
            UUID registradoPor,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "movimiento_fondo")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("fondo_id", UUID.class), fondoId)
                .set(DSL.field("tipo", String.class), tipo)
                .set(DSL.field("monto", BigDecimal.class), monto.monto())
                .set(DSL.field("saldo_resultante", BigDecimal.class), saldoResultante.monto())
                .set(DSL.field("referencia_tipo", String.class), referenciaTipo)
                .set(DSL.field("referencia_id", UUID.class), referenciaId)
                .set(DSL.field("descripcion", String.class), descripcion)
                .set(DSL.field("fecha", OffsetDateTime.class), ahora)
                .set(DSL.field("registrado_por", UUID.class), registradoPor)
                .execute();
        return id;
    }

    /** Mueve el saldo del fondo. La cabecera es cache; la verdad son los movimientos. */
    public boolean moverSaldo(DSLContext dsl, UUID fondoId, Dinero delta, Dinero cubiertoDelta, int versionLeida) {
        return dsl.update(DSL.table(DSL.name("garantia", "fondo_garantia")))
                        .set(
                                DSL.field("saldo_disponible", BigDecimal.class),
                                DSL.field("saldo_disponible", BigDecimal.class).plus(delta.monto()))
                        .set(
                                DSL.field("total_cubierto", BigDecimal.class),
                                DSL.field("total_cubierto", BigDecimal.class).plus(cubiertoDelta.monto()))
                        .set(DSL.field("version", Integer.class), versionLeida + 1)
                        .where(DSL.field("id", UUID.class)
                                .eq(fondoId)
                                .and(DSL.field("version", Integer.class).eq(versionLeida)))
                        .execute()
                == 1;
    }

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

    /** Lo que cada participante aporto al fondo: la base del reparto al cerrarlo. */
    public List<Aportante> aportantes(DSLContext dsl, UUID fondoId, Moneda moneda) {
        return dsl.fetch(
                        """
                        SELECT m.referencia_id AS participante_id, SUM(m.monto) AS aportado
                          FROM garantia.movimiento_fondo m
                         WHERE m.fondo_id = ? AND m.tipo IN ('APORTE_PERIODICO', 'CONSTITUCION')
                         GROUP BY m.referencia_id
                         ORDER BY m.referencia_id
                        """,
                        fondoId)
                .map(f -> new Aportante(
                        f.get("participante_id", UUID.class), Dinero.de(f.get("aportado", BigDecimal.class), moneda)));
    }

    public UUID registrarDevolucion(
            DSLContext dsl,
            UUID fondoId,
            UUID participanteId,
            Dinero aportado,
            Dinero consumido,
            Dinero aDevolver,
            String estado,
            OffsetDateTime ahora) {

        UUID id = UUID.randomUUID();
        dsl.insertInto(DSL.table(DSL.name("garantia", "devolucion_fondo")))
                .set(DSL.field("id", UUID.class), id)
                .set(DSL.field("fondo_id", UUID.class), fondoId)
                .set(DSL.field("participante_id", UUID.class), participanteId)
                .set(DSL.field("monto_aportado", BigDecimal.class), aportado.monto())
                .set(DSL.field("monto_consumido", BigDecimal.class), consumido.monto())
                .set(DSL.field("monto_a_devolver", BigDecimal.class), aDevolver.monto())
                .set(DSL.field("estado", String.class), estado)
                .set(DSL.field("fecha", OffsetDateTime.class), ahora)
                .execute();
        return id;
    }

    private List<org.jooq.Field<?>> campos() {
        return List.of(
                DSL.field("id", UUID.class),
                DSL.field("grupo_id", UUID.class),
                DSL.field("politica_cobertura_id", UUID.class),
                DSL.field("moneda", String.class),
                DSL.field("saldo_disponible", BigDecimal.class),
                DSL.field("total_aportado", BigDecimal.class),
                DSL.field("total_cubierto", BigDecimal.class),
                DSL.field("estado", String.class),
                DSL.field("version", Integer.class));
    }

    private Fondo aFondo(org.jooq.Record f) {
        Moneda moneda = Moneda.valueOf(f.get("moneda", String.class));
        return new Fondo(
                f.get("id", UUID.class),
                f.get("grupo_id", UUID.class),
                f.get("politica_cobertura_id", UUID.class),
                moneda,
                Dinero.de(f.get("saldo_disponible", BigDecimal.class), moneda),
                Dinero.de(f.get("total_aportado", BigDecimal.class), moneda),
                Dinero.de(f.get("total_cubierto", BigDecimal.class), moneda),
                f.get("estado", String.class),
                f.get("version", Integer.class));
    }

    public record Fondo(
            UUID id,
            UUID grupoId,
            UUID politicaId,
            Moneda moneda,
            Dinero saldoDisponible,
            Dinero totalAportado,
            Dinero totalCubierto,
            String estado,
            int version) {}

    public record Deuda(UUID id, UUID usuarioId, Dinero saldoActual, String estado, boolean esSubrogada, int version) {}

    public record Aportante(UUID participanteId, Dinero aportado) {}
}
