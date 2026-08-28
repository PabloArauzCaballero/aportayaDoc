package bo.aportaya.transparencia.web;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.web.seguridad.Publico;
import bo.aportaya.transparencia.aplicacion.CU61VerificarSorteo;
import bo.aportaya.transparencia.aplicacion.CU73VerificarCadena;
import bo.aportaya.transparencia.aplicacion.ListarBloques;
import bo.aportaya.transparencia.dominio.puertos.PaquetesDeSorteo;
import bo.aportaya.transparencia.web.generado.PublicoApi;
import bo.aportaya.transparencia.web.generado.modelo.BloquePublicado;
import bo.aportaya.transparencia.web.generado.modelo.SalidaVerificacionCadena;
import bo.aportaya.transparencia.web.generado.modelo.SalidaVerificacionSorteo;
import bo.aportaya.transparencia.web.generado.modelo.SalidaVerificacionSorteoPaquete;
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
 * <p><b>Una limitacion queda declarada.</b> {@code verificarSorteo} recomputa el
 * compromiso a partir del paquete publicado, y ese paquete vive en
 * {@code grupos.sorteo_turnos}: se pide por contrato, no se lee (invariante 11). Pero
 * la ruta de {@code grupos} que lo publica exige sesion, y esta no la tiene. Un tercero
 * SIN cuenta —el destinatario de esta ruta— recibe hoy «no verificable» en vez del
 * veredicto.
 *
 * <p>No se cierra adivinando. Las dos salidas son troncales: sellar el sorteo en
 * {@code transparencia.registro_sellado} —cuyo {@code CHECK} de {@code tipo_entidad}
 * hoy admite ACUERDO, COBERTURA, ENTREGA, PAGO y SANCION, y no SORTEO— o que ADR-024
 * admita una quinta ruta sin sesion. Cambiar el modelo de datos o un ADR no es una
 * decision de implementacion.
 */
@Publico("CU-61 y CU-73: la verificacion desde afuera es el sentido de estas dos rutas")
@RestController
public class PublicoController implements PublicoApi {

    /** El proceso que atiende lo publico. Fijo, para poder leerlo en la bitacora. */
    private static final UUID PROCESO_PUBLICO = UUID.fromString("00000000-0000-0000-0000-0000000000f0");

    private final CU73VerificarCadena cu73;
    private final CU61VerificarSorteo cu61;
    private final PaquetesDeSorteo paquetes;
    private final ListarBloques bloques;

    public PublicoController(
            CU73VerificarCadena cu73, CU61VerificarSorteo cu61, PaquetesDeSorteo paquetes, ListarBloques bloques) {
        this.cu73 = cu73;
        this.cu61 = cu61;
        this.paquetes = paquetes;
        this.bloques = bloques;
    }

    /**
     * El veredicto sobre un sorteo.
     *
     * <p>El paquete se pide **antes** de abrir la transaccion: es una llamada de red, y
     * una llamada de red adentro es el invariante 6.
     *
     * <p>Si no se pudo obtener, se responde que no verifica, con los dos hashes vacios.
     * Decir que un sorteo es limpio sin haberlo recomputado seria exactamente la
     * afirmacion que este caso de uso existe para reemplazar.
     */
    @Override
    @Publico("CU-61: verificar el sorteo desde afuera es el sentido de esta ruta")
    public ResponseEntity<SalidaVerificacionSorteo> verificarSorteo(UUID sorteoId) {
        bo.aportaya.plataforma.web.traza.Traza.marcarCasoDeUso("CU-61", sorteoId.toString());

        var paquete = paquetes.de(sorteoId);
        if (paquete.isEmpty()) {
            return ResponseEntity.ok(sinPaquete());
        }

        var salida = cu61.verificar(paquete.get(), contexto());

        var respuesta = new SalidaVerificacionSorteo();
        respuesta.setVerifica(salida.verifica());
        respuesta.setHashEsperado(salida.hashEsperado());
        respuesta.setHashRecomputado(salida.hashRecomputado());
        respuesta.setOrdenCoincide(salida.ordenCoincide());
        respuesta.setPrimerCupoDiscrepante(salida.primerCupoDiscrepante());

        var publicado = new SalidaVerificacionSorteoPaquete();
        publicado.setSemilla(salida.semilla());
        publicado.setEntropias(salida.entropias());
        publicado.setMetodo(salida.metodo());
        publicado.setCupos(salida.cupos());
        respuesta.setPaquete(publicado);
        return ResponseEntity.ok(respuesta);
    }

    /** Sin paquete no hay veredicto, y se dice asi en vez de suponerlo. */
    private static SalidaVerificacionSorteo sinPaquete() {
        var respuesta = new SalidaVerificacionSorteo();
        respuesta.setVerifica(false);
        respuesta.setHashEsperado("");
        respuesta.setHashRecomputado("");
        respuesta.setOrdenCoincide(false);

        var vacio = new SalidaVerificacionSorteoPaquete();
        vacio.setSemilla("");
        vacio.setEntropias(List.of());
        vacio.setMetodo("NO_DISPONIBLE");
        vacio.setCupos(List.of());
        respuesta.setPaquete(vacio);
        return respuesta;
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
