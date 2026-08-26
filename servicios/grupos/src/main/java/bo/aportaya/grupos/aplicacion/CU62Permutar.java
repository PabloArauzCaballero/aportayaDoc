package bo.aportaya.grupos.aplicacion;

import bo.aportaya.grupos.dominio.PermutaPosible;
import bo.aportaya.grupos.infraestructura.PermutaRepositorio;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-62 · Permutar turnos entre participantes.
 *
 * <p>Sin aceptacion de la contraparte no hay nada: la solicitud nace
 * {@code PENDIENTE} y el intercambio ocurre recien cuando el otro acepta.
 *
 * <p>El intercambio va entero en una transaccion. A medias, un grupo quedaria con dos
 * turnos en la misma posicion o con un periodo sin beneficiario — y las dos cosas las
 * rechaza el modelo, que es justamente por lo que hay que hacerlo de una vez.
 *
 * <p>Si alguna parte tiene deuda, **no se permuta**: adelantar el cobro de un moroso
 * lo paga el grupo entero.
 */
@Service
public class CU62Permutar {

    private final Datos datos;
    private final PermutaRepositorio permutas;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU62Permutar(Datos datos, PermutaRepositorio permutas, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.permutas = permutas;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public UUID solicitar(EntradaPermuta entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var origen = permutas.turno(dsl, entrada.turnoOrigenId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(62, 1), "Ese turno no existe."));
            var destino = permutas.turno(dsl, entrada.turnoDestinoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(62, 1), "Ese turno no existe."));

            PermutaPosible.impedimento(
                            origen.estado(),
                            destino.estado(),
                            entrada.solicitanteAlDia(),
                            entrada.contraparteAlDia(),
                            entrada.elReglamentoLoPermite())
                    .ifPresent(motivo -> {
                        throw new ErrorDeNegocio(CodigoError.de(62, motivo.numero()), motivo.mensaje());
                    });

            return permutas.solicitar(
                    dsl,
                    entrada.turnoOrigenId(),
                    entrada.turnoDestinoId(),
                    entrada.solicitanteId(),
                    entrada.contraparteId(),
                    entrada.motivo(),
                    ahora);
        });
    }

    /** La contraparte acepta: recien aca se intercambian los dos turnos. */
    @Transactional
    public void aceptar(UUID solicitudId, UUID turnoOrigenId, UUID turnoDestinoId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        datos.conContexto(ctx, dsl -> {
            var origen = permutas.turno(dsl, turnoOrigenId).orElseThrow();
            var destino = permutas.turno(dsl, turnoDestinoId).orElseThrow();

            PermutaPosible.impedimento(origen.estado(), destino.estado(), true, true, true)
                    .ifPresent(motivo -> {
                        throw new ErrorDeNegocio(CodigoError.de(62, motivo.numero()), motivo.mensaje());
                    });

            permutas.intercambiar(dsl, origen, destino);
            permutas.marcar(dsl, solicitudId, "EJECUTADA", ahora);

            // Se notifica a TODO el grupo, no solo a los dos implicados: el calendario
            // de turnos es informacion comun, y enterarse tarde de que cambio es lo
            // que hace que la gente desconfie del que organiza.
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "grupos.turnos_permutados",
                            "solicitud_permuta",
                            solicitudId,
                            Map.of("grupoId", origen.grupoId().toString()),
                            UUID.fromString(ctx.traza().id())));
            return null;
        });
    }

    public record EntradaPermuta(
            UUID turnoOrigenId,
            UUID turnoDestinoId,
            UUID solicitanteId,
            UUID contraparteId,
            String motivo,
            boolean solicitanteAlDia,
            boolean contraparteAlDia,
            boolean elReglamentoLoPermite) {}
}
