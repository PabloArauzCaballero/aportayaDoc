package bo.aportaya.garantia.web;

import bo.aportaya.garantia.aplicacion.CU29DevolverFondo;
import bo.aportaya.garantia.aplicacion.CU66ReemplazarParticipante;
import bo.aportaya.garantia.aplicacion.CU67DisolverGrupo;
import bo.aportaya.garantia.dominio.CuadreDeDisolucion;
import bo.aportaya.garantia.web.generado.GarantiaApi;
import bo.aportaya.garantia.web.generado.modelo.AprobarReemplazo200Response;
import bo.aportaya.garantia.web.generado.modelo.EntradaDevolucionFondo;
import bo.aportaya.garantia.web.generado.modelo.EntradaDisolucion;
import bo.aportaya.garantia.web.generado.modelo.SalidaCierreDisolucion;
import bo.aportaya.garantia.web.generado.modelo.SalidaDevolucionFondo;
import bo.aportaya.garantia.web.generado.modelo.SalidaDisolucion;
import bo.aportaya.garantia.web.generado.modelo.SalidaReemplazo;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /garantia}: el fondo, el reemplazo y la disolucion.
 *
 * <p>La disolucion se inicia y se cierra en dos actos: entre uno y otro hay que
 * devolverle a cada quien lo suyo, y **ni un centavo se pierde ni se inventa** — el
 * cuadre de la masa lo comprueba el dominio antes de escribir nada.
 */
@RestController
public class GarantiaController implements GarantiaApi {

    private final CU29DevolverFondo cu29;
    private final CU66ReemplazarParticipante cu66;
    private final CU67DisolverGrupo cu67;
    private final SesionDeLaPeticion sesion;

    public GarantiaController(
            CU29DevolverFondo cu29,
            CU66ReemplazarParticipante cu66,
            CU67DisolverGrupo cu67,
            SesionDeLaPeticion sesion) {
        this.cu29 = cu29;
        this.cu66 = cu66;
        this.cu67 = cu67;
        this.sesion = sesion;
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<SalidaDevolucionFondo> devolverFondo(UUID grupoId, EntradaDevolucionFondo cuerpo) {
        Traza.marcarCasoDeUso("CU-29", grupoId.toString());

        var salida = cu29.devolver(
                new CU29DevolverFondo.EntradaDevolucion(
                        grupoId, Boolean.TRUE.equals(cuerpo.getGrupoCerrado()), cuerpo.getDeudasVivas()),
                sesion.actual());

        var respuesta = new SalidaDevolucionFondo();
        respuesta.setFondoId(salida.fondoId());
        respuesta.setTotalAportado(MapeoDeGarantia.dinero(salida.totalAportado()));
        respuesta.setTotalDevuelto(MapeoDeGarantia.dinero(salida.totalDevuelto()));
        respuesta.setConsumidoPorCoberturas(MapeoDeGarantia.dinero(salida.consumidoPorCoberturas()));
        respuesta.setDevoluciones(
                salida.devoluciones().stream().map(MapeoDeGarantia::devolucion).toList());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<AprobarReemplazo200Response> aprobarReemplazo(UUID reemplazoId) {
        Traza.marcarCasoDeUso("CU-66", reemplazoId.toString());

        var respuesta = new AprobarReemplazo200Response();
        respuesta.setAprobado(cu66.aprobar(reemplazoId, sesion.actual()));
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<SalidaReemplazo> ejecutarReemplazo(UUID reemplazoId) {
        Traza.marcarCasoDeUso("CU-66", reemplazoId.toString());
        return ResponseEntity.ok(MapeoDeReemplazo.salida(cu66.ejecutar(reemplazoId, sesion.actual())));
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<SalidaDisolucion> iniciarDisolucion(UUID grupoId, EntradaDisolucion cuerpo) {
        Traza.marcarCasoDeUso("CU-67", grupoId.toString());

        var salida = cu67.iniciar(
                new CU67DisolverGrupo.EntradaDisolucion(
                        grupoId,
                        cuerpo.getCausal().getValue(),
                        cuerpo.getMotivo(),
                        MapeoDeGarantia.dinero(cuerpo.getTotalAportado()),
                        MapeoDeGarantia.dinero(cuerpo.getTotalEntregado()),
                        MapeoDeGarantia.dinero(cuerpo.getMasaDisponible()),
                        cuerpo.getPosiciones().stream()
                                .map(p -> new CuadreDeDisolucion.Posicion(
                                        p.getParticipanteId(),
                                        MapeoDeGarantia.dinero(p.getAportado()),
                                        MapeoDeGarantia.dinero(p.getRecibido())))
                                .toList()),
                sesion.actual());

        var respuesta = new SalidaDisolucion();
        respuesta.setDisolucionId(salida.disolucionId());
        respuesta.setEstado(salida.estado());
        respuesta.setMasaARepartir(MapeoDeGarantia.dinero(salida.masaARepartir()));
        respuesta.setTotalADevolver(MapeoDeGarantia.dinero(salida.totalADevolver()));
        respuesta.setTotalACobrar(MapeoDeGarantia.dinero(salida.totalACobrar()));
        respuesta.setLiquidaciones(salida.liquidaciones().stream()
                .map(MapeoDeGarantia::liquidacion)
                .toList());
        respuesta.setEsNueva(salida.esNueva());

        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<SalidaCierreDisolucion> cerrarDisolucion(UUID disolucionId) {
        Traza.marcarCasoDeUso("CU-67", disolucionId.toString());

        var salida = cu67.cerrar(disolucionId, sesion.actual());

        var respuesta = new SalidaCierreDisolucion();
        respuesta.setDisolucionId(salida.disolucionId());
        respuesta.setEstado(SalidaCierreDisolucion.EstadoEnum.fromValue(salida.estado()));
        respuesta.setCerradaEn(salida.cerradaEn());
        return ResponseEntity.ok(respuesta);
    }
}
