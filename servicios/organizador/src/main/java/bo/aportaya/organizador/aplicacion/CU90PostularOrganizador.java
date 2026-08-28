package bo.aportaya.organizador.aplicacion;

import bo.aportaya.organizador.dominio.NivelDeOrganizador;
import bo.aportaya.organizador.dominio.RequisitosDeHabilitacion;
import bo.aportaya.organizador.infraestructura.OrganizadorRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-90 · Postular a organizador y habilitarse.
 *
 * <p>Un organizador administra la plata de otros. Por eso la habilitacion no es un
 * tramite: es la ultima puerta antes de que alguien tenga acceso al fondo de un grupo
 * entero. Los requisitos son **catalogo** (invariante 10) y se deniega por omision
 * (invariante 9): un requisito obligatorio sin dato **no se da por cumplido**.
 *
 * <p>Quien aprueba no puede ser quien postulo (R-SEG-04). Autoaprobarse la
 * habilitacion es exactamente el control que esta regla existe para impedir.
 */
@Service
public class CU90PostularOrganizador {

    private final Datos datos;
    private final OrganizadorRepositorio organizadores;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int limiteInicialDeGrupos;
    private final BigDecimal limiteInicialDeMonto;

    public CU90PostularOrganizador(
            Datos datos,
            OrganizadorRepositorio organizadores,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.organizador.limite-inicial-de-grupos}") int limiteInicialDeGrupos,
            @Value("${aportaya.organizador.limite-inicial-de-monto}") BigDecimal limiteInicialDeMonto) {
        this.datos = datos;
        this.organizadores = organizadores;
        this.outbox = outbox;
        this.reloj = reloj;
        this.limiteInicialDeGrupos = limiteInicialDeGrupos;
        this.limiteInicialDeMonto = limiteInicialDeMonto;
    }

    @Transactional
    public SalidaPostulacion postular(EntradaPostulacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // AP-CU90-01 · R-ORG-01: una postulacion pendiente por usuario. Varias
            // abiertas a la vez significan que dos revisores pueden llegar a
            // conclusiones distintas sobre la misma persona.
            var pendiente = organizadores.solicitudPendiente(dsl, ctx.usuarioId());
            if (pendiente.isPresent()) {
                return new SalidaPostulacion(pendiente.get().id(), "PENDIENTE", false, List.of());
            }
            // AP-CU90-02: quien ya es organizador no vuelve a postularse.
            if (organizadores.porUsuario(dsl, ctx.usuarioId()).isPresent()) {
                throw new ErrorDeNegocio(CodigoError.de(90, 2), "Ese usuario ya es organizador.");
            }
            // AP-CU90-06 · R-UIF-09: sin KYC reforzado no se administra plata ajena.
            if (entrada.kycReforzadoId() == null) {
                throw new ErrorDeNegocio(
                        CodigoError.de(90, 6), "Falta la diligencia reforzada: no se administra plata ajena sin ella.");
            }

            UUID solicitudId = organizadores.postular(
                    dsl,
                    ctx.usuarioId(),
                    entrada.motivacion(),
                    entrada.experienciaDeclarada(),
                    entrada.kycReforzadoId(),
                    entrada.reputacion(),
                    ahora);

            // Se le dice desde ya que le falta. Enterarse recien al ser rechazado, con
            // el expediente cerrado, no le sirve a nadie.
            var veredicto = RequisitosDeHabilitacion.evaluar(
                    organizadores.requisitosDe(dsl, NivelDeOrganizador.APRENDIZ.name()), entrada.medidos());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.postulacion_recibida",
                            "solicitud_organizador",
                            solicitudId,
                            Map.of(
                                    "usuarioId", ctx.usuarioId().toString(),
                                    "cumpleRequisitos", Boolean.toString(veredicto.habilitable())),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaPostulacion(solicitudId, "PENDIENTE", true, veredicto.faltantes());
        });
    }

    /**
     * Aprueba la postulacion y crea al organizador.
     *
     * <p>Nace en {@code CAPACITACION_PENDIENTE}, no habilitado: aprobar la postulacion
     * y entregar el acceso al fondo en el mismo acto salta el paso donde se le explica
     * que responsabilidad esta tomando.
     */
    @Transactional
    public SalidaHabilitacion aprobar(EntradaAprobacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var solicitud = organizadores
                    .verSolicitud(dsl, entrada.solicitudId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(90, 3), "Esa solicitud no existe."));

            // AP-CU90-04 · R-SEG-04: nadie se habilita a si mismo.
            if (solicitud.usuarioId().equals(ctx.usuarioId())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(90, 4), "Quien aprueba una postulacion no puede ser quien la presento.");
            }

            var veredicto = RequisitosDeHabilitacion.evaluar(
                    organizadores.requisitosDe(dsl, NivelDeOrganizador.APRENDIZ.name()), entrada.medidos());
            // AP-CU90-05: no se aprueba a quien no cumple. El veredicto viaja con los
            // faltantes para que el rechazo diga por que, no solo que.
            if (!veredicto.habilitable()) {
                organizadores.resolver(
                        dsl,
                        solicitud.id(),
                        "RECHAZADA",
                        ctx.usuarioId(),
                        veredicto.faltantes().get(0).motivo(),
                        ahora);
                throw new ErrorDeNegocio(
                        CodigoError.de(90, 5),
                        "No cumple los requisitos de habilitacion.",
                        Map.of("faltantes", veredicto.faltantes().toString()));
            }

            if (!organizadores.resolver(dsl, solicitud.id(), "APROBADA", ctx.usuarioId(), null, ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(90, 3), "Esa solicitud ya fue resuelta.");
            }

            UUID organizadorId = organizadores.crear(
                    dsl,
                    solicitud.usuarioId(),
                    "CAPACITACION_PENDIENTE",
                    NivelDeOrganizador.APRENDIZ.name(),
                    limiteInicialDeGrupos,
                    limiteInicialDeMonto,
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.postulacion_aprobada",
                            "organizador",
                            organizadorId,
                            Map.of(
                                    "usuarioId", solicitud.usuarioId().toString(),
                                    "estado", "CAPACITACION_PENDIENTE",
                                    "nivel", NivelDeOrganizador.APRENDIZ.name()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaHabilitacion(organizadorId, "CAPACITACION_PENDIENTE", List.of());
        });
    }

    /**
     * Habilita al organizador una vez completada la capacitacion.
     *
     * <p>La capacitacion aprobada y vigente es un requisito mas, y se comprueba contra
     * la base: darla por hecha porque «ya la hizo el año pasado» es como se habilita a
     * alguien con reglas que cambiaron.
     */
    @Transactional
    public SalidaHabilitacion habilitar(UUID organizadorId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var organizador = organizadores
                    .bloquear(dsl, organizadorId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(90, 3), "Ese organizador no existe."));

            if (organizadores.capacitacionesAprobadas(dsl, organizadorId, ahora.toLocalDate()) == 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(90, 5), "No tiene capacitacion aprobada vigente: no se habilita sin ella.");
            }
            if (!organizadores.cambiarEstado(dsl, organizadorId, "HABILITADO", organizador.version(), ahora)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(90, 3), "Otro cambio movio a ese organizador primero: reintenta.");
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.habilitado",
                            "organizador",
                            organizadorId,
                            Map.of("usuarioId", organizador.usuarioId().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaHabilitacion(organizadorId, "HABILITADO", List.of());
        });
    }

    public record EntradaPostulacion(
            String motivacion,
            String experienciaDeclarada,
            UUID kycReforzadoId,
            BigDecimal reputacion,
            Map<String, BigDecimal> medidos) {}

    public record SalidaPostulacion(
            UUID solicitudId, String estado, boolean esNueva, List<RequisitosDeHabilitacion.Faltante> faltantes) {}

    public record EntradaAprobacion(UUID solicitudId, Map<String, BigDecimal> medidos) {}

    public record SalidaHabilitacion(
            UUID organizadorId, String estado, List<RequisitosDeHabilitacion.Faltante> faltantes) {}
}
