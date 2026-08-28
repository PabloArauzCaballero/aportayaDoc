package bo.aportaya.tarifas.web;

import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import bo.aportaya.tarifas.aplicacion.CU34PublicarTarifario;
import bo.aportaya.tarifas.aplicacion.CU35CerrarLiquidacion;
import bo.aportaya.tarifas.aplicacion.CU36ResolverPrecio;
import bo.aportaya.tarifas.aplicacion.ConsultarTarifarioVigente;
import bo.aportaya.tarifas.web.generado.TarifasApi;
import bo.aportaya.tarifas.web.generado.modelo.EntradaLiquidacion;
import bo.aportaya.tarifas.web.generado.modelo.EntradaPublicacion;
import bo.aportaya.tarifas.web.generado.modelo.EntradaSegmento;
import bo.aportaya.tarifas.web.generado.modelo.PonerTarifarioVigente200Response;
import bo.aportaya.tarifas.web.generado.modelo.ResolverPrecioRequest;
import bo.aportaya.tarifas.web.generado.modelo.SalidaLiquidacion;
import bo.aportaya.tarifas.web.generado.modelo.SalidaPublicacion;
import bo.aportaya.tarifas.web.generado.modelo.SalidaSegmento;
import bo.aportaya.tarifas.web.generado.modelo.SalidaSegmentoAplicado;
import bo.aportaya.tarifas.web.generado.modelo.TarifarioVigente;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /tarifas}: publicar el tarifario, resolver el segmento y cerrar
 * la liquidacion del periodo.
 *
 * <p>Publicar y poner vigente son dos operaciones porque entre una y otra corre el
 * preaviso (R-TAR-05): un tarifario nuevo no rige el dia que se aprueba, rige cuando
 * vencio el aviso a quien lo va a pagar.
 */
@RestController
public class TarifasController implements TarifasApi {

    private final ConsultarTarifarioVigente vigentes;
    private final CU34PublicarTarifario cu34;
    private final CU35CerrarLiquidacion cu35;
    private final CU36ResolverPrecio cu36;
    private final SesionDeLaPeticion sesion;

    public TarifasController(
            ConsultarTarifarioVigente vigentes,
            CU34PublicarTarifario cu34,
            CU35CerrarLiquidacion cu35,
            CU36ResolverPrecio cu36,
            SesionDeLaPeticion sesion) {
        this.vigentes = vigentes;
        this.cu34 = cu34;
        this.cu35 = cu35;
        this.cu36 = cu36;
        this.sesion = sesion;
    }

    /**
     * Cual es el tarifario vigente de un codigo.
     *
     * <p>Sin tarifario vigente no se abre un grupo (R-CON-07). Que no haya ninguno es
     * una respuesta valida, no un error: por eso viaja como {@code vigente: false} y no
     * como un 404 que cada cliente tendria que interpretar.
     */
    @Override
    @Permiso("BILLETERA_VER")
    public ResponseEntity<TarifarioVigente> consultarTarifarioVigente(String codigo) {
        Traza.marcarCasoDeUso("CU-34", codigo);

        var encontrado = vigentes.ejecutar(codigo, sesion.actual());

        var respuesta = new TarifarioVigente();
        respuesta.setVigente(encontrado.isPresent());
        encontrado.ifPresent(respuesta::setTarifarioId);
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("TARIFARIO_PUBLICAR")
    public ResponseEntity<SalidaPublicacion> publicarTarifario(EntradaPublicacion cuerpo) {
        Traza.marcarCasoDeUso("CU-34", cuerpo.getNombre());

        var salida = cu34.publicar(
                new CU34PublicarTarifario.EntradaPublicacion(
                        cuerpo.getTarifarioBaseId(),
                        cuerpo.getNombre(),
                        cuerpo.getTipoCambio().getValue(),
                        cuerpo.getDiasPreaviso(),
                        cuerpo.getAprobadoPor(),
                        cuerpo.getActaComite(),
                        cuerpo.getUrlPublicacion().toString(),
                        cuerpo.getHashDocumento(),
                        cuerpo.getCanalAviso(),
                        Boolean.TRUE.equals(cuerpo.getPermiteRescisionSinCosto()),
                        // El escenario y el resultado de la simulacion de impacto no
                        // viajan en el contrato. Van vacios y queda declarado: es un
                        // hueco de contrato, no una simulacion que dio cero.
                        "{}",
                        "{}",
                        cuerpo.getDeltaIngresoEstimado() == null
                                ? BigDecimal.ZERO
                                : new BigDecimal(cuerpo.getDeltaIngresoEstimado()),
                        cuerpo.getUsuariosImpactados() == null ? 0 : cuerpo.getUsuariosImpactados()),
                sesion.actual());

        var respuesta = new SalidaPublicacion();
        respuesta.setTarifarioNuevoId(salida.tarifarioNuevoId());
        respuesta.setCambioId(salida.cambioId());
        respuesta.setVersion(salida.version());
        respuesta.setEstado(SalidaPublicacion.EstadoEnum.fromValue(salida.estado()));
        respuesta.setEntraEnVigencia(salida.entraEnVigencia());
        respuesta.setRequierePreaviso(salida.requierePreaviso());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("TARIFARIO_PUBLICAR")
    public ResponseEntity<PonerTarifarioVigente200Response> ponerTarifarioVigente(UUID tarifarioId) {
        Traza.marcarCasoDeUso("CU-34", tarifarioId.toString());

        var salida = cu34.ponerVigente(tarifarioId, sesion.actual());

        var respuesta = new PonerTarifarioVigente200Response();
        respuesta.setTarifarioId(salida.tarifarioId());
        respuesta.setEstado(PonerTarifarioVigente200Response.EstadoEnum.fromValue(salida.estado()));
        respuesta.setDesde(salida.desde());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("CATALOGO_EDITAR")
    public ResponseEntity<SalidaSegmento> crearSegmento(EntradaSegmento cuerpo) {
        Traza.marcarCasoDeUso("CU-36", cuerpo.getCodigo());

        var salida = cu36.crear(
                new CU36ResolverPrecio.EntradaSegmento(
                        cuerpo.getCodigo(), cuerpo.getDescripcion(), cuerpo.getCriterio(), cuerpo.getPrioridad()),
                sesion.actual());

        var respuesta = new SalidaSegmento();
        respuesta.setSegmentoId(salida.segmentoId());
        respuesta.setCodigo(salida.codigo());
        respuesta.setPrioridad(salida.prioridad());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("BILLETERA_VER")
    public ResponseEntity<SalidaSegmentoAplicado> resolverPrecio(ResolverPrecioRequest cuerpo) {
        Traza.marcarCasoDeUso("CU-36", String.valueOf(cuerpo.getHechos().size()));

        var eleccion = cu36.resolver(cuerpo.getHechos(), sesion.actual());

        var respuesta = new SalidaSegmentoAplicado();
        respuesta.setCodigo(eleccion.codigo());
        respuesta.setMotivo(eleccion.motivo());
        respuesta.setEvaluable(eleccion.evaluable());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("CONTABILIDAD")
    public ResponseEntity<SalidaLiquidacion> cerrarLiquidacion(String periodo, EntradaLiquidacion cuerpo) {
        Traza.marcarCasoDeUso("CU-35", periodo);

        var salida = cu35.cerrar(
                new CU35CerrarLiquidacion.EntradaLiquidacion(
                        periodo,
                        cuerpo.getDiasSinCerrar(),
                        cuerpo.getExcepcionesAbiertas(),
                        new BigDecimal(cuerpo.getSaldoDeLaCuentaDeIngresos())),
                sesion.actual());

        var respuesta = new SalidaLiquidacion();
        respuesta.setLiquidacionId(salida.liquidacionId());
        respuesta.setPeriodo(salida.periodo());
        respuesta.setTotalCobrado(MapeoDeTarifas.importe(salida.totalCobrado()));
        respuesta.setIngresoNeto(MapeoDeTarifas.importe(salida.ingresoNeto()));
        respuesta.setCuadraContraMayor(salida.cuadraContraMayor());
        respuesta.setYaExistia(salida.yaExistia());

        var estado = salida.yaExistia() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(estado).body(respuesta);
    }
}
