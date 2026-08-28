package bo.aportaya.tarifas.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.tarifas.dominio.PeriodoContable;
import bo.aportaya.tarifas.infraestructura.LiquidacionRepositorio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-35 · Cerrar la liquidacion mensual de ingresos.
 *
 * <p>Un resultado mensual **reproducible desde los devengos**, no desde una planilla
 * aparte. La planilla aparte es como se termina discutiendo cual de los dos numeros es
 * el bueno.
 *
 * <p>Los tres datos que este servicio no puede saber —dias sin cerrar, excepciones
 * abiertas y saldo del mayor— **entran como parametros**: los dias y el mayor son de
 * {@code nucleo_financiero} y las excepciones de {@code aportes}, y este servicio no
 * lee esos esquemas (invariante 11). Quien dispara el cierre los trae resueltos.
 */
@Service
public class CU35CerrarLiquidacion {

    private final Datos datos;
    private final LiquidacionRepositorio liquidaciones;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU35CerrarLiquidacion(Datos datos, LiquidacionRepositorio liquidaciones, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.liquidaciones = liquidaciones;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaLiquidacion cerrar(EntradaLiquidacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        var periodo = new PeriodoContable(entrada.periodo());

        return datos.conContexto(ctx, dsl -> {
            // AP-CU35-04: el mes ya cerrado devuelve lo que hay. No es un error: el
            // planificador reintenta, y volver a cerrar duplicaria el asiento de cierre.
            var existente = liquidaciones.delPeriodo(dsl, periodo.valor());
            if (existente.isPresent()) {
                var previa = existente.get();
                return new SalidaLiquidacion(
                        previa.id(), periodo.valor(), previa.totalCobrado(), previa.ingresoNeto(), true, true, null);
            }

            // AP-CU35-01: la liquidacion mensual se apoya en dias cuadrados, no los
            // reemplaza. Cerrar el mes con dias abiertos es firmar un total del que
            // faltan pedazos.
            if (entrada.diasSinCerrar() > 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(35, 1),
                        "Quedan " + entrada.diasSinCerrar() + " dia(s) sin cerrar en " + periodo.valor() + ".");
            }
            // AP-CU35-02.
            if (entrada.excepcionesAbiertas() > 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(35, 2),
                        "Quedan " + entrada.excepcionesAbiertas() + " excepcion(es) de conciliacion sin resolver.");
            }

            var consolidado = liquidaciones.consolidar(dsl, periodo.valor());
            BigDecimal costoProveedores = liquidaciones.costoDeProveedores(dsl, periodo.valor());

            // AP-CU35-03: si la liquidacion no coincide con el mayor, NO se cierra. El
            // mes queda abierto con su hallazgo: un cierre que no cuadra es un cierre
            // que alguien va a tener que explicar sin datos.
            boolean cuadra = consolidado.cobrado().compareTo(entrada.saldoDeLaCuentaDeIngresos()) == 0;
            if (!cuadra) {
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "tarifas.liquidacion_descuadrada",
                                "liquidacion_ingresos",
                                UUID.nameUUIDFromBytes(
                                        periodo.valor().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                                Map.of(
                                        "periodo", periodo.valor(),
                                        "cobrado", consolidado.cobrado().toPlainString(),
                                        "mayor",
                                                entrada.saldoDeLaCuentaDeIngresos()
                                                        .toPlainString()),
                                UUID.fromString(ctx.traza().id())));
                throw new ErrorDeNegocio(
                        CodigoError.de(35, 3),
                        "La liquidacion no cuadra contra el mayor: cobrado " + consolidado.cobrado() + " contra "
                                + entrada.saldoDeLaCuentaDeIngresos() + ".");
            }

            UUID liquidacionId = liquidaciones.cerrar(
                    dsl,
                    periodo.valor(),
                    periodo.primerDia(),
                    periodo.ultimoDia(),
                    consolidado,
                    costoProveedores,
                    "CERRADA",
                    ctx.usuarioId(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "tarifas.liquidacion_cerrada",
                            "liquidacion_ingresos",
                            liquidacionId,
                            Map.of(
                                    "periodo", periodo.valor(),
                                    "totalCobrado", consolidado.cobrado().toPlainString(),
                                    "totalDevengado", consolidado.devengado().toPlainString(),
                                    "operaciones", Integer.toString(consolidado.operaciones())),
                            UUID.fromString(ctx.traza().id())));

            BigDecimal ingresoNeto = consolidado
                    .cobrado()
                    .subtract(consolidado.devuelto())
                    .subtract(consolidado.impuestos())
                    .subtract(costoProveedores);

            return new SalidaLiquidacion(
                    liquidacionId, periodo.valor(), consolidado.cobrado(), ingresoNeto, true, false, consolidado);
        });
    }

    public record EntradaLiquidacion(
            String periodo, int diasSinCerrar, int excepcionesAbiertas, BigDecimal saldoDeLaCuentaDeIngresos) {}

    public record SalidaLiquidacion(
            UUID liquidacionId,
            String periodo,
            BigDecimal totalCobrado,
            BigDecimal ingresoNeto,
            boolean cuadraContraMayor,
            boolean yaExistia,
            LiquidacionRepositorio.Consolidado consolidado) {}
}
