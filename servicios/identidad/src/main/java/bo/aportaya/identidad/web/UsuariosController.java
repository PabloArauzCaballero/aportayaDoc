package bo.aportaya.identidad.web;

import bo.aportaya.identidad.aplicacion.CU01RegistrarUsuario;
import bo.aportaya.identidad.dominio.DocumentoDeIdentidad;
import bo.aportaya.identidad.web.generado.UsuariosApi;
import bo.aportaya.identidad.web.generado.modelo.EntradaRegistro;
import bo.aportaya.identidad.web.generado.modelo.SalidaRegistro;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.web.seguridad.Publico;
import bo.aportaya.plataforma.web.traza.Traza;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * La pagina de CU-01: traduce y delega, sin logica.
 *
 * <p>Devuelve {@code 202} y no {@code 201}. **No promete la billetera**: esa la abre
 * `nucleo-financiero` al consumir el evento, y decir aca que ya esta abierta seria
 * decirle al cliente que tiene algo que todavia nadie abrio.
 */
@RestController
public class UsuariosController implements UsuariosApi {

    /** El «usuario» del contexto mientras todavia no hay usuario. */
    private static final UUID PROCESO_DE_ALTA = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final CU01RegistrarUsuario cu01;
    private final HttpServletRequest peticion;
    private final String pimienta;

    public UsuariosController(
            CU01RegistrarUsuario cu01,
            HttpServletRequest peticion,
            @Value("${aportaya.seguridad.pimienta}") String pimienta) {
        this.cu01 = cu01;
        this.peticion = peticion;
        this.pimienta = pimienta;
    }

    @Override
    @Publico("CU-01: el alta ocurre antes de que exista la sesion")
    public ResponseEntity<SalidaRegistro> registrarUsuario(UUID idempotencyKey, EntradaRegistro cuerpo) {
        Traza.marcarCasoDeUso("CU-01", String.valueOf(cuerpo.getTelefonoE164()));

        var salida = cu01.ejecutar(mapear(cuerpo), contextoDelAlta());

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new SalidaRegistro(
                        salida.usuarioId(),
                        SalidaRegistro.EstadoEnum.fromValue(salida.estado().name())));
    }

    /**
     * El alta es la unica operacion que corre sin sesion previa: el contexto es el
     * del sistema, y las politicas de fila del rol {@code sistema} son las que
     * permiten escribir la fila de alguien que todavia no existe.
     */
    private ContextoSesion contextoDelAlta() {
        return ContextoSesion.deSistema(PROCESO_DE_ALTA, new bo.aportaya.plataforma.dominio.Traza(Traza.actual()));
    }

    private CU01RegistrarUsuario.EntradaRegistro mapear(EntradaRegistro cuerpo) {
        DocumentoDeIdentidad documento = DocumentoDeIdentidad.de(
                DocumentoDeIdentidad.Tipo.valueOf(
                        tipoDelModelo(cuerpo.getDocumento().getTipo().getValue())),
                cuerpo.getDocumento().getNumero(),
                pimienta,
                "BO");
        return new CU01RegistrarUsuario.EntradaRegistro(
                cuerpo.getTelefonoE164(),
                cuerpo.getNombres(),
                cuerpo.getApellidos(),
                cuerpo.getFechaNacimiento(),
                documento,
                // El cifrado real lo hace el adaptador de archivos; aca la frontera.
                "cifrado:" + documento.hashNumero(),
                "0".repeat(64),
                cuerpo.getAceptaContratos(),
                true,
                Optional.ofNullable(peticion.getRemoteAddr()).orElse("0.0.0.0"),
                Optional.ofNullable(peticion.getHeader("User-Agent")).orElse("desconocido"));
    }

    /** El contrato dice {@code CEX}; el {@code .puml} dice {@code CARNET_EXTRANJERIA}. */
    private String tipoDelModelo(String tipoDelContrato) {
        return "CEX".equals(tipoDelContrato) ? "CARNET_EXTRANJERIA" : tipoDelContrato;
    }
}
