package bo.aportaya.entregas.aplicacion;

import bo.aportaya.entregas.dominio.ReintentoDeDesembolso;
import bo.aportaya.entregas.infraestructura.CuentaDestinoRepositorio;
import bo.aportaya.entregas.infraestructura.DesembolsoRepositorio;
import bo.aportaya.entregas.infraestructura.EntregaRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-28 · Emitir la orden de desembolso y ejecutar el intento.
 *
 * <p>Aca sale la plata. Tres barreras: **una orden viva por entrega** (R-DES-01),
 * **ninguna a cuenta sin verificar** (R-DES-02), y **los errores definitivos no se
 * reintentan**. Insistir contra una cuenta que no existe no la crea: solo demora el
 * momento en que alguien mira el caso, mientras la plata del beneficiario sigue
 * retenida.
 *
 * <p>El saldo lo mueve {@code nucleo-financiero} (invariante 12): este servicio emite
 * la orden y anota que contesto el proveedor.
 */
@Service
public class CU28EmitirDesembolso {

    private final Datos datos;
    private final DesembolsoRepositorio desembolsos;
    private final EntregaRepositorio entregas;
    private final CuentaDestinoRepositorio cuentas;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int intentosMaximos;
    private final Duration baseDeEspera;

    public CU28EmitirDesembolso(
            Datos datos,
            DesembolsoRepositorio desembolsos,
            EntregaRepositorio entregas,
            CuentaDestinoRepositorio cuentas,
            Outbox outbox,
            Reloj reloj,
            int intentosMaximos,
            Duration baseDeEspera) {
        this.datos = datos;
        this.desembolsos = desembolsos;
        this.entregas = entregas;
        this.cuentas = cuentas;
        this.outbox = outbox;
        this.reloj = reloj;
        this.intentosMaximos = intentosMaximos;
        this.baseDeEspera = baseDeEspera;
    }

    @Transactional
    public SalidaOrden emitir(EntradaOrden entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // Invariante 7 · R-DES-01: la clave se valida ANTES de escribir.
            var repetida = desembolsos.porClave(dsl, entrada.entregaId(), entrada.claveIdempotencia());
            if (repetida.isPresent()) {
                var previa = repetida.get();
                return new SalidaOrden(previa.id(), previa.estado(), previa.claveIdempotencia(), 0, false);
            }

            var entrega = entregas.ver(dsl, entrada.entregaId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(28, 1), "Esa entrega no existe."));
            // AP-CU28-01: no se desembolsa una entrega sin autorizar. La autorizacion
            // es la firma de que alguien reviso que corresponde pagarle a esa persona.
            if (!"AUTORIZADA".equals(entrega.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(28, 1), "La entrega esta " + entrega.estado() + ": todavia no se desembolsa.");
            }

            var cuenta = cuentas.ver(dsl, entrada.cuentaDestinoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(28, 2), "Esa cuenta de destino no existe."));
            // AP-CU28-02 · R-DES-02: la BASE tambien lo impide
            // (tg_orden_desembolso_cuenta_verificada); se comprueba aca para que sea
            // una regla de negocio con su mensaje y no un error 500.
            if (!cuenta.estaVerificada()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(28, 2), "No se ordena un desembolso a una cuenta sin verificar.");
            }
            // La ventana de enfriamiento sigue corriendo: verificada no es utilizable.
            if (cuenta.bloqueadaHasta() != null && ahora.isBefore(cuenta.bloqueadaHasta())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(28, 2),
                        "Esa cuenta esta en su ventana de enfriamiento hasta " + cuenta.bloqueadaHasta() + ".");
            }
            // AP-CU28-04: sin el saldo retenido, desembolsar seria pagar con plata que
            // todavia puede gastarse en otra cosa.
            if (!entrada.saldoRetenido()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(28, 4), "El saldo de la entrega no esta retenido: no se ordena el pago.");
            }

            UUID ordenId = desembolsos.emitir(
                    dsl,
                    entrada.entregaId(),
                    entrada.proveedorId(),
                    entrada.cuentaDestinoId(),
                    entrega.neto(),
                    entrada.glosa(),
                    entrada.claveIdempotencia());

            UUID intentoId = desembolsos.registrarIntento(dsl, ordenId, 1, ahora, null, "PENDIENTE", null, null, null);
            desembolsos.cambiarEstado(dsl, ordenId, List.of("CREADA"), "ENVIADA_A_PROVEEDOR");

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "entregas.desembolso_ordenado",
                            "orden_desembolso",
                            ordenId,
                            Map.of(
                                    "entregaId", entrada.entregaId().toString(),
                                    "monto", entrega.neto().toString(),
                                    "cuentaEnmascarada", cuenta.enmascarado(),
                                    "intentoId", intentoId.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaOrden(ordenId, "ENVIADA_A_PROVEEDOR", entrada.claveIdempotencia(), 1, true);
        });
    }

    /**
     * Anota lo que contesto el proveedor.
     *
     * <p>La llamada al proveedor la hace quien orquesta, **fuera de esta transaccion**
     * (invariante 6). Aca se registra el resultado y se decide si hay reintento.
     */
    @Transactional
    public SalidaIntento anotarRespuesta(EntradaRespuesta entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var orden = desembolsos
                    .bloquear(dsl, entrada.ordenId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(28, 5), "Esa orden no existe."));

            if ("ACREDITADA".equals(orden.estado()) || "RECHAZADA".equals(orden.estado())) {
                return new SalidaIntento(null, orden.estado(), 0, null, false);
            }

            if (entrada.exitoso()) {
                // Se CIERRA el intento en curso, no se agrega otro: un intento es una
                // fila. Agregar una al contestar convertiria un intento en dos, y el
                // conteo que decide si se reintenta dejaria de significar algo.
                int numero = desembolsos
                        .cerrarIntentoPendiente(
                                dsl, orden.id(), ahora, "EXITOSO", null, entrada.mensajeProveedor(), null)
                        .orElseGet(() -> desembolsos.intentosDe(dsl, orden.id()));
                desembolsos.acreditar(dsl, orden.id(), entrada.referenciaProveedor(), ahora);

                var entrega = entregas.bloquear(dsl, orden.entregaId()).orElseThrow();
                entregas.cambiarEstado(
                        dsl,
                        entrega.id(),
                        List.of("AUTORIZADA", "EN_PROCESO_DESEMBOLSO"),
                        "ENTREGADA",
                        entrega.version());

                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "entregas.desembolso_acreditado",
                                "orden_desembolso",
                                orden.id(),
                                Map.of(
                                        "entregaId", orden.entregaId().toString(),
                                        "referenciaProveedor", entrada.referenciaProveedor(),
                                        "monto", orden.monto().toString()),
                                UUID.fromString(ctx.traza().id())));

                return new SalidaIntento(orden.id(), "ACREDITADA", numero, null, true);
            }

            // AP-CU28-06: un error definitivo no se reintenta. La cuenta cerrada no se
            // reabre porque insistamos, y el beneficiario merece enterarse hoy.
            int numero = desembolsos.intentosDe(dsl, orden.id());
            var reintento = ReintentoDeDesembolso.siguiente(
                    entrada.codigoError(), numero, intentosMaximos, baseDeEspera, ahora);
            if (desembolsos
                    .cerrarIntentoPendiente(
                            dsl,
                            orden.id(),
                            ahora,
                            "FALLIDO",
                            entrada.codigoError(),
                            entrada.mensajeProveedor(),
                            reintento.orElse(null))
                    .isEmpty()) {
                // No habia intento abierto: el proveedor contesto algo que no se le
                // pidio. Se registra igual, porque perder esa respuesta seria perder
                // la unica noticia que hay de esa plata.
                desembolsos.registrarIntento(
                        dsl,
                        orden.id(),
                        numero + 1,
                        entrada.iniciado(),
                        ahora,
                        "FALLIDO",
                        entrada.codigoError(),
                        entrada.mensajeProveedor(),
                        reintento.orElse(null));
            }

            if (reintento.isEmpty()) {
                desembolsos.cambiarEstado(
                        dsl, orden.id(), List.of("CREADA", "ENVIADA_A_PROVEEDOR", "EN_PROCESO"), "RECHAZADA");
                // La entrega vuelve a estar autorizada y el saldo se libera: el
                // beneficiario tiene que poder corregir su cuenta y volver a intentar.
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "entregas.desembolso_rechazado",
                                "orden_desembolso",
                                orden.id(),
                                Map.of(
                                        "entregaId", orden.entregaId().toString(),
                                        "codigoError", String.valueOf(entrada.codigoError()),
                                        "definitivo",
                                                Boolean.toString(
                                                        ReintentoDeDesembolso.esDefinitivo(entrada.codigoError())),
                                        "monto", orden.monto().toString()),
                                UUID.fromString(ctx.traza().id())));
                return new SalidaIntento(orden.id(), "RECHAZADA", numero, null, true);
            }

            desembolsos.cambiarEstado(
                    dsl, orden.id(), List.of("CREADA", "ENVIADA_A_PROVEEDOR", "EN_PROCESO"), "EN_PROCESO");
            return new SalidaIntento(orden.id(), "EN_PROCESO", numero, reintento.get(), true);
        });
    }

    public record EntradaOrden(
            UUID entregaId,
            UUID proveedorId,
            UUID cuentaDestinoId,
            String glosa,
            String claveIdempotencia,
            boolean saldoRetenido) {}

    public record SalidaOrden(UUID ordenId, String estado, String claveIdempotencia, int intentos, boolean esNueva) {}

    public record EntradaRespuesta(
            UUID ordenId,
            OffsetDateTime iniciado,
            boolean exitoso,
            String referenciaProveedor,
            String codigoError,
            String mensajeProveedor) {}

    public record SalidaIntento(
            UUID ordenId, String estado, int numeroIntento, OffsetDateTime reintentableEn, boolean esNuevo) {}
}
