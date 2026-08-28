package bo.aportaya.transparencia.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.transparencia.infraestructura.CadenaRepositorio;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La cadena publicada de un grupo, bloque por bloque.
 *
 * <p>Es lo que hace verificable a CU-73 desde afuera: quien quiera comprobar la cadena
 * por su cuenta necesita los eslabones —numero, hash anterior, raiz de Merkle y hash—
 * para rehacer el encadenado sin confiar en nuestra respuesta.
 *
 * <p>Lista vacia cuando el grupo todavia no sello nada, y eso **no es un error**: es un
 * grupo joven. Devolver 422 ahi obligaria a cada cliente a distinguir «no hay historia»
 * de «la historia esta rota», que es justo lo que no hay que confundir.
 */
@Service
public class ListarBloques {

    private final Datos datos;
    private final CadenaRepositorio cadenas;

    public ListarBloques(Datos datos, CadenaRepositorio cadenas) {
        this.datos = datos;
        this.cadenas = cadenas;
    }

    @Transactional(readOnly = true)
    public List<Bloque> ejecutar(UUID grupoId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> cadenas.cadenaDe(dsl, grupoId).stream()
                .map(b -> new Bloque(
                        b.numero(),
                        b.hashAnterior(),
                        b.raizMerkle(),
                        b.hash(),
                        b.desde(),
                        b.hasta(),
                        cadenas.hojasDe(dsl, b.id()).size()))
                .toList());
    }

    public record Bloque(
            long numero,
            String hashAnterior,
            String raizMerkle,
            String hash,
            OffsetDateTime desde,
            OffsetDateTime hasta,
            int cantidadEventos) {}
}
