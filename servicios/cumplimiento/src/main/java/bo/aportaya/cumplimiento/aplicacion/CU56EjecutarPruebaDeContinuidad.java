package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.ObjetivosDeContinuidad;
import bo.aportaya.cumplimiento.infraestructura.ContinuidadRepositorio;
import bo.aportaya.cumplimiento.infraestructura.GobiernoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-56 · Ejecutar una prueba de continuidad.
 *
 * <p>**El resultado no lo elige quien ejecuto la prueba.** Sale de comparar el RTO y el
 * RPO obtenidos contra los comprometidos. Dejarlo a criterio convierte cada prueba en
 * EXITOSA y el plan de continuidad en un papel que nadie leyo dos veces.
 *
 * <p>Y una prueba que no alcanza los objetivos **abre plan de accion**. Registrar que se
 * tardo 95 minutos donde se prometieron 60 y no hacer nada es documentar el
 * incumplimiento sin corregirlo, que es peor que no medirlo.
 */
@Service
public class CU56EjecutarPruebaDeContinuidad {

    private final Datos datos;
    private final ContinuidadRepositorio continuidad;
    private final GobiernoRepositorio gobierno;
    private final Outbox outbox;
    private final Reloj reloj;

    /** Cuanto se da para cerrar el plan de accion de una prueba que fallo. Es politica. */
    private final int diasParaRegularizar;

    public CU56EjecutarPruebaDeContinuidad(
            Datos datos,
            ContinuidadRepositorio continuidad,
            GobiernoRepositorio gobierno,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.continuidad.dias-para-regularizar}") int diasParaRegularizar) {
        this.datos = datos;
        this.continuidad = continuidad;
        this.gobierno = gobierno;
        this.outbox = outbox;
        this.reloj = reloj;
        this.diasParaRegularizar = diasParaRegularizar;
    }

    @Transactional
    public SalidaPrueba registrar(EntradaPrueba entrada, ContextoSesion ctx) {
        LocalDate hoy = reloj.ahora().atOffset(ZoneOffset.UTC).toLocalDate();

        return datos.conContexto(ctx, dsl -> {
            // AP-CU56-03: un proceso critico sin plan no se prueba, se declara hallazgo.
            var plan = continuidad
                    .planPorId(dsl, entrada.planContinuidadId())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(56, 3), "Ese proceso critico no tiene plan de continuidad."));

            var resultado = ObjetivosDeContinuidad.evaluar(
                    plan.rtoMinutos(), plan.rpoMinutos(), entrada.rtoObtenido(), entrada.rpoObtenido());

            // AP-CU56-01 · ck_prueba_resultado: una prueba EXITOSA exige acta que la
            // reporte. «Salio bien» tiene que ser algo que alguien firmo.
            if ("EXITOSA".equals(resultado.resultado()) && entrada.actaComiteId() == null) {
                throw new ErrorDeNegocio(
                        CodigoError.de(56, 1), "Una prueba exitosa exige acta de comite que la reporte.");
            }

            UUID pruebaId = continuidad.registrarPrueba(
                    dsl,
                    plan.id(),
                    entrada.actaComiteId(),
                    entrada.ejecutadaPor(),
                    entrada.tipo(),
                    entrada.fecha(),
                    entrada.rtoObtenido(),
                    entrada.rpoObtenido(),
                    resultado.resultado(),
                    resultado.motivo(),
                    entrada.evidenciaUrl());

            UUID planAccionId = null;
            if (resultado.exigePlanDeAccion()) {
                // AP-CU56-02. El hallazgo nombra el proceso: un hallazgo que no dice
                // cual proceso fallo no se puede asignar a nadie.
                var hallazgo = gobierno.abrirHallazgo(
                        dsl,
                        "BCP-" + plan.procesoCritico(),
                        "AUTOEVALUACION",
                        "La prueba de continuidad de «" + plan.procesoCritico() + "» no alcanzo sus objetivos. "
                                + resultado.motivo(),
                        "ALTA",
                        plan.procesoCritico(),
                        hoy,
                        hoy.plusDays(diasParaRegularizar));
                planAccionId = gobierno.abrirPlanDeAccion(
                        dsl,
                        hallazgo.orElse(null),
                        plan.responsableId() != null ? plan.responsableId() : entrada.ejecutadaPor(),
                        "Cerrar la brecha entre los objetivos comprometidos y los obtenidos en la prueba del "
                                + entrada.fecha() + ".",
                        hoy.plusDays(diasParaRegularizar));
            } else {
                // Solo una prueba que salio bien corre la fecha de la proxima. Correrla
                // igual seria darse por probado sin haberlo estado.
                continuidad.moverProximaPrueba(
                        dsl,
                        plan.id(),
                        ObjetivosDeContinuidad.proximaPrueba(entrada.fecha(), plan.periodicidadMeses()));
            }

            var payload = new java.util.LinkedHashMap<String, String>();
            payload.put("procesoCritico", plan.procesoCritico());
            payload.put("resultado", resultado.resultado());
            payload.put("rtoComprometido", Integer.toString(plan.rtoMinutos()));
            payload.put("rtoObtenido", Integer.toString(entrada.rtoObtenido()));
            if (entrada.impactoEnClientes()) {
                // Una conmutacion real que afecto a clientes es un evento de riesgo
                // operativo con perdida cuantificada, no solo un ejercicio.
                payload.put("categoriaEvento", "FALLAS_SISTEMAS");
                payload.put("factorRiesgo", "TECNOLOGIA_INFORMACION");
                payload.put("perdidaBruta", entrada.perdidaCuantificada());
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.prueba_continuidad_registrada",
                            "prueba_continuidad",
                            pruebaId,
                            Map.copyOf(payload),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaPrueba(
                    pruebaId, entrada.rtoObtenido(), entrada.rpoObtenido(), resultado.resultado(), planAccionId);
        });
    }

    public record EntradaPrueba(
            UUID planContinuidadId,
            String tipo,
            LocalDate fecha,
            UUID ejecutadaPor,
            UUID actaComiteId,
            int rtoObtenido,
            int rpoObtenido,
            String evidenciaUrl,
            boolean impactoEnClientes,
            String perdidaCuantificada) {}

    public record SalidaPrueba(UUID pruebaId, int rtoObtenido, int rpoObtenido, String resultado, UUID planAccionId) {}
}
