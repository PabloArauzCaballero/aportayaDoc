package bo.aportaya.auditoria.aplicacion;

import bo.aportaya.auditoria.dominio.RetencionLegal;
import bo.aportaya.auditoria.dominio.TipoDeSolicitud;
import bo.aportaya.auditoria.infraestructura.AnonimizacionRepositorio;
import bo.aportaya.auditoria.infraestructura.PoliticaRetencionRepositorio;
import bo.aportaya.auditoria.infraestructura.SolicitudDatosRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CalendarioHabil;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.PlazoHabil;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-07 · Ejercer derechos sobre datos personales.
 *
 * <p>Atender acceso, rectificacion, oposicion, portabilidad y cancelacion **sin romper
 * la obligacion de conservar** informacion financiera por diez anos.
 *
 * <p>Es la tension entera del caso de uso, y no se resuelve eligiendo un lado:
 * responder «no se puede borrar nada» seria mentirle al titular, y borrar todo seria
 * un incumplimiento. Lo que se hace es repartir —se borra lo que vencio, se
 * seudonimiza lo que la ley obliga a guardar— y **decirle al titular exactamente que
 * quedo y con que base legal**.
 *
 * <p>El plazo legal se calcula al abrir y **se guarda** (invariante 8). Recalcularlo
 * al consultar lo haria moverse solo: bastaria que alguien declare un feriado despues
 * para que el vencimiento cambie sin que nadie lo decida.
 */
@Service
public class CU07EjercerDerechos {

    private final Datos datos;
    private final SolicitudDatosRepositorio solicitudes;
    private final PoliticaRetencionRepositorio politicas;
    private final AnonimizacionRepositorio anonimizaciones;
    private final Outbox outbox;
    private final Reloj reloj;
    private final CalendarioHabil calendario;
    private final int diasDePlazo;

    public CU07EjercerDerechos(
            Datos datos,
            SolicitudDatosRepositorio solicitudes,
            PoliticaRetencionRepositorio politicas,
            AnonimizacionRepositorio anonimizaciones,
            Outbox outbox,
            Reloj reloj,
            CalendarioHabil calendario,
            // El plazo de respuesta es normativo y cambia con la norma: va en
            // configuracion, donde se ve y se audita, no horneado (invariante 10).
            @Value("${auditoria.derechos.dias-habiles-de-respuesta:15}") int diasDePlazo) {
        this.datos = datos;
        this.solicitudes = solicitudes;
        this.politicas = politicas;
        this.anonimizaciones = anonimizaciones;
        this.outbox = outbox;
        this.reloj = reloj;
        this.calendario = calendario;
        this.diasDePlazo = diasDePlazo;
    }

    @Transactional
    public SalidaDerechos ejecutar(EntradaDerechos entrada, ContextoSesion ctx) {
        TipoDeSolicitud tipo = TipoDeSolicitud.de(entrada.tipo());
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        LocalDate hoy = ahora.toLocalDate();

        // El vencimiento se calcula UNA vez, en dias habiles, y se persiste.
        OffsetDateTime limite = PlazoHabil.sumar(hoy, diasDePlazo, calendario)
                .atTime(LocalTime.MAX)
                .atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // AP-CU07-04: dos expedientes abiertos del mismo derecho se pisan, y el
            // segundo reinicia un plazo que ya estaba corriendo.
            if (solicitudes.tieneAbierta(dsl, entrada.usuarioId(), tipo.name())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(7, 4),
                        "Ya hay una solicitud de " + tipo + " abierta para este titular: se atiende esa.");
            }

            UUID solicitudId =
                    solicitudes.abrir(dsl, entrada.usuarioId(), tipo.name(), entrada.descripcion(), limite, ahora);

            if (!tipo.borraDatos()) {
                // Acceso, rectificacion, oposicion y portabilidad no evaluan retencion:
                // no borran nada. Quedan abiertas para que las atienda una persona.
                outbox.emitir(
                        dsl,
                        evento("auditoria.derecho_solicitado", solicitudId, entrada.usuarioId(), tipo.name(), ctx));
                return new SalidaDerechos(solicitudId, limite, "EN_PROCESO", List.of(), Optional.empty(), null);
            }

            RetencionLegal.Desenlace desenlace =
                    RetencionLegal.resolver(politicas.vigentes(dsl, hoy), entrada.ultimaActividad(), hoy);

            UUID procesoId = anonimizaciones.planificar(
                    dsl,
                    entrada.usuarioId(),
                    solicitudId,
                    desenlace.estrategia().name(),
                    desenlace.borrables(),
                    desenlace.retenidasPorLey());

            // PARCIAL y no ATENDIDA cuando queda algo bajo retencion: decirle al titular
            // que se atendio entera una solicitud que no se pudo cumplir entera es la
            // forma mas rapida de que el reclamo vuelva con la razon del otro lado.
            String estado = desenlace.retenidasPorLey().isEmpty() ? "ATENDIDA" : "PARCIAL";
            solicitudes.cerrar(dsl, solicitudId, estado, respuestaPara(desenlace), ahora);

            outbox.emitir(
                    dsl,
                    evento("auditoria.anonimizacion_planificada", procesoId, entrada.usuarioId(), tipo.name(), ctx));

            return new SalidaDerechos(
                    solicitudId,
                    limite,
                    estado,
                    desenlace.retenidasPorLey(),
                    Optional.of(procesoId),
                    desenlace.estrategia().name());
        });
    }

    /** La respuesta al titular: qué se borró, qué quedó y con qué base legal. */
    private static String respuestaPara(RetencionLegal.Desenlace desenlace) {
        if (desenlace.retenidasPorLey().isEmpty()) {
            return "Se eliminaron todos sus datos: ninguna retencion legal seguia vigente.";
        }
        return "Se eliminó lo que ya no está bajo retención y se seudonimizó el resto. "
                + "Sigue conservado por obligación legal: " + String.join("; ", desenlace.retenidasPorLey())
                + ". Esos registros ya no lo identifican.";
    }

    private static EventoDominio evento(
            String tipo, UUID agregadoId, UUID usuarioId, String derecho, ContextoSesion ctx) {
        return new EventoDominio(
                tipo,
                "solicitud_datos_personales",
                agregadoId,
                Map.of("usuarioId", usuarioId.toString(), "derecho", derecho),
                UUID.fromString(ctx.traza().id()));
    }

    /**
     * @param ultimaActividad desde cuando corre la retencion. Lo aporta quien atiende
     *     porque la ultima operacion del titular vive en `nucleo-financiero` y este
     *     servicio no puede consultarla (invariante 11). **Hueco declarado**: cuando el
     *     contrato de `2A` exponga la fecha, deja de ser un parametro.
     */
    public record EntradaDerechos(UUID usuarioId, String tipo, String descripcion, LocalDate ultimaActividad) {}

    public record SalidaDerechos(
            UUID solicitudId,
            OffsetDateTime fechaLimiteLegal,
            String estado,
            List<String> datosRetenidosPorLey,
            Optional<UUID> procesoAnonimizacionId,
            String estrategia) {}
}
