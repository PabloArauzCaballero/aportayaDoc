package bo.aportaya.identidad.aplicacion;

import bo.aportaya.identidad.infraestructura.UsuarioRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Si un telefono ya tiene cuenta.
 *
 * <p>La pregunta {@code grupos} antes de invitar: invitar a quien ya es participante es
 * ruido, y para saberlo hay que poder ir del telefono al usuario.
 *
 * <p>Devuelve el identificador y nada mas. Sin permiso, una ruta que contesta si un
 * telefono tiene cuenta es una forma de enumerar clientes.
 */
@Service
public class BuscarPorTelefono {

    private final Datos datos;
    private final UsuarioRepositorio usuarios;

    public BuscarPorTelefono(Datos datos, UsuarioRepositorio usuarios) {
        this.datos = datos;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> ejecutar(String telefonoE164, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> usuarios.porTelefono(dsl, telefonoE164));
    }
}
