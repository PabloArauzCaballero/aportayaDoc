package bo.aportaya.notificaciones.aplicacion;

import bo.aportaya.notificaciones.dominio.Canal;
import bo.aportaya.notificaciones.dominio.PlantillaRenderizada;
import bo.aportaya.notificaciones.dominio.VentanaDeEnvio;
import bo.aportaya.notificaciones.infraestructura.NotificacionRepositorio;
import bo.aportaya.notificaciones.infraestructura.PlantillaRepositorio;
import bo.aportaya.notificaciones.infraestructura.SupresionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-80 · Despachar una notificacion.
 *
 * <p>**Sin endpoint**: es un consumidor idempotente del outbox. Que un aviso se pueda
 * disparar por API seria una puerta para mandar mensajes a nombre de la entidad sin
 * que ningun evento de dominio lo respalde.
 *
 * <p>Cuatro puertas antes de escribir, en este orden: canal encendido, destinatario no
 * suprimido, canal verificado, plantilla aprobada. El orden importa — comprobar la
 * plantilla primero gastaria una consulta para un mensaje que igual no iba a salir.
 */
@Service
public class CU80DespacharNotificacion {

    private static final String IDIOMA_POR_OMISION = "es";

    private final Datos datos;
    private final NotificacionRepositorio notificaciones;
    private final PlantillaRepositorio plantillas;
    private final SupresionRepositorio supresiones;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Set<Canal> canalesEncendidos;
    private final VentanaDeEnvio ventana;

    public CU80DespacharNotificacion(
            Datos datos,
            NotificacionRepositorio notificaciones,
            PlantillaRepositorio plantillas,
            SupresionRepositorio supresiones,
            Outbox outbox,
            Reloj reloj,
            Set<Canal> canalesEncendidos,
            VentanaDeEnvio ventana) {
        this.datos = datos;
        this.notificaciones = notificaciones;
        this.plantillas = plantillas;
        this.supresiones = supresiones;
        this.outbox = outbox;
        this.reloj = reloj;
        // Interseccion con el piso: la configuracion puede APAGAR un canal encendido,
        // nunca encender uno que el contrato dejo apagado. Es una de las dieciocho
        // prohibiciones, y se hace cumplir aca en vez de confiar en el archivo.
        this.canalesEncendidos = canalesEncendidos.stream()
                .filter(c -> !c.apagadoPorOmision())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.ventana = ventana;
    }

    @Transactional
    public SalidaDespacho ejecutar(EntradaDespacho entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        Canal canal = Canal.valueOf(entrada.canal());

        return datos.conContexto(ctx, dsl -> {
            var evento = notificaciones
                    .evento(dsl, entrada.eventoDominioId())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(80, 1), "Ese evento no esta registrado como notificable."));

            boolean obligatorio = entrada.esObligatorio() || evento.esObligatorio();

            if (!canalesEncendidos.contains(canal)) {
                return noSale(dsl, entrada, ctx, "El canal " + canal + " esta apagado en este entorno.");
            }

            var destino = notificaciones.canalVerificado(dsl, entrada.destinatarioId(), canal);
            if (destino.isEmpty()) {
                // AP-CU80-02. No es una falla del sistema: es que no tenemos por donde
                // hablarle. Se registra para que la app pueda pedir la verificacion.
                return noSale(dsl, entrada, ctx, "El " + canal + " del destinatario no esta verificado.");
            }

            // AP-CU80-03. La supresion NUNCA se fuerza, ni siquiera para un obligatorio:
            // el obligatorio sale por otro canal, no por el que la persona cerro.
            if (supresiones.estaSuprimido(dsl, destino.get().identificador(), canal, evento.categoria())
                    || supresiones.estaSuprimido(dsl, destino.get().identificador(), canal, "TODAS")) {
                return noSale(dsl, entrada, ctx, "El destinatario pidio no recibir esta categoria.");
            }

            // AP-CU80-01. Sin plantilla aprobada no se improvisa el texto.
            var version = plantillas
                    .vigentePara(dsl, entrada.plantillaCodigo(), canal, IDIOMA_POR_OMISION, ahora)
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(80, 1),
                            "No hay plantilla aprobada y vigente para " + entrada.plantillaCodigo() + " por " + canal
                                    + "."));

            String cuerpo;
            try {
                cuerpo = PlantillaRenderizada.render(version.cuerpo(), entrada.variables());
            } catch (PlantillaRenderizada.VariableFaltante faltante) {
                // Un {{monto}} sin resolver en el mensaje de una persona parece un
                // error del sistema justo cuando se le habla de su plata.
                throw new ErrorDeNegocio(CodigoError.de(80, 1), faltante.getMessage());
            }

            VentanaDeEnvio.Decision cuando = ventana.decidir(ahora, obligatorio);
            OffsetDateTime programada = cuando.ahora() ? ahora : cuando.reprogramadaPara();

            var registro = notificaciones.registrar(
                    dsl,
                    entrada.destinatarioId(),
                    entrada.eventoDominioId(),
                    evento.prioridad(),
                    "{}",
                    entrada.claveDeduplicacion(),
                    cuando.ahora() ? "EN_COLA" : "PROGRAMADA",
                    programada,
                    UUID.fromString(ctx.traza().id()),
                    ahora);

            // AP-CU80-04: otra replica ya lo tomo. No es falla, es concurrencia sana.
            if (!registro.esNueva()) {
                return new SalidaDespacho(registro.id(), null, "SUPRIMIDA", "Ese aviso ya estaba despachado.", null);
            }

            // La bandeja recibe SIEMPRE, aunque el push no llegue: un aviso que solo
            // existio como push se perdio si el telefono estaba apagado.
            notificaciones.guardarEnBandeja(
                    dsl,
                    entrada.destinatarioId(),
                    registro.id(),
                    version.asunto() == null ? entrada.plantillaCodigo() : version.asunto(),
                    cuerpo,
                    null);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            cuando.ahora()
                                    ? "notificaciones.notificacion_encolada"
                                    : "notificaciones.notificacion_reprogramada",
                            "notificacion",
                            registro.id(),
                            Map.of(
                                    "usuarioId", entrada.destinatarioId().toString(),
                                    "canal", canal.name(),
                                    "versionPlantillaId", version.id().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaDespacho(
                    registro.id(), null, cuando.ahora() ? "ENCOLADA" : "REPROGRAMADA", null, cuando.reprogramadaPara());
        });
    }

    /**
     * El aviso que no sale deja rastro en el outbox, **no** en {@code notificacion}.
     *
     * <p>Es la base la que decide: {@code tg_notificacion_supresion} rechaza el INSERT
     * de una notificacion para un destinatario suprimido. Insistir seria pelearse con
     * la regla; el rastro va donde si corresponde —un evento— y asi ademas queda uno
     * solo, con el mismo formato, para los tres motivos por los que un aviso no sale.
     */
    private SalidaDespacho noSale(org.jooq.DSLContext dsl, EntradaDespacho entrada, ContextoSesion ctx, String motivo) {

        outbox.emitir(
                dsl,
                new EventoDominio(
                        "notificaciones.notificacion_suprimida",
                        "notificacion",
                        entrada.eventoDominioId(),
                        Map.of(
                                "usuarioId", entrada.destinatarioId().toString(),
                                "canal", entrada.canal(),
                                "motivo", motivo),
                        UUID.fromString(ctx.traza().id())));
        return new SalidaDespacho(null, null, "SUPRIMIDA", motivo, null);
    }

    public record EntradaDespacho(
            UUID eventoDominioId,
            UUID destinatarioId,
            String canal,
            String plantillaCodigo,
            Map<String, String> variables,
            boolean esObligatorio,
            String claveDeduplicacion) {}

    public record SalidaDespacho(
            UUID notificacionId, UUID envioId, String estado, String motivoNoEnvio, OffsetDateTime reprogramadaPara) {}
}
