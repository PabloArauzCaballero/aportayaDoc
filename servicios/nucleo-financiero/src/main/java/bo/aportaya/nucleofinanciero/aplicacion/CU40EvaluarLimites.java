package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.dominio.EvaluacionDeTope;
import bo.aportaya.nucleofinanciero.dominio.EvaluacionDeTope.Resultado;
import bo.aportaya.nucleofinanciero.dominio.VentanaDeLimite;
import bo.aportaya.nucleofinanciero.infraestructura.CuentaBilleteraRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.LimiteRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-40 · Evaluar limites antes de una operacion.
 *
 * <p>**Vive en este carril y no en cumplimiento**, aunque planes/07 lo asigne al 1C.
 * Su tabla central —{@code consumo_limite}— esta en el esquema de la billetera, y
 * cumplimiento no lee esquemas ajenos (invariante 11). Su propia descomposicion lo
 * dice: «se ejecuta dentro de la transaccion de la operacion», y esa transaccion es
 * la de la billetera. Queda declarado en el informe del carril.
 *
 * <p>No abre transaccion propia cuando lo llama otro caso de uso: los metodos
 * {@code dentroDe} reciben el {@code DSLContext} de quien ya la abrio. Abrir una
 * segunda romperia el invariante 2 y, peor, dejaria el bloqueo del acumulado fuera de
 * la transaccion que decide.
 */
@Service
public class CU40EvaluarLimites {

    private final Datos datos;
    private final CuentaBilleteraRepositorio cuentas;
    private final LimiteRepositorio limites;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU40EvaluarLimites(
            Datos datos, CuentaBilleteraRepositorio cuentas, LimiteRepositorio limites, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.cuentas = cuentas;
        this.limites = limites;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    /** La consulta suelta, para que la app pueda mostrar cuanto queda del mes. */
    @Transactional
    public SalidaLimites ejecutar(EntradaLimites entrada, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> evaluar(dsl, entrada, ctx, false));
    }

    /**
     * La version que usan las operaciones: **dentro de su transaccion**, y lanza.
     *
     * <p>Devuelve los limites evaluados para que quien llama pueda acumular despues de
     * escribir el movimiento. Acumular aca seria contar una operacion que todavia
     * puede fallar.
     */
    public List<LimiteAplicado> exigirDentroDe(DSLContext dsl, EntradaLimites entrada, ContextoSesion ctx) {
        SalidaLimites salida = evaluar(dsl, entrada, ctx, true);
        if (!salida.permitido()) {
            throw new ErrorDeNegocio(codigoDe(salida.motivoRechazo()), salida.motivoRechazo());
        }
        return salida.aplicados();
    }

    private SalidaLimites evaluar(DSLContext dsl, EntradaLimites entrada, ContextoSesion ctx, boolean bloqueando) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        var cuenta = cuentas.ver(dsl, entrada.cuentaBilleteraId())
                .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(40, 1), "Esa billetera no existe."));

        var vigentes = limites.vigentes(dsl, entrada.concepto(), cuenta.nivelDiligencia(), ahora.toLocalDate());
        List<EvaluacionDeTope.Tope> topes = new ArrayList<>();
        List<LimiteAplicado> aplicados = new ArrayList<>();

        for (var limite : vigentes) {
            var rango = VentanaDeLimite.resolver(limite.ventana(), ahora);
            var consumo = bloqueando
                    ? limites.acumuladoBloqueado(dsl, cuenta.id(), limite.id(), rango.inicio(), limite.moneda())
                    : new LimiteRepositorio.Consumo(Dinero.cero(limite.moneda()), 0);
            topes.add(new EvaluacionDeTope.Tope(
                    entrada.concepto(),
                    limite.ventana(),
                    limite.montoMaximo(),
                    limite.cantidadMaxima(),
                    consumo.monto(),
                    consumo.cantidad()));
            aplicados.add(new LimiteAplicado(limite.id(), rango.inicio(), rango.fin()));
        }

        Resultado resultado = EvaluacionDeTope.evaluar(topes, entrada.monto());

        if (!resultado.permitido()) {
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.limite_rechazo",
                            "cuenta_billetera",
                            cuenta.id(),
                            Map.of("concepto", entrada.concepto(), "motivo", resultado.motivoRechazo()),
                            UUID.fromString(ctx.traza().id())));
        }

        return new SalidaLimites(
                resultado.permitido(),
                resultado.evaluados().stream()
                        .map(t -> new TopeEvaluado(
                                t.ventana(),
                                t.montoMaximo().map(Dinero::toString).orElse(null),
                                t.consumido().toString(),
                                t.disponible().toString()))
                        .toList(),
                resultado.motivoRechazo(),
                aplicados);
    }

    /** Suma la operacion al acumulado. Se llama DESPUES de escribir el movimiento. */
    public void acumularDentroDe(DSLContext dsl, UUID cuentaId, List<LimiteAplicado> aplicados, Dinero monto) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        for (LimiteAplicado limite : aplicados) {
            limites.acumular(dsl, cuentaId, limite.limiteId(), limite.inicio(), limite.fin(), monto, ahora);
        }
    }

    /** Un importe reversado no cuenta contra el limite: le comeria el cupo a la persona. */
    public void devolverDentroDe(DSLContext dsl, UUID cuentaId, List<LimiteAplicado> aplicados, Dinero monto) {
        for (LimiteAplicado limite : aplicados) {
            limites.devolver(dsl, cuentaId, limite.limiteId(), limite.inicio(), monto);
        }
    }

    private CodigoError codigoDe(String motivo) {
        if (motivo.startsWith("No hay limite configurado")) {
            return CodigoError.de(40, 1); // SIN_LIMITE_CONFIGURADO
        }
        return motivo.contains("cantidad maxima")
                ? CodigoError.de(40, 3) // CANTIDAD_EXCEDIDA
                : CodigoError.de(40, 2); // LIMITE_EXCEDIDO
    }

    public record EntradaLimites(UUID cuentaBilleteraId, String concepto, Dinero monto) {}

    public record SalidaLimites(
            boolean permitido,
            List<TopeEvaluado> limitesEvaluados,
            String motivoRechazo,
            List<LimiteAplicado> aplicados) {}

    public record TopeEvaluado(String ventana, String tope, String consumido, String disponible) {}

    public record LimiteAplicado(UUID limiteId, OffsetDateTime inicio, OffsetDateTime fin) {}
}
