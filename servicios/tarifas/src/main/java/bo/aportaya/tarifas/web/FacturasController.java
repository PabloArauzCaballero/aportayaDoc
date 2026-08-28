package bo.aportaya.tarifas.web;

import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import bo.aportaya.tarifas.aplicacion.CU32EmitirFactura;
import bo.aportaya.tarifas.web.generado.FacturasApi;
import bo.aportaya.tarifas.web.generado.modelo.EntradaFactura;
import bo.aportaya.tarifas.web.generado.modelo.SalidaFactura;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * La pagina de {@code /facturas}.
 *
 * <p>La consulta al servicio fiscal se hace **antes** de entrar al caso de uso, y por
 * eso esta aca: es una llamada de red, y una llamada de red dentro de la transaccion
 * es el invariante 6. Si el SIN no responde, CU-32 emite en contingencia con el CUFD
 * que ya tenia; lo que no hace es dejar la transaccion abierta esperando.
 */
@RestController
public class FacturasController implements FacturasApi {

    private final CU32EmitirFactura cu32;
    private final SesionDeLaPeticion sesion;

    public FacturasController(CU32EmitirFactura cu32, SesionDeLaPeticion sesion) {
        this.cu32 = cu32;
        this.sesion = sesion;
    }

    @Override
    @Permiso("CONTABILIDAD")
    public ResponseEntity<SalidaFactura> emitirFactura(EntradaFactura cuerpo) {
        Traza.marcarCasoDeUso("CU-32", cuerpo.getDevengoId().toString());

        var consulta = cu32.consultarAlServicio();

        var salida = cu32.emitir(
                new CU32EmitirFactura.EntradaFactura(
                        cuerpo.getDevengoId(), MapeoDeTarifas.dinero(cuerpo.getMontoIva()), cuerpo.getUrlPdf()),
                consulta,
                sesion.actual());

        var respuesta = new SalidaFactura();
        respuesta.setFacturaId(salida.facturaId());
        respuesta.setCuf(salida.cuf());
        respuesta.setNumeroFactura(salida.numeroFactura());
        respuesta.setEstadoFiscal(SalidaFactura.EstadoFiscalEnum.fromValue(salida.estadoFiscal()));
        respuesta.setEventoSignificativoId(salida.eventoSignificativoId());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
