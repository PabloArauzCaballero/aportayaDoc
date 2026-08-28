package bo.aportaya.garantia.web;

import bo.aportaya.garantia.aplicacion.CU27RestringirDeudor;
import bo.aportaya.garantia.web.generado.CobranzaApi;
import bo.aportaya.garantia.web.generado.modelo.LevantarRestriccion200Response;
import bo.aportaya.garantia.web.generado.modelo.LevantarRestriccionRequest;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * La pagina de {@code /cobranza}: levantar una restriccion.
 *
 * <p>Levantar es un acto con motivo escrito y no un borrado: la restriccion que hubo
 * sigue estando, y por eso se puede explicar por que se levanto.
 */
@RestController
public class CobranzaController implements CobranzaApi {

    private final CU27RestringirDeudor cu27;
    private final SesionDeLaPeticion sesion;

    public CobranzaController(CU27RestringirDeudor cu27, SesionDeLaPeticion sesion) {
        this.cu27 = cu27;
        this.sesion = sesion;
    }

    @Override
    @Permiso("GRUPO_ADMINISTRAR")
    public ResponseEntity<LevantarRestriccion200Response> levantarRestriccion(
            UUID restriccionId, LevantarRestriccionRequest cuerpo) {
        Traza.marcarCasoDeUso("CU-27", restriccionId.toString());

        var respuesta = new LevantarRestriccion200Response();
        respuesta.setLevantada(cu27.levantar(restriccionId, cuerpo.getMotivo(), sesion.actual()));
        return ResponseEntity.ok(respuesta);
    }
}
