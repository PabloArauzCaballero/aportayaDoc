package bo.aportaya.notificaciones.aplicacion;

import bo.aportaya.notificaciones.dominio.Canal;
import bo.aportaya.notificaciones.dominio.FirmaDeWebhook;
import bo.aportaya.notificaciones.dominio.IntencionEntrante;
import bo.aportaya.notificaciones.dominio.IntencionEntrante.Intencion;
import bo.aportaya.notificaciones.infraestructura.RespuestaRepositorio;
import bo.aportaya.notificaciones.infraestructura.SupresionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.Consumidos;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-82 · Procesar una respuesta entrante.
 *
 * <p>El webhook es la unica puerta del sistema que abre alguien de afuera, asi que las
 * dos primeras cosas que hace son verificar la firma y no revelar nada.
 *
 * <p>**No revelar nada** es literal: si el remitente no corresponde a un canal
 * verificado, la respuesta es la misma que si el mensaje fuera duplicado. Contestar
 * «ese numero no existe» le confirmaria a cualquiera quien es cliente y quien no,
 * usando nuestro propio webhook como directorio.
 */
@Service
public class CU82ProcesarRespuesta {

    private final Datos datos;
    private final RespuestaRepositorio respuestas;
    private final SupresionRepositorio supresiones;
    private final Consumidos consumidos;
    private final Outbox outbox;
    private final Reloj reloj;
    private final String secretoWebhook;

    public CU82ProcesarRespuesta(
            Datos datos,
            RespuestaRepositorio respuestas,
            SupresionRepositorio supresiones,
            Consumidos consumidos,
            Outbox outbox,
            Reloj reloj,
            String secretoWebhook) {
        this.datos = datos;
        this.respuestas = respuestas;
        this.supresiones = supresiones;
        this.consumidos = consumidos;
        this.outbox = outbox;
        this.reloj = reloj;
        this.secretoWebhook = secretoWebhook;
    }

    @Transactional
    public SalidaRespuesta ejecutar(EntradaRespuesta entrada, ContextoSesion ctx) {
        // AP-CU82-01. La firma se verifica ANTES de tocar la base: una carga util sin
        // firma valida no merece ni una consulta.
        if (!FirmaDeWebhook.verifica(entrada.cargaUtil(), entrada.firma(), secretoWebhook)) {
            throw new ErrorDeNegocio(CodigoError.de(82, 1), "La firma del webhook no valida.");
        }

        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        Canal canal = Canal.valueOf(entrada.canal());

        return datos.conContexto(ctx, dsl -> {
            // AP-CU82-03. El id del mensaje del proveedor es la clave: el mismo webhook
            // reintentado no vuelve a ejecutar la accion.
            UUID idMensaje = UUID.nameUUIDFromBytes(
                    entrada.claveIdempotencia().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!consumidos.registrar(dsl, idMensaje, "proveedor-mensajeria")) {
                return new SalidaRespuesta(null, Intencion.DESCONOCIDA.name(), "TICKET_ABIERTO", null);
            }

            var remitente = respuestas.porIdentificador(dsl, entrada.remitente(), canal.name());
            if (remitente.isEmpty()) {
                // AP-CU82-02: se registra el intento y se responde lo mismo que a un
                // duplicado. Nada en la respuesta distingue un caso del otro.
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "notificaciones.remitente_desconocido",
                                "respuesta_entrante",
                                idMensaje,
                                Map.of("canal", canal.name()),
                                UUID.fromString(ctx.traza().id())));
                return new SalidaRespuesta(null, Intencion.DESCONOCIDA.name(), "TICKET_ABIERTO", null);
            }

            Intencion intencion = IntencionEntrante.clasificar(entrada.contenido());
            String accion = IntencionEntrante.accionPara(intencion);

            UUID respuestaId = respuestas.registrar(
                    dsl,
                    remitente.get().canalVinculadoId(),
                    entrada.notificacionRelacionadaId(),
                    entrada.contenido(),
                    IntencionEntrante.comoLoGuardaLaBase(intencion),
                    accion,
                    ahora,
                    ahora);

            if (intencion == Intencion.BAJA) {
                // La baja se aplica ya, en esta transaccion. Diferirla a un evento
                // dejaria una ventana en la que se le sigue escribiendo a quien acaba
                // de pedir que no lo hagan.
                supresiones.suprimir(dsl, entrada.remitente(), canal, "SOLICITUD_LEGAL", "TODAS", false, ahora);
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "notificaciones.respuesta_procesada",
                            "respuesta_entrante",
                            respuestaId,
                            Map.of(
                                    "usuarioId", remitente.get().usuarioId().toString(),
                                    "intencion", intencion.name(),
                                    "accion", accion),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaRespuesta(respuestaId, intencion.name(), accion, null);
        });
    }

    public record EntradaRespuesta(
            String canal,
            String remitente,
            String firma,
            String cargaUtil,
            String contenido,
            String claveIdempotencia,
            Optional<UUID> notificacionRelacionadaId) {}

    public record SalidaRespuesta(UUID respuestaId, String intencion, String accion, UUID referenciaId) {}
}
