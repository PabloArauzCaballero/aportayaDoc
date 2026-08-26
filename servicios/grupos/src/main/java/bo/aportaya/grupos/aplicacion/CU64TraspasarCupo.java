package bo.aportaya.grupos.aplicacion;

import bo.aportaya.grupos.dominio.TraspasoAdmisible;
import bo.aportaya.grupos.infraestructura.TraspasoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
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
 * CU-64 · Traspasar un cupo.
 *
 * <p>Dos afirmaciones que sostienen todo lo demas:
 *
 * <ul>
 *   <li><b>El turno no se toca.</b> La posicion en el calendario es del cupo, no de
 *       la persona: si se moviera, traspasar seria una forma de adelantar el propio
 *       turno, y el sorteo dejaria de significar nada.
 *   <li><b>La deuda no viaja con el cupo.</b> Las obligaciones vencidas se quedan con
 *       quien las genero; solo las futuras pasan al entrante.
 * </ul>
 */
@Service
public class CU64TraspasarCupo {

    private final Datos datos;
    private final TraspasoRepositorio traspasos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU64TraspasarCupo(Datos datos, TraspasoRepositorio traspasos, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.traspasos = traspasos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public UUID ejecutar(EntradaTraspaso entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var cupo = traspasos
                    .estadoDelCupo(dsl, entrada.cupoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(64, 1), "Ese cupo no existe."));

            TraspasoAdmisible.impedimento(
                            cupo.estado(),
                            cupo.turnoCobrado(),
                            entrada.salienteAlDia(),
                            entrada.kycDelEntrante(),
                            entrada.kycMinimoDelGrupo(),
                            entrada.reputacionDelEntrante(),
                            entrada.reputacionMinimaDelGrupo(),
                            entrada.hayAcuerdoSiSeExige())
                    .ifPresent(motivo -> {
                        throw new ErrorDeNegocio(CodigoError.de(64, motivo.numero()), motivo.mensaje());
                    });

            UUID saliente = cupo.participanteId()
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(64, 1), "Ese cupo no tiene titular."));

            UUID traspaso = traspasos.registrar(
                    dsl,
                    entrada.cupoId(),
                    saliente,
                    entrada.entranteId(),
                    entrada.motivo(),
                    entrada.derechoDeCobroTransferido(),
                    entrada.acuerdoId(),
                    ahora);

            traspasos.traspasar(dsl, entrada.cupoId(), entrada.entranteId(), saliente, entrada.motivo(), ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "grupos.cupo_traspasado",
                            "traspaso_cupo",
                            traspaso,
                            Map.of(
                                    "grupoId",
                                    cupo.grupoId().toString(),
                                    "cupoId",
                                    entrada.cupoId().toString()),
                            UUID.fromString(ctx.traza().id())));
            return traspaso;
        });
    }

    public record EntradaTraspaso(
            UUID cupoId,
            UUID entranteId,
            String motivo,
            boolean salienteAlDia,
            String kycDelEntrante,
            String kycMinimoDelGrupo,
            int reputacionDelEntrante,
            int reputacionMinimaDelGrupo,
            boolean hayAcuerdoSiSeExige,
            boolean derechoDeCobroTransferido,
            Optional<UUID> acuerdoId) {}
}
