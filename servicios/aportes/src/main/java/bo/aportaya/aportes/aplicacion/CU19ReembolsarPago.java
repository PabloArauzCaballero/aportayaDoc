package bo.aportaya.aportes.aplicacion;

import bo.aportaya.aportes.dominio.MotivoDeReembolso;
import bo.aportaya.aportes.dominio.SaldoDeLaObligacion;
import bo.aportaya.aportes.dominio.TipoDeDisputa;
import bo.aportaya.aportes.infraestructura.ObligacionRepositorio;
import bo.aportaya.aportes.infraestructura.PagoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-19 · Reembolsar un pago y atender una disputa.
 *
 * <p>Devolver plata exige **cuatro ojos**: quien aprueba no puede ser quien solicita.
 * Un reembolso es la via mas corta para sacar dinero del sistema con una excusa
 * verosimil, y por eso la separacion de funciones no es formalismo.
 *
 * <p>Al reembolsar, la obligacion **vuelve a estar pendiente por ese importe**. Es lo
 * que hace que el grupo no pierda: si la cuota queda dada por pagada, el faltante lo
 * absorben los demas participantes sin enterarse.
 */
@Service
public class CU19ReembolsarPago {

    private final Datos datos;
    private final PagoRepositorio pagos;
    private final ObligacionRepositorio obligaciones;
    private final Consumidos consumidos;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Duration plazoDeDisputa;

    public CU19ReembolsarPago(
            Datos datos,
            PagoRepositorio pagos,
            ObligacionRepositorio obligaciones,
            Consumidos consumidos,
            Outbox outbox,
            Reloj reloj,
            Duration plazoDeDisputa) {
        this.datos = datos;
        this.pagos = pagos;
        this.obligaciones = obligaciones;
        this.consumidos = consumidos;
        this.outbox = outbox;
        this.reloj = reloj;
        this.plazoDeDisputa = plazoDeDisputa;
    }

    @Transactional
    public SalidaSolicitud solicitar(EntradaReembolso entrada, ContextoSesion ctx) {
        // El motivo se valida antes de abrir nada: la base solo admite cuatro, y
        // enterarse en el INSERT convierte una regla de negocio en un error 500.
        String motivo = MotivoDeReembolso.exigir(entrada.motivo());
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var pago = pagos.ver(dsl, entrada.pagoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(19, 1), "Ese pago no existe."));

            // AP-CU19-01: no se reembolsa lo que todavia no entro. Devolver contra un
            // pago sin conciliar es regalar plata que quiza nunca llegue.
            if (!"ACREDITADO".equals(pago.estado()) && !"CONCILIADO".equals(pago.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(19, 1), "Ese pago esta " + pago.estado() + ": todavia no se puede reembolsar.");
            }

            // AP-CU19-02: contra lo YA reembolsado, no contra el monto original. Dos
            // reembolsos parciales que juntos superan el pago devuelven de mas.
            Dinero yaDevuelto = pagos.reembolsadoDe(dsl, pago.id(), pago.monto().moneda());
            Dinero disponible = pago.monto().menos(yaDevuelto);
            if (entrada.monto().esMayorQue(disponible)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(19, 2),
                        "El monto excede el pago: quedan " + disponible + " por reembolsar.",
                        Map.of("pagado", pago.monto().toString(), "yaReembolsado", yaDevuelto.toString()));
            }

            UUID reembolsoId =
                    pagos.solicitarReembolso(dsl, pago.id(), entrada.monto(), motivo, ctx.usuarioId(), ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "aportes.reembolso_solicitado",
                            "reembolso",
                            reembolsoId,
                            Map.of(
                                    "pagoId",
                                    pago.id().toString(),
                                    "monto",
                                    entrada.monto().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaSolicitud(reembolsoId, "SOLICITADO", disponible.menos(entrada.monto()));
        });
    }

    /** Aprobar y ejecutar. Quien aprueba no puede ser quien solicito. */
    @Transactional
    public SalidaEjecucion aprobar(UUID reembolsoId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var reembolso = pagos.verReembolso(dsl, reembolsoId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(19, 3), "Ese reembolso no existe."));

            // AP-CU19-04 · R-SEG-04.
            if (reembolso.solicitadoPor().equals(ctx.usuarioId())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(19, 4), "Quien aprueba un reembolso no puede ser quien lo solicito.");
            }
            if (!pagos.ejecutarReembolso(dsl, reembolsoId, ctx.usuarioId(), ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(19, 3), "Ese reembolso ya no esta solicitado.");
            }

            var pago = pagos.ver(dsl, reembolso.pagoId()).orElseThrow();
            var obligacion = obligaciones.bloquear(dsl, pago.obligacionId()).orElseThrow();

            // La obligacion vuelve a estar pendiente por ese importe: si quedara dada
            // por pagada, el faltante lo absorben los demas del grupo sin enterarse.
            var saldoDespues = new SaldoDeLaObligacion.Estado(
                    obligacion.saldo().esperado(),
                    obligacion.saldo().pagado().menos(reembolso.monto()),
                    obligacion.saldo().condonado(),
                    obligacion.saldo().cubiertoPorGarantia());
            boolean vencida = obligacion.finDeGracia().isBefore(ahora.toLocalDate());
            String estadoNuevo = SaldoDeLaObligacion.estadoSegunSaldo(saldoDespues, vencida);
            obligaciones.revertirPago(dsl, obligacion.id(), reembolso.monto(), estadoNuevo);

            // El movimiento de dinero lo hace nucleo-financiero (invariante 12).
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "aportes.reembolso_ejecutado",
                            "reembolso",
                            reembolsoId,
                            Map.of(
                                    "pagoId", pago.id().toString(),
                                    "obligacionId", obligacion.id().toString(),
                                    "monto", reembolso.monto().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaEjecucion(reembolsoId, "EJECUTADO", obligacion.id(), estadoNuevo);
        });
    }

    /**
     * Registra una disputa del proveedor, con su plazo **guardado**.
     *
     * <p>El plazo no se recalcula al mirar el tablero (invariante 8): moverlo cada vez
     * que cambie la politica es un argumento que el proveedor no acepta.
     */
    @Transactional
    public SalidaDisputa registrarDisputa(EntradaDisputa entrada, ContextoSesion ctx) {
        String tipo = TipoDeDisputa.exigir(entrada.tipo());
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // AP-CU19-06: el proveedor reenvia. Una disputa abierta por pago basta.
            UUID idMensaje = UUID.nameUUIDFromBytes(
                    entrada.referenciaDelProveedor().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!consumidos.registrar(dsl, idMensaje, "proveedor-pago")) {
                return new SalidaDisputa(null, ahora, false);
            }
            if (pagos.hayDisputaAbierta(dsl, entrada.pagoId())) {
                return new SalidaDisputa(null, ahora, false);
            }

            OffsetDateTime limite = ahora.plus(plazoDeDisputa);
            UUID disputaId = pagos.abrirDisputa(
                    dsl, entrada.pagoId(), tipo, entrada.descripcion(), entrada.montoDisputado(), "[]", ahora, limite);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "aportes.disputa_abierta",
                            "disputa_pago",
                            disputaId,
                            Map.of(
                                    "pagoId", entrada.pagoId().toString(),
                                    "fechaLimiteRespuesta", limite.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaDisputa(disputaId, limite, true);
        });
    }

    public record EntradaReembolso(UUID pagoId, Dinero monto, String motivo) {}

    public record SalidaSolicitud(UUID reembolsoId, String estado, Dinero disponibleRestante) {}

    public record SalidaEjecucion(UUID reembolsoId, String estado, UUID obligacionId, String estadoObligacion) {}

    public record EntradaDisputa(
            UUID pagoId, String tipo, String descripcion, Dinero montoDisputado, String referenciaDelProveedor) {}

    public record SalidaDisputa(UUID disputaId, OffsetDateTime fechaLimiteRespuesta, boolean esNueva) {}
}
