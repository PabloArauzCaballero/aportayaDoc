package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.infraestructura.IncidenteSeguridadRepositorio;
import bo.aportaya.cumplimiento.infraestructura.RiesgoOperativoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-55 · el control diario: un incidente cuyo plazo de reporte vencio se convierte en
 * hallazgo.
 *
 * <p>Es lo que impide que «reportar en plazo» sea una intencion. El plazo esta guardado
 * en la fila; lo unico que falta es alguien que mire, y ese alguien no puede ser una
 * persona acordandose.
 *
 * <p>El hallazgo <b>no reemplaza al reporte</b>: el incidente sigue teniendo que
 * reportarse. Llegar tarde es una observacion; no reportar es un incumplimiento, y son
 * dos cosas distintas que este control mantiene separadas.
 */
@Service
public class CU55EscalarIncidentesVencidos {

    private final Datos datos;
    private final IncidenteSeguridadRepositorio incidentes;
    private final RiesgoOperativoRepositorio riesgos;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int diasDeRegularizacion;
    private final int topePorCorrida;

    public CU55EscalarIncidentesVencidos(
            Datos datos,
            IncidenteSeguridadRepositorio incidentes,
            RiesgoOperativoRepositorio riesgos,
            Outbox outbox,
            Reloj reloj,
            @Value("${cumplimiento.incidentes.dias-de-regularizacion:15}") int diasDeRegularizacion,
            @Value("${cumplimiento.incidentes.tope-por-corrida:200}") int topePorCorrida) {
        this.datos = datos;
        this.incidentes = incidentes;
        this.riesgos = riesgos;
        this.outbox = outbox;
        this.reloj = reloj;
        this.diasDeRegularizacion = diasDeRegularizacion;
        this.topePorCorrida = topePorCorrida;
    }

    @Transactional
    public List<UUID> ejecutar(ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            List<UUID> abiertos = new ArrayList<>();

            for (var incidente : incidentes.vencidosSinReportar(dsl, ahora, topePorCorrida)) {
                String codigo = RiesgoOperativoRepositorio.codigoDerivadoDe(incidente.id());

                // Idempotente por el hecho: correr el control dos veces el mismo dia no
                // abre dos hallazgos por el mismo incidente.
                if (riesgos.hallazgoConCodigo(dsl, codigo).isPresent()) {
                    continue;
                }

                UUID hallazgo = riesgos.abrirHallazgo(
                        dsl,
                        codigo,
                        null,
                        "Incidente %s: venció el plazo de reporte al organismo el %s y no consta reportado."
                                .formatted(incidente.codigo(), incidente.plazoReporte()),
                        // Un plazo regulatorio vencido no tiene severidad media: es lo
                        // que el supervisor va a mirar primero.
                        severidadDe(incidente.severidad()),
                        reloj.hoy(),
                        reloj.hoy().plusDays(diasDeRegularizacion));
                abiertos.add(hallazgo);

                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "seguridad.plazo_reporte_vencido",
                                "incidente_seguridad",
                                incidente.id(),
                                Map.of("hallazgoId", hallazgo.toString(), "codigo", incidente.codigo()),
                                UUID.fromString(ctx.traza().id())));
            }
            return List.copyOf(abiertos);
        });
    }

    /** Un incidente critico o alto vencido es critico; el resto, alto. Nunca menos. */
    private static String severidadDe(String severidadDelIncidente) {
        return "CRITICA".equals(severidadDelIncidente) || "ALTA".equals(severidadDelIncidente) ? "CRITICA" : "ALTA";
    }
}
