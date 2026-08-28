package bo.aportaya.erp.aplicacion;

import bo.aportaya.erp.infraestructura.PresupuestoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-101 · Presupuestar por centro de costo.
 *
 * <p>**Un presupuesto por centro de costo y ejercicio** (R-CTB-03). Dos presupuestos
 * vigentes del mismo centro dejan a cada area eligiendo cual mirar, y el control de
 * ejecucion deja de significar algo.
 *
 * <p>Y **aprobar deja firma y fecha** ({@code ck_presupuesto_aprobacion}): un presupuesto
 * aprobado sin saber por quien no compromete a nadie.
 */
@Service
public class CU101Presupuestar {

    private final Datos datos;
    private final PresupuestoRepositorio presupuestos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU101Presupuestar(Datos datos, PresupuestoRepositorio presupuestos, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.presupuestos = presupuestos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaPresupuesto crear(EntradaPresupuesto entrada, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            UUID id;
            try {
                id = presupuestos.crearPresupuesto(
                        dsl, entrada.centroCostoId(), entrada.ejercicioFiscalId(), entrada.nombre());
            } catch (org.jooq.exception.IntegrityConstraintViolationException
                    | org.springframework.dao.DataIntegrityViolationException e) {
                throw new ErrorDeNegocio(
                        CodigoError.de(101, 2),
                        "Ese centro de costo ya tiene presupuesto para el ejercicio (R-CTB-03).");
            }

            BigDecimal total = BigDecimal.ZERO;
            for (var partida : entrada.partidas()) {
                try {
                    presupuestos.agregarPartida(
                            dsl,
                            id,
                            partida.cuentaContableId(),
                            partida.periodoContableId(),
                            partida.monto(),
                            entrada.moneda());
                } catch (org.jooq.exception.IntegrityConstraintViolationException
                        | org.springframework.dao.DataIntegrityViolationException e) {
                    // uq_partida_presupuesto_cuenta_periodo: la misma cuenta y el mismo
                    // mes dos veces sumarian el doble sin que se note en el total.
                    throw new ErrorDeNegocio(
                            CodigoError.de(101, 2),
                            "Hay dos partidas para la misma cuenta y periodo: el total quedaria inflado.");
                }
                total = total.add(partida.monto());
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.presupuesto_creado",
                            "presupuesto",
                            id,
                            Map.of(
                                    "centroCostoId", entrada.centroCostoId().toString(),
                                    "partidas",
                                            Integer.toString(entrada.partidas().size()),
                                    "total", total.toPlainString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaPresupuesto(id, "BORRADOR", entrada.partidas().size(), total);
        });
    }

    /**
     * Agrega una partida a un presupuesto que todavia se puede editar.
     *
     * <p>Un presupuesto CERRADO no recibe partidas nuevas: agregar despues del cierre
     * cambiaria la base contra la que ya se midio la ejecucion, y el area cuyo desvio se
     * discutio la semana pasada tendria de pronto otro numero.
     */
    @Transactional
    public UUID agregarPartida(UUID presupuestoId, Partida partida, String moneda, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            String estado = presupuestos
                    .estadoDelPresupuesto(dsl, presupuestoId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(101, 1), "Ese presupuesto no existe."));
            // AP-CU101-03.
            if ("CERRADO".equals(estado)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(101, 3), "El presupuesto esta cerrado: no admite partidas nuevas.");
            }
            return presupuestos.agregarPartida(
                    dsl,
                    presupuestoId,
                    partida.cuentaContableId(),
                    partida.periodoContableId(),
                    partida.monto(),
                    moneda);
        });
    }

    @Transactional
    public SalidaPresupuesto aprobar(UUID presupuestoId, ContextoSesion ctx) {
        var ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            if (!presupuestos.aprobar(dsl, presupuestoId, ctx.usuarioId(), ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(101, 3), "Ese presupuesto ya no esta en borrador.");
            }
            var partidas = presupuestos.partidas(dsl, presupuestoId);
            BigDecimal total = partidas.stream()
                    .map(PresupuestoRepositorio.Partida::presupuestado)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.presupuesto_aprobado",
                            "presupuesto",
                            presupuestoId,
                            Map.of("aprobadoPor", ctx.usuarioId().toString(), "total", total.toPlainString()),
                            UUID.fromString(ctx.traza().id())));
            return new SalidaPresupuesto(presupuestoId, "APROBADO", partidas.size(), total);
        });
    }

    /**
     * La ejecucion contra lo presupuestado, partida por partida.
     *
     * <p>Se devuelve **el detalle, no solo el total**: un presupuesto ejecutado al 95%
     * puede tener una partida al 300% y otra sin tocar, y el promedio esconde justo lo
     * que hay que mirar.
     */
    @Transactional
    public List<Ejecucion> ejecucion(UUID presupuestoId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> presupuestos.partidas(dsl, presupuestoId).stream()
                .map(p -> new Ejecucion(
                        p.cuentaId(),
                        p.periodoId(),
                        p.presupuestado(),
                        p.ejecutado(),
                        p.presupuestado().subtract(p.ejecutado()),
                        p.ejecutado().compareTo(p.presupuestado()) > 0))
                .toList());
    }

    public record Partida(UUID cuentaContableId, UUID periodoContableId, BigDecimal monto) {}

    public record EntradaPresupuesto(
            UUID centroCostoId, UUID ejercicioFiscalId, String nombre, String moneda, List<Partida> partidas) {}

    public record SalidaPresupuesto(UUID presupuestoId, String estado, int partidas, BigDecimal total) {}

    public record Ejecucion(
            UUID cuentaId,
            UUID periodoId,
            BigDecimal presupuestado,
            BigDecimal ejecutado,
            BigDecimal disponible,
            boolean sobreejecutado) {}
}
