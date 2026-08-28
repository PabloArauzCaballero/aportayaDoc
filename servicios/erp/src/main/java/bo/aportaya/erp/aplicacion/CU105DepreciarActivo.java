package bo.aportaya.erp.aplicacion;

import bo.aportaya.erp.dominio.Depreciacion;
import bo.aportaya.erp.infraestructura.ActivosRepositorio;
import bo.aportaya.erp.infraestructura.PeriodoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-105 · Depreciar un activo fijo.
 *
 * <p>**Una corrida por activo y periodo** ({@code uq_depreciacion_activo_periodo}).
 * Correr la depreciacion dos veces el mismo mes duplica el gasto y baja el resultado del
 * ejercicio por un error de operacion, no por el negocio.
 *
 * <p>**Y nunca por debajo del valor residual.** La ultima cuota se ajusta a lo que falta
 * aunque salga distinta de las anteriores: repetir la cuota teorica dejaria el activo
 * valiendo menos que nada, y {@code ck_activo_fijo_depreciacion} lo rechaza.
 */
@Service
public class CU105DepreciarActivo {

    private final Datos datos;
    private final ActivosRepositorio activos;
    private final PeriodoRepositorio periodos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU105DepreciarActivo(
            Datos datos, ActivosRepositorio activos, PeriodoRepositorio periodos, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.activos = activos;
        this.periodos = periodos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    /** La corrida mensual: todos los activos vivos, en el periodo indicado. */
    @Transactional
    public SalidaCorrida correr(UUID periodoId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var periodo = periodos.periodoPorId(dsl, periodoId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(105, 3), "Ese periodo no existe."));
            // Depreciar sobre un periodo cerrado cambiaria un resultado ya publicado.
            if (!"ABIERTO".equals(periodo.estado())) {
                throw new ErrorDeNegocio(CodigoError.de(105, 3), "El periodo esta cerrado: no admite depreciaciones.");
            }

            var corridas = new ArrayList<Corrida>();
            BigDecimal total = BigDecimal.ZERO;
            int yaCorridos = 0;
            int agotados = 0;

            for (var activo : activos.depreciables(dsl)) {
                if (activos.depreciacionDe(dsl, activo.id(), periodoId).isPresent()) {
                    // El reintento es inocuo: la corrida del mes ya se hizo para este
                    // activo y no se duplica.
                    yaCorridos++;
                    continue;
                }
                var cuota = Depreciacion.cuotaMensual(
                        activo.costoAdquisicion(), activo.valorResidual(), activo.acumulada(), activo.vidaUtilMeses());
                if (cuota.monto().signum() == 0) {
                    // Totalmente depreciado: sigue en el inventario pero ya no genera
                    // gasto. Forzar una cuota de cero rompería ck_depreciacion_monto.
                    agotados++;
                    continue;
                }

                UUID id = activos.depreciar(
                        dsl,
                        activo.id(),
                        periodoId,
                        cuota.monto(),
                        activo.moneda(),
                        cuota.acumuladaNueva(),
                        activo.costoAdquisicion(),
                        ahora);
                total = total.add(cuota.monto());
                corridas.add(new Corrida(
                        activo.id(),
                        id,
                        cuota.monto(),
                        Depreciacion.valorEnLibros(activo.costoAdquisicion(), cuota.acumuladaNueva()),
                        cuota.totalmenteDepreciado()));
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.depreciacion_corrida",
                            "periodo_contable",
                            periodoId,
                            Map.of(
                                    "activosDepreciados", Integer.toString(corridas.size()),
                                    "yaCorridos", Integer.toString(yaCorridos),
                                    "totalmenteDepreciados", Integer.toString(agotados),
                                    "totalDepreciado", total.toPlainString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCorrida(periodoId, List.copyOf(corridas), total, yaCorridos, agotados);
        });
    }

    /**
     * Deprecia UN activo. Es la puerta que usan las pruebas y el backoffice; la corrida
     * mensual la llama en bucle y tolera lo que aca es un rechazo.
     */
    @Transactional
    public Corrida depreciar(UUID activoId, UUID periodoId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var periodo = periodos.periodoPorId(dsl, periodoId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(105, 3), "Ese periodo no existe."));
            if (!"ABIERTO".equals(periodo.estado())) {
                throw new ErrorDeNegocio(CodigoError.de(105, 3), "El periodo esta cerrado: no admite depreciaciones.");
            }
            // AP-CU105-03: un activo dado de baja o vendido ya no esta en la lista de
            // depreciables. Seguir depreciandolo generaria gasto por algo que la empresa
            // ya no tiene.
            var activo = activos.porId(dsl, activoId)
                    .orElseThrow(
                            () -> new ErrorDeNegocio(CodigoError.de(105, 3), "Ese activo no existe o no esta ACTIVO."));

            // AP-CU105-01 · uq_depreciacion_activo_periodo.
            if (activos.depreciacionDe(dsl, activoId, periodoId).isPresent()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(105, 1), "Ese activo ya se deprecio en el periodo " + periodo.mes() + ".");
            }

            var cuota = Depreciacion.cuotaMensual(
                    activo.costoAdquisicion(), activo.valorResidual(), activo.acumulada(), activo.vidaUtilMeses());
            // AP-CU105-02: llegado al valor residual, no se deprecia mas. Forzar una
            // cuota de cero rompería ck_depreciacion_activo_monto, y forzar una positiva
            // dejaria el activo valiendo menos que su residual.
            if (cuota.monto().signum() == 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(105, 2), "El activo ya llego a su valor residual: no se deprecia mas.");
            }

            UUID id = activos.depreciar(
                    dsl,
                    activoId,
                    periodoId,
                    cuota.monto(),
                    activo.moneda(),
                    cuota.acumuladaNueva(),
                    activo.costoAdquisicion(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.activo_depreciado",
                            "depreciacion_activo",
                            id,
                            Map.of(
                                    "activoId", activoId.toString(),
                                    "monto", cuota.monto().toPlainString(),
                                    "valorEnLibros",
                                            Depreciacion.valorEnLibros(
                                                            activo.costoAdquisicion(), cuota.acumuladaNueva())
                                                    .toPlainString()),
                            UUID.fromString(ctx.traza().id())));

            return new Corrida(
                    activoId,
                    id,
                    cuota.monto(),
                    Depreciacion.valorEnLibros(activo.costoAdquisicion(), cuota.acumuladaNueva()),
                    cuota.totalmenteDepreciado());
        });
    }

    public record Corrida(
            UUID activoId,
            UUID depreciacionId,
            BigDecimal monto,
            BigDecimal valorEnLibros,
            boolean totalmenteDepreciado) {}

    public record SalidaCorrida(
            UUID periodoId,
            List<Corrida> depreciaciones,
            BigDecimal totalDepreciado,
            int yaCorridos,
            int totalmenteDepreciados) {}
}
