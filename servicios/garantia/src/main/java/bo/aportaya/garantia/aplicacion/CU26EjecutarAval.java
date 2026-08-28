package bo.aportaya.garantia.aplicacion;

import bo.aportaya.garantia.infraestructura.DeudaRepositorio;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-26 · Ejecutar el aval y subrogar la deuda.
 *
 * <p>Un avalista acepto responder por **una cantidad concreta**. Cobrarle mas es
 * cobrarle algo que nunca acepto (R-GAR-04), y es la clase de cosa que hace que nadie
 * vuelva a avalar a nadie. El tope se mide contra lo **ya ejecutado**: dos ejecuciones
 * parciales que juntas lo superan lo superan igual.
 *
 * <p>Y el avalista que paga **se subroga**: la deuda no desaparece, cambia de acreedor.
 * Si desapareciera, el deudor original se quedaria sin deber nada porque otro pago por
 * el, y el avalista sin nada que reclamar.
 */
@Service
public class CU26EjecutarAval {

    private final Datos datos;
    private final GestionRepositorio gestion;
    private final FondoRepositorio fondos;
    private final DeudaRepositorio deudas;
    private final ExpedienteRepositorio expedientes;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Duration plazoDeRespuesta;

    public CU26EjecutarAval(
            Datos datos,
            GestionRepositorio gestion,
            FondoRepositorio fondos,
            DeudaRepositorio deudas,
            ExpedienteRepositorio expedientes,
            Outbox outbox,
            Reloj reloj,
            Duration plazoDeRespuesta) {
        this.datos = datos;
        this.gestion = gestion;
        this.fondos = fondos;
        this.deudas = deudas;
        this.expedientes = expedientes;
        this.outbox = outbox;
        this.reloj = reloj;
        this.plazoDeRespuesta = plazoDeRespuesta;
    }

    @Transactional
    public SalidaEjecucion ejecutar(UUID expedienteId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var expediente = expedientes
                    .bloquear(dsl, expedienteId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(26, 1), "Ese expediente no existe."));
            var deuda = deudas.deudaDe(dsl, expedienteId)
                    .orElseThrow(() ->
                            new ErrorDeNegocio(CodigoError.de(26, 2), "Ese expediente no tiene deuda que ejecutar."));

            // AP-CU26-01: sin aval vigente no hay a quien ejecutarle. Denegar por
            // omision: ejecutarle a alguien que no acepto avalar es cobrarle sin causa.
            var aval = gestion.avalVigente(dsl, expediente.grupoId(), expediente.participanteId())
                    .orElseThrow(
                            () -> new ErrorDeNegocio(CodigoError.de(26, 1), "Ese participante no tiene aval vigente."));

            // R-GAR-03: una ejecucion por aval y expediente.
            var previa = gestion.ejecucionDe(dsl, aval.id(), expedienteId);
            if (previa.isPresent()) {
                return new SalidaEjecucion(
                        previa.get(), null, Dinero.cero(deuda.saldoActual().moneda()), null, false);
            }

            // R-GAR-04: el tope, medido contra lo ya ejecutado. Lo verifica tambien el
            // trigger tg_ejecucion_aval_tope; aca es una regla de negocio con mensaje.
            var tope = gestion.topeDe(dsl, aval, deuda.saldoActual().moneda());
            Dinero aEjecutar = tope.ejecutable(deuda.saldoActual());

            UUID ejecucionId = gestion.ejecutarAval(
                    dsl, aval.id(), expedienteId, deuda.id(), aEjecutar, ahora, ahora.plus(plazoDeRespuesta), true);

            // El avalista se subroga: pasa a ser el acreedor de lo que pago.
            UUID coberturaId = fondos.coberturaDe(dsl, expedienteId).orElse(null);
            UUID subrogacionId = null;
            if (coberturaId != null) {
                subrogacionId =
                        gestion.subrogar(dsl, coberturaId, deuda.id(), "FONDO_GARANTIA", "AVALISTA", aEjecutar, ahora);
                gestion.marcarDeudaSubrogada(dsl, deuda.id(), deuda.version());
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "garantia.aval_ejecutado",
                            "ejecucion_aval",
                            ejecucionId,
                            Map.of(
                                    "expedienteId", expedienteId.toString(),
                                    "avalistaUsuarioId",
                                            aval.avalistaUsuarioId().toString(),
                                    "montoEjecutado", aEjecutar.toString(),
                                    "topeDisponible", tope.disponible().toString(),
                                    "plazoRespuesta",
                                            ahora.plus(plazoDeRespuesta).toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaEjecucion(ejecucionId, subrogacionId, aEjecutar, aval.avalistaUsuarioId(), true);
        });
    }

    public record SalidaEjecucion(
            UUID ejecucionId, UUID subrogacionId, Dinero montoEjecutado, UUID avalistaUsuarioId, boolean esNueva) {}
}
