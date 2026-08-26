package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.EstadoDeLicencia;
import bo.aportaya.cumplimiento.dominio.HabilitacionDeServicio;
import bo.aportaya.cumplimiento.dominio.HabilitacionDeServicio.Decision;
import bo.aportaya.cumplimiento.infraestructura.LicenciaRepositorio;
import bo.aportaya.cumplimiento.infraestructura.SandboxRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-46 · Verificar el alcance de la licencia.
 *
 * <p>Es el caso de uso que impide el error regulatorio mas caro y mas facil de
 * cometer: lanzar una funcion que la autorizacion no cubre. Por eso **deniega por
 * omision** (invariante 9): sin licencia legible, sin alcance o sin sandbox, la
 * respuesta es no. Un fallo de lectura no puede traducirse en «segui adelante».
 *
 * <p>No tiene endpoint. Lo consultan los otros servicios antes de habilitar una
 * operacion, y el planificador antes de un despliegue.
 */
@Service
public class CU46VerificarAlcance {

    private final Datos datos;
    private final LicenciaRepositorio licencias;
    private final SandboxRepositorio sandboxes;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Set<String> serviciosDeSalida;

    public CU46VerificarAlcance(
            Datos datos,
            LicenciaRepositorio licencias,
            SandboxRepositorio sandboxes,
            Outbox outbox,
            Reloj reloj,
            // La boveda no trae un catalogo de «servicios de salida». Se declara en
            // configuracion, donde se ve y se audita, en vez de hornearse aca.
            @Value("${cumplimiento.servicios-de-salida:RETIRO}") Set<String> serviciosDeSalida) {
        this.datos = datos;
        this.licencias = licencias;
        this.sandboxes = sandboxes;
        this.outbox = outbox;
        this.reloj = reloj;
        this.serviciosDeSalida = Set.copyOf(serviciosDeSalida);
    }

    @Transactional
    public SalidaAlcance ejecutar(EntradaAlcance entrada, ContextoSesion ctx) {
        LocalDate hoy = reloj.ahora().atOffset(ZoneOffset.UTC).toLocalDate();

        return datos.conContexto(ctx, dsl -> {
            var licencia = licencias.vigente(dsl, hoy);

            // AP-CU46-01: sin fila de licencia no hay nada que interpretar. Denegar
            // por omision es la unica lectura segura de una tabla vacia.
            EstadoDeLicencia estado =
                    licencia.map(LicenciaRepositorio.Licencia::estado).orElse(EstadoDeLicencia.EN_TRAMITE);
            boolean vigente =
                    licencia.map(LicenciaRepositorio.Licencia::vigente).orElse(false);
            Set<String> alcance =
                    licencia.map(LicenciaRepositorio.Licencia::alcance).orElse(Set.of());

            Decision decision = HabilitacionDeServicio.resolver(
                    entrada.servicio(),
                    estado,
                    vigente,
                    alcance,
                    sandboxes.activoPara(dsl, entrada.servicio(), hoy, entrada.usuariosRegistrados()),
                    serviciosDeSalida);

            if (!decision.habilitado()) {
                // El rechazo se registra ANTES de lanzarlo: el evento va en la misma
                // transaccion y sobrevive, porque el error de negocio no revierte lo
                // que ya se escribio en el outbox de esta transaccion... salvo que el
                // llamador la revierta. Por eso se emite y se devuelve la decision, y
                // es el llamador quien decide si eso es un rechazo duro.
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "cumplimiento.servicio_rechazado_por_alcance",
                                "licencia_regulatoria",
                                entrada.usuarioId()
                                        .orElse(UUID.fromString(ctx.traza().id())),
                                Map.of(
                                        "servicio", entrada.servicio(),
                                        "estadoLicencia", estado.name(),
                                        "motivo", decision.motivo().orElse("")),
                                UUID.fromString(ctx.traza().id())));
            }

            return new SalidaAlcance(
                    decision.habilitado(),
                    decision.via().name(),
                    decision.limites()
                            .map(l -> new LimitesSandbox(
                                    l.usuarios(), l.montoOperacion().toPlainString()))
                            .orElse(null),
                    decision.motivo().orElse(null));
        });
    }

    /**
     * La version que corta: la usan los servicios que no quieren decidir dos veces.
     * Traduce la decision al codigo de error que el contrato promete.
     */
    @Transactional
    public void exigirHabilitado(EntradaAlcance entrada, ContextoSesion ctx) {
        SalidaAlcance salida = ejecutar(entrada, ctx);
        if (salida.habilitado()) {
            return;
        }
        throw new ErrorDeNegocio(codigoDe(entrada, salida), salida.motivo());
    }

    private CodigoError codigoDe(EntradaAlcance entrada, SalidaAlcance salida) {
        String motivo = salida.motivo() == null ? "" : salida.motivo();
        if (motivo.contains("tope de")) {
            return CodigoError.de(46, 3); // SANDBOX_AGOTADO
        }
        if (motivo.contains("SUSPENDIDA") || motivo.contains("REVOCADA")) {
            return CodigoError.de(46, 4); // LICENCIA_SUSPENDIDA
        }
        if (motivo.startsWith("Todavia no hay licencia")) {
            return CodigoError.de(46, 1); // LICENCIA_NO_OTORGADA
        }
        return CodigoError.de(46, 2); // FUERA_DE_ALCANCE (R-LIC-01)
    }

    public record EntradaAlcance(String servicio, Optional<UUID> usuarioId, int usuariosRegistrados) {

        /** El caso comun: preguntar por un servicio, sin sandbox de por medio. */
        public static EntradaAlcance de(String servicio) {
            return new EntradaAlcance(servicio, Optional.empty(), 0);
        }
    }

    public record SalidaAlcance(boolean habilitado, String via, LimitesSandbox limitesSandbox, String motivo) {}

    public record LimitesSandbox(int usuarios, String montoOperacion) {}
}
