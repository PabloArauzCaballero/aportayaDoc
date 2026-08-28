package bo.aportaya.transparencia.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.transparencia.dominio.IndicadoresDeReputacion;
import bo.aportaya.transparencia.dominio.PuntajeDeReputacion;
import bo.aportaya.transparencia.infraestructura.ModeloRepositorio;
import bo.aportaya.transparencia.infraestructura.ReputacionRepositorio;
import bo.aportaya.transparencia.infraestructura.SnapshotRepositorio;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-71 · Recalcular el puntaje de reputacion.
 *
 * <p>**Un solo puntaje vigente por usuario** (R-REP-02) y **el total es la suma de sus
 * componentes** (R-REP-03, lo verifica {@code tg_puntaje_cuadra}). Las dos reglas
 * apuntan a lo mismo: que el numero que decide si alguien entra a un grupo se pueda
 * abrir factor por factor y discutir.
 *
 * <p>Con menos eventos que el minimo del modelo, el nivel es {@code SIN_HISTORIAL}. No
 * es un castigo: es decir la verdad. Poner un puntaje bajo a quien todavia no tiene
 * historial lo trata como si hubiera fallado.
 */
@Service
public class CU71RecalcularPuntaje {

    private final Datos datos;
    private final ReputacionRepositorio reputaciones;
    private final ModeloRepositorio modelos;
    private final SnapshotRepositorio snapshots;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Duration periodoDeRecalculo;

    /**
     * Donde empieza cada nivel de confianza. Es politica, no constante (invariante 10):
     * mover el corte de «confiable» decide a quien se le abre un grupo, y esa palanca
     * tiene que poder moverse sin desplegar.
     */
    private final List<PuntajeDeReputacion.Corte> escalaDeConfianza;

    public CU71RecalcularPuntaje(
            Datos datos,
            ReputacionRepositorio reputaciones,
            ModeloRepositorio modelos,
            SnapshotRepositorio snapshots,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.reputacion.periodo-de-recalculo}") Duration periodoDeRecalculo,
            List<PuntajeDeReputacion.Corte> escalaDeConfianza) {
        this.datos = datos;
        this.reputaciones = reputaciones;
        this.modelos = modelos;
        this.snapshots = snapshots;
        this.outbox = outbox;
        this.reloj = reloj;
        this.periodoDeRecalculo = periodoDeRecalculo;
        this.escalaDeConfianza = escalaDeConfianza;
    }

    @Transactional
    public SalidaPuntaje recalcular(EntradaPuntaje entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var modelo = modelos.modeloVigente(dsl, ahora)
                    .orElseThrow(
                            () -> new ErrorDeNegocio(CodigoError.de(71, 1), "No hay modelo de scoring en produccion."));

            OffsetDateTime desde = ahora.minusMonths(modelo.ventanaMeses());
            int eventos = reputaciones.eventosEnVentana(dsl, entrada.usuarioId(), desde);
            var factores = modelos.factoresDe(dsl, modelo.id());

            var resultado = PuntajeDeReputacion.calcular(
                    modelo.puntajeBase(),
                    modelo.puntajeMinimo(),
                    modelo.puntajeMaximo(),
                    modelo.minimoDeEventos(),
                    eventos,
                    factores,
                    entrada.mediciones(),
                    entrada.medicionesAnteriores(),
                    escalaDeConfianza);

            var anterior = reputaciones.puntajeDe(dsl, entrada.usuarioId());
            // R-REP-02: un solo puntaje vigente. Se reemplaza, no se acumula: dos
            // vigentes harian que dos consultas del mismo dia dieran numeros distintos.
            reputaciones.borrarPuntaje(dsl, entrada.usuarioId());

            UUID puntajeId = reputaciones.guardarPuntaje(
                    dsl,
                    entrada.usuarioId(),
                    modelo.id(),
                    modelo.version(),
                    resultado,
                    entrada.indicadores(),
                    eventos,
                    ahora,
                    ahora.plus(periodoDeRecalculo));

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "transparencia.puntaje_recalculado",
                            "puntaje_reputacion",
                            puntajeId,
                            Map.of(
                                    "usuarioId", entrada.usuarioId().toString(),
                                    "puntaje", resultado.puntaje().toPlainString(),
                                    "nivelConfianza", resultado.nivelDeConfianza(),
                                    "anterior",
                                            anterior.map(p -> p.puntaje().toPlainString())
                                                    .orElse("SIN_PUNTAJE"),
                                    "eventosConsiderados", Integer.toString(eventos),
                                    "modeloVersion", modelo.version()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaPuntaje(
                    puntajeId,
                    resultado.puntaje(),
                    resultado.nivelDeConfianza(),
                    resultado.componentes(),
                    anterior.map(ReputacionRepositorio.Puntaje::puntaje).orElse(null),
                    eventos);
        });
    }

    /** Una foto del puntaje, para congelarlo en un certificado o al entrar a un grupo. */
    @Transactional
    public UUID tomarSnapshot(UUID usuarioId, String motivo, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var puntaje = reputaciones
                    .puntajeDe(dsl, usuarioId)
                    .orElseThrow(
                            () -> new ErrorDeNegocio(CodigoError.de(71, 2), "Ese usuario no tiene puntaje calculado."));
            // La foto guarda los factores, no solo el numero: un certificado que dice
            // «85» sin decir de que esta hecho no se puede verificar.
            String factores = """
                    {"puntaje":"%s","nivel":"%s","modelo":"%s","eventos":%d}"""
                    .formatted(
                            puntaje.puntaje().toPlainString(),
                            puntaje.nivelConfianza(),
                            puntaje.modeloVersion(),
                            puntaje.eventosConsiderados());
            return snapshots.tomarSnapshot(
                    dsl, usuarioId, puntaje.puntaje(), puntaje.nivelConfianza(), factores, motivo, ahora);
        });
    }

    public record EntradaPuntaje(
            UUID usuarioId,
            List<PuntajeDeReputacion.Medicion> mediciones,
            List<PuntajeDeReputacion.Medicion> medicionesAnteriores,
            IndicadoresDeReputacion indicadores) {}

    public record SalidaPuntaje(
            UUID puntajeId,
            java.math.BigDecimal puntaje,
            String nivelDeConfianza,
            List<PuntajeDeReputacion.Componente> componentes,
            java.math.BigDecimal puntajeAnterior,
            int eventosConsiderados) {}
}
