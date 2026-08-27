package bo.aportaya.cumplimiento.trabajos;

import bo.aportaya.cumplimiento.aplicacion.CU55EscalarIncidentesVencidos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El disparador del control diario de CU-55. <b>No decide nada</b>: arma el contexto y
 * llama al caso de uso.
 *
 * <p>Corre despues del de CU-54 y no a la misma hora, aunque los dos escriban hallazgos:
 * separarlos hace que, cuando algo falle, se sepa cual de los dos fallo sin tener que
 * desenredar dos corridas simultaneas en la misma bitacora.
 */
@Component
public class ControlDeIncidentesVencidos {

    private static final Logger LOG = LoggerFactory.getLogger(ControlDeIncidentesVencidos.class);

    private static final UUID PROCESO = UUID.fromString("00000000-0000-4000-8000-000000000055");

    private final CU55EscalarIncidentesVencidos escalar;

    public ControlDeIncidentesVencidos(CU55EscalarIncidentesVencidos escalar) {
        this.escalar = escalar;
    }

    @Scheduled(cron = "${cumplimiento.incidentes.cron-vencidos:0 45 6 * * *}", zone = "America/La_Paz")
    public void correr() {
        var abiertos = escalar.ejecutar(
                ContextoSesion.deSistema(PROCESO, new Traza(UUID.randomUUID().toString())));
        if (!abiertos.isEmpty()) {
            // Un plazo regulatorio vencido no es una nota informativa: se anuncia como
            // advertencia para que aparezca donde alguien mira.
            LOG.warn("CU-55 · {} incidente(s) con plazo de reporte vencido escalados a hallazgo", abiertos.size());
        }
    }
}
