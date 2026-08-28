package bo.aportaya.entregas.aplicacion;

import bo.aportaya.entregas.dominio.LiquidacionDeEntrega;
import bo.aportaya.entregas.infraestructura.EntregaRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-22 · Liquidar y entregar el fondo.
 *
 * <p>Es el momento en que el pasanaku cumple lo que prometio. Tres cosas lo protegen:
 * **una entrega por turno** (R-GRP-01), **quien autoriza no ejecuta** (R-SEG-04), y el
 * **neto nunca es negativo** — que las deducciones superen la bolsa significa que el
 * beneficiario terminaria debiendo por cobrar su turno.
 *
 * <p>Los totales de la cabecera los recalcula la base ({@code tg_deduccion_recalcula})
 * cada vez que entra una deduccion. Escribirlos a mano permitiria que un neto y sus
 * deducciones dejaran de coincidir sin que nada avise.
 */
@Service
public class CU22LiquidarEntrega {

    private final Datos datos;
    private final EntregaRepositorio entregas;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU22LiquidarEntrega(Datos datos, EntregaRepositorio entregas, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.entregas = entregas;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaLiquidacion liquidar(EntradaLiquidacion entrada, ContextoSesion ctx) {
        // El neto se comprueba ANTES de escribir: una entrega a medio liquidar, con
        // deducciones cargadas y sin cabecera coherente, es peor que ninguna.
        var calculo = LiquidacionDeEntrega.liquidar(entrada.bruto(), entrada.deducciones());

        return datos.conContexto(ctx, dsl -> {
            // AP-CU22-01: no se liquida una bolsa incompleta. Entregar menos de lo que
            // el grupo prometio, sin decirlo, es la forma mas rapida de perder la
            // confianza de todos a la vez.
            if (entrada.bruto().esMayorQue(entrada.recaudado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(22, 1),
                        "La bolsa esta incompleta: se recaudo " + entrada.recaudado() + " de " + entrada.bruto() + ".");
            }

            UUID entregaId = entregas.crear(
                    dsl,
                    entrada.grupoId(),
                    entrada.periodoId(),
                    entrada.turnoId(),
                    entrada.cupoId(),
                    entrada.beneficiarioId(),
                    entrada.bruto(),
                    entrada.metodoDesembolso(),
                    entrada.fechaProgramada());

            // Cada deduccion con su tipo y su origen. «Le descontamos 518» no se puede
            // explicar; «comision 18, aporte propio 500» si.
            for (var deduccion : calculo.deducciones()) {
                entregas.agregarDeduccion(dsl, entregaId, deduccion);
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "entregas.entrega_liquidada",
                            "entrega_fondo",
                            entregaId,
                            Map.of(
                                    "turnoId", entrada.turnoId().toString(),
                                    "bruto", calculo.bruto().toString(),
                                    "deducciones", calculo.totalDeducciones().toString(),
                                    "neto", calculo.neto().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaLiquidacion(
                    entregaId, calculo.bruto(), calculo.totalDeducciones(), calculo.neto(), "PROGRAMADA");
        });
    }

    /**
     * Autoriza la entrega.
     *
     * <p>Una validacion bloqueante sin resolver la frena: son las que preguntan si el
     * beneficiario sigue al dia, si la cuenta esta verificada, si no hay una incidencia
     * abierta. Saltearlas para «no demorar la entrega» es como se entrega mal.
     */
    @Transactional
    public SalidaAutorizacion autorizar(UUID entregaId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var entrega = entregas.bloquear(dsl, entregaId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(22, 3), "Esa entrega no existe."));

            // AP-CU22-02.
            int bloqueantes = entregas.bloqueantesSinAprobar(dsl, entregaId);
            if (bloqueantes > 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(22, 2), "Hay " + bloqueantes + " validacion(es) bloqueante(s) sin resolver.");
            }
            if (!entregas.autorizar(dsl, entregaId, ctx.usuarioId(), ahora, entrega.version())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(22, 3), "Esa entrega ya no admite autorizacion: esta " + entrega.estado() + ".");
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "entregas.entrega_autorizada",
                            "entrega_fondo",
                            entregaId,
                            Map.of(
                                    "autorizadaPor", ctx.usuarioId().toString(),
                                    "neto", entrega.neto().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaAutorizacion(entregaId, "AUTORIZADA", ctx.usuarioId());
        });
    }

    /**
     * Ejecuta la entrega.
     *
     * <p>**Quien autoriza no ejecuta** (R-SEG-04). No es formalismo: una sola persona
     * que autoriza y ejecuta puede sacar el fondo entero de un grupo sin que nadie mas
     * lo vea pasar.
     */
    @Transactional
    public SalidaEjecucion ejecutar(UUID entregaId, Dinero entregado, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var entrega = entregas.bloquear(dsl, entregaId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(22, 3), "Esa entrega no existe."));

            // AP-CU22-04 · R-SEG-04.
            if (ctx.usuarioId().equals(entrega.autorizadaPor())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(22, 4), "Quien ejecuta la entrega no puede ser quien la autorizo.");
            }
            if (!entregas.marcarEntregada(dsl, entregaId, ctx.usuarioId(), entregado, ahora, entrega.version())) {
                throw new ErrorDeNegocio(CodigoError.de(22, 3), "Esa entrega no estaba lista para ejecutarse.");
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "entregas.entrega_ejecutada",
                            "entrega_fondo",
                            entregaId,
                            Map.of(
                                    "ejecutadaPor", ctx.usuarioId().toString(),
                                    "montoEntregado", entregado.toString(),
                                    "beneficiarioId", entrega.beneficiarioId().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaEjecucion(entregaId, "ENTREGADA", entregado);
        });
    }

    public record EntradaLiquidacion(
            UUID grupoId,
            UUID periodoId,
            UUID turnoId,
            UUID cupoId,
            UUID beneficiarioId,
            Dinero bruto,
            Dinero recaudado,
            List<LiquidacionDeEntrega.Deduccion> deducciones,
            String metodoDesembolso,
            LocalDate fechaProgramada) {}

    public record SalidaLiquidacion(
            UUID entregaId, Dinero bruto, Dinero totalDeducciones, Dinero neto, String estado) {}

    public record SalidaAutorizacion(UUID entregaId, String estado, UUID autorizadaPor) {}

    public record SalidaEjecucion(UUID entregaId, String estado, Dinero montoEntregado) {}
}
