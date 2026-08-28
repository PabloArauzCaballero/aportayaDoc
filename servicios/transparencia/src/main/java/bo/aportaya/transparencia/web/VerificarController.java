package bo.aportaya.transparencia.web;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.web.seguridad.Publico;
import bo.aportaya.transparencia.aplicacion.CU75EmitirCertificado;
import bo.aportaya.transparencia.web.generado.VerificarApi;
import bo.aportaya.transparencia.web.generado.modelo.SalidaVerificacionCertificado;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * La ruta que comprueba un certificado de reputacion desde afuera.
 *
 * <p>Publica y sin sesion: un certificado que solo puede verificar quien ya tiene
 * cuenta no le sirve al banco ni al arrendador a quien se lo muestra. Devuelve si vale
 * y hasta cuando, y nada mas — el contenido lo trae quien lo presenta.
 */
@RestController
public class VerificarController implements VerificarApi {

    /** El proceso que atiende lo publico. Fijo, para poder leerlo en la bitacora. */
    private static final UUID PROCESO_PUBLICO = UUID.fromString("00000000-0000-0000-0000-0000000000f5");

    private final CU75EmitirCertificado cu75;

    public VerificarController(CU75EmitirCertificado cu75) {
        this.cu75 = cu75;
    }

    @Override
    @Publico("CU-75: un certificado se verifica desde afuera o no sirve de certificado")
    public ResponseEntity<SalidaVerificacionCertificado> verificarCertificado(String codigo) {
        bo.aportaya.plataforma.web.traza.Traza.marcarCasoDeUso("CU-75", codigo);

        // Sin contenido a cotejar: esta ruta dice si el certificado esta vigente, no si
        // el papel que alguien muestra coincide. Cotejar el contenido exige recibirlo, y
        // eso lo hace el verificador con el hash que el propio certificado publica.
        var salida = cu75.verificarPublico(codigo, Map.of(), contexto());

        var respuesta = new SalidaVerificacionCertificado();
        respuesta.setValido(salida.valido());
        respuesta.setEstado(SalidaVerificacionCertificado.EstadoEnum.fromValue(salida.estado()));
        respuesta.setEmitidoEn(salida.emitidoEn());
        respuesta.setExpiraEn(salida.expiraEn());
        return ResponseEntity.ok(respuesta);
    }

    private ContextoSesion contexto() {
        return ContextoSesion.deSistema(PROCESO_PUBLICO, new Traza(bo.aportaya.plataforma.web.traza.Traza.actual()));
    }
}
