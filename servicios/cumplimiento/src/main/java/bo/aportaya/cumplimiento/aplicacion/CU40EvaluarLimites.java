package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.EvaluacionDeLimites;
import bo.aportaya.cumplimiento.infraestructura.LimiteRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-40 · Evaluar limites antes de una operacion.
 *
 * <p>**Ninguna operacion se aplica sin evaluar limites, y sin limite configurado se
 * deniega** (R-LIM-01). Permitir lo no configurado significa que un olvido en el
 * catalogo abre la puerta de par en par, y ese olvido no se descubre hasta que alguien
 * lo aprovecha.
 *
 * <p>El rechazo dice **cuanto queda disponible**. Alguien que intenta retirar Bs 3.000
 * y tiene 800 puede retirar 800 hoy; decirle solo «excede el limite» lo deja sin saber
 * que hacer, y termina intentando cinco veces.
 *
 * <p>Este servicio **decide, no descuenta**: el consumo lo escribe el nucleo financiero
 * al aplicar la operacion, en su propia transaccion (invariantes 11 y 12).
 */
@Service
public class CU40EvaluarLimites {

    private final Datos datos;
    private final LimiteRepositorio limites;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU40EvaluarLimites(Datos datos, LimiteRepositorio limites, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.limites = limites;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public EvaluacionDeLimites.Veredicto evaluar(EntradaLimite entrada, ContextoSesion ctx) {
        var hoy = reloj.ahora().atOffset(ZoneOffset.UTC).toLocalDate();

        return datos.conContexto(ctx, dsl -> {
            String nivel = limites.nivelDeLaCuenta(dsl, entrada.cuentaBilleteraId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(40, 1), "Esa cuenta de billetera no existe."));

            var topes = limites.conConsumo(dsl, entrada.cuentaBilleteraId(), nivel, entrada.concepto(), hoy).stream()
                    .map(t -> new EvaluacionDeLimites.Tope(
                            t.concepto(),
                            t.ventana(),
                            t.montoMaximo(),
                            t.cantidadMaxima(),
                            t.consumido(),
                            t.cantidadConsumida()))
                    .toList();

            var veredicto = EvaluacionDeLimites.evaluar(topes, entrada.monto());

            // La evaluacion queda registrada tanto si permite como si no: sin el rastro,
            // nadie puede reconstruir despues por que una operacion se dejo pasar.
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.limites_evaluados",
                            "cuenta_billetera",
                            entrada.cuentaBilleteraId(),
                            Map.of(
                                    "concepto", entrada.concepto(),
                                    "monto", entrada.monto().toPlainString(),
                                    "nivelDiligencia", nivel,
                                    "permitido", Boolean.toString(veredicto.permitido()),
                                    "motivoRechazo",
                                            veredicto.motivoRechazo() == null ? "" : veredicto.motivoRechazo()),
                            UUID.fromString(ctx.traza().id())));

            // AP-CU40-01 · denegar por omision.
            if (veredicto.sinLimiteConfigurado()) {
                throw new ErrorDeNegocio(CodigoError.de(40, 1), veredicto.motivoRechazo());
            }
            // AP-CU40-02 · el acumulado mas la operacion supera el tope. Se devuelve el
            // veredicto en vez de lanzar: quien llama necesita los disponibles para
            // decirselos al usuario.
            return veredicto;
        });
    }

    public record EntradaLimite(UUID cuentaBilleteraId, String concepto, BigDecimal monto) {}
}
