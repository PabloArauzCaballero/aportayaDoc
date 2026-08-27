package bo.aportaya.cumplimiento.trabajos;

import bo.aportaya.cumplimiento.aplicacion.CU54EscalarPlanesVencidos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * El disparador del control diario de CU-54. <b>No decide nada</b>: arma el contexto y
 * llama al caso de uso.
 *
 * <p>Es deliberadamente flaco. Un trabajo programado con logica adentro es logica que
 * no se puede probar sin esperar a que suene el reloj, y termina siendo la parte del
 * sistema que nadie revisa porque «corre sola».
 *
 * <p>El contexto es {@code ContextoSesion.deSistema}: un rol con sus propias politicas
 * de fila, no una excepcion a las politicas. Un trabajo que corre sin contexto corre
 * sin RLS, y eso no falla — devuelve filas de todos.
 */
@Component
public class ControlDePlanesVencidos {

    private static final Logger LOG = LoggerFactory.getLogger(ControlDePlanesVencidos.class);

    /** Identifica al proceso en la bitacora. Es el mismo siempre: es el mismo control. */
    private static final UUID PROCESO = UUID.fromString("00000000-0000-4000-8000-000000000054");

    private final CU54EscalarPlanesVencidos escalar;

    public ControlDePlanesVencidos(CU54EscalarPlanesVencidos escalar) {
        this.escalar = escalar;
    }

    /**
     * Corre una vez al dia, temprano.
     *
     * <p><b>Sin lock de planificador, y es correcto igual.</b> Los planes se toman
     * {@code FOR UPDATE SKIP LOCKED} y el codigo del hallazgo se deriva del plan, asi
     * que dos replicas se reparten el trabajo y una segunda corrida no abre un segundo
     * expediente. Un lock ahorraria trabajo repetido; no es lo que hace correcto esto,
     * y confundir las dos cosas es como se escriben los controles que duplican
     * expedientes el dia que el lock falla.
     */
    @Scheduled(cron = "${cumplimiento.riesgos.cron-planes-vencidos:0 15 6 * * *}", zone = "America/La_Paz")
    public void correr() {
        var abiertos = escalar.ejecutar(
                ContextoSesion.deSistema(PROCESO, new Traza(UUID.randomUUID().toString())));
        if (!abiertos.isEmpty()) {
            // Se registra solo cuando hubo algo. Un control que anuncia todos los dias
            // que no encontro nada entrena a que nadie lo lea.
            LOG.warn("CU-54 · {} plan(es) de accion vencidos escalados a hallazgo", abiertos.size());
        }
    }
}
