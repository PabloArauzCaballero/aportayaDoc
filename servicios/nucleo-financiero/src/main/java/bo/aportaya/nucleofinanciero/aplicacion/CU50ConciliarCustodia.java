package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.dominio.RatioDeCobertura;
import bo.aportaya.nucleofinanciero.dominio.RatioDeCobertura.Resultado;
import bo.aportaya.nucleofinanciero.infraestructura.ConciliacionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-50 · Conciliar la custodia y verificar el encaje.
 *
 * <p>Todos los dias se pregunta lo mismo: ¿hay en el banco tanto dinero como el que
 * dicen las billeteras? Si la respuesta es no, el sistema entra en modo restringido
 * —no salen retiros nuevos— pero **si acepta recargas**: dejar entrar plata mejora el
 * encaje, y frenarla lo empeoraria justo cuando hace falta lo contrario.
 *
 * <p>El emitido **llega desde afuera**: lo calcula quien corre la conciliacion sumando
 * los cierres diarios. Este caso de uso no lo deriva por su cuenta para que el numero
 * que se compara sea el mismo que quedo cerrado y firmado, no uno recalculado despues.
 */
@Service
public class CU50ConciliarCustodia {

    private final Datos datos;
    private final ConciliacionRepositorio conciliaciones;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU50ConciliarCustodia(Datos datos, ConciliacionRepositorio conciliaciones, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.conciliaciones = conciliaciones;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaConciliacion ejecutar(EntradaConciliacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // AP-CU50-03: una conciliacion por cuenta y dia. Repetirla escribiria dos
            // verdades sobre el mismo dia y ninguna seria la buena.
            if (conciliaciones.existeDelDia(dsl, entrada.cuentaCustodiaId(), entrada.fecha())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(50, 3),
                        "Ya hay una conciliacion de esa cuenta de custodia para el " + entrada.fecha() + ".");
            }

            // AP-CU50-01: sin cierres diarios no hay contra que comparar.
            if (!conciliaciones.haySaldosDelDia(dsl, entrada.fecha())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(50, 1),
                        "No hay cierres diarios del " + entrada.fecha() + ": no hay nada que conciliar.");
            }

            Resultado resultado = RatioDeCobertura.calcular(
                    entrada.saldoEmitido(), entrada.saldoCustodia(), entrada.saldoEnTransito());

            UUID conciliacionId = conciliaciones.registrar(
                    dsl,
                    entrada.cuentaCustodiaId(),
                    entrada.fecha(),
                    entrada.saldoEmitido(),
                    entrada.saldoCustodia(),
                    entrada.saldoEnTransito(),
                    resultado.cumpleEncaje(),
                    ctx.usuarioId(),
                    ahora);

            if (!resultado.cumpleEncaje()) {
                // El modo restringido no se decide aca: lo aplica la base a cada
                // retiro, mirando la ultima conciliacion. El evento es para que
                // alguien lo mire, no para gobernar la operacion.
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "nucleo_financiero.encaje_incumplido",
                                "conciliacion_custodia",
                                conciliacionId,
                                Map.of(
                                        "fecha", entrada.fecha().toString(),
                                        "ratio", resultado.ratio().toPlainString(),
                                        "diferencia", resultado.diferencia().toString()),
                                UUID.fromString(ctx.traza().id())));
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.custodia_conciliada",
                            "conciliacion_custodia",
                            conciliacionId,
                            Map.of(
                                    "fecha", entrada.fecha().toString(),
                                    "cumpleEncaje", Boolean.toString(resultado.cumpleEncaje())),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaConciliacion(
                    conciliacionId,
                    resultado.ratio(),
                    resultado.cumpleEncaje(),
                    resultado.diferencia(),
                    resultado.cumpleEncaje() ? "CUADRADA" : "DESCUADRADA");
        });
    }

    public record EntradaConciliacion(
            UUID cuentaCustodiaId, LocalDate fecha, Dinero saldoEmitido, Dinero saldoCustodia, Dinero saldoEnTransito) {

        public static EntradaConciliacion deHoy(
                UUID cuentaCustodiaId, LocalDate fecha, String emitido, String custodia) {
            return new EntradaConciliacion(
                    cuentaCustodiaId,
                    fecha,
                    Dinero.de(emitido, Moneda.BOB),
                    Dinero.de(custodia, Moneda.BOB),
                    Dinero.cero(Moneda.BOB));
        }
    }

    public record SalidaConciliacion(
            UUID conciliacionId,
            java.math.BigDecimal ratioCobertura,
            boolean cumpleEncaje,
            Dinero diferencia,
            String estado) {}
}
