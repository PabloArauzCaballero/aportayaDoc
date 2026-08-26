package bo.aportaya.identidad.aplicacion;

import bo.aportaya.identidad.infraestructura.AccesosRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-08 · Revocar un rol de operador.
 *
 * <p>Revocar y cerrar las sesiones van en la MISMA transaccion. Separarlas deja una
 * ventana en la que el permiso ya no existe y la sesion que lo usaba sigue viva —y esa
 * ventana es exactamente el momento en que uno revoca porque tiene una razon urgente.
 */
@Service
public class CU08RevocarRol {

    /** Sin quien administre accesos, la plataforma queda cerrada para siempre. */
    private static final String PERMISO_DE_ADMINISTRACION = "ACCESOS_ADMINISTRAR";

    private final Datos datos;
    private final AccesosRepositorio accesos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU08RevocarRol(Datos datos, AccesosRepositorio accesos, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.accesos = accesos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public int ejecutar(UUID asignacionId, String motivo, boolean cerrarSesiones, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            UUID titular = accesos.titularDe(dsl, asignacionId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(8, 1), "Esa asignacion no existe."));

            // Solo importa si ESTA asignacion es la que otorga la administracion de
            // accesos: revocar cualquier otra no puede dejar la plataforma cerrada.
            boolean otorgaLaAdministracion = accesos.rolDe(dsl, asignacionId)
                    .map(rol -> accesos.permisosDelRol(dsl, rol).contains(PERMISO_DE_ADMINISTRACION))
                    .orElse(false);
            if (otorgaLaAdministracion && accesos.cuantosConservan(dsl, PERMISO_DE_ADMINISTRACION, asignacionId) == 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(8, 6), "La plataforma no puede quedarse sin quien administre accesos.");
            }

            accesos.revocar(dsl, asignacionId, motivo, ahora);
            int sesionesCerradas = cerrarSesiones ? accesos.cerrarSesionesDe(dsl, titular, motivo, ahora) : 0;

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "identidad.rol_revocado",
                            "asignacion_rol",
                            asignacionId,
                            Map.of("usuarioId", titular.toString()),
                            UUID.fromString(ctx.traza().id())));

            return sesionesCerradas;
        });
    }
}
