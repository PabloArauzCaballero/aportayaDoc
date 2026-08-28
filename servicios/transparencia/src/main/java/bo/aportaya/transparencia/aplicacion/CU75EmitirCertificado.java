package bo.aportaya.transparencia.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.transparencia.dominio.CertificadoVerificable;
import bo.aportaya.transparencia.infraestructura.CadenaRepositorio;
import bo.aportaya.transparencia.infraestructura.CertificadoRepositorio;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-75 · Emitir un certificado de reputacion verificable.
 *
 * <p>Sirve para alquilar un cuarto, pedir fiado en la tienda del barrio o entrar a un
 * grupo de otra plataforma: **una prueba que el titular controla**. Por eso elige que
 * incluir campo por campo, y por eso lo que no eligio no se filtra por el hash.
 *
 * <p>Tres cosas se hacen a proposito y cuestan un poco mas:
 *
 * <ul>
 *   <li>**Un codigo inexistente responde igual que uno revocado**: {@code NO_VALIDO},
 *       sin detalle. Distinguirlos le dice a quien prueba codigos al azar cuando
 *       acerto.
 *   <li>**El codigo es azar criptografico**, nunca un correlativo.
 *   <li>**Solo el titular emite el suyo** (R-SEG-03). Emitir el certificado de otro
 *       seria publicar su reputacion sin que se entere.
 * </ul>
 */
@Service
public class CU75EmitirCertificado {

    private final Datos datos;
    private final CertificadoRepositorio certificados;
    private final CadenaRepositorio cadenas;
    private final Outbox outbox;
    private final Reloj reloj;
    private final SecureRandom azar;
    private final String claveDeFirma;
    private final String baseUrlPublica;

    public CU75EmitirCertificado(
            Datos datos,
            CertificadoRepositorio certificados,
            CadenaRepositorio cadenas,
            Outbox outbox,
            Reloj reloj,
            SecureRandom azar,
            @Value("${aportaya.certificados.clave-de-firma}") String claveDeFirma,
            @Value("${aportaya.certificados.base-url-publica}") String baseUrlPublica) {
        this.datos = datos;
        this.certificados = certificados;
        this.cadenas = cadenas;
        this.outbox = outbox;
        this.reloj = reloj;
        this.azar = azar;
        this.claveDeFirma = claveDeFirma;
        this.baseUrlPublica = baseUrlPublica;
    }

    @Transactional
    public SalidaCertificado emitir(EntradaCertificado entrada, ContextoSesion ctx) {
        // R-SEG-03. Se comprueba antes de tocar la base: no hace falta abrir nada para
        // saber que esta persona no es la titular.
        if (!entrada.usuarioId().equals(ctx.usuarioId())) {
            throw new ErrorDeNegocio(
                    CodigoError.de(75, 4), "Solo el titular puede emitir su certificado de reputacion.");
        }
        // AP-CU75-02. Un certificado sobre una identidad sin verificar es un documento
        // que dice «esta persona es de fiar» sin saber quien es.
        if (!entrada.identidadVerificada()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(75, 2),
                    "Para emitir un certificado hay que completar la verificacion de identidad.");
        }
        if (entrada.snapshotId() == null) {
            throw new ErrorDeNegocio(CodigoError.de(75, 1), "No hay una foto de reputacion de la cual emitir.");
        }

        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        var contenido = CertificadoVerificable.contenido(entrada.disponibles(), entrada.incluir());
        String hashContenido = CertificadoVerificable.hash(contenido);
        String firma = CertificadoVerificable.firmar(hashContenido, claveDeFirma);

        byte[] semilla = new byte[16];
        azar.nextBytes(semilla);
        String codigo = CertificadoVerificable.codigo(semilla);
        String url = baseUrlPublica + "/verificar/" + codigo;

        return datos.conContexto(ctx, dsl -> {
            // AP-CU75-03. Una foto, un certificado: emitir dos sobre la misma foto seria
            // el mismo documento con dos codigos, y revocar uno dejaria el otro vivo.
            // Si ya hay uno vigente **se devuelve ese**, que es lo que el titular
            // necesita; no se le niega el pedido por algo que ya tiene.
            var existente = certificados.certificadoPorSnapshot(dsl, entrada.snapshotId());
            if (existente.isPresent()) {
                var yaEmitido = existente.get();
                if (yaEmitido.revocadoEn() != null) {
                    throw new ErrorDeNegocio(
                            CodigoError.de(75, 5),
                            "El certificado de esa foto fue revocado; hace falta una foto nueva.");
                }
                return new SalidaCertificado(
                        yaEmitido.id(),
                        yaEmitido.codigoVerificacion(),
                        yaEmitido.urlPublica(),
                        yaEmitido.hashContenido(),
                        yaEmitido.emitidoEn(),
                        yaEmitido.expiraEn(),
                        contenido);
            }

            UUID id;
            try {
                id = certificados.emitirCertificado(
                        dsl,
                        entrada.usuarioId(),
                        entrada.snapshotId(),
                        codigo,
                        hashContenido,
                        firma,
                        url,
                        ahora,
                        ahora.plusDays(entrada.vigenciaDias()));
            } catch (org.jooq.exception.IntegrityConstraintViolationException
                    | org.springframework.dao.DataIntegrityViolationException e) {
                // La comprobacion de arriba no alcanza cuando dos peticiones leen a la
                // vez: quien sostiene la regla es
                // uq_certificado_reputacion_snapshot_id. Se atrapan las dos formas
                // porque jOOQ traduce a la excepcion de Spring solo cuando corre dentro
                // del arranque de Spring Boot, y en las pruebas de CU no.
                throw new ErrorDeNegocio(
                        CodigoError.de(75, 3),
                        "Ya existe un certificado emitido sobre esa foto de reputacion.",
                        Map.of("snapshotId", entrada.snapshotId().toString()));
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "transparencia.certificado_emitido",
                            "certificado_reputacion",
                            id,
                            Map.of(
                                    "usuarioId", entrada.usuarioId().toString(),
                                    "codigoVerificacion", codigo,
                                    "expiraEn",
                                            ahora.plusDays(entrada.vigenciaDias())
                                                    .toString(),
                                    "camposIncluidos", String.join(",", contenido.keySet())),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCertificado(
                    id, codigo, url, hashContenido, ahora, ahora.plusDays(entrada.vigenciaDias()), contenido);
        });
    }

    /**
     * La consulta publica. **Sin sesion**: es el punto del certificado.
     *
     * <p>Se registra en {@code verificacion_publica} incluso cuando el codigo no
     * existe. Un patron de codigos inexistentes consultados en rafaga es exactamente lo
     * que hay que poder ver.
     */
    @Transactional
    public SalidaVerificacion verificarPublico(
            String codigo, Map<String, String> contenidoPublicado, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var certificado = certificados.certificadoPorCodigo(dsl, codigo);
            String estado = CertificadoVerificable.estado(
                    certificado.isPresent(),
                    certificado
                            .map(CertificadoRepositorio.Certificado::revocadoEn)
                            .orElse(null),
                    certificado
                            .map(CertificadoRepositorio.Certificado::expiraEn)
                            .orElse(ahora),
                    ahora);

            boolean valido = "VIGENTE".equals(estado);
            String hashRecomputado =
                    contenidoPublicado == null ? null : CertificadoVerificable.hash(contenidoPublicado);
            if (valido && hashRecomputado != null) {
                valido = hashRecomputado.equals(certificado.get().hashContenido());
            }

            cadenas.registrarVerificacion(
                    dsl,
                    codigo,
                    "CERTIFICADO_REPUTACION",
                    certificado.map(CertificadoRepositorio.Certificado::id).orElse(new UUID(0, 0)),
                    certificado
                            .map(CertificadoRepositorio.Certificado::hashContenido)
                            .orElse(""),
                    hashRecomputado,
                    certificado.isEmpty() ? "SIN_DATOS" : valido ? "COINCIDE" : "NO_COINCIDE",
                    ahora);

            return new SalidaVerificacion(
                    valido,
                    estado,
                    // No se devuelve la fecha de un codigo inexistente: es el unico
                    // campo que distinguiria NO_VALIDO de REVOCADO.
                    valido ? certificado.get().emitidoEn() : null,
                    "NO_VALIDO".equals(estado)
                            ? null
                            : certificado
                                    .map(CertificadoRepositorio.Certificado::expiraEn)
                                    .orElse(null));
        });
    }

    /**
     * Revoca el certificado. Lo usa tambien el derecho de supresion (CU-07): al
     * suprimir, la URL deja de resolver.
     */
    @Transactional
    public void revocar(String codigo, String motivo, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        datos.conContexto(ctx, dsl -> {
            var certificado = certificados
                    .certificadoPorCodigo(dsl, codigo)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(75, 5), "Ese certificado no esta vigente."));
            if (!certificados.revocarCertificado(dsl, certificado.id(), ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(75, 5), "Ese certificado ya estaba revocado.");
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "transparencia.certificado_revocado",
                            "certificado_reputacion",
                            certificado.id(),
                            Map.of("motivo", motivo),
                            UUID.fromString(ctx.traza().id())));
            return null;
        });
    }

    /**
     * @param disponibles todo lo que se podria mostrar, ya resuelto desde la foto
     * @param incluir lo que el titular eligio; lo demas no entra ni al hash
     */
    public record EntradaCertificado(
            UUID usuarioId,
            UUID snapshotId,
            Map<String, String> disponibles,
            Set<String> incluir,
            int vigenciaDias,
            boolean identidadVerificada) {}

    public record SalidaCertificado(
            UUID certificadoId,
            String codigoVerificacion,
            String urlPublica,
            String hashContenido,
            OffsetDateTime emitidoEn,
            OffsetDateTime expiraEn,
            Map<String, String> contenido) {}

    public record SalidaVerificacion(
            boolean valido, String estado, OffsetDateTime emitidoEn, OffsetDateTime expiraEn) {}
}
