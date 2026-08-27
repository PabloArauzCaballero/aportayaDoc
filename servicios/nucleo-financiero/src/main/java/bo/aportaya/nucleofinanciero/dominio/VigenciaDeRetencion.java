package bo.aportaya.nucleofinanciero.dominio;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * CU-13 · Cuando vence una retencion. Puro.
 *
 * <p>**Toda retencion expira, salvo la orden de autoridad** (R-BIL-08). No es un
 * detalle: una retencion sin vencimiento es plata de alguien inmovilizada para
 * siempre porque un proceso se olvido de liberarla. El unico caso en que eso es
 * legitimo es cuando un juez lo ordeno, y ahi el que decide cuando termina es el juez.
 */
public final class VigenciaDeRetencion {

    private VigenciaDeRetencion() {}

    public enum Motivo {
        APORTE_PROGRAMADO,
        ENTREGA_EN_CURSO,
        DISPUTA,
        ORDEN_AUTORIDAD,
        ANTIFRAUDE,
        COMISION_PENDIENTE;

        /** La unica que puede vivir sin fecha de fin. */
        public boolean puedeSerIndefinida() {
            return this == ORDEN_AUTORIDAD;
        }
    }

    /**
     * @param diasPorPolitica los dias de vigencia que fija {@code politica_billetera}.
     *     Es politica de producto, no una constante del programa.
     * @param pedido el vencimiento que pidio quien retiene, si lo trajo
     */
    public static Optional<OffsetDateTime> resolver(
            Motivo motivo, Optional<OffsetDateTime> pedido, int diasPorPolitica, OffsetDateTime ahora) {

        if (pedido.isPresent()) {
            if (!pedido.get().isAfter(ahora)) {
                throw new IllegalArgumentException("Una retencion no puede vencer antes de empezar");
            }
            return pedido;
        }
        if (motivo.puedeSerIndefinida()) {
            // Sin fecha, y a proposito: la levanta la misma autoridad que la puso.
            return Optional.empty();
        }
        return Optional.of(ahora.plusDays(diasPorPolitica));
    }

    /** Una retencion cerrada no admite mas operaciones: ni liberar ni ejecutar dos veces. */
    public static boolean estaAbierta(String estado) {
        return "VIGENTE".equals(estado);
    }
}
