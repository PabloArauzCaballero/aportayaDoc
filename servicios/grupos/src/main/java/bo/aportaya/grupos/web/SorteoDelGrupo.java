package bo.aportaya.grupos.web;

import bo.aportaya.grupos.aplicacion.CU60Sortear;
import bo.aportaya.grupos.aplicacion.Consultas;
import bo.aportaya.grupos.web.generado.modelo.CompromisoDeSorteo;
import bo.aportaya.grupos.web.generado.modelo.EntradaCompromiso;
import bo.aportaya.grupos.web.generado.modelo.EntradaRevelacion;
import bo.aportaya.grupos.web.generado.modelo.RevelacionDeSorteo;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Las dos fases del sorteo: comprometer y revelar.
 *
 * <p>El compromiso publica **solo el hash** de la semilla. Sin esa separacion, quien
 * ejecuta el sorteo podria probar semillas hasta que salga el orden que le conviene: el
 * compromiso es lo que convierte «confia en nosotros» en «verificalo vos».
 *
 * <p>La semilla vuelve a quien lo dispara porque tiene que devolverla al revelar.
 * Guardarla del lado del servidor entre las dos fases haria posible cambiarla, que es
 * justo lo que este protocolo existe para impedir.
 */
@Component
class SorteoDelGrupo {

    private final CU60Sortear cu60;
    private final Consultas consultas;

    SorteoDelGrupo(CU60Sortear cu60, Consultas consultas) {
        this.cu60 = cu60;
        this.consultas = consultas;
    }

    ResponseEntity<CompromisoDeSorteo> comprometer(UUID grupoId, EntradaCompromiso cuerpo, ContextoSesion ctx) {
        var compromiso = cu60.comprometer(grupoId, entropias(cuerpo), Optional.empty(), ctx);

        var respuesta = new CompromisoDeSorteo();
        respuesta.setSorteoId(compromiso.sorteoId());
        respuesta.setHashSemilla(compromiso.hashSemilla());
        respuesta.setAlgoritmo(CompromisoDeSorteo.AlgoritmoEnum.FISHER_YATES_SHA256);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    ResponseEntity<RevelacionDeSorteo> revelar(UUID grupoId, EntradaRevelacion cuerpo, ContextoSesion ctx) {
        var revelacion = cu60.revelar(
                cuerpo.getSorteoId(),
                cuerpo.getSemilla(),
                cuerpo.getEntropias() == null ? List.of() : cuerpo.getEntropias(),
                consultas.periodosDe(grupoId, ctx),
                consultas.montoEstimado(grupoId, ctx),
                Optional.empty(),
                ctx);

        var respuesta = new RevelacionDeSorteo();
        respuesta.setSorteoId(revelacion.sorteoId());
        respuesta.setVerificado(revelacion.verificado());
        respuesta.setSemilla(cuerpo.getSemilla());
        respuesta.setCuposEnOrden(revelacion.cuposEnOrden());
        return ResponseEntity.ok(respuesta);
    }

    private static List<String> entropias(EntradaCompromiso cuerpo) {
        return cuerpo == null || cuerpo.getEntropias() == null ? List.of() : cuerpo.getEntropias();
    }
}
