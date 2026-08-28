package bo.aportaya.publicidad.web;

import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import bo.aportaya.publicidad.aplicacion.CU110AltaDeAnunciante;
import bo.aportaya.publicidad.aplicacion.CU111CrearCampana;
import bo.aportaya.publicidad.aplicacion.CU112ModerarPieza;
import bo.aportaya.publicidad.aplicacion.CU113EntregarAnuncio;
import bo.aportaya.publicidad.aplicacion.CU114LiquidarPublicidad;
import bo.aportaya.publicidad.web.generado.PublicidadApi;
import bo.aportaya.publicidad.web.generado.modelo.EntradaAnunciante;
import bo.aportaya.publicidad.web.generado.modelo.EntradaAnuncio;
import bo.aportaya.publicidad.web.generado.modelo.EntradaCampana;
import bo.aportaya.publicidad.web.generado.modelo.EntradaConversion;
import bo.aportaya.publicidad.web.generado.modelo.EntradaEntrega;
import bo.aportaya.publicidad.web.generado.modelo.EntradaLiquidacion;
import bo.aportaya.publicidad.web.generado.modelo.EntradaPieza;
import bo.aportaya.publicidad.web.generado.modelo.EntradaRechazo;
import bo.aportaya.publicidad.web.generado.modelo.EntradaRevision;
import bo.aportaya.publicidad.web.generado.modelo.EntradaSocio;
import bo.aportaya.publicidad.web.generado.modelo.SalidaAnunciante;
import bo.aportaya.publicidad.web.generado.modelo.SalidaAnuncio;
import bo.aportaya.publicidad.web.generado.modelo.SalidaCampana;
import bo.aportaya.publicidad.web.generado.modelo.SalidaClic;
import bo.aportaya.publicidad.web.generado.modelo.SalidaConsumo;
import bo.aportaya.publicidad.web.generado.modelo.SalidaConversion;
import bo.aportaya.publicidad.web.generado.modelo.SalidaEntrega;
import bo.aportaya.publicidad.web.generado.modelo.SalidaLiquidacion;
import bo.aportaya.publicidad.web.generado.modelo.SalidaPieza;
import bo.aportaya.publicidad.web.generado.modelo.SalidaRevision;
import bo.aportaya.publicidad.web.generado.modelo.SalidaSocio;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /publicidad}: traducen y delegan, sin logica.
 *
 * <p>La entrega de un anuncio es la unica operacion del sistema que responde
 * {@code 200} con el cuerpo vacio de contenido —{@code anuncioId} nulo— en vez de
 * fallar: que un espacio no muestre publicidad porque nadie tiene presupuesto es el
 * comportamiento correcto, no un error del cliente.
 */
@RestController
public class PublicidadController implements PublicidadApi {

    private final CU110AltaDeAnunciante cu110;
    private final CU111CrearCampana cu111;
    private final CU112ModerarPieza cu112;
    private final CU113EntregarAnuncio cu113;
    private final CU114LiquidarPublicidad cu114;
    private final SesionDeLaPeticion sesion;

    public PublicidadController(
            CU110AltaDeAnunciante cu110,
            CU111CrearCampana cu111,
            CU112ModerarPieza cu112,
            CU113EntregarAnuncio cu113,
            CU114LiquidarPublicidad cu114,
            SesionDeLaPeticion sesion) {
        this.cu110 = cu110;
        this.cu111 = cu111;
        this.cu112 = cu112;
        this.cu113 = cu113;
        this.cu114 = cu114;
        this.sesion = sesion;
    }

    // ------------------------------------------------------------------ CU-110 --

    @Override
    @Permiso("PUBLICIDAD_ANUNCIANTES")
    public ResponseEntity<SalidaSocio> postularSocioComercial(UUID idempotencyKey, EntradaSocio cuerpo) {
        Traza.marcarCasoDeUso("CU-110", cuerpo.getNumeroDocumento());

        UUID id = cu110.postularSocio(MapeoDePublicidad.socio(cuerpo), sesion.actual());
        return ResponseEntity.status(HttpStatus.CREATED).body(socio(id, SalidaSocio.EstadoEnum.POSTULADO));
    }

    @Override
    @Permiso("PUBLICIDAD_ANUNCIANTES")
    public ResponseEntity<SalidaSocio> verificarSocioComercial(UUID socioId, UUID idempotencyKey) {
        Traza.marcarCasoDeUso("CU-110", socioId.toString());

        cu110.verificarSocio(socioId, sesion.actual());
        return ResponseEntity.ok(socio(socioId, SalidaSocio.EstadoEnum.ACTIVO));
    }

    @Override
    @Permiso("PUBLICIDAD_ANUNCIANTES")
    public ResponseEntity<SalidaAnunciante> darDeAltaAnunciante(UUID idempotencyKey, EntradaAnunciante cuerpo) {
        Traza.marcarCasoDeUso("CU-110", cuerpo.getTipo().getValue());

        var salida = cu110.darDeAlta(MapeoDePublicidad.anunciante(cuerpo), sesion.actual());

        var respuesta = new SalidaAnunciante();
        respuesta.setAnuncianteId(salida.anuncianteId());
        respuesta.setCuentaPublicitariaId(salida.cuentaPublicitariaId());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    // ------------------------------------------------------------------ CU-111 --

    @Override
    @Permiso("PUBLICIDAD_CAMPANA_GESTIONAR")
    public ResponseEntity<SalidaCampana> crearCampana(UUID idempotencyKey, EntradaCampana cuerpo) {
        Traza.marcarCasoDeUso("CU-111", cuerpo.getNombre());

        var salida = cu111.crear(MapeoDePublicidad.campana(cuerpo), sesion.actual());
        return ResponseEntity.status(HttpStatus.CREATED).body(MapeoDePublicidad.campana(salida));
    }

    @Override
    @Permiso("PUBLICIDAD_APROBAR_CAMPANA")
    public ResponseEntity<SalidaCampana> aprobarCampana(UUID campanaId, UUID idempotencyKey) {
        Traza.marcarCasoDeUso("CU-111", campanaId.toString());
        return ResponseEntity.ok(MapeoDePublicidad.campana(cu111.aprobar(campanaId, sesion.actual())));
    }

    @Override
    @Permiso("PUBLICIDAD_APROBAR_CAMPANA")
    public ResponseEntity<SalidaCampana> rechazarCampana(UUID campanaId, UUID idempotencyKey, EntradaRechazo cuerpo) {
        Traza.marcarCasoDeUso("CU-111", campanaId.toString());

        var salida = cu111.rechazar(campanaId, cuerpo.getMotivo(), sesion.actual());
        return ResponseEntity.ok(MapeoDePublicidad.campana(salida));
    }

    // ------------------------------------------------------------------ CU-112 --

    @Override
    @Permiso("PUBLICIDAD_ANUNCIANTES")
    public ResponseEntity<SalidaPieza> subirPiezaCreativa(UUID idempotencyKey, EntradaPieza cuerpo) {
        Traza.marcarCasoDeUso("CU-112", cuerpo.getTitulo());

        UUID id = cu112.subir(MapeoDePublicidad.pieza(cuerpo), sesion.actual());

        var respuesta = new SalidaPieza();
        respuesta.setPiezaCreativaId(id);
        respuesta.setEstadoModeracion(SalidaPieza.EstadoModeracionEnum.PENDIENTE);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("PUBLICIDAD_MODERAR")
    public ResponseEntity<SalidaRevision> moderarPiezaCreativa(
            UUID piezaId, UUID idempotencyKey, EntradaRevision cuerpo) {
        Traza.marcarCasoDeUso("CU-112", piezaId.toString());

        var salida = cu112.moderar(
                new CU112ModerarPieza.EntradaRevision(
                        piezaId, cuerpo.getDecision().getValue(), cuerpo.getMotivo()),
                sesion.actual());

        var respuesta = new SalidaRevision();
        respuesta.setRevisionId(salida.revisionId());
        respuesta.setEstadoModeracion(SalidaRevision.EstadoModeracionEnum.fromValue(salida.estadoModeracion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    // ------------------------------------------------------------------ CU-113 --

    @Override
    @Permiso("PUBLICIDAD_CAMPANA_GESTIONAR")
    public ResponseEntity<SalidaAnuncio> programarAnuncio(UUID idempotencyKey, EntradaAnuncio cuerpo) {
        Traza.marcarCasoDeUso("CU-113", cuerpo.getConjuntoAnunciosId().toString());

        UUID id = cu113.programar(cuerpo.getConjuntoAnunciosId(), cuerpo.getPiezaCreativaId(), sesion.actual());

        var respuesta = new SalidaAnuncio();
        respuesta.setAnuncioId(id);
        respuesta.setEstado(SalidaAnuncio.EstadoEnum.PROGRAMADO);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("PUBLICIDAD_CAMPANA_GESTIONAR")
    public ResponseEntity<SalidaEntrega> entregarAnuncio(UUID espacioId, UUID idempotencyKey, EntradaEntrega cuerpo) {
        Traza.marcarCasoDeUso("CU-113", espacioId.toString());

        var salida = cu113.entregar(
                new CU113EntregarAnuncio.Entrada(espacioId, cuerpo == null ? null : cuerpo.getUsuarioId()),
                sesion.actual());

        var respuesta = new SalidaEntrega();
        respuesta.setAnuncioId(salida.anuncioId());
        respuesta.setImpresionId(salida.impresionId());
        respuesta.setCosto(MapeoDePublicidad.costo(salida.costo()));
        if (salida.motivo() != null) {
            respuesta.setMotivo(SalidaEntrega.MotivoEnum.fromValue(salida.motivo()));
        }
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("PUBLICIDAD_CAMPANA_GESTIONAR")
    public ResponseEntity<SalidaClic> registrarClic(UUID impresionId, UUID idempotencyKey) {
        Traza.marcarCasoDeUso("CU-113", impresionId.toString());

        var salida = cu113.registrarClic(impresionId, sesion.actual());

        var respuesta = new SalidaClic();
        respuesta.setClicId(salida.clicId());
        respuesta.setCosto(MapeoDePublicidad.costo(salida.costo()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("PUBLICIDAD_CAMPANA_GESTIONAR")
    public ResponseEntity<SalidaConversion> registrarConversion(UUID idempotencyKey, EntradaConversion cuerpo) {
        Traza.marcarCasoDeUso("CU-113", cuerpo.getTipo().getValue());

        UUID id = cu113.registrarConversion(
                cuerpo.getClicId(),
                cuerpo.getImpresionId(),
                cuerpo.getTipo().getValue(),
                cuerpo.getReferenciaId(),
                sesion.actual());

        var respuesta = new SalidaConversion();
        respuesta.setConversionId(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    // ------------------------------------------------------------------ CU-114 --

    @Override
    @Permiso("PUBLICIDAD_ANUNCIANTES")
    public ResponseEntity<SalidaConsumo> consumoDelPeriodo(UUID cuentaId, String periodo) {
        Traza.marcarCasoDeUso("CU-114", periodo);

        var total = cu114.consumoDelPeriodo(cuentaId, periodo, sesion.actual());

        var respuesta = new SalidaConsumo();
        respuesta.setCuentaPublicitariaId(cuentaId);
        respuesta.setPeriodo(periodo);
        respuesta.setTotal(MapeoDePublicidad.monto(total));
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("PUBLICIDAD_LIQUIDAR")
    public ResponseEntity<SalidaLiquidacion> liquidarPeriodo(
            UUID cuentaId, UUID idempotencyKey, EntradaLiquidacion cuerpo) {
        Traza.marcarCasoDeUso("CU-114", cuerpo.getPeriodo());

        var salida = cu114.liquidar(MapeoDePublicidad.liquidacion(cuentaId, cuerpo), sesion.actual());

        var respuesta = new SalidaLiquidacion();
        respuesta.setFacturaPublicidadId(salida.facturaPublicidadId());
        respuesta.setMontoTotal(MapeoDePublicidad.monto(salida.montoTotal()));
        respuesta.setMoneda(SalidaLiquidacion.MonedaEnum.fromValue(salida.moneda()));
        respuesta.setEstado(SalidaLiquidacion.EstadoEnum.fromValue(salida.estado()));
        respuesta.setCuentaPorCobrarId(salida.cuentaPorCobrarId());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    private SalidaSocio socio(UUID id, SalidaSocio.EstadoEnum estado) {
        var respuesta = new SalidaSocio();
        respuesta.setSocioComercialId(id);
        respuesta.setEstado(estado);
        return respuesta;
    }
}
