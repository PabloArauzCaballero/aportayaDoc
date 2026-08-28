package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.QuorumDeComite;
import bo.aportaya.cumplimiento.infraestructura.EvaluacionProductoRepositorio;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-94 · Elevar una decision al comite de gobierno.
 *
 * <p>Es donde las decisiones dejan de ser de una persona. Y por eso las cuatro
 * condiciones, que no son tramite:
 *
 * <ul>
 *   <li>**Quorum.** Menos asistentes que el minimo y la decision es de quienes pudieron
 *       venir, no del comite.
 *   <li>**Composicion.** Tres asistentes sin el rol de cumplimiento no son un comite de
 *       cumplimiento: falta justamente quien tenia que objetar.
 *   <li>**Nadie vota su propio asunto.** Si vota, la decision queda viciada aunque el
 *       resultado sea el correcto.
 *   <li>**Cada compromiso sale con responsable y fecha.** Un acta con decisiones y sin
 *       compromisos es un acta que no cambia nada.
 * </ul>
 *
 * <p>Y lo que decide el comite **surte efecto en la misma transaccion**: si aprueba una
 * evaluacion de producto, la evaluacion queda aprobada acá, no en un proceso posterior
 * que alguien puede olvidar.
 */
@Service
public class CU94ElevarAlComite {

    private final Datos datos;
    private final GobiernoRepositorio gobierno;
    private final EvaluacionProductoRepositorio evaluaciones;
    private final Outbox outbox;
    private final Reloj reloj;
    private final String baseUrlDeActas;

    public CU94ElevarAlComite(
            Datos datos,
            GobiernoRepositorio gobierno,
            EvaluacionProductoRepositorio evaluaciones,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.publicacion.base-url}") String baseUrlDeActas) {
        this.datos = datos;
        this.gobierno = gobierno;
        this.evaluaciones = evaluaciones;
        this.outbox = outbox;
        this.reloj = reloj;
        this.baseUrlDeActas = baseUrlDeActas;
    }

    @Transactional
    public SalidaActa sesionar(EntradaSesion entrada, ContextoSesion ctx) {
        LocalDate hoy = reloj.ahora().atOffset(ZoneOffset.UTC).toLocalDate();

        return datos.conContexto(ctx, dsl -> {
            var comite = gobierno.comitePorTipo(dsl, entrada.comiteTipo())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(94, 6), "No existe el comite " + entrada.comiteTipo() + "."));
            // AP-CU94-06.
            if (!comite.activo()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(94, 6), "El comite " + entrada.comiteTipo() + " no esta activo.");
            }

            var veredicto =
                    QuorumDeComite.revisar(entrada.asistentes(), comite.quorumMinimo(), comite.composicionRequerida());
            // AP-CU94-01.
            if (!veredicto.hayQuorum()) {
                throw new ErrorDeNegocio(CodigoError.de(94, 1), veredicto.motivo());
            }
            // AP-CU94-02.
            if (!veredicto.composicionCompleta()) {
                throw new ErrorDeNegocio(CodigoError.de(94, 2), veredicto.motivo());
            }
            // AP-CU94-03.
            var viciados = QuorumDeComite.votosViciados(entrada.votos(), entrada.interesadosPorAsunto());
            if (!viciados.isEmpty()) {
                throw new ErrorDeNegocio(CodigoError.de(94, 3), String.join(" ", viciados));
            }
            var sinMotivo = QuorumDeComite.abstencionesSinMotivo(entrada.votos());
            if (!sinMotivo.isEmpty()) {
                throw new ErrorDeNegocio(CodigoError.de(94, 3), String.join(" ", sinMotivo));
            }

            UUID actaId = gobierno.levantarActa(
                    dsl,
                    comite.id(),
                    ctx.usuarioId(),
                    entrada.numeroDeActa(),
                    entrada.fechaSesion(),
                    entrada.asistentesJson(),
                    true,
                    entrada.temasJson(),
                    entrada.decisionesJson(),
                    baseUrlDeActas + "/actas/" + entrada.numeroDeActa(),
                    entrada.hashDocumento());

            // Lo aprobado surte efecto AQUI. Dejarlo para despues abre la puerta a que
            // el acta diga una cosa y el sistema otra.
            var planes = new ArrayList<UUID>();
            int resueltos = 0;
            int pospuestos = 0;
            for (var asunto : entrada.asuntos()) {
                if ("POSPONER".equals(asunto.decision())) {
                    pospuestos++;
                    continue;
                }
                resueltos++;
                if ("APROBAR".equals(asunto.decision())
                        && "EVALUACION_RIESGO_PRODUCTO".equals(asunto.referenciaTipo())) {
                    evaluaciones.aprobar(dsl, asunto.referenciaId(), ctx.usuarioId(), entrada.fechaSesion());
                }
                for (var compromiso : asunto.compromisos()) {
                    planes.add(gobierno.abrirPlanDeAccion(
                            dsl, null, compromiso.responsableId(), compromiso.descripcion(), compromiso.fechaLimite()));
                }
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.acta_comite_cerrada",
                            "acta_comite",
                            actaId,
                            Map.of(
                                    "comite", entrada.comiteTipo(),
                                    "numero", entrada.numeroDeActa(),
                                    "asuntosResueltos", Integer.toString(resueltos),
                                    "asuntosPospuestos", Integer.toString(pospuestos),
                                    "compromisos", Integer.toString(planes.size())),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaActa(
                    actaId,
                    true,
                    true,
                    resueltos,
                    pospuestos,
                    List.copyOf(planes),
                    proximaSesion(entrada.fechaSesion(), comite.periodicidad()));
        });
    }

    /** Cuando le toca sesionar de nuevo, segun su periodicidad minima. */
    private LocalDate proximaSesion(LocalDate ultima, String periodicidad) {
        return switch (periodicidad) {
            case "DIARIA", "CONTINUA" -> ultima.plusDays(1);
            case "SEMANAL" -> ultima.plusWeeks(1);
            case "QUINCENAL" -> ultima.plusWeeks(2);
            case "MENSUAL" -> ultima.plusMonths(1);
            case "BIMESTRAL" -> ultima.plusMonths(2);
            case "TRIMESTRAL" -> ultima.plusMonths(3);
            case "SEMESTRAL" -> ultima.plusMonths(6);
            default -> ultima.plusYears(1);
        };
    }

    public record Compromiso(String descripcion, UUID responsableId, LocalDate fechaLimite) {}

    public record Asunto(
            String referenciaTipo,
            UUID referenciaId,
            String resumen,
            String decision,
            String fundamento,
            List<Compromiso> compromisos) {}

    /**
     * @param interesadosPorAsunto quienes tienen interes directo, por indice de asunto.
     *     Lo declara quien convoca; no se adivina desde el codigo
     */
    public record EntradaSesion(
            String comiteTipo,
            String numeroDeActa,
            LocalDate fechaSesion,
            List<QuorumDeComite.Asistente> asistentes,
            List<QuorumDeComite.Voto> votos,
            Map<Integer, Set<UUID>> interesadosPorAsunto,
            List<Asunto> asuntos,
            String asistentesJson,
            String temasJson,
            String decisionesJson,
            String hashDocumento) {}

    public record SalidaActa(
            UUID actaId,
            boolean quorumAlcanzado,
            boolean composicionCompleta,
            int asuntosResueltos,
            int asuntosPospuestos,
            List<UUID> planesGenerados,
            LocalDate proximaSesionLimite) {}
}
