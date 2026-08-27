package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.infraestructura.CuentaBilleteraRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.LibroDeBilletera;
import bo.aportaya.nucleofinanciero.infraestructura.LibroDeBilletera.Pata;
import bo.aportaya.nucleofinanciero.infraestructura.ReversoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-14 · Reversar una transaccion.
 *
 * <p>**Reversar es escribir el espejo, no borrar el original.** El libro es
 * append-only (R-AUD-01) y la unica correccion valida es el movimiento inverso. La
 * transaccion original conserva sus movimientos sin un solo cambio: lo que paso, paso,
 * y el extracto tiene que poder contar la historia completa — el cobro y su
 * devolucion, no un cobro que se desvanecio.
 *
 * <p>Cuatro ojos: quien autoriza no puede ser quien solicita. Un reverso es la
 * operacion con la que se puede sacar plata de cualquier cuenta sin que el titular
 * intervenga, asi que exige dos personas por definicion (R-SEG-04).
 */
@Service
public class CU14ReversarTransaccion {

    private static final int MOTIVO_MINIMO = 20;

    private final Datos datos;
    private final ReversoRepositorio reversos;
    private final CuentaBilleteraRepositorio cuentas;
    private final LibroDeBilletera libro;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU14ReversarTransaccion(
            Datos datos,
            ReversoRepositorio reversos,
            CuentaBilleteraRepositorio cuentas,
            LibroDeBilletera libro,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.reversos = reversos;
        this.cuentas = cuentas;
        this.libro = libro;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaReverso ejecutar(EntradaReverso entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        // AP-CU14-02 · R-SEG-04. Se comprueba ANTES de tocar la base: un reverso sin
        // cuatro ojos no merece ni una consulta.
        if (entrada.autorizadaPor().equals(ctx.usuarioId())) {
            throw new ErrorDeNegocio(
                    CodigoError.de(14, 2), "Quien autoriza un reverso no puede ser quien lo solicita.");
        }
        if (entrada.motivo() == null || entrada.motivo().trim().length() < MOTIVO_MINIMO) {
            // Un motivo de tres palabras no explica nada seis meses despues, que es
            // justo cuando alguien pregunta por que se le saco plata de la cuenta.
            throw new ErrorDeNegocio(
                    CodigoError.de(14, 2), "El motivo del reverso tiene que explicar que paso, no resumirlo.");
        }

        return datos.conContexto(ctx, dsl -> {
            var original = reversos.original(dsl, entrada.transaccionOriginalId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(14, 1), "Esa transaccion no existe."));

            // AP-CU14-01 · R-BIL-15.
            if (!original.esReversable()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(14, 1), "Esa transaccion esta " + original.estado() + ": no se reversa.");
            }
            if (reversos.yaFueReversada(dsl, original.id())) {
                throw new ErrorDeNegocio(CodigoError.de(14, 1), "Esa transaccion ya fue reversada.");
            }

            // Se bloquean las cuentas que el reverso va a tocar, en orden de
            // identificador: es el mismo cuidado que en la transferencia.
            original.patas().stream()
                    .map(ReversoRepositorio.Movimiento::cuentaId)
                    .distinct()
                    .sorted()
                    .forEach(cuenta -> cuentas.bloquear(dsl, cuenta));

            // AP-CU14-03: si el espejo dejaria una cuenta en negativo, no se fuerza.
            // Se registra una obligacion de restitucion y el saldo NO baja de cero —
            // el error fue nuestro y la deuda queda a la vista, no escondida en un
            // saldo negativo que la persona no entiende.
            List<Pata> espejo = new ArrayList<>();
            boolean generaRestitucion = false;
            for (var pata : original.patas()) {
                if ("CREDITO".equals(pata.sentido())) {
                    var cuenta = cuentas.ver(dsl, pata.cuentaId()).orElseThrow();
                    if (pata.monto().esMayorQue(cuenta.disponible()) && !cuenta.permiteNegativo()) {
                        generaRestitucion = true;
                    }
                }
                espejo.add(
                        new Pata(pata.cuentaId(), pata.sentidoInverso(), pata.monto(), "Reverso de: " + pata.glosa()));
            }

            if (generaRestitucion) {
                UUID reversoId = reversos.registrar(
                        dsl,
                        original.id(),
                        null,
                        entrada.autorizadaPor(),
                        entrada.tipo(),
                        entrada.motivo(),
                        original.montoTotal(),
                        ahora);
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "nucleo_financiero.restitucion_requerida",
                                "reverso_transaccion",
                                reversoId,
                                Map.of(
                                        "transaccionOriginalId", original.id().toString(),
                                        "monto", original.montoTotal().toString(),
                                        "motivo", entrada.motivo()),
                                UUID.fromString(ctx.traza().id())));
                return new SalidaReverso(reversoId, null, true);
            }

            UUID transaccionReverso = libro.registrar(
                    dsl,
                    "REVERSO",
                    "AJUSTE",
                    original.id(),
                    "API",
                    original.montoTotal(),
                    entrada.claveIdempotencia(),
                    Optional.of(entrada.autorizadaPor()),
                    espejo,
                    ahora);

            UUID reversoId = reversos.registrar(
                    dsl,
                    original.id(),
                    transaccionReverso,
                    entrada.autorizadaPor(),
                    entrada.tipo(),
                    entrada.motivo(),
                    original.montoTotal(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.transaccion_reversada",
                            "reverso_transaccion",
                            reversoId,
                            Map.of(
                                    "transaccionOriginalId", original.id().toString(),
                                    "transaccionReversoId", transaccionReverso.toString(),
                                    "tipo", entrada.tipo()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaReverso(reversoId, transaccionReverso, false);
        });
    }

    public record EntradaReverso(
            String claveIdempotencia, UUID transaccionOriginalId, String tipo, String motivo, UUID autorizadaPor) {}

    public record SalidaReverso(UUID reversoId, UUID transaccionReversoId, boolean generaObligacionDeRestitucion) {}
}
