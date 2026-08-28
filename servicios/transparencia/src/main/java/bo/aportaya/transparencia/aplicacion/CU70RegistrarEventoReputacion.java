package bo.aportaya.transparencia.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.transparencia.infraestructura.ModeloRepositorio;
import bo.aportaya.transparencia.infraestructura.ReputacionRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-70 · Registrar un evento de reputacion.
 *
 * <p>**Un evento por hecho** (R-REP-01): no se puntua dos veces lo mismo. Un aporte
 * puntual contado dos veces le sube el score a alguien por algo que hizo una sola vez, y
 * el score deja de significar lo que dice.
 *
 * <p>El impacto sale de {@code regla_impacto_evento} —catalogo, invariante 10—, no de
 * constantes en el codigo. Y **sin regla no se puntua**: inventar un impacto para un
 * tipo de evento que nadie configuro es decidir a mano cuanto vale la conducta de
 * alguien.
 */
@Service
public class CU70RegistrarEventoReputacion {

    private final Datos datos;
    private final ReputacionRepositorio reputaciones;
    private final ModeloRepositorio modelos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU70RegistrarEventoReputacion(
            Datos datos, ReputacionRepositorio reputaciones, ModeloRepositorio modelos, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.reputaciones = reputaciones;
        this.modelos = modelos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaEvento registrar(EntradaEvento entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // R-REP-01 · invariante 7: la clave se valida ANTES de escribir.
            var repetido = reputaciones.eventoDelHecho(
                    dsl, entrada.usuarioId(), entrada.referenciaTipo(), entrada.referenciaId(), entrada.tipo());
            if (repetido.isPresent()) {
                return new SalidaEvento(repetido.get(), BigDecimal.ZERO, null, false);
            }

            var modelo = modelos.modeloVigente(dsl, ahora)
                    .orElseThrow(
                            () -> new ErrorDeNegocio(CodigoError.de(70, 2), "No hay modelo de scoring en produccion."));
            // AP-CU70-01: sin regla no se puntua. Inventar un impacto es decidir a mano
            // cuanto vale la conducta de alguien.
            var regla = modelos.reglaDe(dsl, modelo.id(), entrada.tipo())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(70, 1),
                            "El modelo " + modelo.version() + " no tiene regla para «" + entrada.tipo() + "»."));

            // La reincidencia multiplica, con tope. Sin tope, el quinto incumplimiento
            // hundiria el score de alguien mas que los cuatro anteriores juntos, y eso
            // no lo puede explicar nadie.
            int repeticiones = reputaciones.repeticionesDe(dsl, entrada.usuarioId(), entrada.tipo());
            BigDecimal impacto = regla.impactoBase();
            if (repeticiones > 0) {
                impacto = impacto.multiply(regla.multiplicadorPorReincidencia().pow(Math.min(repeticiones, 3)));
            }
            impacto = impacto.setScale(2, RoundingMode.HALF_EVEN);
            if (impacto.abs().compareTo(regla.impactoMaximo().abs()) > 0) {
                impacto = regla.impactoMaximo();
            }

            UUID eventoId = reputaciones.registrarEvento(
                    dsl,
                    entrada.usuarioId(),
                    entrada.grupoId(),
                    entrada.participanteId(),
                    entrada.tipo(),
                    entrada.referenciaTipo(),
                    entrada.referenciaId(),
                    impacto,
                    regla.codigoFactor(),
                    entrada.descripcion(),
                    modelo.version(),
                    entrada.esReversible(),
                    entrada.ocurridoEn());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "transparencia.evento_reputacion_registrado",
                            "evento_reputacion",
                            eventoId,
                            Map.of(
                                    "usuarioId", entrada.usuarioId().toString(),
                                    "tipo", entrada.tipo(),
                                    "impacto", impacto.toPlainString(),
                                    "factorAfectado", regla.codigoFactor(),
                                    "modeloVersion", modelo.version()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaEvento(eventoId, impacto, regla.codigoFactor(), true);
        });
    }

    /**
     * El evento compensatorio de uno que ya sumo o resto puntos.
     *
     * <p>**No se borra el original ni se lo edita**: {@code evento_reputacion} es
     * append-only, y ademas borrarlo dejaria un puntaje que nadie puede reconstruir.
     * Entra una fila nueva con el impacto exacto invertido —el que se aplico, no el que
     * la regla de hoy diria— y con {@code revertido_por_id} apuntando al original.
     *
     * <p>El impacto se invierte **desde el evento original**. Recalcularlo con la regla
     * vigente dejaria un residuo cada vez que el modelo cambia: se habrian sumado 5
     * puntos y restado 7.
     */
    @Transactional
    public SalidaEvento compensar(UUID eventoOriginalId, String descripcion, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var original = reputaciones
                    .eventoPorId(dsl, eventoOriginalId)
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(70, 3), "No existe el evento de reputacion a compensar."));
            if (!original.esReversible()) {
                throw new ErrorDeNegocio(CodigoError.de(70, 4), "Ese evento se registro como no reversible.");
            }
            // La unicidad de R-REP-01 no cubre las compensaciones: llevan
            // referencia_origen_id nulo y la base las dejaria repetirse. Se comprueba
            // aca, antes de escribir (invariante 7).
            var yaCompensado = reputaciones.compensacionDe(dsl, eventoOriginalId);
            if (yaCompensado.isPresent()) {
                return new SalidaEvento(yaCompensado.get(), BigDecimal.ZERO, original.factorAfectado(), false);
            }

            BigDecimal impacto = original.impacto().negate();
            UUID id = reputaciones.registrarCompensacion(
                    dsl,
                    original,
                    impacto,
                    descripcion,
                    eventoOriginalId,
                    reloj.ahora().atOffset(ZoneOffset.UTC));

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "transparencia.evento_reputacion_compensado",
                            "evento_reputacion",
                            id,
                            Map.of(
                                    "usuarioId", original.usuarioId().toString(),
                                    "revertidoPorId", eventoOriginalId.toString(),
                                    "impacto", impacto.toPlainString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaEvento(id, impacto, original.factorAfectado(), true);
        });
    }

    public record EntradaEvento(
            UUID usuarioId,
            UUID grupoId,
            UUID participanteId,
            String tipo,
            String referenciaTipo,
            UUID referenciaId,
            String descripcion,
            boolean esReversible,
            OffsetDateTime ocurridoEn) {}

    public record SalidaEvento(UUID eventoId, BigDecimal impacto, String factorAfectado, boolean esNuevo) {}
}
