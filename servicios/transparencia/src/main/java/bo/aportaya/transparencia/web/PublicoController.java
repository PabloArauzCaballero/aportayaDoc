package bo.aportaya.transparencia.web;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.web.seguridad.Publico;
import bo.aportaya.transparencia.aplicacion.CU73VerificarCadena;
import bo.aportaya.transparencia.aplicacion.ListarBloques;
import bo.aportaya.transparencia.web.generado.PublicoApi;
import bo.aportaya.transparencia.web.generado.modelo.BloquePublicado;
import bo.aportaya.transparencia.web.generado.modelo.SalidaVerificacionCadena;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las dos rutas publicas de verificacion.
 *
 * <p>**Son de las cuatro unicas rutas sin sesion de todo el sistema** (ADR-024), y
 * existen para que un tercero SIN cuenta compruebe por su cuenta que el sorteo fue
 * limpio y que la cadena no se toco. Exigirles sesion las vaciaria de sentido: la
 * verificacion que solo pueden hacer los de adentro no verifica nada.
 *
 * <p>No exponen ningun dato de nadie: devuelven en que bloque se rompio la cadena, si
 * se rompio.
 *
 * <p><b>{@code verificarSorteo} no esta implementada</b>, y esta declarado: CU-61
 * recomputa el compromiso a partir del paquete publicado —semilla, entropias y orden—,
 * y ese paquete vive en {@code grupos.sorteo_turno}. Este servicio no puede leerlo
 * (invariante 11) y todavia no se sella como hecho de la cadena, que seria la forma
 * natural de tenerlo aca. Se cierra sellando el sorteo en CU-72 o publicandolo
 * {@code grupos} en su contrato.
 *
 * <p>El {@code @Publico} de clase cubre esa ruta: es publica igual que la otra, y sin
 * decision de acceso declarada el proceso no levanta.
 */
@Publico("CU-61 y CU-73: la verificacion desde afuera es el sentido de estas dos rutas")
@RestController
public class PublicoController implements PublicoApi {

    /** El proceso que atiende lo publico. Fijo, para poder leerlo en la bitacora. */
    private static final UUID PROCESO_PUBLICO = UUID.fromString("00000000-0000-0000-0000-0000000000f0");

    private final CU73VerificarCadena cu73;
    private final ListarBloques bloques;

    public PublicoController(CU73VerificarCadena cu73, ListarBloques bloques) {
        this.cu73 = cu73;
        this.bloques = bloques;
    }

    @Override
    @Publico("CU-72: los eslabones se publican para que cualquiera rehaga el encadenado")
    public ResponseEntity<List<BloquePublicado>> listarBloques(UUID grupoId) {
        bo.aportaya.plataforma.web.traza.Traza.marcarCasoDeUso("CU-72", grupoId.toString());

        return ResponseEntity.ok(bloques.ejecutar(grupoId, contexto()).stream()
                .map(b -> {
                    var salida = new BloquePublicado();
                    salida.setNumeroBloque(b.numero());
                    salida.setHashAnterior(b.hashAnterior());
                    salida.setRaizMerkle(b.raizMerkle());
                    salida.setHashBloque(b.hash());
                    salida.setDesde(b.desde());
                    salida.setHasta(b.hasta());
                    salida.setCantidadEventos(b.cantidadEventos());
                    return salida;
                })
                .toList());
    }

    @Override
    @Publico("CU-73: la cadena de transparencia se comprueba desde afuera o no se comprueba")
    public ResponseEntity<SalidaVerificacionCadena> verificarCadena(UUID grupoId) {
        bo.aportaya.plataforma.web.traza.Traza.marcarCasoDeUso("CU-73", grupoId.toString());

        var salida = cu73.verificar(grupoId, contexto());

        var respuesta = new SalidaVerificacionCadena();
        respuesta.setIntegra(salida.integra());
        respuesta.setBloquesVerificados(salida.bloquesVerificados());
        respuesta.setPrimerBloqueFallido(salida.primerBloqueFallido());
        if (salida.componenteFallido() != null) {
            respuesta.setComponenteFallido(
                    SalidaVerificacionCadena.ComponenteFallidoEnum.fromValue(salida.componenteFallido()));
        }
        respuesta.setUltimoSellado(salida.ultimoSellado());
        return ResponseEntity.ok(respuesta);
    }

    private ContextoSesion contexto() {
        return ContextoSesion.deSistema(PROCESO_PUBLICO, new Traza(bo.aportaya.plataforma.web.traza.Traza.actual()));
    }
}
