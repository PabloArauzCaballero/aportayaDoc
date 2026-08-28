package bo.aportaya.organizador.aplicacion;

import bo.aportaya.organizador.dominio.AccionSensible;
import bo.aportaya.organizador.dominio.ClaveDeTarea;
import bo.aportaya.organizador.infraestructura.AutomatizacionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-96 · Programar y ejecutar una tarea automatizada.
 *
 * <p>**Una tarea por clave de idempotencia** (R-ORG-07). La clave es determinista —
 * regla, grupo y minuto programado— porque si dependiera del reloj de la corrida, cada
 * reintento del planificador generaria una tarea nueva y una regla de «aplicar mora»
 * cobraria el recargo tantas veces como se reintente.
 *
 * <p>Las acciones sensibles nacen en {@code REQUIERE_APROBACION}, no en
 * {@code PROGRAMADA}: la confirmacion humana tiene que ocurrir **antes** de que la
 * tarea sea elegible, no despues de que ya movio la plata.
 */
@Service
public class CU96EjecutarTarea {

    private final Datos datos;
    private final AutomatizacionRepositorio automatizaciones;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int intentosMaximos;

    public CU96EjecutarTarea(
            Datos datos,
            AutomatizacionRepositorio automatizaciones,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.automatizacion.intentos-maximos}") int intentosMaximos) {
        this.datos = datos;
        this.automatizaciones = automatizaciones;
        this.outbox = outbox;
        this.reloj = reloj;
        this.intentosMaximos = intentosMaximos;
    }

    @Transactional
    public SalidaProgramacion programar(EntradaProgramacion entrada, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var regla = automatizaciones
                    .verRegla(dsl, entrada.reglaId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(96, 2), "Esa regla no existe."));

            // AP-CU96-02: una regla apagada no programa nada. Que exista no significa
            // que este encendida, y programar desde una apagada es ejecutar algo que
            // alguien decidio no ejecutar.
            if (!regla.activa()) {
                throw new ErrorDeNegocio(CodigoError.de(96, 2), "La regla " + regla.codigo() + " esta inactiva.");
            }

            // R-ORG-07 · invariante 7: la clave se valida ANTES de escribir.
            var clave = ClaveDeTarea.de(regla.id(), entrada.grupoId(), entrada.programadaPara());
            var repetida = automatizaciones.porClave(dsl, clave.valor());
            if (repetida.isPresent()) {
                var previa = repetida.get();
                return new SalidaProgramacion(previa.id(), previa.estado(), clave.valor(), false);
            }

            // AP-CU96-03 · R-ORG-06: lo sensible espera a que una persona confirme.
            String estadoInicial =
                    AccionSensible.exigeConfirmacion(regla.accion()) ? "REQUIERE_APROBACION" : "PROGRAMADA";

            UUID tareaId = automatizaciones.programar(
                    dsl,
                    regla.id(),
                    entrada.grupoId(),
                    regla.accion(),
                    entrada.programadaPara(),
                    estadoInicial,
                    clave.valor());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.tarea_programada",
                            "tarea_automatizada",
                            tareaId,
                            Map.of(
                                    "reglaCodigo", regla.codigo(),
                                    "grupoId", entrada.grupoId().toString(),
                                    "estado", estadoInicial,
                                    "programadaPara", entrada.programadaPara().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaProgramacion(tareaId, estadoInicial, clave.valor(), true);
        });
    }

    /** La confirmacion humana que R-ORG-06 exige. La da una persona, no el planificador. */
    @Transactional
    public SalidaProgramacion aprobar(UUID tareaId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var tarea = automatizaciones
                    .bloquear(dsl, tareaId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(96, 1), "Esa tarea no existe."));

            if (!automatizaciones.cambiarEstadoDeTarea(
                    dsl, tareaId, List.of("REQUIERE_APROBACION"), "PROGRAMADA", tarea.intentos())) {
                throw new ErrorDeNegocio(CodigoError.de(96, 3), "Esa tarea no estaba esperando aprobacion.");
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.tarea_aprobada",
                            "tarea_automatizada",
                            tareaId,
                            Map.of("aprobadaPor", ctx.usuarioId().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaProgramacion(tareaId, "PROGRAMADA", tarea.claveIdempotencia(), false);
        });
    }

    /**
     * Anota el resultado de una ejecucion.
     *
     * <p>La accion real la ejecuta el servicio dueno del efecto —quien mueve la plata,
     * quien manda el aviso—; aca se registra que paso. Ejecutar la accion desde este
     * servicio seria escribir esquemas ajenos (invariante 11).
     */
    @Transactional
    public SalidaEjecucion anotarEjecucion(EntradaEjecucion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var tarea = automatizaciones
                    .bloquear(dsl, entrada.tareaId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(96, 1), "Esa tarea no existe."));

            // AP-CU96-04: una tarea que espera aprobacion NO se ejecuta. Es el agujero
            // que R-ORG-06 cierra: la confirmacion tiene que ser previa al efecto.
            if ("REQUIERE_APROBACION".equals(tarea.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(96, 4), "Esa tarea espera confirmacion humana: no se ejecuta sola.");
            }
            // AP-CU96-05: una tarea ya completada no se vuelve a ejecutar.
            if ("COMPLETADA".equals(tarea.estado()) || "CANCELADA".equals(tarea.estado())) {
                return new SalidaEjecucion(null, tarea.estado(), tarea.intentos(), false);
            }

            UUID ejecucionId = automatizaciones.registrarEjecucion(
                    dsl,
                    tarea.id(),
                    entrada.iniciada(),
                    ahora,
                    entrada.resultado(),
                    entrada.registrosAfectados(),
                    entrada.detalleJson(),
                    entrada.mensajeError());

            int intentos = tarea.intentos() + 1;
            String estadoNuevo =
                    switch (entrada.resultado()) {
                        case "EXITO" -> "COMPLETADA";
                        // Una ejecucion PARCIAL no se da por completa: dejarla asi esconderia
                        // los registros que quedaron sin tocar.
                        case "PARCIAL" -> intentos >= intentosMaximos ? "FALLIDA" : "PROGRAMADA";
                        default -> intentos >= intentosMaximos ? "FALLIDA" : "PROGRAMADA";
                    };
            automatizaciones.cambiarEstadoDeTarea(
                    dsl, tarea.id(), List.of("PROGRAMADA", "EN_EJECUCION"), estadoNuevo, intentos);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "EXITO".equals(entrada.resultado())
                                    ? "organizador.tarea_completada"
                                    : "organizador.tarea_fallida",
                            "tarea_automatizada",
                            tarea.id(),
                            Map.of(
                                    "resultado", entrada.resultado(),
                                    "registrosAfectados", Integer.toString(entrada.registrosAfectados()),
                                    "intentos", Integer.toString(intentos),
                                    "estado", estadoNuevo),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaEjecucion(ejecucionId, estadoNuevo, intentos, true);
        });
    }

    public record EntradaProgramacion(UUID reglaId, UUID grupoId, OffsetDateTime programadaPara) {}

    public record SalidaProgramacion(UUID tareaId, String estado, String claveIdempotencia, boolean esNueva) {}

    public record EntradaEjecucion(
            UUID tareaId,
            OffsetDateTime iniciada,
            String resultado,
            int registrosAfectados,
            String detalleJson,
            String mensajeError) {}

    public record SalidaEjecucion(UUID ejecucionId, String estadoDeLaTarea, int intentos, boolean esNueva) {}
}
