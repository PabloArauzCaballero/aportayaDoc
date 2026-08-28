package bo.aportaya.entregas.web;

import bo.aportaya.entregas.aplicacion.CU18RegistrarCuentaDestino;
import bo.aportaya.entregas.dominio.puertos.TitularDeLaBilletera;
import bo.aportaya.entregas.web.generado.CuentasBancariasApi;
import bo.aportaya.entregas.web.generado.modelo.DesignarCuentaPrincipal200Response;
import bo.aportaya.entregas.web.generado.modelo.Disponibilidad;
import bo.aportaya.entregas.web.generado.modelo.EntradaRegistroCuenta;
import bo.aportaya.entregas.web.generado.modelo.SalidaRegistroCuenta;
import bo.aportaya.entregas.web.generado.modelo.SalidaVerificacionCuenta;
import bo.aportaya.entregas.web.generado.modelo.VerificarCuentaDestinoRequest;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /cuentas-bancarias}.
 *
 * <p>El alta comprueba que la cuenta sea de quien la registra (AP-CU18-01, R-SEG-02).
 * El nombre y el documento del titular viven en {@code identidad}, asi que **se
 * preguntan**: este servicio no lee ese esquema (invariante 11) y no le sirve que se
 * los declare el mismo que registra la cuenta —seria compararlo consigo mismo y la
 * regla pasaria siempre—. La pregunta sale antes de abrir la transaccion (invariante 6),
 * y si {@code identidad} no contesta, la respuesta es no.
 */
@RestController
@Permiso("BILLETERA_OPERAR")
public class CuentasBancariasController implements CuentasBancariasApi {

    private final CU18RegistrarCuentaDestino cu18;
    private final TitularDeLaBilletera titular;
    private final SesionDeLaPeticion sesion;

    public CuentasBancariasController(
            CU18RegistrarCuentaDestino cu18, TitularDeLaBilletera titular, SesionDeLaPeticion sesion) {
        this.cu18 = cu18;
        this.titular = titular;
        this.sesion = sesion;
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaRegistroCuenta> registrarCuentaDestino(EntradaRegistroCuenta cuerpo) {
        var ctx = sesion.actual();
        Traza.marcarCasoDeUso("CU-18", cuerpo.getEntidadFinanciera());

        // AP-CU18-01 · R-SEG-02. Se resuelve ANTES de la transaccion: es una llamada de
        // red, y una llamada de red adentro es el invariante 6.
        if (!titular.esElMismo(ctx.usuarioId(), cuerpo.getTitularNombre(), cuerpo.getTitularDocumento())) {
            throw new ErrorDeNegocio(
                    CodigoError.de(18, 1), "El titular de la cuenta no coincide con quien la registra.");
        }

        var salida = cu18.registrar(
                new CU18RegistrarCuentaDestino.EntradaRegistro(
                        cuerpo.getTipoCuenta().getValue(),
                        cuerpo.getEntidadFinanciera(),
                        cuerpo.getNumeroCuenta(),
                        cuerpo.getNumeroCifrado(),
                        cuerpo.getTitularNombre(),
                        cuerpo.getTitularDocumento(),
                        // Ya comprobados contra identidad: se pasan iguales para que el
                        // atomo del dominio siga siendo el unico que decide.
                        cuerpo.getTitularNombre(),
                        cuerpo.getTitularDocumento(),
                        cuerpo.getMoneda().getValue()),
                ctx);

        var respuesta = new SalidaRegistroCuenta();
        respuesta.setCuentaId(salida.cuentaId());
        respuesta.setNumeroEnmascarado(salida.numeroEnmascarado());
        respuesta.setEstado(SalidaRegistroCuenta.EstadoEnum.fromValue(salida.estado()));
        respuesta.setEsNueva(salida.esNueva());

        var estado = salida.esNueva() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(respuesta);
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
