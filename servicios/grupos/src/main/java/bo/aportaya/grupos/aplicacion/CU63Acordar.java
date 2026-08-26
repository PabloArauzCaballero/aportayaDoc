package bo.aportaya.grupos.aplicacion;

import bo.aportaya.grupos.dominio.ComputoDeVotacion;
import bo.aportaya.grupos.dominio.ComputoDeVotacion.Sentido;
import bo.aportaya.grupos.dominio.TipoDeAcuerdo;
import bo.aportaya.grupos.infraestructura.AcuerdoRepositorio;
import bo.aportaya.grupos.infraestructura.AcuerdoRepositorio.ComputoResumen;
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
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-63 · Proponer y votar un acuerdo.
 *
 * <p>Ningun efecto colectivo se ejecuta sin acuerdo aprobado, y el efecto va en la
 * MISMA transaccion que la aprobacion: si el efecto falla, el acuerdo no queda
 * aprobado sin su efecto — que seria un grupo convencido de que decidio algo que no
 * ocurrio.
 */
@Service
public class CU63Acordar {

    private final Datos datos;
    private final AcuerdoRepositorio acuerdos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU63Acordar(Datos datos, AcuerdoRepositorio acuerdos, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.acuerdos = acuerdos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public UUID proponer(EntradaPropuesta entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        TipoDeAcuerdo tipo = TipoDeAcuerdo.valueOf(entrada.tipo());

        return datos.conContexto(ctx, dsl -> {
            // No se vota dos veces lo mismo en paralelo: dos votaciones abiertas
            // sobre el mismo objeto pueden aprobar cosas contradictorias.
            if (acuerdos.hayAcuerdoAbierto(dsl, entrada.grupoId(), tipo.name(), entrada.afectado())) {
                throw new ErrorDeNegocio(CodigoError.de(63, 2), "Ya hay una votacion abierta sobre lo mismo.");
            }

            UUID acuerdoId = acuerdos.proponer(
                    dsl,
                    entrada.grupoId(),
                    tipo.name(),
                    entrada.descripcion(),
                    entrada.propuestoPor(),
                    entrada.quorumRequerido(),
                    entrada.afectado(),
                    ahora,
                    entrada.cierraEn());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "grupos.acuerdo_propuesto",
                            "acuerdo",
                            acuerdoId,
                            Map.of("grupoId", entrada.grupoId().toString(), "tipo", tipo.name()),
                            UUID.fromString(ctx.traza().id())));
            return acuerdoId;
        });
    }

    @Transactional
    public Sentido votar(UUID acuerdoId, UUID participanteId, String sentidoPedido, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var acuerdo = acuerdos.porId(dsl, acuerdoId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(63, 1), "Ese acuerdo no existe."));

            if (!"ABIERTO".equals(acuerdo.estado()) || ahora.isAfter(acuerdo.cierraEn())) {
                throw new ErrorDeNegocio(CodigoError.de(63, 4), "La votacion ya esta cerrada.");
            }

            TipoDeAcuerdo tipo = TipoDeAcuerdo.valueOf(acuerdo.tipo());
            boolean esParteInteresada = tipo.tieneParteInteresada()
                    && acuerdo.afectado().map(participanteId::equals).orElse(false);

            // El afectado vota, y su voto QUEDA REGISTRADO: se anota como abstencion
            // con peso cero. Impedirle votar lo dejaria sin constancia de que se
            // presento; dejarlo ponderar convertiria el quorum en una formalidad.
            Sentido sentido = esParteInteresada ? Sentido.ABSTENCION : Sentido.valueOf(sentidoPedido);
            BigDecimal peso = esParteInteresada ? BigDecimal.ZERO : acuerdos.pesoDe(dsl, participanteId);

            acuerdos.votar(dsl, acuerdoId, participanteId, sentido.name(), peso, ahora);
            return sentido;
        });
    }

    /** Al alcanzarse el quorum o vencer el plazo: computa, resuelve y emite. */
    @Transactional
    public String resolver(UUID acuerdoId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var acuerdo = acuerdos.porId(dsl, acuerdoId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(63, 1), "Ese acuerdo no existe."));

            ComputoDeVotacion computo = ComputoDeVotacion.de(
                    acuerdos.votosDe(dsl, acuerdoId), acuerdos.pesoTotalDelGrupo(dsl, acuerdo.grupoId()));

            // Sin quorum al vencer el plazo el acuerdo EXPIRA: no se ejecuta nada, y
            // el modelo no tiene un estado «rechazado por quorum» que inventar.
            String estado;
            if (computo.alcanza(acuerdo.quorum())) {
                estado = "APROBADO";
            } else if (ahora.isAfter(acuerdo.cierraEn())) {
                estado = "EXPIRADO";
            } else {
                estado = "RECHAZADO";
            }

            acuerdos.resolver(dsl, acuerdoId, estado, resumen(computo), ahora);
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "grupos.acuerdo_resuelto",
                            "acuerdo",
                            acuerdoId,
                            Map.of("grupoId", acuerdo.grupoId().toString(), "estado", estado),
                            UUID.fromString(ctx.traza().id())));
            return estado;
        });
    }

    private ComputoResumen resumen(ComputoDeVotacion computo) {
        return new ComputoResumen(
                computo.aFavor().shortValue(),
                computo.enContra().shortValue(),
                computo.abstenciones().shortValue());
    }

    public record EntradaPropuesta(
            UUID grupoId,
            String tipo,
            String descripcion,
            UUID propuestoPor,
            BigDecimal quorumRequerido,
            Optional<UUID> afectado,
            OffsetDateTime cierraEn) {}
}
