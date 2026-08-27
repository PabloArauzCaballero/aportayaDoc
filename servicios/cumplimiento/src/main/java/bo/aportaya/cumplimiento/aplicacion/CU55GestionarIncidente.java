package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.PlazosDelIncidente;
import bo.aportaya.cumplimiento.infraestructura.IncidenteSeguridadRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeDominio;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
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
 * CU-55 · Gestionar un incidente de seguridad de la informacion.
 *
 * <p>Contener rapido, <b>reportar en plazo</b> y notificar a los titulares cuando hay
 * datos personales afectados. Tres relojes distintos corriendo en paralelo, y los tres
 * guardados.
 *
 * <p>Dos decisiones que este caso de uso toma y que deciden si el expediente sirve:
 *
 * <ul>
 *   <li><b>Sin responsable designado no se abre el incidente.</b> No es burocracia: sin
 *       designacion activa no hay a quien le corran los plazos ni quien firme ante el
 *       organismo, y el expediente que se abriria no tendria dueno.
 *   <li><b>Los datos personales afectados no los declara quien reporta: los decide el
 *       activo.</b> Quien detecta un incidente a las tres de la manana no esta en
 *       condiciones de determinar el alcance, y si la clasificacion del activo dice que
 *       hay datos personales, hay datos personales.
 * </ul>
 *
 * <p><b>Vive en `cumplimiento` y no en `auditoria`</b>, aunque su ficha diga
 * {@code openapi/auditoria.yaml}: las tablas que escribe estan en el esquema
 * {@code cumplimiento} y {@code svc_auditoria} no tiene {@code GRANT} sobre el
 * (invariante 11).
 */
@Service
public class CU55GestionarIncidente {

    private final Datos datos;
    private final IncidenteSeguridadRepositorio incidentes;
    private final Outbox outbox;
    private final Reloj reloj;
    private final PlazosDelIncidente plazos;

    public CU55GestionarIncidente(
            Datos datos,
            IncidenteSeguridadRepositorio incidentes,
            Outbox outbox,
            Reloj reloj,
            PlazosDelIncidente plazos) {
        this.datos = datos;
        this.incidentes = incidentes;
        this.outbox = outbox;
        this.reloj = reloj;
        this.plazos = plazos;
    }

    /** Abre el expediente y arranca los relojes. */
    @Transactional
    public SalidaIncidente registrar(EntradaIncidente entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // AP-CU55-01
            UUID responsable = incidentes
                    .responsableDeSeguridad(dsl, reloj.hoy())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(55, 1),
                            "No hay responsable de seguridad de la informacion designado y activo:"
                                    + " sin el no hay a quien le corran los plazos."));

            Optional<IncidenteSeguridadRepositorio.Activo> activo =
                    entrada.activoInformacionId().flatMap(id -> incidentes.activo(dsl, id));

            // El activo manda sobre lo declarado. Si el inventario dice que ese activo
            // contiene datos personales, los contiene — aunque quien reporta a las tres
            // de la manana haya marcado que no.
            boolean datosPersonales = activo.map(IncidenteSeguridadRepositorio.Activo::contieneDatosPersonales)
                    .orElse(entrada.datosPersonalesAfectados());

            OffsetDateTime plazoReporte;
            try {
                plazoReporte = plazos.plazoDeReporte(entrada.severidad(), entrada.detectadoEn());
            } catch (ErrorDeDominio sinPlazo) {
                throw new ErrorDeNegocio(CodigoError.de(55, 2), sinPlazo.getMessage());
            }

            UUID incidenteId = incidentes.abrir(
                    dsl,
                    codigoDe(entrada),
                    entrada.activoInformacionId().orElse(null),
                    responsable,
                    entrada.tipo(),
                    entrada.severidad(),
                    datosPersonales,
                    entrada.usuariosAfectados(),
                    entrada.detectadoEn(),
                    plazoReporte);

            entrada.eventoRiesgoId().ifPresent(evento -> incidentes.enlazarConEventoDeRiesgo(dsl, incidenteId, evento));

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "seguridad.incidente_detectado",
                            "incidente_seguridad",
                            incidenteId,
                            Map.of(
                                    "severidad", entrada.severidad(),
                                    "tipo", entrada.tipo(),
                                    "datosPersonalesAfectados", String.valueOf(datosPersonales)),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaIncidente(
                    incidenteId,
                    codigoDe(entrada),
                    plazoReporte,
                    datosPersonales,
                    datosPersonales ? Optional.of(plazos.plazoDeNotificacion(entrada.detectadoEn())) : Optional.empty(),
                    // El contrato del tercero se resuelve POR EL ACTIVO. `incidente_seguridad`
                    // no tiene columna propia para el, y agregarsela duplicaria un dato que ya
                    // vive —y se mantiene— en el inventario de activos. Si el activo pasa por
                    // un proveedor, el incidente queda enlazado a su contrato por ese camino.
                    activo.flatMap(IncidenteSeguridadRepositorio.Activo::contratoTerceroId),
                    ahora);
        });
    }

    /**
     * Deja constancia de que se reporto al organismo.
     *
     * <p>Se reporta <b>igual aunque el plazo haya vencido</b>: llegar tarde es una
     * observacion, no reportar es un incumplimiento. Lo que no se hace es disimularlo —
     * el vencimiento queda visible y el control diario abre el hallazgo.
     */
    @Transactional
    public void reportarAlOrganismo(UUID incidenteId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        datos.conContexto(ctx, dsl -> {
            var estado = incidentes
                    .estado(dsl, incidenteId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(55, 2), "Ese incidente no existe."));

            incidentes.marcarReportado(dsl, incidenteId, ahora);
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "seguridad.incidente_reportado",
                            "incidente_seguridad",
                            incidenteId,
                            Map.of(
                                    "codigo", estado.codigo(),
                                    "enPlazo", String.valueOf(!ahora.isAfter(estado.plazoReporte()))),
                            UUID.fromString(ctx.traza().id())));
            return null;
        });
    }

    /**
     * Deja constancia de la notificacion a los titulares.
     *
     * <p>Se notifica con lo que se sabe, sin esperar al numero final (flujo alternativo
     * 5a): un aviso tardio y exacto le sirve menos a la persona afectada que uno a
     * tiempo y aproximado.
     */
    @Transactional
    public void notificarTitulares(UUID incidenteId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        datos.conContexto(ctx, dsl -> {
            var estado = incidentes
                    .estado(dsl, incidenteId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(55, 3), "Ese incidente no existe."));

            // AP-CU55-03. Notificar un incidente que no afecto datos personales
            // asustaria a gente sin motivo y gastaria la credibilidad del canal para
            // cuando de verdad haga falta.
            if (!estado.datosPersonalesAfectados()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(55, 3), "Ese incidente no afecto datos personales: no hay a quien notificar.");
            }

            incidentes.marcarNotificado(dsl, incidenteId, ahora);
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "seguridad.titulares_notificados",
                            "incidente_seguridad",
                            incidenteId,
                            Map.of("codigo", estado.codigo()),
                            UUID.fromString(ctx.traza().id())));
            return null;
        });
    }

    /**
     * El codigo del incidente, derivado del hecho.
     *
     * <p>Dos avisos del mismo incidente —el monitoreo y la persona que lo vio— producen
     * el mismo codigo y el segundo choca contra el UNIQUE, en vez de abrir dos
     * expedientes con dos relojes distintos para el mismo hecho.
     */
    private static String codigoDe(EntradaIncidente entrada) {
        String hecho = String.join(
                SEPARADOR,
                entrada.detectadoEn().toString(),
                entrada.tipo(),
                entrada.activoInformacionId().map(UUID::toString).orElse(""));
        return "INC-%s-%s"
                .formatted(entrada.detectadoEn().toLocalDate().toString().replace("-", ""), huella(hecho));
    }

    /** Un caracter que no puede aparecer dentro de ningun campo del hecho. */
    private static final String SEPARADOR = String.valueOf((char) 0x1f);

    private static String huella(String hecho) {
        try {
            byte[] digerido = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(hecho.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                hex.append("%02X".formatted(digerido[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("esta JVM no trae SHA-256", imposible);
        }
    }

    /**
     * @param datosPersonalesAfectados lo que declara quien reporta. <b>Lo puede
     *     sobrescribir el activo</b>: la clasificacion del inventario sabe mas que la
     *     persona que esta apagando el incendio.
     */
    public record EntradaIncidente(
            String tipo,
            String severidad,
            Optional<UUID> activoInformacionId,
            boolean datosPersonalesAfectados,
            int usuariosAfectados,
            OffsetDateTime detectadoEn,
            Optional<UUID> eventoRiesgoId) {}

    public record SalidaIncidente(
            UUID incidenteId,
            String codigo,
            OffsetDateTime plazoReporte,
            boolean requiereNotificarTitulares,
            Optional<OffsetDateTime> plazoNotificacion,
            Optional<UUID> contratoTerceroId,
            OffsetDateTime registradoEn) {}
}
