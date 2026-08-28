package bo.aportaya.garantia.aplicacion;

import bo.aportaya.garantia.infraestructura.ExpedienteRepositorio;
import bo.aportaya.garantia.infraestructura.FondoRepositorio;
import bo.aportaya.garantia.infraestructura.GestionRepositorio;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-66 · Reemplazar a un participante moroso.
 *
 * <p>Un cupo vacio a mitad del pasanaku es plata que falta cada periodo. Reemplazarlo
 * mantiene el grupo en pie — pero **la deuda del saliente no se le traslada al
 * entrante** salvo que este la asuma expresamente. Nadie entra a un grupo a pagar lo
 * que otro dejo sin decirselo.
 *
 * <p>Lo que el entrante **no** asume queda como deuda del saliente. Si se perdonara,
 * la perdida la absorberian los demas participantes sin enterarse.
 */
@Service
public class CU66ReemplazarParticipante {

    private final Datos datos;
    private final GestionRepositorio gestion;
    private final FondoRepositorio fondos;
    private final ExpedienteRepositorio expedientes;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU66ReemplazarParticipante(
            Datos datos,
            GestionRepositorio gestion,
            FondoRepositorio fondos,
            ExpedienteRepositorio expedientes,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.gestion = gestion;
        this.fondos = fondos;
        this.expedientes = expedientes;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaReemplazo proponer(EntradaReemplazo entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var expediente = expedientes
                    .ver(dsl, entrada.expedienteId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(66, 1), "Ese expediente no existe."));

            Dinero deudaTotal = fondos.deudaDe(dsl, expediente.id())
                    .map(FondoRepositorio.Deuda::saldoActual)
                    .orElse(expediente.montoInvolucrado());

            // AP-CU66-02: el entrante no puede asumir mas de lo que hay. Asumir de mas
            // seria cobrarle una deuda que no existe.
            if (entrada.deudaQueAsumeElEntrante().esMayorQue(deudaTotal)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(66, 2),
                        "El entrante no puede asumir " + entrada.deudaQueAsumeElEntrante() + ": la deuda es de "
                                + deudaTotal + ".");
            }
            // AP-CU66-03: el entrante no es el saliente. Un reemplazo consigo mismo no
            // resuelve nada y borra el rastro del incumplimiento.
            if (entrada.entranteId() != null && entrada.entranteId().equals(expediente.participanteId())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(66, 3), "El entrante no puede ser el mismo participante que sale.");
            }

            Dinero retenida = deudaTotal.menos(entrada.deudaQueAsumeElEntrante());
            String estado = entrada.entranteId() == null ? "BUSCANDO" : "PROPUESTO";

            UUID reemplazoId = gestion.proponerReemplazo(
                    dsl,
                    expediente.grupoId(),
                    entrada.cupoId(),
                    expediente.id(),
                    expediente.participanteId(),
                    entrada.entranteId(),
                    entrada.deudaQueAsumeElEntrante(),
                    retenida,
                    entrada.conservaOrdenDeTurno(),
                    estado,
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "garantia.reemplazo_propuesto",
                            "reemplazo_participante",
                            reemplazoId,
                            Map.of(
                                    "grupoId", expediente.grupoId().toString(),
                                    "salienteId", expediente.participanteId().toString(),
                                    "deudaAsumida",
                                            entrada.deudaQueAsumeElEntrante().toString(),
                                    // Lo que el entrante NO asume queda como deuda del
                                    // saliente: perdonarla se la cobraria a los demas.
                                    "deudaRetenidaPorElSaliente", retenida.toString(),
                                    "estado", estado),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaReemplazo(reemplazoId, estado, entrada.deudaQueAsumeElEntrante(), retenida, true);
        });
    }

    /** Ejecuta el reemplazo aprobado. El cupo cambia de manos. */
    @Transactional
    public SalidaReemplazo ejecutar(UUID reemplazoId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            if (!gestion.cambiarEstadoDeReemplazo(dsl, reemplazoId, "APROBADO", "EJECUTADO")) {
                throw new ErrorDeNegocio(CodigoError.de(66, 4), "Ese reemplazo no estaba aprobado: no se ejecuta.");
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "garantia.reemplazo_ejecutado",
                            "reemplazo_participante",
                            reemplazoId,
                            Map.of("ejecutadoPor", ctx.usuarioId().toString(), "momento", ahora.toString()),
                            UUID.fromString(ctx.traza().id())));
            return new SalidaReemplazo(reemplazoId, "EJECUTADO", null, null, false);
        });
    }

    /** Aprueba el reemplazo propuesto: es el grupo el que decide quien entra. */
    @Transactional
    public boolean aprobar(UUID reemplazoId, ContextoSesion ctx) {
        return datos.conContexto(
                ctx, dsl -> gestion.cambiarEstadoDeReemplazo(dsl, reemplazoId, "PROPUESTO", "APROBADO"));
    }

    public record EntradaReemplazo(
            UUID expedienteId,
            UUID cupoId,
            UUID entranteId,
            Dinero deudaQueAsumeElEntrante,
            boolean conservaOrdenDeTurno) {}

    public record SalidaReemplazo(
            UUID reemplazoId,
            String estado,
            Dinero deudaAsumidaPorElEntrante,
            Dinero deudaRetenidaPorElSaliente,
            boolean esNuevo) {}
}
