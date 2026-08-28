package bo.aportaya.organizador.aplicacion;

import bo.aportaya.organizador.dominio.DebidoProceso;
import bo.aportaya.organizador.infraestructura.DesempenoRepositorio;
import bo.aportaya.organizador.infraestructura.OrganizadorRepositorio;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-93 · Sancionar al organizador y resolver su apelacion.
 *
 * <p>Dos reglas hacen que esto sea un procedimiento y no un castigo: **una apelacion
 * por sancion**, y **la resuelve quien no la aplico** (R-ORG-05). Sin la segunda,
 * apelar es pedirle a la misma persona que se desdiga.
 *
 * <p>El plazo para apelar se **guarda** al sancionar (R-CON-01, invariante 8). Un
 * plazo que se recalcula al mirarlo es un plazo que el sancionado no puede planificar,
 * y que se le puede acortar despues sin que se entere.
 */
@Service
public class CU93SancionarOrganizador {

    private final Datos datos;
    private final DesempenoRepositorio desempenos;
    private final OrganizadorRepositorio organizadores;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Duration plazoParaApelar;

    public CU93SancionarOrganizador(
            Datos datos,
            DesempenoRepositorio desempenos,
            OrganizadorRepositorio organizadores,
            Outbox outbox,
            Reloj reloj,
            Duration plazoParaApelar) {
        this.datos = datos;
        this.desempenos = desempenos;
        this.organizadores = organizadores;
        this.outbox = outbox;
        this.reloj = reloj;
        this.plazoParaApelar = plazoParaApelar;
    }

    @Transactional
    public SalidaSancion sancionar(EntradaSancion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var organizador = organizadores
                    .bloquear(dsl, entrada.organizadorId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(93, 1), "Ese organizador no existe."));

            // AP-CU93-05: sin motivo escrito no hay sancion. «Bajo desempeno» a secas
            // no se puede apelar porque no dice contra que defenderse.
            if (entrada.motivo() == null || entrada.motivo().isBlank()) {
                throw new ErrorDeNegocio(CodigoError.de(93, 5), "Una sancion sin motivo escrito no se puede apelar.");
            }
            // AP-CU93-06: quien sanciona no puede ser el sancionado.
            if (organizador.usuarioId().equals(ctx.usuarioId())) {
                throw new ErrorDeNegocio(CodigoError.de(93, 6), "Nadie se sanciona a si mismo.");
            }

            OffsetDateTime hasta = entrada.duracion().map(ahora::plus).orElse(null);
            UUID sancionId = desempenos.sancionar(
                    dsl,
                    organizador.id(),
                    entrada.evaluacionId().orElse(null),
                    entrada.tipo(),
                    entrada.motivo(),
                    ahora,
                    hasta,
                    ctx.usuarioId());

            // Una suspension o inhabilitacion cambia el estado: mientras esta vigente
            // no administra grupos nuevos. Una advertencia no lo hace — es un aviso.
            String estadoNuevo =
                    switch (entrada.tipo()) {
                        case "SUSPENSION" -> "SUSPENDIDO";
                        case "INHABILITACION" -> "DESHABILITADO";
                        case "REDUCCION_LIMITE" -> "LIMITADO";
                        default -> null;
                    };
            if (estadoNuevo != null) {
                organizadores.cambiarEstado(dsl, organizador.id(), estadoNuevo, organizador.version(), ahora);
            }

            var proceso = new DebidoProceso(ahora, plazoParaApelar);
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.sancionado",
                            "sancion_organizador",
                            sancionId,
                            Map.of(
                                    "organizadorId", organizador.id().toString(),
                                    "tipo", entrada.tipo(),
                                    "motivo", entrada.motivo(),
                                    "puedeApelarHasta",
                                            proceso.limiteParaApelar().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaSancion(sancionId, entrada.tipo(), estadoNuevo, proceso.limiteParaApelar());
        });
    }

    /**
     * Presenta la apelacion.
     *
     * <p><b>Hueco declarado.</b> La fila de {@code apelacion_sancion_org} <b>no se
     * puede escribir todavia</b>: {@code ck_apelacion_org_resuelta} exige que el estado
     * sea {@code PENDIENTE} o que {@code resuelta_en}, {@code resuelta_por} y
     * {@code resolucion} esten los tres presentes — y {@code PENDIENTE} no esta entre
     * los estados que admite {@code ck_apelacion_sancion_org_estado}. Las dos
     * restricciones juntas hacen imposible registrar una apelacion abierta.
     *
     * <p>Manda la DDL. Lo que si se registra, y es lo que sostiene el debido proceso:
     * la sancion pasa a {@code APELADA} —con lo que no se puede dar por cumplida— y el
     * argumento viaja en el evento. La fila de la apelacion se escribe entera al
     * resolverla. Ver H-7 en {@code planes/informes/carril-2E.md}.
     */
    @Transactional
    public SalidaApelacion apelar(EntradaApelacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var sancion = desempenos
                    .verSancion(dsl, entrada.sancionId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(93, 1), "Esa sancion no existe."));
            var organizador = organizadores.ver(dsl, sancion.organizadorId()).orElseThrow();

            // AP-CU93-04: apela el sancionado, no un tercero.
            if (!organizador.usuarioId().equals(ctx.usuarioId())) {
                throw new ErrorDeNegocio(CodigoError.de(93, 4), "La sancion la apela quien la recibio.");
            }
            // AP-CU93-02: el plazo se guardo al sancionar y no se recalcula.
            var proceso = new DebidoProceso(sancion.vigenteDesde(), plazoParaApelar);
            if (!proceso.admiteApelacionEn(ahora)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(93, 2), "El plazo para apelar vencio el " + proceso.limiteParaApelar() + ".");
            }
            var previa = desempenos.apelacionDe(dsl, sancion.id());
            if (previa.isPresent()) {
                return new SalidaApelacion(previa.get(), "RESUELTA", false);
            }
            // La sancion APELADA es la marca de que hay una apelacion abierta: no se
            // puede dar por cumplida mientras lo este.
            if (!desempenos.cambiarEstadoDeSancion(dsl, sancion.id(), "VIGENTE", "APELADA")) {
                return new SalidaApelacion(null, sancion.estado(), false);
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.sancion_apelada",
                            "sancion_organizador",
                            sancion.id(),
                            Map.of(
                                    "sancionId", sancion.id().toString(),
                                    "argumento", entrada.argumento(),
                                    "evidencias", entrada.evidenciasJson(),
                                    "presentadaEn", ahora.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaApelacion(null, "APELADA", true);
        });
    }

    /** La resuelve quien no aplico la sancion. Es la regla, no un formalismo. */
    @Transactional
    public SalidaResolucion resolver(EntradaResolucion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var sancion = desempenos
                    .verSancion(dsl, entrada.sancionId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(93, 1), "Esa sancion no existe."));
            if (!"APELADA".equals(sancion.estado())) {
                throw new ErrorDeNegocio(CodigoError.de(93, 1), "Esa sancion no tiene apelacion abierta que resolver.");
            }
            if (desempenos.apelacionDe(dsl, sancion.id()).isPresent()) {
                throw new ErrorDeNegocio(CodigoError.de(93, 1), "Esa apelacion ya fue resuelta.");
            }

            // AP-CU93-03 · R-ORG-05.
            DebidoProceso.exigirRevisorDistinto(sancion.aplicadaPor(), ctx.usuarioId());

            String estadoDeLaApelacion = entrada.aceptada() ? "ACEPTADA" : "RECHAZADA";
            // La fila se escribe ENTERA aca: la base no admite una a medias.
            UUID apelacionId = desempenos.registrarApelacionResuelta(
                    dsl,
                    sancion.id(),
                    entrada.argumento(),
                    entrada.evidenciasJson(),
                    estadoDeLaApelacion,
                    ctx.usuarioId(),
                    entrada.resolucion(),
                    ahora);

            // Aceptada la apelacion, la sancion se REVOCA y el organizador vuelve. Si
            // se rechaza, la sancion sigue vigente y el plazo ya corrio.
            String estadoDeLaSancion = entrada.aceptada() ? "REVOCADA" : "VIGENTE";
            desempenos.cambiarEstadoDeSancion(dsl, sancion.id(), "APELADA", estadoDeLaSancion);

            if (entrada.aceptada()) {
                var organizador =
                        organizadores.bloquear(dsl, sancion.organizadorId()).orElseThrow();
                organizadores.cambiarEstado(dsl, organizador.id(), "HABILITADO", organizador.version(), ahora);
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.apelacion_resuelta",
                            "apelacion_sancion_org",
                            apelacionId,
                            Map.of(
                                    "sancionId", sancion.id().toString(),
                                    "resultado", estadoDeLaApelacion,
                                    "estadoDeLaSancion", estadoDeLaSancion),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaResolucion(apelacionId, estadoDeLaApelacion, estadoDeLaSancion);
        });
    }

    public record EntradaSancion(
            UUID organizadorId, Optional<UUID> evaluacionId, String tipo, String motivo, Optional<Duration> duracion) {}

    public record SalidaSancion(
            UUID sancionId, String tipo, String estadoDelOrganizador, OffsetDateTime puedeApelarHasta) {}

    public record EntradaApelacion(UUID sancionId, String argumento, String evidenciasJson) {}

    public record SalidaApelacion(UUID apelacionId, String estado, boolean esNueva) {}

    public record EntradaResolucion(
            UUID sancionId, boolean aceptada, String argumento, String evidenciasJson, String resolucion) {}

    public record SalidaResolucion(UUID apelacionId, String estadoDeLaApelacion, String estadoDeLaSancion) {}
}
