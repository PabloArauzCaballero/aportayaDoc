package bo.aportaya.tarifas.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.tarifas.dominio.EstadoDelDevengo;
import bo.aportaya.tarifas.dominio.PeriodoContable;
import bo.aportaya.tarifas.dominio.VigenciaDeCotizacion;
import bo.aportaya.tarifas.infraestructura.CotizacionRepositorio;
import bo.aportaya.tarifas.infraestructura.DevengoRepositorio;
import bo.aportaya.tarifas.infraestructura.TarifarioRepositorio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-31 · Devengar y cobrar la comision.
 *
 * <p>Separar **ganar** de **cobrar**. El ingreso se reconoce cuando ocurre el hecho
 * generador; el cobro es otra cosa y puede fallar, exonerarse o devolverse sin que el
 * ingreso desaparezca del registro. Si se registrara solo lo cobrado, un mes malo
 * pareceria un mes sin ventas.
 *
 * <p>El movimiento de dinero lo hace **nucleo-financiero** (invariante 12): este
 * servicio emite el evento con la via de cobro elegida y anota el {@code cargo_comision}
 * que espera su confirmacion.
 */
@Service
public class CU31DevengarComision {

    /** Tres intentos y pasa a cuenta por cobrar. Es politica, no capricho del codigo. */
    private final int intentosAntesDeIncobrable;

    private final Datos datos;
    private final DevengoRepositorio devengos;
    private final CotizacionRepositorio cotizaciones;
    private final TarifarioRepositorio tarifarios;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int diasParaCobrar;

    public CU31DevengarComision(
            Datos datos,
            DevengoRepositorio devengos,
            CotizacionRepositorio cotizaciones,
            TarifarioRepositorio tarifarios,
            Outbox outbox,
            Reloj reloj,
            int intentosAntesDeIncobrable,
            int diasParaCobrar) {
        this.datos = datos;
        this.devengos = devengos;
        this.cotizaciones = cotizaciones;
        this.tarifarios = tarifarios;
        this.outbox = outbox;
        this.reloj = reloj;
        this.intentosAntesDeIncobrable = intentosAntesDeIncobrable;
        this.diasParaCobrar = diasParaCobrar;
    }

    @Transactional
    public SalidaDevengo devengar(EntradaDevengo entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // R-TAR-05 · invariante 7: la clave se valida antes de escribir.
            var repetido = devengos.porClave(dsl, entrada.grupoId().orElse(null), entrada.claveIdempotencia());
            if (repetido.isPresent()) {
                var previo = repetido.get();
                return new SalidaDevengo(previo.id(), null, previo.estado(), previo.montoTotal(), false);
            }

            var cotizacion = cotizaciones
                    .ver(dsl, entrada.cotizacionId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(31, 2), "Esa cotizacion no existe."));

            // AP-CU31-02: no se devenga con un precio que el usuario ya no vio.
            if (new VigenciaDeCotizacion(cotizacion.validaHasta()).vencidaEn(ahora)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(31, 2), "La cotizacion vencio: hay que recalcular antes de devengar.");
            }

            var concepto = tarifarios
                    .conceptoPorId(dsl, cotizacion.conceptoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(31, 4), "Ese concepto no existe."));

            // AP-CU31-04: sin cuenta de ingreso mapeada el asiento no se puede armar, y
            // un ingreso sin asiento es un ingreso que la contabilidad no ve.
            if (concepto.cuentaIngresoId() == null) {
                throw new ErrorDeNegocio(
                        CodigoError.de(31, 4),
                        "El concepto " + concepto.codigo() + " no tiene cuenta de ingreso mapeada.");
            }

            String estadoInicial = entrada.exentoTotal() ? EstadoDelDevengo.EXONERADO : EstadoDelDevengo.DEVENGADO;
            var periodo = PeriodoContable.de(ahora);

            UUID devengoId = devengos.registrar(
                    dsl,
                    concepto.id(),
                    entrada.tarifarioId(),
                    cotizacion.id(),
                    entrada.grupoId().orElse(null),
                    entrada.usuarioObligadoId(),
                    entrada.referenciaTipo(),
                    entrada.referenciaId(),
                    cotizacion.montoBase(),
                    cotizacion.montoComision(),
                    entrada.descuento(),
                    cotizacion.montoImpuesto(),
                    entrada.exentoTotal() ? Dinero.cero(cotizacion.montoTotal().moneda()) : cotizacion.montoTotal(),
                    estadoInicial,
                    periodo.valor(),
                    entrada.claveIdempotencia(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "tarifas.comision_devengada",
                            "devengo_comision",
                            devengoId,
                            Map.of(
                                    "conceptoCodigo", concepto.codigo(),
                                    "cuentaIngresoId",
                                            concepto.cuentaIngresoId().toString(),
                                    "montoTotal", cotizacion.montoTotal().toString(),
                                    "periodoContable", periodo.valor(),
                                    "estado", estadoInicial),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaDevengo(devengoId, null, estadoInicial, cotizacion.montoTotal(), true);
        });
    }

    /**
     * Anota el resultado de un intento de cobro.
     *
     * <p>El cobro real lo ejecuta quien mueve el dinero. Aca se registra que paso y se
     * decide si toca reintentar o pasar a cobranza: tres fallos y el devengo va a
     * cuenta por cobrar, sin borrarse. La deuda no desaparece porque el debito falle.
     */
    @Transactional
    public SalidaCobro anotarCobro(EntradaCobro entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // El candado de fila es la barrera: la tabla es append-only y no hay UPDATE
            // que sirva de exclusion, asi que dos cobros del mismo devengo se ponen en
            // fila y el segundo ve el cargo que dejo el primero.
            var devengo = devengos.bloquear(dsl, entrada.devengoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(31, 1), "Ese devengo no existe."));

            int intentosPrevios = devengos.intentosDe(dsl, devengo.id());
            Dinero yaCobrado =
                    devengos.cobradoDe(dsl, devengo.id(), devengo.montoTotal().moneda());

            if (entrada.exitoso()) {
                // Cobrar dos veces la misma comision es el defecto que esta operacion
                // no puede tener: si ya hay un cargo cobrado, no se agrega otro.
                if (yaCobrado.monto().signum() > 0) {
                    throw new ErrorDeNegocio(
                            CodigoError.de(31, 1), "Ese devengo ya tiene un cobro registrado por " + yaCobrado + ".");
                }
                UUID cargoId = devengos.registrarCargo(
                        dsl,
                        devengo.id(),
                        entrada.formaCobro(),
                        devengo.montoTotal(),
                        "COBRADO",
                        intentosPrevios + 1,
                        null,
                        ahora);
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "tarifas.comision_cobrada",
                                "devengo_comision",
                                devengo.id(),
                                Map.of(
                                        "cargoId",
                                        cargoId.toString(),
                                        "monto",
                                        devengo.montoTotal().toString()),
                                UUID.fromString(ctx.traza().id())));
                return new SalidaCobro(cargoId, "COBRADO", EstadoDelDevengo.COBRADO, null);
            }

            UUID cargoId = devengos.registrarCargo(
                    dsl,
                    devengo.id(),
                    entrada.formaCobro(),
                    devengo.montoTotal(),
                    "FALLIDO",
                    intentosPrevios + 1,
                    entrada.error(),
                    null);

            // AP-CU31-03: agotados los intentos, entra al circuito de cobranza. El
            // devengo NO se marca incobrable con un UPDATE —la tabla no lo admite—:
            // la cuenta por cobrar es el registro de que la deuda sigue viva.
            // El cargo FALLIDO ya se inserto arriba, asi que este conteo lo incluye.
            // Sumarle uno abriria la cuenta por cobrar un intento antes de tiempo, y
            // en el intento siguiente la abriria otra vez — la base lo rechaza por
            // unico, pero el defecto es contar mal, no que la base avise.
            int fallidos = devengos.fallidosDe(dsl, devengo.id());
            if (fallidos >= intentosAntesDeIncobrable) {
                UUID cuentaId = devengos.abrirCuentaPorCobrar(
                        dsl,
                        devengo.id(),
                        devengo.usuarioObligadoId(),
                        devengo.montoTotal(),
                        ahora.toLocalDate().plusDays(diasParaCobrar));
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "tarifas.comision_incobrable",
                                "devengo_comision",
                                devengo.id(),
                                Map.of(
                                        "cuentaPorCobrarId", cuentaId.toString(),
                                        "intentos", Integer.toString(fallidos)),
                                UUID.fromString(ctx.traza().id())));
                return new SalidaCobro(cargoId, "FALLIDO", EstadoDelDevengo.INCOBRABLE, cuentaId);
            }

            return new SalidaCobro(cargoId, "FALLIDO", EstadoDelDevengo.DEVENGADO, null);
        });
    }

    public record EntradaDevengo(
            String claveIdempotencia,
            UUID cotizacionId,
            UUID tarifarioId,
            String referenciaTipo,
            UUID referenciaId,
            UUID usuarioObligadoId,
            Optional<UUID> grupoId,
            Dinero descuento,
            boolean exentoTotal) {}

    public record SalidaDevengo(UUID devengoId, UUID cargoId, String estado, Dinero montoTotal, boolean esNuevo) {}

    public record EntradaCobro(UUID devengoId, String formaCobro, boolean exitoso, String error) {}

    public record SalidaCobro(UUID cargoId, String estadoDelCargo, String estadoDelDevengo, UUID cuentaPorCobrarId) {}
}
