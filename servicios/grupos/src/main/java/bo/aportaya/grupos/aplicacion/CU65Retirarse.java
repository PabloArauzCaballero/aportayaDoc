package bo.aportaya.grupos.aplicacion;

import bo.aportaya.grupos.dominio.PosicionAlRetirarse;
import bo.aportaya.grupos.infraestructura.RetiroRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-65 · Retirarse de un grupo.
 *
 * <p>El numero exacto se calcula y se **muestra antes de confirmar**, con su
 * desglose. Un retiro donde el monto aparece despues es un retiro que alguien va a
 * discutir, y con razon.
 *
 * <p>Los importes vienen de afuera —lo aportado y la deuda viven en
 * `nucleo-financiero` y `garantia`— porque este servicio no lee esos esquemas
 * (invariante 11). Lo que aporta `grupos` es la decision: quien debe a quien.
 */
@Service
public class CU65Retirarse {

    private final Datos datos;
    private final RetiroRepositorio retiros;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU65Retirarse(Datos datos, RetiroRepositorio retiros, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.retiros = retiros;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaRetiro solicitar(EntradaRetiro entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            String estado = retiros.estadoDelParticipante(dsl, entrada.participanteId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(65, 1), "Ese participante no existe."));
            if (!"ACTIVO".equals(estado)) {
                throw new ErrorDeNegocio(CodigoError.de(65, 1), "Ese participante ya no esta activo en el grupo.");
            }

            boolean arranco = "EN_CURSO".equals(retiros.estadoDelGrupoDe(dsl, entrada.participanteId()));
            PosicionAlRetirarse posicion = PosicionAlRetirarse.calcular(
                    entrada.yaCobroSuTurno(),
                    arranco,
                    entrada.totalAportado(),
                    entrada.deudaVigente(),
                    entrada.aportesRestantesDelCiclo());

            UUID solicitud = retiros.solicitar(
                    dsl,
                    entrada.participanteId(),
                    entrada.motivo(),
                    posicion.tipo().name(),
                    posicion.tipo() == PosicionAlRetirarse.Tipo.ACREEDORA && arranco,
                    posicion.monto().monto(),
                    ahora);

            return new SalidaRetiro(solicitud, posicion);
        });
    }

    /** Aprobar, retirar y liberar el cupo van juntos: a medias queda un cupo fantasma. */
    @Transactional
    public void aprobar(
            UUID solicitudId,
            UUID participanteId,
            PosicionAlRetirarse posicion,
            Optional<UUID> planDePago,
            String motivo,
            ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        datos.conContexto(ctx, dsl -> {
            // Sin plan de pago, un retiro deudor no se aprueba: dejarlo irse seria
            // dejar que el resto del grupo le cubra lo que falta.
            if (posicion.exigePlanDePago() && planDePago.isEmpty()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(65, 2), "Para retirarte con saldo pendiente hace falta un plan de pago.");
            }

            retiros.aprobar(dsl, solicitudId, planDePago);
            retiros.retirar(dsl, participanteId, motivo, ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "grupos.participante_retirado",
                            "participante",
                            participanteId,
                            Map.of(
                                    "solicitudId",
                                    solicitudId.toString(),
                                    "posicion",
                                    posicion.tipo().name()),
                            UUID.fromString(ctx.traza().id())));
            return null;
        });
    }

    public record EntradaRetiro(
            UUID participanteId,
            String motivo,
            boolean yaCobroSuTurno,
            Dinero totalAportado,
            Dinero deudaVigente,
            Dinero aportesRestantesDelCiclo) {}

    public record SalidaRetiro(UUID solicitudId, PosicionAlRetirarse posicion) {}
}
