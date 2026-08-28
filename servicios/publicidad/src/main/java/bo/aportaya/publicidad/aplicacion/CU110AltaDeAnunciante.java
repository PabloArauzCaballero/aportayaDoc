package bo.aportaya.publicidad.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.publicidad.dominio.ReferenciaDelAnunciante;
import bo.aportaya.publicidad.infraestructura.AnuncianteRepositorio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-110 · Dar de alta un anunciante y su cuenta publicitaria.
 *
 * <p>Un anunciante es un organizador de la plataforma o un negocio de afuera, **nunca
 * los dos y nunca ninguno** (R-PUB-01). La regla la sostiene
 * {@code ck_anunciante_tipo_exclusivo}; aca se comprueba antes para poder explicarla,
 * porque un CHECK violado le llega al anunciante como un error sin nombre.
 *
 * <p>El alta del anunciante y la apertura de su cuenta van en la misma transaccion: un
 * anunciante sin cuenta no puede financiar nada, y quedaria como una fila que solo
 * sirve para confundir a quien la encuentre.
 */
@Service
public class CU110AltaDeAnunciante {

    private final Datos datos;
    private final AnuncianteRepositorio anunciantes;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU110AltaDeAnunciante(Datos datos, AnuncianteRepositorio anunciantes, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.anunciantes = anunciantes;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    /** Postula un negocio externo. Queda POSTULADO hasta que Operaciones lo verifique. */
    @Transactional
    public UUID postularSocio(EntradaSocio entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> {
            var existente = anunciantes.socioPorDocumento(dsl, entrada.numeroDocumento());
            if (existente.isPresent()) {
                // El reintento es inocuo: devuelve el que hay. Dos socios con el mismo
                // documento serian dos deudores por la misma publicidad.
                return UUID.fromString(existente.get());
            }
            UUID id = anunciantes.altaDeSocio(
                    dsl,
                    entrada.razonSocial(),
                    entrada.numeroDocumento(),
                    entrada.rubro(),
                    entrada.emailContacto(),
                    ahora);
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "publicidad.socio_comercial_postulado",
                            "socio_comercial",
                            id,
                            Map.of("numeroDocumento", entrada.numeroDocumento()),
                            UUID.fromString(ctx.traza().id())));
            return id;
        });
    }

    /** Operaciones verifica al socio: recien ahi puede ser anunciante. */
    @Transactional
    public boolean verificarSocio(UUID socioId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var socio = anunciantes
                    .socio(dsl, socioId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(110, 3), "Ese socio comercial no existe."));
            if (!"POSTULADO".equals(socio.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(110, 3),
                        "El socio comercial esta " + socio.estado() + ": la verificacion es del alta.");
            }
            boolean verificado = anunciantes.verificarSocio(dsl, socioId, ctx.usuarioId());
            if (verificado) {
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "publicidad.socio_comercial_verificado",
                                "socio_comercial",
                                socioId,
                                Map.of("verificadoPor", ctx.usuarioId().toString()),
                                UUID.fromString(ctx.traza().id())));
            }
            return verificado;
        });
    }

    @Transactional
    public Salida darDeAlta(EntradaAnunciante entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        var referencia =
                new ReferenciaDelAnunciante(entrada.tipo(), entrada.organizadorId(), entrada.socioComercialId());

        // AP-CU110-02 · R-PUB-01. Se comprueba antes de escribir para poder decir cual
        // de las dos referencias sobra; el CHECK diria solo que la fila no vale.
        if (!referencia.esValida()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(110, 2),
                    "El tipo %s no concuerda con las referencias recibidas (%s)."
                            .formatted(entrada.tipo(), referencia.veredicto()));
        }

        return datos.conContexto(ctx, dsl -> {
            comprobarReferencia(dsl, referencia);

            UUID anuncianteId = anunciantes.altaDeAnunciante(
                    dsl,
                    entrada.tipo(),
                    entrada.organizadorId(),
                    entrada.socioComercialId(),
                    entrada.razonSocialFacturacion(),
                    ahora);
            UUID cuentaId =
                    anunciantes.abrirCuenta(dsl, anuncianteId, entrada.limiteGastoMensual(), entrada.moneda(), ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "publicidad.anunciante_creado",
                            "anunciante",
                            anuncianteId,
                            Map.of(
                                    "tipo", entrada.tipo(),
                                    "cuentaPublicitariaId", cuentaId.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new Salida(anuncianteId, cuentaId);
        });
    }

    /**
     * Que la referencia exista y este en condiciones.
     *
     * <p>Para un socio comercial la comprobacion es local. Para un organizador hay que
     * mirar el esquema de otro servicio, y esta declarado como hueco del carril: el
     * modelo pone la clave foranea del lado de publicidad, asi que no hay forma de
     * cerrarlo sin una llamada de red — que dentro de la transaccion prohibe el
     * invariante 6.
     */
    private void comprobarReferencia(DSLContext dsl, ReferenciaDelAnunciante referencia) {
        if (ReferenciaDelAnunciante.SOCIO_COMERCIAL.equals(referencia.tipo())) {
            var socio = anunciantes
                    .socio(dsl, referencia.socioComercialId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(110, 3), "Ese socio comercial no existe."));
            // AP-CU110-03: sin verificar, no hay anunciante.
            if (!"ACTIVO".equals(socio.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(110, 3),
                        "El socio comercial esta " + socio.estado() + ": todavia no puede anunciar.");
            }
            return;
        }
        var fila = dsl.fetchOne("SELECT estado FROM organizador.organizador WHERE id = ?", referencia.organizadorId());
        String estado = fila == null ? null : fila.get(0, String.class);
        // AP-CU110-01: un organizador suspendido no compra visibilidad.
        if (!"HABILITADO".equals(estado)) {
            throw new ErrorDeNegocio(
                    CodigoError.de(110, 1),
                    "El organizador esta " + (estado == null ? "inexistente" : estado) + ": no puede anunciar.");
        }
    }

    public record EntradaSocio(String razonSocial, String numeroDocumento, String rubro, String emailContacto) {}

    public record EntradaAnunciante(
            String tipo,
            UUID organizadorId,
            UUID socioComercialId,
            String razonSocialFacturacion,
            BigDecimal limiteGastoMensual,
            String moneda) {}

    public record Salida(UUID anuncianteId, UUID cuentaPublicitariaId) {}
}
