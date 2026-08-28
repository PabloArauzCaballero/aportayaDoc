package bo.aportaya.identidad.web;

import bo.aportaya.identidad.aplicacion.EmitirAcceso;
import bo.aportaya.plataforma.web.seguridad.Publico;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La clave publica con la que los otros trece servicios verifican la firma.
 *
 * <p>Publica por definicion: una clave publica que exige sesion no sirve para verificar
 * la sesion. Lo que se expone es solo la parte publica —modulo y exponente—, nunca la
 * privada, y por eso publicarla no le da a nadie la capacidad de emitir.
 *
 * <p><b>No esta en {@code openapi/identidad.yaml} a proposito.</b> No es una operacion
 * de negocio sino infraestructura de la plataforma, igual que {@code /actuator/health}:
 * su forma la fija ADR-024 y RFC 7517, no nuestro contrato. Ponerla en el contrato la
 * haria caer fuera de los prefijos reservados de identidad, que es una regla sobre las
 * rutas del producto.
 */
@RestController
public class JwksController {

    private final EmitirAcceso acceso;

    public JwksController(EmitirAcceso acceso) {
        this.acceso = acceso;
    }

    @GetMapping("/.well-known/jwks.json")
    @Publico("ADR-024: la clave publica se publica; es lo que permite validar sin preguntar")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok(acceso.clavesPublicas());
    }
}
