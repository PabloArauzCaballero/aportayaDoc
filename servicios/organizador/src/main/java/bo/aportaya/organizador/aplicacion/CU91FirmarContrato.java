package bo.aportaya.organizador.aplicacion;

import bo.aportaya.organizador.infraestructura.ContratoRepositorio;
import bo.aportaya.organizador.infraestructura.OrganizadorRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-91 · Firmar y rescindir el contrato de organizador.
 *
 * <p>**Sin contrato vigente no se crean grupos** (R-ORG-02). No es burocracia: el
 * contrato es lo unico que dice, por escrito y oponible, que obligaciones asumio quien
 * va a manejar la plata de otros y por que causales se lo puede sacar.
 *
 * <p>Un contrato firmado **no se modifica** (R-ORG-03): se emite version nueva.
 * Cambiarle una clausula a un documento ya firmado lo vuelve inoponible, que es lo
 * contrario de para lo que existe.
 */
@Service
public class CU91FirmarContrato {

    private final Datos datos;
    private final ContratoRepositorio contratos;
    private final OrganizadorRepositorio organizadores;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU91FirmarContrato(
            Datos datos,
            ContratoRepositorio contratos,
            OrganizadorRepositorio organizadores,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.contratos = contratos;
        this.organizadores = organizadores;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaEmision emitir(EntradaEmision entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            organizadores
                    .ver(dsl, entrada.organizadorId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(91, 1), "Ese organizador no existe."));

            // AP-CU91-02 · R-ORG-02: un solo contrato vigente. Dos vigentes a la vez
            // significan que ante un incumplimiento hay dos textos que invocar, y el
            // organizador puede elegir el que mas le convenga.
            if (contratos
                    .vigente(dsl, entrada.organizadorId(), ahora.toLocalDate())
                    .isPresent()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(91, 2),
                        "Ese organizador ya tiene contrato vigente: primero se rescinde el anterior.");
            }

            UUID contratoId = contratos.emitir(
                    dsl,
                    entrada.organizadorId(),
                    entrada.version(),
                    entrada.contenidoHash(),
                    entrada.obligaciones(),
                    entrada.causalesRescision(),
                    ahora.toLocalDate());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.contrato_emitido",
                            "contrato_organizador",
                            contratoId,
                            Map.of(
                                    "organizadorId", entrada.organizadorId().toString(),
                                    "version", entrada.version(),
                                    "contenidoHash", entrada.contenidoHash()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaEmision(contratoId, entrada.version(), false);
        });
    }

    /**
     * Firma el contrato.
     *
     * <p>Solo lo firma el propio organizador: una firma puesta por otro no obliga a
     * nadie, y es exactamente lo que un juez descartaria.
     */
    @Transactional
    public SalidaFirma firmar(UUID contratoId, UUID tokenFirmaId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var contrato = contratos
                    .ver(dsl, contratoId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(91, 1), "Ese contrato no existe."));
            var organizador = organizadores.ver(dsl, contrato.organizadorId()).orElseThrow();

            // AP-CU91-04.
            if (!organizador.usuarioId().equals(ctx.usuarioId())) {
                throw new ErrorDeNegocio(CodigoError.de(91, 4), "El contrato lo firma el organizador, no un tercero.");
            }
            // AP-CU91-03 · R-ORG-03: firmar dos veces sobrescribiria la evidencia de
            // cuando y con que token se firmo de verdad.
            if (!contratos.firmar(dsl, contratoId, tokenFirmaId, ahora)) {
                return new SalidaFirma(contratoId, contrato.firmadoEn(), false);
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.contrato_firmado",
                            "contrato_organizador",
                            contratoId,
                            Map.of(
                                    "organizadorId", contrato.organizadorId().toString(),
                                    "firmadoEn", ahora.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaFirma(contratoId, ahora, true);
        });
    }

    /**
     * Rescinde el contrato.
     *
     * <p>El organizador con grupos en curso **no puede irse dejandolos**: rescindir
     * mientras administra plata ajena es abandonar a los participantes a mitad del
     * ciclo, y el contrato existe para que eso no pase sin consecuencias.
     */
    @Transactional
    public SalidaRescision rescindir(EntradaRescision entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var contrato = contratos
                    .ver(dsl, entrada.contratoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(91, 1), "Ese contrato no existe."));
            var organizador = organizadores.ver(dsl, contrato.organizadorId()).orElseThrow();

            // AP-CU91-05.
            if (!contrato.estaFirmado()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(91, 5), "Ese contrato nunca se firmo: no hay nada que rescindir.");
            }
            // AP-CU91-06.
            if (organizador.gruposActivos() > 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(91, 6),
                        "Tiene " + organizador.gruposActivos()
                                + " grupo(s) en curso: no se rescinde dejando participantes a mitad del ciclo.");
            }
            if (!contratos.rescindir(dsl, entrada.contratoId(), entrada.motivo(), ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(91, 5), "Ese contrato ya estaba rescindido.");
            }

            // Sin contrato vigente no se crean grupos: el estado lo refleja.
            organizadores.cambiarEstado(dsl, organizador.id(), "RETIRADO", organizador.version(), ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.contrato_rescindido",
                            "contrato_organizador",
                            entrada.contratoId(),
                            Map.of(
                                    "organizadorId", organizador.id().toString(),
                                    "motivo", entrada.motivo()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaRescision(entrada.contratoId(), ahora, "RETIRADO");
        });
    }

    /** Si puede crear grupos, y si no, por que no. */
    @Transactional(readOnly = true)
    public boolean puedeCrearGrupos(UUID organizadorId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> {
            var organizador = organizadores.ver(dsl, organizadorId);
            if (organizador.isEmpty() || !"HABILITADO".equals(organizador.get().estado())) {
                return false;
            }
            var contrato = contratos.vigente(dsl, organizadorId, ahora.toLocalDate());
            return contrato.isPresent() && contrato.get().estaFirmado();
        });
    }

    public record EntradaEmision(
            UUID organizadorId, String version, String contenidoHash, String obligaciones, String causalesRescision) {}

    public record SalidaEmision(UUID contratoId, String version, boolean yaExistia) {}

    public record SalidaFirma(UUID contratoId, OffsetDateTime firmadoEn, boolean esNueva) {}

    public record EntradaRescision(UUID contratoId, String motivo) {}

    public record SalidaRescision(UUID contratoId, OffsetDateTime rescindidoEn, String estadoDelOrganizador) {}
}
