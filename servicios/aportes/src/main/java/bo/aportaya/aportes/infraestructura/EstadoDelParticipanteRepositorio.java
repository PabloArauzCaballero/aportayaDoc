package bo.aportaya.aportes.infraestructura;

import bo.aportaya.aportes.dominio.EstadoDePagos;
import java.math.BigDecimal;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * Lo que otros servicios necesitan saber de un participante y no pueden leer.
 *
 * <p>Grupos pregunta si esta al dia antes de permitir una permuta; nucleo-financiero
 * pregunta si tiene obligaciones abiertas antes de cerrarle la billetera. Ninguno de
 * los dos puede mirar {@code aportes.obligacion_aporte} (invariante 11), asi que este
 * repositorio arma la respuesta y el contrato la publica.
 *
 * <p>Los estados que cuentan como deuda salen del CHECK de la tabla, no de una lista
 * paralela: EN_MORA y VENCIDO son deuda; PENDIENTE y PROGRAMADO son futuro, no atraso.
 */
@Component
public class EstadoDelParticipanteRepositorio {

    private static final String CONSULTA =
            """
            SELECT
              COALESCE(SUM(o.monto_pagado), 0)                                        AS total_aportado,
              COALESCE(SUM(o.saldo_pendiente) FILTER (
                        WHERE o.estado IN ('EN_MORA', 'VENCIDO')), 0)                 AS deuda_vigente,
              COALESCE(SUM(o.saldo_pendiente) FILTER (
                        WHERE o.estado IN ('PENDIENTE', 'PROGRAMADO')), 0)            AS por_aportar,
              COUNT(*) FILTER (WHERE o.estado IN ('EN_MORA', 'VENCIDO'))::int         AS obligaciones_en_mora,
              COUNT(*) FILTER (WHERE o.estado IN ('PENDIENTE', 'PROGRAMADO',
                                                  'EN_MORA', 'VENCIDO',
                                                  'PAGADO_PARCIAL'))::int             AS obligaciones_abiertas,
              MAX(o.moneda)                                                           AS moneda
              FROM aportes.obligacion_aporte o
             WHERE o.participante_id = ?
            """;

    public EstadoDePagos de(DSLContext dsl, UUID participanteId) {
        var fila = dsl.fetchOne(CONSULTA, participanteId);
        if (fila == null) {
            return EstadoDePagos.sinObligaciones();
        }
        String moneda = fila.get("moneda", String.class);
        return new EstadoDePagos(
                fila.get("obligaciones_en_mora", Integer.class) == 0,
                fila.get("total_aportado", BigDecimal.class),
                fila.get("deuda_vigente", BigDecimal.class),
                fila.get("por_aportar", BigDecimal.class),
                fila.get("obligaciones_abiertas", Integer.class),
                moneda == null ? "BOB" : moneda);
    }

    /** Cuantos participantes de un grupo estan en mora. Lo pregunta CU-68 al postular. */
    public int morososDelGrupo(DSLContext dsl, UUID grupoId) {
        var fila = dsl.fetchOne(
                """
                SELECT COUNT(DISTINCT o.participante_id)::int AS morosos
                  FROM aportes.obligacion_aporte o
                 WHERE o.grupo_id = ? AND o.estado IN ('EN_MORA', 'VENCIDO')
                """,
                grupoId);
        return fila == null ? 0 : fila.get("morosos", Integer.class);
    }
}
