package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.PlazoDelReclamo;
import bo.aportaya.cumplimiento.infraestructura.GobiernoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.ReclamoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CalendarioHabil;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-52 · Atender un reclamo en plazo.
 *
 * <p>Un reclamo es la unica via que tiene alguien para discutirle algo a la plataforma.
 * Las tres cosas que lo hacen real:
 *
 * <ul>
 *   <li>**El plazo se calcula al ingresar y se guarda** (R-CON-01). Recalcularlo al
 *       consultar es la forma en que un plazo se estira sin que nadie lo decida.
 *   <li>**La prorroga se comunica al cliente antes de que venza el plazo original.**
 *       Avisar despues no es avisar.
 *   <li>**Dar la razon con monto exige la devolucion** (R-CON-04). Un FAVORABLE sin plata
 *       de vuelta es darle la razon a alguien de mentira.
 * </ul>
 */
@Service
public class CU52AtenderReclamo {

    private final Datos datos;
    private final ReclamoRepositorio reclamos;
    private final GobiernoRepositorio gobierno;
    private final Outbox outbox;
    private final Reloj reloj;
    private final CalendarioHabil calendario;

    /** Dias habiles de primera respuesta y tope de la prorroga. Es normativo. */
    private final int diasHabilesDeRespuesta;

    private final int maximoDiasDeProrroga;

    public CU52AtenderReclamo(
            Datos datos,
            ReclamoRepositorio reclamos,
            GobiernoRepositorio gobierno,
            Outbox outbox,
            Reloj reloj,
            CalendarioHabil calendario,
            @Value("${aportaya.reclamos.dias-habiles-de-respuesta}") int diasHabilesDeRespuesta,
            @Value("${aportaya.reclamos.maximo-dias-de-prorroga}") int maximoDiasDeProrroga) {
        this.datos = datos;
        this.reclamos = reclamos;
        this.gobierno = gobierno;
        this.outbox = outbox;
        this.reloj = reloj;
        this.calendario = calendario;
        this.diasHabilesDeRespuesta = diasHabilesDeRespuesta;
        this.maximoDiasDeProrroga = maximoDiasDeProrroga;
    }

    @Transactional
    public SalidaReclamo ingresar(EntradaReclamo entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var punto = reclamos.puntoPorCodigo(dsl, entrada.puntoReclamoCodigo())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(52, 1), "El punto de reclamo no existe."));
            // AP-CU52-01: un canal apagado no recibe reclamos, y decirlo es mejor que
            // aceptarlo y no atenderlo.
            if (!punto.activo()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(52, 1), "El canal " + entrada.puntoReclamoCodigo() + " no esta habilitado.");
            }

            OffsetDateTime plazo = PlazoDelReclamo.vence(ahora, diasHabilesDeRespuesta, calendario);
            String codigo = "REC-" + ahora.getYear() + "-"
                    + UUID.randomUUID().toString().substring(0, 8);

            UUID id = reclamos.ingresar(
                    dsl,
                    codigo,
                    entrada.usuarioId(),
                    punto.id(),
                    entrada.categoria(),
                    entrada.producto(),
                    entrada.montoReclamado(),
                    entrada.descripcion(),
                    entrada.canalIngreso(),
                    diasHabilesDeRespuesta,
                    ahora,
                    plazo,
                    // R-CON-05: diez años. Se guarda al ingresar porque despues nadie se
                    // acuerda de ponerlo, y la base lo exige.
                    PlazoDelReclamo.conservarHasta(ahora));

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.reclamo_ingresado",
                            "reclamo_cliente",
                            id,
                            Map.of(
                                    "codigo", codigo,
                                    "categoria", entrada.categoria(),
                                    "plazoRespuesta", plazo.toString(),
                                    "diasHabilesPlazo", Integer.toString(diasHabilesDeRespuesta)),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaReclamo(id, codigo, plazo, diasHabilesDeRespuesta, "INGRESADO");
        });
    }

    @Transactional
    public void prorrogar(EntradaProrroga entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        datos.conContexto(ctx, dsl -> {
            var reclamo = reclamos.porId(dsl, entrada.reclamoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(52, 1), "Ese reclamo no existe."));

            var veredicto = PlazoDelReclamo.revisarProrroga(
                    reclamo.fechaIngreso(),
                    reclamo.plazoRespuesta(),
                    entrada.prorrogaHasta(),
                    entrada.comunicadaAlCliente(),
                    entrada.comunicadaAlOrganismo(),
                    entrada.justificacion(),
                    maximoDiasDeProrroga);
            if (!veredicto.admisible()) {
                throw new ErrorDeNegocio(CodigoError.de(52, 2), veredicto.motivo());
            }

            if (!reclamos.prorrogar(
                    dsl,
                    entrada.reclamoId(),
                    entrada.prorrogaHasta(),
                    entrada.comunicadaAlCliente(),
                    entrada.comunicadaAlOrganismo(),
                    entrada.justificacion())) {
                throw new ErrorDeNegocio(CodigoError.de(52, 2), "Ese reclamo ya tiene una prorroga otorgada.");
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.reclamo_prorrogado",
                            "reclamo_cliente",
                            entrada.reclamoId(),
                            Map.of(
                                    "prorrogaHasta", entrada.prorrogaHasta().toString(),
                                    "comunicadaAlCliente",
                                            entrada.comunicadaAlCliente().toString()),
                            UUID.fromString(ctx.traza().id())));
            return null;
        });
    }

    @Transactional
    public SalidaReclamo responder(EntradaRespuesta entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var reclamo = reclamos.porId(dsl, entrada.reclamoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(52, 1), "Ese reclamo no existe."));

            // AP-CU52-03 · R-CON-04. Se comprueba antes de escribir para poder explicar
            // el rechazo, aunque ck_reclamo_reparacion tambien lo frenaria.
            if ("FAVORABLE".equals(entrada.resultado())
                    && reclamo.montoReclamado() != null
                    && entrada.devolucionId() == null) {
                throw new ErrorDeNegocio(
                        CodigoError.de(52, 3),
                        "Un reclamo favorable con monto exige la devolucion asociada (R-CON-04).");
            }

            if (!reclamos.cerrar(
                    dsl,
                    entrada.reclamoId(),
                    entrada.resultado(),
                    entrada.respuesta(),
                    entrada.devolucionId(),
                    ctx.usuarioId(),
                    ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(52, 1), "Ese reclamo ya estaba cerrado.");
            }

            OffsetDateTime limite =
                    reclamo.plazoProrrogadoHasta() != null ? reclamo.plazoProrrogadoHasta() : reclamo.plazoRespuesta();
            boolean fueraDePlazo = ahora.isAfter(limite);
            if (fueraDePlazo) {
                // AP-CU52-04: se responde igual, y el incumplimiento queda visible.
                gobierno.abrirHallazgo(
                        dsl,
                        "REC-" + entrada.reclamoId().toString().substring(0, 8),
                        "AUTOEVALUACION",
                        "Reclamo respondido fuera del plazo comprometido (" + limite + ").",
                        "MEDIA",
                        "ATENCION_AL_CONSUMIDOR",
                        ahora.toLocalDate(),
                        ahora.toLocalDate().plusDays(30));
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.reclamo_respondido",
                            "reclamo_cliente",
                            entrada.reclamoId(),
                            Map.of(
                                    "resultado", entrada.resultado(),
                                    "fueraDePlazo", Boolean.toString(fueraDePlazo),
                                    "conDevolucion", Boolean.toString(entrada.devolucionId() != null)),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaReclamo(entrada.reclamoId(), null, limite, diasHabilesDeRespuesta, "CERRADO");
        });
    }

    public record EntradaReclamo(
            UUID usuarioId,
            String puntoReclamoCodigo,
            String categoria,
            String producto,
            BigDecimal montoReclamado,
            String descripcion,
            String canalIngreso) {}

    public record EntradaProrroga(
            UUID reclamoId,
            OffsetDateTime prorrogaHasta,
            OffsetDateTime comunicadaAlCliente,
            OffsetDateTime comunicadaAlOrganismo,
            String justificacion) {}

    /**
     * @param devolucionId la devolucion la ejecuta el servicio de tarifas (CU-33) y
     *     llega resuelta: aca no se escribe su esquema (invariante 11)
     */
    public record EntradaRespuesta(UUID reclamoId, String resultado, String respuesta, UUID devolucionId) {}

    public record SalidaReclamo(
            UUID reclamoId, String codigo, OffsetDateTime plazoRespuesta, int diasHabilesPlazo, String estado) {}
}
