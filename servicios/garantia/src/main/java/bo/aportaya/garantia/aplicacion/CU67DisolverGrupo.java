package bo.aportaya.garantia.aplicacion;

import bo.aportaya.garantia.dominio.CuadreDeDisolucion;
import bo.aportaya.garantia.infraestructura.DisolucionRepositorio;
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
 * CU-67 · Disolver el grupo anticipadamente.
 *
 * <p>Es el peor momento posible: unos ya cobraron su turno y otros no, y todos
 * pusieron. La liquidacion **tiene que cuadrar al centavo** y mirar la posicion de cada
 * uno —lo aportado menos lo recibido—; repartir sin eso le devolveria a quien ya cobro
 * lo mismo que a quien nunca cobro, y eso no es disolver: es premiar al que llego
 * primero.
 *
 * <p>La cuenta del grupo **cierra en cero** (R-GRP-13, {@code tg_disolucion_cuadra}).
 * Un grupo disuelto con saldo es plata de alguien que quedo sin dueno.
 */
@Service
public class CU67DisolverGrupo {

    private final Datos datos;
    private final DisolucionRepositorio disoluciones;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU67DisolverGrupo(Datos datos, DisolucionRepositorio disoluciones, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.disoluciones = disoluciones;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaDisolucion iniciar(EntradaDisolucion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // Una disolucion por grupo. Dos abiertas darian dos repartos distintos de
            // la misma plata.
            var previa = disoluciones.disolucionDe(dsl, entrada.grupoId());
            if (previa.isPresent()) {
                return new SalidaDisolucion(
                        previa.get(),
                        "YA_INICIADA",
                        entrada.masaDisponible(),
                        Dinero.cero(entrada.masaDisponible().moneda()),
                        Dinero.cero(entrada.masaDisponible().moneda()),
                        List.of(),
                        false);
            }

            // AP-CU67-01: sin causal escrita no se disuelve. Un grupo se disuelve por
            // algo, y ese algo tiene que quedar dicho para los que pierden con la
            // decision.
            if (entrada.motivo() == null || entrada.motivo().isBlank()) {
                throw new ErrorDeNegocio(CodigoError.de(67, 1), "No se disuelve un grupo sin motivo escrito.");
            }

            var cuadre = CuadreDeDisolucion.liquidar(entrada.masaDisponible(), entrada.posiciones());

            UUID disolucionId = disoluciones.iniciarDisolucion(
                    dsl,
                    entrada.grupoId(),
                    entrada.causal(),
                    entrada.motivo(),
                    entrada.totalAportado(),
                    entrada.totalEntregado(),
                    entrada.masaDisponible(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "garantia.disolucion_calculada",
                            "disolucion_anticipada",
                            disolucionId,
                            Map.of(
                                    "grupoId", entrada.grupoId().toString(),
                                    "causal", entrada.causal(),
                                    "masaARepartir", cuadre.masaARepartir().toString(),
                                    "totalADevolver", cuadre.totalADevolver().toString(),
                                    "totalACobrar", cuadre.totalACobrar().toString(),
                                    "participantes",
                                            Integer.toString(
                                                    cuadre.liquidaciones().size())),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaDisolucion(
                    disolucionId,
                    "CALCULADA",
                    cuadre.masaARepartir(),
                    cuadre.totalADevolver(),
                    cuadre.totalACobrar(),
                    cuadre.liquidaciones(),
                    true);
        });
    }

    /**
     * Cierra la disolucion.
     *
     * <p>La BASE exige que la cuenta del grupo quede en cero ({@code
     * tg_disolucion_cuadra}). No es una formalidad contable: un saldo que sobra es
     * plata de alguien, y cerrar sin repartirla la deja sin dueno.
     */
    @Transactional
    public SalidaCierre cerrar(UUID disolucionId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            if (!disoluciones.cerrarDisolucion(dsl, disolucionId, ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(67, 4), "Esa disolucion no estaba lista para cerrarse.");
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "garantia.disolucion_cerrada",
                            "disolucion_anticipada",
                            disolucionId,
                            Map.of("cerradaPor", ctx.usuarioId().toString(), "momento", ahora.toString()),
                            UUID.fromString(ctx.traza().id())));
            return new SalidaCierre(disolucionId, "CERRADA", ahora);
        });
    }

    public record EntradaDisolucion(
            UUID grupoId,
            String causal,
            String motivo,
            Dinero totalAportado,
            Dinero totalEntregado,
            Dinero masaDisponible,
            List<CuadreDeDisolucion.Posicion> posiciones) {}

    public record SalidaDisolucion(
            UUID disolucionId,
            String estado,
            Dinero masaARepartir,
            Dinero totalADevolver,
            Dinero totalACobrar,
            List<CuadreDeDisolucion.Liquidacion> liquidaciones,
            boolean esNueva) {}

    public record SalidaCierre(UUID disolucionId, String estado, OffsetDateTime cerradaEn) {}
}
