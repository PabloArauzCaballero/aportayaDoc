package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.infraestructura.CasoLftRepositorio;
import bo.aportaya.cumplimiento.infraestructura.MonitoreoLftRepositorio;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-44 · De alerta de monitoreo a reporte de operacion sospechosa.
 *
 * <p>**Rige el deber de reserva: el titular no se entera de nada.** Ni pantalla, ni
 * aviso, ni un cambio de estado que se le note. Avisarle a alguien que esta siendo
 * investigado por lavado es, literalmente, el delito de tipping-off.
 *
 * <p>Y tres controles que sostienen que la investigacion valga:
 *
 * <ul>
 *   <li>**Ninguna alerta se cierra sin conclusion** (R-UIF-07). «Revisado» no le sirve a
 *       nadie dos años despues.
 *   <li>**Quien revisa no es quien analizo** ({@code ck_caso_revision}). Revisarse a uno
 *       mismo no es revisar.
 *   <li>**El caso nace con plazo** (R-UIF-08). Una investigacion sin fecha limite se
 *       convierte en una investigacion sin fin.
 * </ul>
 */
@Service
public class CU44InvestigarYReportar {

    private final Datos datos;
    private final MonitoreoLftRepositorio monitoreo;
    private final CasoLftRepositorio casos;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Map<String, Duration> plazoPorSeveridad;

    public CU44InvestigarYReportar(
            Datos datos,
            MonitoreoLftRepositorio monitoreo,
            CasoLftRepositorio casos,
            Outbox outbox,
            Reloj reloj,
            Map<String, Duration> plazoPorSeveridad) {
        this.datos = datos;
        this.monitoreo = monitoreo;
        this.casos = casos;
        this.outbox = outbox;
        this.reloj = reloj;
        this.plazoPorSeveridad = plazoPorSeveridad;
    }

    /** Agrupa alertas del mismo cliente en un caso y le pone plazo. */
    @Transactional
    public SalidaCaso abrirCaso(EntradaCaso entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var alertas = monitoreo.alertas(dsl, entrada.alertaIds());
            if (alertas.isEmpty()) {
                throw new ErrorDeNegocio(CodigoError.de(44, 1), "No hay alertas para investigar.");
            }
            // Un caso reune alertas de UN cliente. Mezclar clientes en un expediente
            // hace que la decision sobre uno contamine al otro.
            var titulares = alertas.stream()
                    .map(MonitoreoLftRepositorio.Alerta::usuarioId)
                    .distinct()
                    .toList();
            if (titulares.size() > 1) {
                throw new ErrorDeNegocio(
                        CodigoError.de(44, 1), "Las alertas son de titulares distintos: van en casos separados.");
            }

            String severidad = alertas.stream()
                    .map(MonitoreoLftRepositorio.Alerta::severidad)
                    .max(java.util.Comparator.comparingInt(CU44InvestigarYReportar::peso))
                    .orElse("MEDIA");
            // R-UIF-08 · el plazo se guarda al abrir, no se recalcula al consultar.
            OffsetDateTime plazo = ahora.plus(plazoPorSeveridad.getOrDefault(severidad, Duration.ofDays(30)));

            UUID casoId = casos.abrir(
                    dsl,
                    titulares.get(0),
                    entrada.analistaId(),
                    "ALERTA",
                    prioridadDe(severidad),
                    entrada.resumen(),
                    ahora,
                    plazo);

            for (var alerta : alertas) {
                monitoreo.asignar(dsl, alerta.id(), entrada.analistaId());
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.caso_abierto",
                            "caso_investigacion_lft",
                            casoId,
                            Map.of(
                                    "analistaId", entrada.analistaId().toString(),
                                    "alertas", Integer.toString(alertas.size()),
                                    "plazoLimite", plazo.toString(),
                                    // Sin usuarioId en el payload: el deber de reserva
                                    // alcanza tambien a la bandeja de eventos.
                                    "severidad", severidad),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCaso(casoId, "ABIERTO", plazo, null);
        });
    }

    /** Cierra el caso con su decision. REPORTAR exige el ROS ya radicado. */
    @Transactional
    public SalidaCaso decidir(EntradaDecision entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        // AP-CU44-01 · R-UIF-07: la conclusion es la unica parte que sirve despues.
        if (entrada.conclusion() == null || entrada.conclusion().trim().length() < 20) {
            throw new ErrorDeNegocio(
                    CodigoError.de(44, 1), "Una alerta no se cierra sin conclusion escrita (R-UIF-07).");
        }
        // AP-CU44-02: revisarse a uno mismo no es revisar.
        if (entrada.revisadoPor() != null && entrada.revisadoPor().equals(entrada.analistaId())) {
            throw new ErrorDeNegocio(CodigoError.de(44, 2), "La revision del caso tiene que ser independiente.");
        }
        // AP-CU44-03: decidir reportar sin narrativa deja a la UIF sin nada que leer.
        if ("REPORTAR".equals(entrada.decision()) && entrada.reporteSospechosoId() == null) {
            throw new ErrorDeNegocio(
                    CodigoError.de(44, 3),
                    "Reportar exige el reporte de operacion sospechosa ya radicado, con su narrativa y tipologia.");
        }

        return datos.conContexto(ctx, dsl -> {
            var caso = casos.porId(dsl, entrada.casoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(44, 4), "Ese caso no existe."));
            // AP-CU44-04: pasado el plazo la decision se registra igual —no cerrar seria
            // peor— pero queda marcada como fuera de plazo.
            boolean vencido = ahora.isAfter(caso.plazoLimite());

            casos.decidir(
                    dsl,
                    entrada.casoId(),
                    entrada.decision(),
                    entrada.conclusion(),
                    entrada.revisadoPor(),
                    entrada.reporteSospechosoId(),
                    ahora);

            for (UUID alertaId : entrada.alertaIds()) {
                monitoreo.cerrar(
                        dsl,
                        alertaId,
                        "DESCARTAR".equals(entrada.decision()) ? "DESCARTADA" : "ESCALADA",
                        entrada.conclusion(),
                        entrada.casoId(),
                        ahora);
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.caso_decidido",
                            "caso_investigacion_lft",
                            entrada.casoId(),
                            Map.of(
                                    "decision", entrada.decision(),
                                    "fueraDePlazo", Boolean.toString(vencido),
                                    // NUNCA se notifica al titular: es el deber de
                                    // reserva, y el evento lo dice explicito para que
                                    // ningun consumidor lo intente.
                                    "notificarAlTitular", "false"),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCaso(entrada.casoId(), "CERRADO", caso.plazoLimite(), entrada.reporteSospechosoId());
        });
    }

    private static int peso(String severidad) {
        return switch (severidad) {
            case "CRITICA" -> 4;
            case "ALTA" -> 3;
            case "MEDIA" -> 2;
            default -> 1;
        };
    }

    private static String prioridadDe(String severidad) {
        return switch (severidad) {
            case "CRITICA" -> "URGENTE";
            case "ALTA" -> "ALTA";
            case "MEDIA" -> "MEDIA";
            default -> "BAJA";
        };
    }

    public record EntradaCaso(List<UUID> alertaIds, UUID analistaId, String resumen) {}

    /**
     * @param reporteSospechosoId el ROS vive en el esquema de auditoria y **no se
     *     escribe desde aca** (invariante 11): llega ya radicado, con su narrativa
     */
    public record EntradaDecision(
            UUID casoId,
            List<UUID> alertaIds,
            UUID analistaId,
            UUID revisadoPor,
            String decision,
            String conclusion,
            UUID reporteSospechosoId) {}

    public record SalidaCaso(UUID casoId, String estado, OffsetDateTime plazoLimite, UUID reporteSospechosoId) {}
}
