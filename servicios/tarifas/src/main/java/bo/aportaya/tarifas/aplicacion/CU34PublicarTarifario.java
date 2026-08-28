package bo.aportaya.tarifas.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.tarifas.dominio.EntradaEnVigencia;
import bo.aportaya.tarifas.infraestructura.CambioTarifarioRepositorio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-34 · Publicar un tarifario nuevo con preaviso.
 *
 * <p>Un incremento **no entra en vigencia sin preaviso cumplido** (R-TAR-08). Es la
 * unica diferencia real entre subir un precio y cobrarle de sorpresa a alguien que ya
 * no puede irse sin perder lo que puso en el pasanaku.
 *
 * <p>El anterior no se borra: pasa a SUSTITUIDO con su vigencia cerrada (R-TAR-01,
 * R-TAR-02). Poder decir que se cobraba en una fecha pasada es lo unico que responde
 * un reclamo de hace seis meses.
 */
@Service
public class CU34PublicarTarifario {

    private final Datos datos;
    private final CambioTarifarioRepositorio tarifarios;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU34PublicarTarifario(Datos datos, CambioTarifarioRepositorio tarifarios, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.tarifarios = tarifarios;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaPublicacion publicar(EntradaPublicacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var base = tarifarios
                    .ver(dsl, entrada.tarifarioBaseId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(34, 2), "Ese tarifario no existe."));

            // AP-CU34-03 · R-LIC-03: toda politica vigente tiene acta de aprobacion.
            // Un tarifario sin acta es un precio que alguien puso solo.
            if (entrada.actaComite() == null || entrada.actaComite().isBlank()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(34, 3), "No se publica un tarifario sin acta de aprobacion del comite.");
            }

            boolean requierePreaviso = EntradaEnVigencia.exigePreaviso(entrada.tipoCambio());
            // Una reduccion puede entrar sin preaviso, pero igual se publica y se
            // registra: bajar el precio sin decirlo tambien deja al usuario sin saber
            // que le cobran.
            int dias = requierePreaviso ? entrada.diasPreaviso() : 0;
            var vigencia = new EntradaEnVigencia(ahora, dias, requierePreaviso);

            UUID nuevoId = tarifarios.clonar(dsl, base, entrada.nombre(), dias, vigencia.momento());
            UUID cambioId = tarifarios.registrarCambio(
                    dsl,
                    base.id(),
                    nuevoId,
                    entrada.aprobadoPor(),
                    entrada.tipoCambio(),
                    requierePreaviso,
                    dias,
                    entrada.permiteRescisionSinCosto());

            tarifarios.guardarSimulacion(
                    dsl,
                    nuevoId,
                    ctx.usuarioId(),
                    entrada.escenarioJson(),
                    entrada.resultadoJson(),
                    entrada.deltaIngresoEstimado(),
                    entrada.usuariosImpactados(),
                    ahora);

            tarifarios.publicar(
                    dsl,
                    nuevoId,
                    entrada.urlPublicacion(),
                    entrada.hashDocumento(),
                    entrada.aprobadoPor(),
                    entrada.actaComite(),
                    ahora);
            tarifarios.anotarAviso(dsl, cambioId, entrada.canalAviso(), entrada.usuariosImpactados(), ahora);
            tarifarios.cambiarEstado(dsl, nuevoId, "BORRADOR", "EN_PREAVISO");

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "tarifas.tarifario_en_preaviso",
                            "tarifario",
                            nuevoId,
                            Map.of(
                                    "codigo", base.codigo(),
                                    "version", Integer.toString(base.version() + 1),
                                    "entraEnVigencia", vigencia.momento().toString(),
                                    "diasPreaviso", Integer.toString(dias)),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaPublicacion(
                    nuevoId, cambioId, base.version() + 1, "EN_PREAVISO", vigencia.momento(), requierePreaviso);
        });
    }

    /**
     * Cumplido el preaviso, el nuevo entra y el anterior queda SUSTITUIDO.
     *
     * <p>El plazo lo verifica **la base** ({@code tg_tarifario_preaviso}). Se comprueba
     * tambien aca para que sea una regla de negocio con su mensaje, no un error 500 con
     * el nombre de un trigger.
     */
    @Transactional
    public SalidaVigencia ponerVigente(UUID tarifarioNuevoId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var nuevo = tarifarios
                    .ver(dsl, tarifarioNuevoId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(34, 2), "Ese tarifario no existe."));
            // AP-CU34-02 · R-TAR-02.
            if ("VIGENTE".equals(nuevo.estado()) || "SUSTITUIDO".equals(nuevo.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(34, 2),
                        "Un tarifario " + nuevo.estado() + " es inmutable: hay que crear una version nueva.");
            }

            var cambio = tarifarios
                    .cambioDe(dsl, tarifarioNuevoId)
                    .orElseThrow(() ->
                            new ErrorDeNegocio(CodigoError.de(34, 3), "Ese tarifario no tiene expediente de cambio."));

            var vigencia = new EntradaEnVigencia(cambio.fechaAviso(), cambio.diasPreaviso(), cambio.requierePreaviso());
            // AP-CU34-01 · R-TAR-08.
            if (cambio.requierePreaviso() && (cambio.fechaAviso() == null || !vigencia.cumplidoEn(ahora))) {
                throw new ErrorDeNegocio(
                        CodigoError.de(34, 1),
                        "El preaviso de " + cambio.diasPreaviso() + " dias todavia no se cumple.");
            }

            // El anterior se cierra ANTES: si no, el EXCLUDE de R-TAR-01 rechaza el
            // nuevo por solapamiento, que es exactamente lo que tiene que hacer.
            tarifarios.sustituir(dsl, entradaAnterior(dsl, tarifarioNuevoId), ahora);
            if (!tarifarios.cambiarEstado(dsl, tarifarioNuevoId, nuevo.estado(), "VIGENTE")) {
                throw new ErrorDeNegocio(
                        CodigoError.de(34, 2), "Ese tarifario ya no estaba en " + nuevo.estado() + ".");
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "tarifas.tarifario_vigente",
                            "tarifario",
                            tarifarioNuevoId,
                            Map.of("codigo", nuevo.codigo(), "desde", ahora.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaVigencia(tarifarioNuevoId, "VIGENTE", ahora);
        });
    }

    private UUID entradaAnterior(org.jooq.DSLContext dsl, UUID nuevoId) {
        return dsl.select(org.jooq.impl.DSL.field("tarifario_anterior_id", UUID.class))
                .from(org.jooq.impl.DSL.table(org.jooq.impl.DSL.name("catalogo", "tarifario")))
                .where(org.jooq.impl.DSL.field("id", UUID.class).eq(nuevoId))
                .fetchOne(0, UUID.class);
    }

    public record EntradaPublicacion(
            UUID tarifarioBaseId,
            String nombre,
            String tipoCambio,
            int diasPreaviso,
            UUID aprobadoPor,
            String actaComite,
            String urlPublicacion,
            String hashDocumento,
            String canalAviso,
            boolean permiteRescisionSinCosto,
            String escenarioJson,
            String resultadoJson,
            BigDecimal deltaIngresoEstimado,
            int usuariosImpactados) {}

    public record SalidaPublicacion(
            UUID tarifarioNuevoId,
            UUID cambioId,
            int version,
            String estado,
            OffsetDateTime entraEnVigencia,
            boolean requierePreaviso) {}

    public record SalidaVigencia(UUID tarifarioId, String estado, OffsetDateTime desde) {}
}
