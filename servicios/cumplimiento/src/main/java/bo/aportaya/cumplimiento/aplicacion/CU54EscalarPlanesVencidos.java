package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.infraestructura.RiesgoOperativoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-54 · el control diario: un plan de accion vencido se convierte en hallazgo.
 *
 * <p>Es la parte del caso de uso que hace que el plan de accion signifique algo. Sin
 * esto, «responsable y plazo» son dos columnas: el plazo pasa, nadie mira, y el evento
 * de riesgo queda registrado y sin remediar — que es peor que no haberlo registrado,
 * porque ademas quedo la constancia de que se sabia.
 *
 * <p><b>Corre en varias replicas sin lock de planificador, y es correcto igual.</b> Dos
 * cosas lo sostienen, y ninguna es el planificador:
 *
 * <ul>
 *   <li>Los planes se toman {@code FOR UPDATE SKIP LOCKED}: dos replicas se reparten
 *       filas distintas en vez de pelear por las mismas.
 *   <li>El codigo del hallazgo se <b>deriva del plan</b>, no de la ejecucion. Correr el
 *       control dos veces el mismo dia no abre dos hallazgos: el segundo encuentra el
 *       primero. La idempotencia viene del hecho, no de que alguien acuerde no repetir.
 * </ul>
 *
 * <p>Un lock de planificador ahorraria trabajo repetido; no es lo que hace correcto
 * esto. Confundir las dos cosas es como se escriben los controles que duplican
 * expedientes el dia que el lock falla.
 */
@Service
public class CU54EscalarPlanesVencidos {

    private final Datos datos;
    private final RiesgoOperativoRepositorio riesgos;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int diasDeRegularizacion;
    private final int topePorCorrida;

    public CU54EscalarPlanesVencidos(
            Datos datos,
            RiesgoOperativoRepositorio riesgos,
            Outbox outbox,
            Reloj reloj,
            @Value("${cumplimiento.riesgos.dias-de-regularizacion:30}") int diasDeRegularizacion,
            @Value("${cumplimiento.riesgos.tope-por-corrida:200}") int topePorCorrida) {
        this.datos = datos;
        this.riesgos = riesgos;
        this.outbox = outbox;
        this.reloj = reloj;
        this.diasDeRegularizacion = diasDeRegularizacion;
        this.topePorCorrida = topePorCorrida;
    }

    /**
     * @return los hallazgos abiertos en esta corrida. Vacio es el resultado normal.
     */
    @Transactional
    public List<UUID> ejecutar(ContextoSesion ctx) {
        LocalDate hoy = reloj.hoy();

        return datos.conContexto(ctx, dsl -> {
            List<UUID> abiertos = new ArrayList<>();

            // Hay tope por corrida a proposito. Sin el, el primer dia de produccion
            // —cuando se cargue el historico— una sola transaccion tomaria miles de
            // filas y las tendria bloqueadas hasta el commit. Lo que no entra hoy entra
            // manana: el control es diario, no unico.
            for (var plan : riesgos.planesVencidos(dsl, hoy, topePorCorrida)) {
                riesgos.marcarVencido(dsl, plan.id());

                // Idempotencia: si ya hay hallazgo por ese plan, no se abre otro.
                if (riesgos.hallazgoConCodigo(dsl, RiesgoOperativoRepositorio.codigoDerivadoDe(plan.id()))
                        .isPresent()) {
                    continue;
                }

                UUID hallazgo = riesgos.abrirHallazgo(
                        dsl,
                        RiesgoOperativoRepositorio.codigoDerivadoDe(plan.id()),
                        plan.responsableId(),
                        "Plan de accion vencido el %s sin cierre: %s"
                                .formatted(plan.fechaCompromiso(), plan.descripcion()),
                        severidadPor(plan.fechaCompromiso(), hoy),
                        hoy,
                        hoy.plusDays(diasDeRegularizacion));
                abiertos.add(hallazgo);

                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "plan.vencido",
                                "plan_accion_riesgo",
                                plan.id(),
                                Map.of("hallazgoId", hallazgo.toString(), "responsable", nombre(plan.responsableId())),
                                UUID.fromString(ctx.traza().id())));
            }
            return List.copyOf(abiertos);
        });
    }

    /**
     * Cuanto mas vencido, mas grave.
     *
     * <p>Los cortes no son cifras regulatorias: son la escala interna del control, y por
     * eso viven en configuracion y no en el catalogo. Un plan que lleva medio ano
     * vencido no es el mismo problema que uno vencido ayer, y darles la misma severidad
     * hace que la lista de hallazgos criticos deje de significar algo.
     */
    private String severidadPor(LocalDate compromiso, LocalDate hoy) {
        long dias = java.time.temporal.ChronoUnit.DAYS.between(compromiso, hoy);
        if (dias >= cortesDeSeveridad[2]) {
            return "CRITICA";
        }
        if (dias >= cortesDeSeveridad[1]) {
            return "ALTA";
        }
        if (dias >= cortesDeSeveridad[0]) {
            return "MEDIA";
        }
        return "BAJA";
    }

    private final int[] cortesDeSeveridad = {7, 30, 90};

    private static String nombre(UUID responsableId) {
        return responsableId == null ? "" : responsableId.toString();
    }
}
