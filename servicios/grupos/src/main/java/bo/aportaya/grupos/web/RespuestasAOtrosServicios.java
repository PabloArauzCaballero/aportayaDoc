package bo.aportaya.grupos.web;

import bo.aportaya.grupos.aplicacion.Consultas;
import bo.aportaya.grupos.web.generado.modelo.ActividadEnGrupos;
import bo.aportaya.grupos.web.generado.modelo.AliasResuelto;
import bo.aportaya.grupos.web.generado.modelo.PaqueteDelSorteo;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Las tres respuestas que este servicio le debe a los demas.
 *
 * <p>No son casos de uso: son lo que permite que {@code nucleo-financiero} transfiera a
 * un alias, sepa si puede cerrar una billetera, y que {@code transparencia} verifique un
 * sorteo — **sin leer este esquema** (invariante 11).
 *
 * <p>Vive aparte del controlador porque el generador agrupa las trece operaciones de
 * {@code /grupos} en una sola interfaz: dos {@code @RestController} registrarian dos
 * veces cada mapeo. Lo que si se puede separar es a donde delega.
 */
@Component
class RespuestasAOtrosServicios {

    private final Consultas consultas;

    RespuestasAOtrosServicios(Consultas consultas) {
        this.consultas = consultas;
    }

    /**
     * La persona detras de un alias, y nada mas: esto no es un directorio.
     *
     * <p>Devuelve el usuario, no la cuenta. La cuenta vive en
     * {@code nucleo_financiero} y este servicio no lee ese esquema (invariante 11):
     * quien pregunta ya sabe traducir un usuario a su billetera, porque es suya.
     */
    ResponseEntity<AliasResuelto> resolverAlias(String alias, ContextoSesion ctx) {
        var usuario = consultas.usuarioDelAlias(alias, ctx);

        var respuesta = new AliasResuelto();
        respuesta.setExiste(usuario.isPresent());
        usuario.ifPresent(respuesta::setUsuarioId);
        return ResponseEntity.ok(respuesta);
    }

    /** En cuantos grupos vivos participa. Cero significa que puede cerrar su billetera. */
    ResponseEntity<ActividadEnGrupos> consultarActividad(UUID usuarioId, ContextoSesion ctx) {
        var respuesta = new ActividadEnGrupos();
        respuesta.setGruposActivos(consultas.gruposActivosDe(usuarioId, ctx));
        return ResponseEntity.ok(respuesta);
    }

    /** El paquete del sorteo, para que se pueda rehacer desde afuera. */
    ResponseEntity<PaqueteDelSorteo> consultarPaqueteDelSorteo(UUID sorteoId, ContextoSesion ctx) {
        var paquete = consultas
                .paqueteDelSorteo(sorteoId, ctx)
                .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(60, 1), "Ese sorteo no existe."));

        var respuesta = new PaqueteDelSorteo();
        respuesta.setSorteoId(sorteoId);
        respuesta.setHashComprometido(paquete.hashComprometido());
        respuesta.setSemillaRevelada(paquete.semillaRevelada());
        respuesta.setEntropias(paquete.entropias());
        respuesta.setMetodo(paquete.metodo());
        respuesta.setCuposEnOrdenOriginal(MapeoDeGrupos.comoEnteros(paquete.ordenPublicado()));
        respuesta.setOrdenPublicado(MapeoDeGrupos.comoEnteros(paquete.ordenPublicado()));
        return ResponseEntity.ok(respuesta);
    }
}
