package bo.aportaya.garantia.aplicacion;

import bo.aportaya.garantia.dominio.DevolucionDelFondo;
import bo.aportaya.garantia.infraestructura.FondoRepositorio;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-29 · Devolver los aportes del fondo de garantia.
 *
 * <p>Cuando el grupo termina, lo que quedo del fondo vuelve a quienes lo pusieron. **No
 * se devuelve mas de lo aportado ni un importe negativo** (R-GAR-06), y lo que el fondo
 * gasto en cubrir lo pierden **todos en proporcion**: esa es la idea de un fondo mutual.
 * Descontarselo solo a quien incumplio seria una deuda, no una garantia — y para eso ya
 * esta la subrogacion.
 *
 * <p>El remanente se ajusta en la ultima devolucion para que la suma cierre exacta
 * contra el saldo. Sin eso, repartir entre tres deja centavos flotando que nadie sabe
 * de quien son.
 */
@Service
public class CU29DevolverFondo {

    private final Datos datos;
    private final FondoRepositorio fondos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU29DevolverFondo(Datos datos, FondoRepositorio fondos, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.fondos = fondos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaDevolucion devolver(EntradaDevolucion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var fondo = fondos.delGrupo(dsl, entrada.grupoId())
                    .orElseThrow(
                            () -> new ErrorDeNegocio(CodigoError.de(29, 1), "Ese grupo no tiene fondo de garantia."));

            // AP-CU29-01: no se devuelve con el grupo en curso. El fondo esta ahi para
            // cubrir lo que falta, y devolverlo antes lo deja sin respaldo.
            if (!entrada.grupoCerrado()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(29, 1),
                        "El grupo todavia no termino: devolver el fondo ahora lo deja sin respaldo.");
            }
            // AP-CU29-02: con deudas vivas tampoco. Devolver mientras alguien debe es
            // repartir plata que todavia tiene dueno.
            if (entrada.deudasVivas() > 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(29, 2),
                        "Quedan " + entrada.deudasVivas() + " deuda(s) sin resolver: el fondo no se reparte todavia.");
            }

            // Una devolucion por participante y fondo: la BASE lo sostiene
            // (uq_devolucion_fondo_participante). Si el fondo ya quedo en cero, el
            // reparto ya se hizo — repetirlo escribiria filas de cero que no
            // significan nada y romperia ese unico.
            if (fondo.saldoDisponible().monto().signum() == 0) {
                return new SalidaDevolucion(
                        fondo.id(),
                        fondo.totalAportado(),
                        Dinero.cero(fondo.moneda()),
                        fondo.totalAportado(),
                        List.of());
            }

            var aportantes = fondos.aportantes(dsl, fondo.id(), fondo.moneda()).stream()
                    .map(a -> new DevolucionDelFondo.Aportante(a.participanteId(), a.aportado()))
                    .toList();
            var reparto = DevolucionDelFondo.repartir(fondo.saldoDisponible(), aportantes);

            Dinero consumidoTotal = reparto.totalAportado().menos(reparto.saldoARepartir());
            Dinero devuelto = Dinero.cero(fondo.moneda());
            for (var devolucion : reparto.devoluciones()) {
                // Lo consumido se le imputa a cada uno en proporcion a lo que puso.
                Dinero consumidoSuyo = devolucion.aportado().menos(devolucion.aDevolver());
                fondos.registrarDevolucion(
                        dsl,
                        fondo.id(),
                        devolucion.participanteId(),
                        devolucion.aportado(),
                        consumidoSuyo,
                        devolucion.aDevolver(),
                        "CALCULADA",
                        ahora);
                devuelto = devuelto.mas(devolucion.aDevolver());
            }

            Dinero saldoDespues = fondo.saldoDisponible().menos(devuelto);
            if (!fondos.moverSaldo(
                    dsl,
                    fondo.id(),
                    Dinero.cero(fondo.moneda()).menos(devuelto),
                    Dinero.cero(fondo.moneda()),
                    fondo.version())) {
                throw new ErrorDeNegocio(CodigoError.de(29, 1), "Otro proceso movio el fondo primero: reintenta.");
            }
            fondos.registrarMovimiento(
                    dsl,
                    fondo.id(),
                    "DEVOLUCION_A_PARTICIPANTES",
                    devuelto,
                    saldoDespues,
                    "DEVOLUCION",
                    fondo.id(),
                    "Devolucion del fondo al cerrar el grupo",
                    ctx.usuarioId(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "garantia.fondo_devuelto",
                            "fondo_garantia",
                            fondo.id(),
                            Map.of(
                                    "grupoId", entrada.grupoId().toString(),
                                    "totalAportado", reparto.totalAportado().toString(),
                                    "totalDevuelto", devuelto.toString(),
                                    "consumidoPorCoberturas", consumidoTotal.toString(),
                                    "beneficiarios",
                                            Integer.toString(
                                                    reparto.devoluciones().size())),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaDevolucion(
                    fondo.id(), reparto.totalAportado(), devuelto, consumidoTotal, reparto.devoluciones());
        });
    }

    public record EntradaDevolucion(UUID grupoId, boolean grupoCerrado, int deudasVivas) {}

    public record SalidaDevolucion(
            UUID fondoId,
            Dinero totalAportado,
            Dinero totalDevuelto,
            Dinero consumidoPorCoberturas,
            List<DevolucionDelFondo.Devolucion> devoluciones) {}
}
