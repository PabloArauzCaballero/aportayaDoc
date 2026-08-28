package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.infraestructura.DestinoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las dos lecturas propias que hacen falta para resolver un destino.
 *
 * <p>Estan en su propia clase, y no dentro de {@link ResolverDestino}, porque
 * {@code @Transactional} solo tiene efecto cuando la llamada entra por el proxy: un
 * metodo transaccional invocado desde otro metodo del mismo objeto corre sin
 * transaccion y sin {@code SET LOCAL}, que es exactamente el invariante 3 roto en
 * silencio.
 */
@Service
public class CuentasDelDestino {

    private final Datos datos;
    private final DestinoRepositorio destinos;

    public CuentasDelDestino(Datos datos, DestinoRepositorio destinos) {
        this.datos = datos;
        this.destinos = destinos;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> deUsuario(UUID usuarioId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> destinos.cuentaDeUsuario(dsl, usuarioId));
    }

    @Transactional(readOnly = true)
    public Optional<UUID> deGrupo(UUID grupoId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> destinos.cuentaDeGrupo(dsl, grupoId));
    }
}
