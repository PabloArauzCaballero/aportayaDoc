package bo.aportaya.organizador.aplicacion;

import bo.aportaya.organizador.dominio.NivelDeOrganizador;
import bo.aportaya.organizador.dominio.PuntajeDeDesempeno;
import bo.aportaya.organizador.infraestructura.DesempenoRepositorio;
import bo.aportaya.organizador.infraestructura.OrganizadorRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-92 · Evaluar el desempeno del organizador.
 *
 * <p>Una evaluacion por organizador y periodo (R-ORG-04). Dos del mismo mes permiten
 * elegir la que mas convenga, y entonces la evaluacion deja de significar algo.
 *
 * <p>La evaluacion **sugiere** un nivel; no lo aplica sola. Un ascenso sube el limite
 * de plata ajena que esa persona puede tener en curso, y eso no lo decide una formula
 * sin que nadie mire. Bajar si es automatico: cuando algo va mal, esperar no mejora
 * nada.
 */
@Service
public class CU92EvaluarDesempeno {

    private final Datos datos;
    private final DesempenoRepositorio desempenos;
    private final OrganizadorRepositorio organizadores;
    private final Outbox outbox;
    private final Reloj reloj;
    private final BigDecimal umbralDeAscenso;
    private final BigDecimal umbralDeDescenso;

    public CU92EvaluarDesempeno(
            Datos datos,
            DesempenoRepositorio desempenos,
            OrganizadorRepositorio organizadores,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.organizador.umbral-de-ascenso}") BigDecimal umbralDeAscenso,
            @Value("${aportaya.organizador.umbral-de-descenso}") BigDecimal umbralDeDescenso) {
        this.datos = datos;
        this.desempenos = desempenos;
        this.organizadores = organizadores;
        this.outbox = outbox;
        this.reloj = reloj;
        this.umbralDeAscenso = umbralDeAscenso;
        this.umbralDeDescenso = umbralDeDescenso;
    }

    @Transactional
    public SalidaEvaluacion evaluar(EntradaEvaluacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var organizador = organizadores
                    .bloquear(dsl, entrada.organizadorId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(92, 1), "Ese organizador no existe."));

            // AP-CU92-01 · R-ORG-04: una evaluacion por organizador y periodo.
            var previa = desempenos.evaluacionDe(dsl, organizador.id(), entrada.periodo());
            if (previa.isPresent()) {
                return new SalidaEvaluacion(previa.get(), organizador.nivel(), organizador.nivel(), null, false);
            }
            // AP-CU92-02: sin metricas no hay evaluacion. Poner cero seria decir que lo
            // hizo pesimo, cuando lo cierto es que no se sabe.
            if (entrada.metricas().isEmpty()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(92, 2), "No hay metricas del periodo " + entrada.periodo() + ".");
            }

            var puntaje = PuntajeDeDesempeno.calcular(entrada.metricas());
            var nivelActual = NivelDeOrganizador.exigir(organizador.nivel());
            var nivelSugerido = sugerir(nivelActual, puntaje.puntajeGlobal());

            UUID evaluacionId = desempenos.registrarEvaluacion(
                    dsl,
                    organizador.id(),
                    entrada.periodo(),
                    entrada.morosidad(),
                    entrada.tasaFinalizacion(),
                    entrada.satisfaccion(),
                    entrada.tiempoRespuestaHoras(),
                    entrada.incidenciasAbiertas(),
                    entrada.coberturasConsumidas(),
                    puntaje.puntajeGlobal(),
                    nivelSugerido.name(),
                    accionRecomendadaDe(nivelActual, nivelSugerido, puntaje.puntajeGlobal()),
                    ahora);

            // Las metricas se guardan siempre: es lo que permite abrir el numero si el
            // organizador apela. «El sistema lo calculo» no se puede apelar.
            desempenos.guardarMetricas(dsl, evaluacionId, puntaje.metricas());

            // AP-CU92-03: el descenso se aplica; el ascenso queda sugerido.
            boolean seAplico = false;
            if (nivelSugerido.distanciaHasta(nivelActual) > 0) {
                seAplico = organizadores.cambiarNivel(
                        dsl,
                        organizador.id(),
                        nivelSugerido.name(),
                        entrada.limiteDeGruposDelNivel(),
                        entrada.limiteDeMontoDelNivel(),
                        organizador.version());
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.desempeno_evaluado",
                            "evaluacion_desempeno",
                            evaluacionId,
                            Map.of(
                                    "organizadorId", organizador.id().toString(),
                                    "periodo", entrada.periodo(),
                                    "puntaje", puntaje.puntajeGlobal().toPlainString(),
                                    "nivelActual", nivelActual.name(),
                                    "nivelSugerido", nivelSugerido.name(),
                                    "aplicado", Boolean.toString(seAplico)),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaEvaluacion(
                    evaluacionId, nivelActual.name(), nivelSugerido.name(), puntaje.puntajeGlobal(), true);
        });
    }

    /**
     * El nivel que sugieren los numeros.
     *
     * <p>Un escalon por vez hacia arriba (lo impone {@link NivelDeOrganizador}), sin
     * limite hacia abajo. Saltar dos de golpe le entrega a alguien un limite que nunca
     * sostuvo, y el historial que probaria que puede sostenerlo es el que no tiene.
     */
    private NivelDeOrganizador sugerir(NivelDeOrganizador actual, BigDecimal puntaje) {
        if (puntaje.compareTo(umbralDeDescenso) < 0) {
            return NivelDeOrganizador.APRENDIZ;
        }
        if (puntaje.compareTo(umbralDeAscenso) >= 0) {
            var siguiente =
                    switch (actual) {
                        case APRENDIZ -> NivelDeOrganizador.ESTANDAR;
                        case ESTANDAR -> NivelDeOrganizador.SENIOR;
                        case SENIOR, MAESTRO -> NivelDeOrganizador.MAESTRO;
                    };
            return actual.admiteMoverseA(siguiente) ? siguiente : actual;
        }
        return actual;
    }

    private String accionRecomendadaDe(NivelDeOrganizador actual, NivelDeOrganizador sugerido, BigDecimal puntaje) {
        int distancia = actual.distanciaHasta(sugerido);
        if (distancia > 0) {
            return "Ascenso sugerido a " + sugerido + " con puntaje " + puntaje.toPlainString();
        }
        if (distancia < 0) {
            return "Descenso a " + sugerido + " por puntaje " + puntaje.toPlainString();
        }
        return "Se mantiene en " + actual;
    }

    public record EntradaEvaluacion(
            UUID organizadorId,
            String periodo,
            List<PuntajeDeDesempeno.Metrica> metricas,
            BigDecimal morosidad,
            BigDecimal tasaFinalizacion,
            BigDecimal satisfaccion,
            BigDecimal tiempoRespuestaHoras,
            int incidenciasAbiertas,
            int coberturasConsumidas,
            int limiteDeGruposDelNivel,
            BigDecimal limiteDeMontoDelNivel) {}

    public record SalidaEvaluacion(
            UUID evaluacionId, String nivelActual, String nivelSugerido, BigDecimal puntaje, boolean esNueva) {}
}
