package bo.aportaya.garantia.aplicacion;

import bo.aportaya.garantia.dominio.RestriccionInterna;
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
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-27 · Restringir al deudor e incluirlo en la lista interna.
 *
 * <p>Una restriccion vigente por usuario (R-GAR-05), y **su levantamiento se motiva**.
 * La restriccion no es un castigo permanente: es una medida con plazo, y quien la
 * levanta antes deja escrito por que — sin eso, levantarla se convierte en un favor que
 * nadie puede auditar.
 *
 * <p>La restriccion **no le cierra la puerta a pagar**. Bloquear al deudor de todo,
 * incluido el camino para regularizarse, garantiza que no se regularice.
 */
@Service
public class CU27RestringirDeudor {

    private final Datos datos;
    private final GestionRepositorio gestion;
    private final FondoRepositorio fondos;
    private final DeudaRepositorio deudas;
    private final ExpedienteRepositorio expedientes;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU27RestringirDeudor(
            Datos datos,
            GestionRepositorio gestion,
            FondoRepositorio fondos,
            DeudaRepositorio deudas,
            ExpedienteRepositorio expedientes,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.gestion = gestion;
        this.fondos = fondos;
        this.deudas = deudas;
        this.expedientes = expedientes;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaRestriccion restringir(EntradaRestriccion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var expediente = expedientes
                    .ver(dsl, entrada.expedienteId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(27, 1), "Ese expediente no existe."));

            // AP-CU27-06 · R-SEG-04: nadie se restringe a si mismo, ni el imputado
            // decide su propia sancion.
            if (expediente.usuarioId().equals(ctx.usuarioId())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(27, 6), "Quien aplica la restriccion no puede ser el restringido.");
            }
            // AP-CU27-02: sin motivo escrito no hay restriccion. «Deudor» a secas no le
            // dice a la persona contra que puede apelar.
            if (entrada.motivo() == null || entrada.motivo().isBlank()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(27, 2), "Una restriccion sin motivo escrito no se puede apelar.");
            }

            // R-GAR-05: una vigente por usuario. La segunda no abre otra.
            var vigente = gestion.restriccionVigente(dsl, expediente.usuarioId(), ahora);
            if (vigente.isPresent()) {
                return new SalidaRestriccion(
                        vigente.get().id(), vigente.get().nivel(), vigente.get().hasta(), false);
            }

            Dinero adeudado = deudas.deudaDe(dsl, expediente.id())
                    .map(DeudaRepositorio.Deuda::saldoActual)
                    .orElse(expediente.montoInvolucrado());
            OffsetDateTime hasta =
                    RestriccionInterna.venceEn(ahora, entrada.duracion().orElse(null));

            UUID restriccionId = gestion.restringir(
                    dsl,
                    expediente.usuarioId(),
                    expediente.id(),
                    entrada.motivo(),
                    entrada.nivel(),
                    adeudado,
                    ahora,
                    hasta);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "garantia.usuario_restringido",
                            "lista_restriccion_interna",
                            restriccionId,
                            Map.of(
                                    "usuarioId", expediente.usuarioId().toString(),
                                    "nivel", entrada.nivel(),
                                    "motivo", entrada.motivo(),
                                    "montoAdeudado", adeudado.toString(),
                                    // Lo que NO se restringe viaja en el evento: quien
                                    // consuma esto tiene que saber que pagar sigue abierto.
                                    "puedeSeguir", "PAGAR_DEUDA,VER_ESTADO",
                                    "vigenteHasta", hasta == null ? "INDEFINIDA" : hasta.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaRestriccion(restriccionId, entrada.nivel(), hasta, true);
        });
    }

    /** El levantamiento **exige motivo escrito** (R-GAR-05). */
    @Transactional
    public boolean levantar(UUID restriccionId, String motivo, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            if (motivo == null || motivo.isBlank()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(27, 3),
                        "Levantar una restriccion sin motivo escrito la convierte en un favor que nadie puede auditar.");
            }
            boolean levantada = gestion.levantar(dsl, restriccionId, ctx.usuarioId(), motivo, ahora);
            if (levantada) {
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "garantia.restriccion_levantada",
                                "lista_restriccion_interna",
                                restriccionId,
                                Map.of(
                                        "motivo",
                                        motivo,
                                        "levantadaPor",
                                        ctx.usuarioId().toString()),
                                UUID.fromString(ctx.traza().id())));
            }
            return levantada;
        });
    }

    /** Si el usuario puede hacer algo, segun su restriccion vigente. */
    @Transactional(readOnly = true)
    public boolean puede(UUID usuarioId, String accion, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> {
            var vigente = gestion.restriccionVigente(dsl, usuarioId, ahora);
            if (vigente.isEmpty()) {
                return true;
            }
            // Pagar la deuda y ver su estado NUNCA se restringen: cerrarle esa puerta a
            // quien debe es asegurarse de que no vuelva.
            return !RestriccionInterna.esTipoValido(accion);
        });
    }

    public record EntradaRestriccion(UUID expedienteId, String nivel, String motivo, Optional<Duration> duracion) {}

    public record SalidaRestriccion(UUID restriccionId, String nivel, OffsetDateTime vigenteHasta, boolean esNueva) {}
}
