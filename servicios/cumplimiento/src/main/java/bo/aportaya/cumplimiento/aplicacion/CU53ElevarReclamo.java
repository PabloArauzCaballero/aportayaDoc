package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.infraestructura.ReclamoRepositorio;
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
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-53 · Elevar un reclamo a segunda instancia.
 *
 * <p>La decision de elevar **es del cliente, no nuestra**. Por eso la plataforma no la
 * rechaza aunque el plazo de la norma haya vencido: se registra con el vencimiento
 * marcado y que resuelva el organismo. Poner una traba aca seria usar un plazo procesal
 * para quedarnos con la ultima palabra.
 *
 * <p>Lo que si se exige es que **la primera instancia haya sido respondida**: elevar algo
 * que todavia estamos por contestar convertiria el trámite en una carrera.
 */
@Service
public class CU53ElevarReclamo {

    private final Datos datos;
    private final ReclamoRepositorio reclamos;
    private final Outbox outbox;
    private final Reloj reloj;
    private final String baseUrlDeExpedientes;

    /** Dias que la norma da al cliente para elevar tras la respuesta. */
    private final int diasParaElevar;

    public CU53ElevarReclamo(
            Datos datos,
            ReclamoRepositorio reclamos,
            Outbox outbox,
            Reloj reloj,
            String baseUrlDeExpedientes,
            int diasParaElevar) {
        this.datos = datos;
        this.reclamos = reclamos;
        this.outbox = outbox;
        this.reloj = reloj;
        this.baseUrlDeExpedientes = baseUrlDeExpedientes;
        this.diasParaElevar = diasParaElevar;
    }

    @Transactional
    public SalidaInstancia elevar(EntradaInstancia entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var reclamo = reclamos.porId(dsl, entrada.reclamoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(53, 1), "Ese reclamo no existe."));

            // AP-CU53-01: mientras la primera instancia corre, no hay que elevar nada.
            if (reclamo.fechaRespuesta() == null) {
                throw new ErrorDeNegocio(
                        CodigoError.de(53, 1),
                        "El reclamo todavia esta en plazo de primera instancia; aun no fue respondido.");
            }
            // AP-CU53-02.
            if (reclamos.instanciaAbierta(dsl, entrada.reclamoId(), entrada.instancia())
                    .isPresent()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(53, 2), "Ya hay una elevacion abierta ante " + entrada.instancia() + ".");
            }
            // AP-CU53-03: sin rastro tecnico no se puede sostener nada ante el
            // supervisor, y la ausencia de rastro es un hallazgo en si misma.
            if (!entrada.hayEvidenciaTecnica()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(53, 3),
                        "No hay rastro suficiente del caso: la elevacion se presentaria sin sustento.");
            }

            UUID id = reclamos.elevar(dsl, entrada.reclamoId(), entrada.instancia(), entrada.numeroExpediente(), ahora);

            // El plazo de la norma vencio, pero la elevacion NO se rechaza: se marca.
            boolean plazoVencido = ahora.isAfter(reclamo.fechaRespuesta().plusDays(diasParaElevar));

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.reclamo_elevado",
                            "instancia_reclamo",
                            id,
                            Map.of(
                                    "reclamoId", entrada.reclamoId().toString(),
                                    "instancia", entrada.instancia(),
                                    "plazoVencido", Boolean.toString(plazoVencido),
                                    "fechaElevacion", ahora.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaInstancia(
                    id, "PRESENTADA", baseUrlDeExpedientes + "/instancias/" + id, null, plazoVencido);
        });
    }

    /**
     * Resuelve la instancia.
     *
     * <p>Un resarcimiento **exige la transaccion que lo materializa**: la resolucion dice
     * cuanto, y sin el movimiento asociado el cliente tiene un papel que le da la razon
     * y no tiene la plata.
     */
    @Transactional
    public SalidaInstancia resolver(EntradaResolucion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        if (entrada.montoResarcido() != null
                && entrada.montoResarcido().signum() > 0
                && entrada.transaccionDelResarcimientoId() == null) {
            throw new ErrorDeNegocio(
                    CodigoError.de(53, 3),
                    "Un resarcimiento sin la transaccion que lo materializa es un papel, no una devolucion.");
        }

        return datos.conContexto(ctx, dsl -> {
            if (!reclamos.resolverInstancia(
                    dsl, entrada.instanciaId(), entrada.resolucion(), entrada.montoResarcido(), ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(53, 2), "Esa instancia ya fue resuelta o desistida.");
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.instancia_resuelta",
                            "instancia_reclamo",
                            entrada.instanciaId(),
                            Map.of(
                                    "montoResarcido",
                                            entrada.montoResarcido() == null
                                                    ? "0"
                                                    : entrada.montoResarcido().toPlainString(),
                                    "transaccionResarcimiento",
                                            entrada.transaccionDelResarcimientoId() == null
                                                    ? ""
                                                    : entrada.transaccionDelResarcimientoId()
                                                            .toString()),
                            UUID.fromString(ctx.traza().id())));
            return new SalidaInstancia(
                    entrada.instanciaId(),
                    "RESUELTA",
                    baseUrlDeExpedientes + "/instancias/" + entrada.instanciaId(),
                    entrada.montoResarcido(),
                    false);
        });
    }

    public record EntradaInstancia(
            UUID reclamoId, String instancia, String numeroExpediente, boolean hayEvidenciaTecnica) {}

    public record EntradaResolucion(
            UUID instanciaId, String resolucion, BigDecimal montoResarcido, UUID transaccionDelResarcimientoId) {}

    public record SalidaInstancia(
            UUID instanciaId,
            String estado,
            String expedienteUrl,
            BigDecimal montoResarcido,
            boolean plazoDeElevacionVencido) {}
}
