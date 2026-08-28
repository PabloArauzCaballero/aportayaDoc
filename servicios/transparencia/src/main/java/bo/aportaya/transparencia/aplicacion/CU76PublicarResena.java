package bo.aportaya.transparencia.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.transparencia.dominio.ModeracionDeResena;
import bo.aportaya.transparencia.infraestructura.ResenaRepositorio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-76 · Resenar a un participante y moderar la resena.
 *
 * <p>Una resena es la unica pieza de este servicio que **no sale de un hecho medible**,
 * y por eso es la que mas cuidado necesita. Las defensas:
 *
 * <ul>
 *   <li>**Solo resena quien convivio** (R-REP-06, lo verifica {@code
 *       tg_resena_convivencia}). Sin convivencia, la resena es una opinion sobre un
 *       desconocido, o una represalia.
 *   <li>**Nadie se resena a si mismo**, y una vez por autor, evaluado, grupo y
 *       dimension.
 *   <li>**Se resena al cerrar el ciclo**, no durante. Resenar a quien todavia te debe
 *       plata convierte la resena en una herramienta de presion.
 *   <li>**Nada se publica sin moderar**, y lo que trae datos personales se retiene, no
 *       se recorta: recortar deja el dato en la base.
 *   <li>**Una opinion pesa menos que un pago.** El peso de la resena es una fraccion
 *       del de los factores de pago; si no, tres personas enojadas pesarian mas que un
 *       ano de aportes puntuales.
 * </ul>
 */
@Service
public class CU76PublicarResena {

    private final Datos datos;
    private final ResenaRepositorio resenas;
    private final Outbox outbox;
    private final Reloj reloj;
    private final BigDecimal pesoBaseDeResena;

    /** Cuanto se atenua una opinion por conflicto o por volumen. Configuracion, no constante. */
    private final ModeracionDeResena.Atenuacion atenuacion;

    public CU76PublicarResena(
            Datos datos,
            ResenaRepositorio resenas,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.resenas.peso-base}") BigDecimal pesoBaseDeResena,
            ModeracionDeResena.Atenuacion atenuacion) {
        this.datos = datos;
        this.resenas = resenas;
        this.outbox = outbox;
        this.reloj = reloj;
        this.pesoBaseDeResena = pesoBaseDeResena;
        this.atenuacion = atenuacion;
    }

    @Transactional
    public SalidaResena publicar(EntradaResena entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        // AP-CU76-02: el ciclo tiene que estar cerrado. El estado del grupo vive en
        // otro esquema y llega resuelto (invariante 11).
        if (!entrada.cicloCerrado()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(76, 2), "Las resenas se escriben al cerrar el ciclo, no mientras corre.");
        }
        // AP-CU76-06: la ventana existe para que la resena hable de lo que se recuerda.
        if (entrada.cerradoEn() != null && ahora.isAfter(entrada.cerradoEn().plusDays(entrada.diasDeVentana()))) {
            throw new ErrorDeNegocio(
                    CodigoError.de(76, 6),
                    "El plazo para resenar vencio el "
                            + entrada.cerradoEn()
                                    .plusDays(entrada.diasDeVentana())
                                    .toLocalDate() + ".");
        }

        var veredicto = ModeracionDeResena.revisar(entrada.comentario());
        BigDecimal peso = ModeracionDeResena.peso(
                pesoBaseDeResena, entrada.autorFueExpulsado(), entrada.resenasPreviasDelAutor(), atenuacion);

        return datos.conContexto(ctx, dsl -> {
            var duplicada = resenas.resenaDe(
                    dsl,
                    entrada.grupoId(),
                    entrada.autorParticipanteId(),
                    entrada.evaluadoUsuarioId(),
                    entrada.dimension());
            if (duplicada.isPresent()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(76, 3), "Ya resenaste a esa persona en ese grupo y esa dimension.");
            }

            UUID resenaId;
            try {
                resenaId = resenas.resenar(
                        dsl,
                        entrada.grupoId(),
                        entrada.autorParticipanteId(),
                        entrada.evaluadoUsuarioId(),
                        entrada.calificacion(),
                        entrada.comentario(),
                        entrada.dimension(),
                        // Todo nace PENDIENTE. Incluso lo que la maquina no marco: la
                        // primera pasada es una ayuda, no una autorizacion.
                        "PENDIENTE",
                        ahora);
            } catch (org.jooq.exception.DataAccessException | org.springframework.dao.DataAccessException e) {
                // tg_resena_convivencia levanta las dos negativas de R-REP-06 con el
                // mismo prefijo; lo que las separa es que una nombra al propio autor.
                // Se distingue por «mismo» y no por la frase entera porque el mensaje de
                // la base lleva tildes y no conviene atarse a como se escriben.
                String causa = raizDe(e);
                if (causa != null && causa.contains("R-REP-06")) {
                    if (causa.contains("mismo")) {
                        throw new ErrorDeNegocio(CodigoError.de(76, 4), "Nadie puede resenarse a si mismo (R-REP-06).");
                    }
                    throw new ErrorDeNegocio(CodigoError.de(76, 1), "No compartiste grupo con esa persona (R-REP-06).");
                }
                throw new ErrorDeNegocio(
                        CodigoError.de(76, 3), "Ya resenaste a esa persona en ese grupo y esa dimension.");
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "transparencia.resena_creada",
                            "resena_participante",
                            resenaId,
                            Map.of(
                                    "evaluadoUsuarioId",
                                            entrada.evaluadoUsuarioId().toString(),
                                    "dimension", entrada.dimension(),
                                    "retenidaPorRevision", Boolean.toString(veredicto.retener()),
                                    "marcas", String.join(",", veredicto.marcas()),
                                    "pesoEnReputacion", peso.toPlainString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaResena(resenaId, "PENDIENTE", peso, null, veredicto.marcas());
        });
    }

    /**
     * La decision humana. **Solo al publicar** entra el evento de reputacion: una
     * resena pendiente no mueve el puntaje de nadie.
     */
    @Transactional
    public SalidaResena moderar(EntradaModeracion entrada, ContextoSesion ctx) {
        String estado =
                switch (entrada.decision()) {
                    case "APROBAR" -> "PUBLICADA";
                    case "RECHAZAR" -> "RECHAZADA";
                    // El comentario se oculta pero la calificacion se conserva: el dato
                    // personal desaparece de la vista, la opinion sigue contando.
                    case "PUBLICAR_SIN_COMENTARIO" -> "OCULTA";
                    default ->
                        throw new ErrorDeNegocio(
                                CodigoError.de(76, 5), "Decision de moderacion desconocida: " + entrada.decision());
                };

        return datos.conContexto(ctx, dsl -> {
            if (!resenas.moderar(dsl, entrada.resenaId(), estado, ctx.usuarioId())) {
                throw new ErrorDeNegocio(CodigoError.de(76, 5), "Esa resena ya fue moderada.");
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "RECHAZADA".equals(estado)
                                    ? "transparencia.resena_rechazada"
                                    : "transparencia.resena_publicada",
                            "resena_participante",
                            entrada.resenaId(),
                            Map.of(
                                    "estadoModeracion", estado,
                                    "motivo", entrada.motivo(),
                                    "pesoEnReputacion", pesoBaseDeResena.toPlainString()),
                            UUID.fromString(ctx.traza().id())));
            return new SalidaResena(
                    entrada.resenaId(), estado, pesoBaseDeResena, entrada.motivo(), java.util.List.of());
        });
    }

    /** El mensaje del fondo del pozo: es donde la base dice que regla se rompio. */
    private static String raizDe(Throwable e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return String.valueOf(raiz.getMessage());
    }

    public record EntradaResena(
            UUID grupoId,
            UUID autorParticipanteId,
            UUID evaluadoUsuarioId,
            int calificacion,
            String dimension,
            String comentario,
            boolean cicloCerrado,
            OffsetDateTime cerradoEn,
            int diasDeVentana,
            boolean autorFueExpulsado,
            int resenasPreviasDelAutor) {}

    public record EntradaModeracion(UUID resenaId, String decision, String motivo) {}

    public record SalidaResena(
            UUID resenaId,
            String estadoModeracion,
            BigDecimal pesoEnReputacion,
            String motivoModeracion,
            java.util.List<String> marcas) {}
}
