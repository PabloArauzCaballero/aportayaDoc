package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.EvidenciaDeAceptacion;
import bo.aportaya.cumplimiento.dominio.VersionAceptable;
import bo.aportaya.cumplimiento.dominio.VersionAceptable.Resultado;
import bo.aportaya.cumplimiento.infraestructura.AceptacionRepositorio;
import bo.aportaya.cumplimiento.infraestructura.ContratoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-05 · Aceptar el contrato de adhesion y el tarifario.
 *
 * <p>Vive en {@code cumplimiento} y no en {@code identidad}, aunque la ficha del caso
 * de uso apunte a {@code openapi/identidad.yaml}: sus dos tablas —{@code
 * contrato_adhesion} y {@code aceptacion_contrato}— estan en el esquema de
 * cumplimiento, y el esquema tiene precedencia sobre la ficha. Implementarlo en
 * identidad exigiria leer un esquema ajeno, que es el invariante 11.
 *
 * <p>La aceptacion es **append-only**. Cuando sale una version nueva no se actualiza
 * la fila vieja: se agrega otra. La aceptacion de la version 3 tiene que seguir
 * existiendo despues de que se publique la 4, porque durante esos meses la persona
 * estuvo atada a la 3 y eso es un hecho que ya paso.
 */
@Service
public class CU05AceptarContrato {

    private final Datos datos;
    private final ContratoRepositorio contratos;
    private final AceptacionRepositorio aceptaciones;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU05AceptarContrato(
            Datos datos,
            ContratoRepositorio contratos,
            AceptacionRepositorio aceptaciones,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.contratos = contratos;
        this.aceptaciones = aceptaciones;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaAceptacion ejecutar(EntradaAceptacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var contrato = contratos.porId(dsl, entrada.contratoId());

            // AP-CU05-01 / AP-CU05-02. Se evalua con el atomo puro para que la regla
            // se pueda probar sin base.
            Resultado resultado = VersionAceptable.evaluar(
                    contrato.map(ContratoRepositorio.Contrato::comoVersion), entrada.version());
            if (resultado == Resultado.NO_VIGENTE) {
                throw new ErrorDeNegocio(
                        CodigoError.de(5, 1),
                        "Ese contrato no esta vigente: no se puede aceptar una version retirada.");
            }
            if (resultado == Resultado.DESACTUALIZADA) {
                throw new ErrorDeNegocio(
                        CodigoError.de(5, 2),
                        "Hay una version mas nueva del contrato: hay que leerla antes de aceptar.");
            }

            // AP-CU05-03 · R-CON-07: aceptar sin tarifario publicado seria firmar
            // condiciones economicas que nadie pudo leer.
            if (!contratos.hayTarifarioPublicado(dsl, ahora.toLocalDate())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(5, 3), "Todavia no hay tarifario publicado: no se puede aceptar el contrato.");
            }

            String hashEvidencia = EvidenciaDeAceptacion.armar(
                    contrato.get().hashDocumento(),
                    entrada.usuarioId(),
                    entrada.version(),
                    entrada.ip(),
                    entrada.dispositivoId(),
                    ahora);

            UUID aceptacionId = aceptaciones.registrar(
                    dsl,
                    entrada.contratoId(),
                    entrada.usuarioId(),
                    entrada.version(),
                    entrada.ip(),
                    entrada.dispositivoId(),
                    entrada.tokenFirmaId(),
                    hashEvidencia,
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.contrato_aceptado",
                            "aceptacion_contrato",
                            aceptacionId,
                            Map.of(
                                    "usuarioId", entrada.usuarioId().toString(),
                                    "contratoId", entrada.contratoId().toString(),
                                    "version", Integer.toString(entrada.version())),
                            UUID.fromString(ctx.traza().id())));

            // Los consentimientos por finalidad se guardan en `identidad.consentimiento`,
            // que es esquema ajeno: se piden por evento, no con un INSERT desde aca.
            for (String finalidad : entrada.consentimientosOtorgados()) {
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "cumplimiento.consentimiento_registrado",
                                "aceptacion_contrato",
                                aceptacionId,
                                Map.of("usuarioId", entrada.usuarioId().toString(), "finalidad", finalidad),
                                UUID.fromString(ctx.traza().id())));
            }

            return new SalidaAceptacion(aceptacionId, hashEvidencia, ahora);
        });
    }

    /**
     * R-CON-06 · ¿esta esa persona habilitada para operar ese tipo de contrato?
     *
     * <p>Devuelve tambien la version que le falta, porque «no podes operar» sin decir
     * que hacer para poder es una pared, no un mensaje.
     */
    @Transactional
    public EstadoDeAdhesion estadoDe(UUID usuarioId, String tipo, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var contrato = contratos.vigentePorTipo(dsl, tipo);
            if (contrato.isEmpty()) {
                return new EstadoDeAdhesion(false, null, null);
            }
            var ultima = aceptaciones.ultimaDe(dsl, usuarioId, contrato.get().id());
            short vigente = contrato.get().version();
            boolean alDia = ultima.map(a -> a.version() == vigente).orElse(false);
            return new EstadoDeAdhesion(
                    alDia, (int) vigente, ultima.map(a -> (int) a.version()).orElse(null));
        });
    }

    public record EntradaAceptacion(
            UUID usuarioId,
            UUID contratoId,
            int version,
            Optional<UUID> tokenFirmaId,
            Optional<String> ip,
            Optional<UUID> dispositivoId,
            List<String> consentimientosOtorgados) {

        public static EntradaAceptacion simple(UUID usuarioId, UUID contratoId, int version) {
            return new EntradaAceptacion(
                    usuarioId, contratoId, version, Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        }
    }

    public record SalidaAceptacion(UUID aceptacionId, String hashEvidencia, OffsetDateTime aceptadoEn) {}

    public record EstadoDeAdhesion(boolean alDia, Integer versionVigente, Integer versionAceptada) {}
}
