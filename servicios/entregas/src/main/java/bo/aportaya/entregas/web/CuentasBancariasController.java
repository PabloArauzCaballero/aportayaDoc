package bo.aportaya.entregas.web;

import bo.aportaya.entregas.aplicacion.CU18RegistrarCuentaDestino;
import bo.aportaya.entregas.web.generado.CuentasBancariasApi;
import bo.aportaya.entregas.web.generado.modelo.DesignarCuentaPrincipal200Response;
import bo.aportaya.entregas.web.generado.modelo.Disponibilidad;
import bo.aportaya.entregas.web.generado.modelo.SalidaVerificacionCuenta;
import bo.aportaya.entregas.web.generado.modelo.VerificarCuentaDestinoRequest;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /cuentas-bancarias}.
 *
 * <p><b>{@code registrarCuentaDestino} no esta implementada, y no es un olvido.</b>
 * CU-18 comprueba que el titular de la cuenta sea el titular de la billetera
 * (AP-CU18-01, R-SEG-02) comparando nombre y documento del usuario contra los de la
 * cuenta. Esos dos datos viven en {@code identidad.usuario}, que este servicio no
 * puede leer (invariante 11), y el token no los lleva. Resolverlo tomando el nombre y
 * el documento del propio cuerpo de la peticion haria que la comprobacion se compare
 * consigo misma: pasaria siempre, y la regla quedaria escrita pero muerta.
 *
 * <p>Queda declarado en {@code planes/informes/carril-2E.md}. La ruta responde
 * {@code 501} —el metodo por omision de la interfaz generada— hasta que exista la
 * forma de traer esa identidad sin romper el invariante.
 *
 * <p>El {@code @Permiso} de clase cubre esa ruta: sin decision de acceso declarada, el
 * proceso no levanta, y una ruta sin implementar no es una excusa para dejarla abierta.
 */
@RestController
@Permiso("BILLETERA_OPERAR")
public class CuentasBancariasController implements CuentasBancariasApi {

    private final CU18RegistrarCuentaDestino cu18;
    private final SesionDeLaPeticion sesion;

    public CuentasBancariasController(CU18RegistrarCuentaDestino cu18, SesionDeLaPeticion sesion) {
        this.cu18 = cu18;
        this.sesion = sesion;
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaVerificacionCuenta> verificarCuentaDestino(
            UUID cuentaId, VerificarCuentaDestinoRequest cuerpo) {
        Traza.marcarCasoDeUso("CU-18", cuentaId.toString());

        var salida = cu18.verificar(cuentaId, cuerpo.getMetodo(), sesion.actual());

        var respuesta = new SalidaVerificacionCuenta();
        respuesta.setCuentaId(salida.cuentaId());
        respuesta.setEstado(salida.estado());
        respuesta.setDisponibleDesde(salida.disponibleDesde());
        respuesta.setEsNueva(salida.esNueva());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<Disponibilidad> consultarDisponibilidad(UUID cuentaId) {
        Traza.marcarCasoDeUso("CU-18", cuentaId.toString());

        var salida = cu18.disponibilidad(cuentaId, sesion.actual());

        var respuesta = new Disponibilidad();
        respuesta.setDisponible(salida.disponible());
        respuesta.setRestanteSegundos(Math.toIntExact(salida.restante().toSeconds()));
        respuesta.setMotivo(salida.motivo());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<DesignarCuentaPrincipal200Response> designarCuentaPrincipal(UUID cuentaId) {
        Traza.marcarCasoDeUso("CU-18", cuentaId.toString());

        boolean designada = cu18.designarPrincipal(cuentaId, sesion.actual());

        var respuesta = new DesignarCuentaPrincipal200Response();
        respuesta.setDesignada(designada);
        return ResponseEntity.ok(respuesta);
    }
}
