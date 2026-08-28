package bo.aportaya.cumplimiento.web;

import bo.aportaya.cumplimiento.aplicacion.CU46VerificarAlcance;
import bo.aportaya.cumplimiento.web.generado.LicenciaApi;
import bo.aportaya.cumplimiento.web.generado.modelo.SalidaAlcance;
import bo.aportaya.cumplimiento.web.generado.modelo.SalidaAlcanceLimitesSandbox;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * La pagina de {@code /licencia}: si un servicio financiero esta habilitado hoy.
 *
 * <p>La responde con **denegar por omision** (invariante 9): sin licencia vigente que
 * cubra ese servicio, la respuesta es que no, no que todavia no se sabe. Es la consulta
 * que los otros trece servicios hacen antes de abrir una billetera o crear un grupo.
 *
 * <p>El conteo de usuarios registrados —que decide si el arenero todavia alcanza— es de
 * {@code identidad}. Este servicio no lo puede leer (invariante 11), asi que la ruta
 * responde con cero: **la respuesta mas conservadora es la que no consume cupo del
 * arenero**. Queda declarado en el informe del carril.
 */
@RestController
public class LicenciaController implements LicenciaApi {

    private final CU46VerificarAlcance cu46;
    private final SesionDeLaPeticion sesion;

    public LicenciaController(CU46VerificarAlcance cu46, SesionDeLaPeticion sesion) {
        this.cu46 = cu46;
        this.sesion = sesion;
    }

    @Override
    @Permiso("SOPORTE")
    public ResponseEntity<SalidaAlcance> verificarAlcance(String servicio, UUID usuarioId) {
        Traza.marcarCasoDeUso("CU-46", servicio);

        var salida = cu46.ejecutar(
                new CU46VerificarAlcance.EntradaAlcance(servicio, Optional.ofNullable(usuarioId), 0), sesion.actual());

        var respuesta = new SalidaAlcance();
        respuesta.setHabilitado(salida.habilitado());
        respuesta.setVia(SalidaAlcance.ViaEnum.fromValue(salida.via()));
        respuesta.setMotivo(salida.motivo());
        if (salida.limitesSandbox() != null) {
            var limites = new SalidaAlcanceLimitesSandbox();
            limites.setUsuarios(salida.limitesSandbox().usuarios());
            limites.setMontoOperacion(salida.limitesSandbox().montoOperacion());
            respuesta.setLimitesSandbox(limites);
        }
        return ResponseEntity.ok(respuesta);
    }
}
