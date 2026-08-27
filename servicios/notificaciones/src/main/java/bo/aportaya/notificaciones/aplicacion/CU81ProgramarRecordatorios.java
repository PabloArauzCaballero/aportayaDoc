package bo.aportaya.notificaciones.aplicacion;

import bo.aportaya.notificaciones.dominio.EscaleraDeRecordatorios;
import bo.aportaya.notificaciones.dominio.EscaleraDeRecordatorios.Resultado;
import bo.aportaya.notificaciones.infraestructura.ProgramacionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-81 · Programar recordatorios de aporte.
 *
 * <p>Trabajo diario, **idempotente por dia**: correrlo dos veces el mismo dia no manda
 * dos recordatorios. La idempotencia sale de la fecha, no de una clave del llamador.
 *
 * <p>Las obligaciones **llegan desde afuera**. Viven en el esquema de aportes y
 * notificaciones no las lee (invariante 11): quien dispara el trabajo las trae ya
 * resueltas, con si estan pagas y cuantos avisos se mandaron.
 */
@Service
public class CU81ProgramarRecordatorios {

    private final Datos datos;
    private final ProgramacionRepositorio programaciones;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU81ProgramarRecordatorios(Datos datos, ProgramacionRepositorio programaciones, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.programaciones = programaciones;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaRecordatorios ejecutar(EntradaRecordatorios entrada, ContextoSesion ctx) {
        LocalDate hoy = reloj.ahora().atOffset(ZoneOffset.UTC).toLocalDate();

        // AP-CU81-01: un periodo cerrado no tiene nada que recordar.
        if (!entrada.periodoAbierto()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(81, 1), "Ese periodo no esta abierto: no hay aportes que recordar.");
        }

        return datos.conContexto(ctx, dsl -> {
            List<ProgramacionRepositorio.Escalon> escalones = programaciones.para(dsl, entrada.grupoId());

            // AP-CU81-02: sin escalera propia se aplican solo los obligatorios, que
            // son las filas con grupo_id nulo. No se inventa una escalera por omision.
            boolean hayPropia = escalones.stream().anyMatch(ProgramacionRepositorio.Escalon::esDelGrupo);

            Map<EscaleraDeRecordatorios.Escalon, Integer> desfases = desfasesDe(escalones);
            List<EscaleraDeRecordatorios.Paso> pasos =
                    EscaleraDeRecordatorios.calcular(entrada.vencimiento(), desfases);
            Optional<EscaleraDeRecordatorios.Escalon> escalonHoy = EscaleraDeRecordatorios.escalonDeHoy(pasos, hoy);

            List<DetalleObligacion> detalle = new ArrayList<>();
            int enviados = 0;
            int cancelados = 0;
            int pospuestos = 0;

            for (Obligacion obligacion : entrada.obligaciones()) {
                Resultado resultado = EscaleraDeRecordatorios.debeRecordar(
                        obligacion.yaPago(),
                        obligacion.suprimido(),
                        obligacion.enviosPrevios(),
                        entrada.topeDeEnvios(),
                        escalonHoy.isPresent());

                switch (resultado) {
                    case ENVIADO -> {
                        enviados++;
                        // No manda el aviso: lo PIDE. Despachar es CU-80, y meterlo
                        // aca acoplaria la escalera al transporte.
                        outbox.emitir(
                                dsl,
                                new EventoDominio(
                                        "notificaciones.recordatorio_debido",
                                        "programacion_recordatorio",
                                        obligacion.obligacionId(),
                                        Map.of(
                                                "usuarioId",
                                                        obligacion.usuarioId().toString(),
                                                "escalon",
                                                        escalonHoy.orElseThrow().name(),
                                                "periodoId", entrada.periodoId().toString()),
                                        UUID.fromString(ctx.traza().id())));
                    }
                    case CANCELADO_YA_PAGADO -> cancelados++;
                    case POSPUESTO_TOPE, SUPRIMIDO -> pospuestos++;
                }
                detalle.add(new DetalleObligacion(obligacion.obligacionId(), resultado.name()));
            }

            return new SalidaRecordatorios(
                    entrada.obligaciones().size(), enviados, cancelados, pospuestos, hayPropia, detalle);
        });
    }

    /**
     * Traduce las filas de {@code programacion_recordatorio} a los cuatro escalones.
     *
     * <p>El signo del desfase decide: negativo es antes del vencimiento, cero es el
     * dia, y positivo es despues. Es el mismo criterio con que se guarda la fila.
     */
    private Map<EscaleraDeRecordatorios.Escalon, Integer> desfasesDe(List<ProgramacionRepositorio.Escalon> escalones) {
        Map<EscaleraDeRecordatorios.Escalon, Integer> desfases = new EnumMap<>(EscaleraDeRecordatorios.Escalon.class);
        for (var fila : escalones) {
            int dias = fila.desfaseDias();
            EscaleraDeRecordatorios.Escalon cual;
            if (dias < 0) {
                cual = EscaleraDeRecordatorios.Escalon.PREVIO;
            } else if (dias == 0) {
                cual = EscaleraDeRecordatorios.Escalon.VENCIMIENTO;
            } else if (dias <= 3) {
                cual = EscaleraDeRecordatorios.Escalon.GRACIA;
            } else {
                cual = EscaleraDeRecordatorios.Escalon.POST_GRACIA;
            }
            // El del grupo gana: la consulta ya los ordeno con el propio primero.
            desfases.putIfAbsent(cual, dias);
        }
        return desfases;
    }

    public record Obligacion(UUID obligacionId, UUID usuarioId, boolean yaPago, boolean suprimido, int enviosPrevios) {}

    public record EntradaRecordatorios(
            UUID periodoId,
            Optional<UUID> grupoId,
            LocalDate vencimiento,
            boolean periodoAbierto,
            int topeDeEnvios,
            List<Obligacion> obligaciones) {}

    public record SalidaRecordatorios(
            int programados,
            int enviados,
            int cancelados,
            int pospuestos,
            boolean conEscaleraPropia,
            List<DetalleObligacion> detalle) {}

    public record DetalleObligacion(UUID obligacionId, String resultado) {}
}
