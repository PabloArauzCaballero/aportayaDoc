package bo.aportaya.grupos.aplicacion;

import bo.aportaya.grupos.dominio.SorteoDeterminista;
import bo.aportaya.grupos.infraestructura.SorteoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Ids;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-60 · Sortear los turnos, en dos actos: compromiso y revelacion.
 *
 * <p>El compromiso publica **solo el hash** de la semilla. Sin esa separacion, quien
 * ejecuta el sorteo podria probar semillas hasta que salga el orden que le conviene y
 * publicar esa: el compromiso es lo que convierte «confia en nosotros» en «verificalo
 * vos».
 *
 * <p>La revelacion va entera en una transaccion: **o hay turnos completos o no hay
 * ninguno**. Un sorteo a medias deja un grupo con tres turnos asignados y tres sin
 * asignar, y no hay forma de arreglarlo que no sea repetirlo entero.
 */
@Service
public class CU60Sortear {

    private static final String ALGORITMO = "FISHER_YATES_SHA256";

    private final Datos datos;
    private final SorteoRepositorio sorteos;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Ids ids;

    public CU60Sortear(Datos datos, SorteoRepositorio sorteos, Outbox outbox, Reloj reloj, Ids ids) {
        this.datos = datos;
        this.sorteos = sorteos;
        this.outbox = outbox;
        this.reloj = reloj;
        this.ids = ids;
    }

    /** Fase 1: se publica el hash y nada mas. La semilla la guarda quien ejecuta. */
    @Transactional
    public Compromiso comprometer(
            UUID grupoId, List<String> entropias, Optional<OffsetDateTime> fechaPrevista, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        // Azar criptografico: una semilla que se puede adivinar es un sorteo que se
        // puede adivinar, y el compromiso no protegeria de nada.
        String semilla = ids.nuevo().toString() + ids.nuevo();
        String hash = SorteoDeterminista.comprometer(semilla, entropias);

        return datos.conContexto(ctx, dsl -> {
            // AP-CU60-01: no se sortea un grupo a medio armar.
            if (!sorteos.estaConformado(dsl, grupoId)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(60, 1), "Todavia quedan cupos libres: el grupo no esta conformado.");
            }
            // AP-CU60-05: cada participante con su reglamento aceptado.
            if (!sorteos.todosAceptaronElReglamento(dsl, grupoId)) {
                throw new ErrorDeNegocio(CodigoError.de(60, 5), "Falta que alguien acepte el reglamento del grupo.");
            }
            // AP-CU60-02: el compromiso se publica una sola vez.
            if (sorteos.yaHuboSorteo(dsl, grupoId)) {
                throw new ErrorDeNegocio(CodigoError.de(60, 2), "Este grupo ya tiene su sorteo.");
            }
            UUID sorteoId = sorteos.comprometer(dsl, grupoId, hash, ALGORITMO, ctx.usuarioId(), ahora, fechaPrevista);
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "grupos.sorteo_comprometido",
                            "sorteo_turnos",
                            sorteoId,
                            Map.of("grupoId", grupoId.toString(), "hashSemilla", hash),
                            UUID.fromString(ctx.traza().id())));
            return new Compromiso(sorteoId, hash, semilla);
        });
    }

    /** Fase 2: se revela, se verifica y recien entonces se crean los turnos. */
    @Transactional
    public Revelacion revelar(
            UUID sorteoId,
            String semilla,
            List<String> entropias,
            List<UUID> periodosEnOrden,
            BigDecimal montoEstimado,
            Optional<OffsetDateTime> fechaPrevista,
            ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var compromiso = sorteos.compromisoDe(dsl, sorteoId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(60, 2), "Ese sorteo no existe."));

            // AP-CU60-03: no se revela antes de la fecha comprometida. El compromiso
            // pierde sentido si quien lo publico puede adelantarlo cuando le conviene.
            if (fechaPrevista.map(ahora::isBefore).orElse(false)) {
                throw new ErrorDeNegocio(CodigoError.de(60, 3), "Todavia no llego la fecha comprometida para revelar.");
            }

            if (!SorteoDeterminista.verifica(semilla, entropias, compromiso.hash())) {
                // Jamas se publica un resultado cuyo hash no cierra. El sorteo se
                // anula y se recomienza con semilla nueva; el anterior queda visible.
                sorteos.anular(dsl, sorteoId, ahora);
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "grupos.sorteo_anulado",
                                "sorteo_turnos",
                                sorteoId,
                                Map.of("grupoId", compromiso.grupoId().toString(), "motivo", "hash_no_verifica"),
                                UUID.fromString(ctx.traza().id())));
                return new Revelacion(sorteoId, List.of(), false);
            }

            List<UUID> enOrden = SorteoDeterminista.ordenar(sorteos.cuposPorNumero(dsl, compromiso.grupoId()), semilla);
            sorteos.crearTurnos(dsl, compromiso.grupoId(), periodosEnOrden, enOrden, montoEstimado);
            sorteos.revelar(dsl, sorteoId, semilla, comoJson(enOrden), ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "grupos.sorteo_revelado",
                            "sorteo_turnos",
                            sorteoId,
                            Map.of("grupoId", compromiso.grupoId().toString(), "semilla", semilla),
                            UUID.fromString(ctx.traza().id())));

            return new Revelacion(sorteoId, enOrden, true);
        });
    }

    private String comoJson(List<UUID> cupos) {
        return cupos.stream().map(c -> "\"" + c + "\"").collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    public record Compromiso(UUID sorteoId, String hashSemilla, String semilla) {}

    public record Revelacion(UUID sorteoId, List<UUID> cuposEnOrden, boolean verificado) {}
}
