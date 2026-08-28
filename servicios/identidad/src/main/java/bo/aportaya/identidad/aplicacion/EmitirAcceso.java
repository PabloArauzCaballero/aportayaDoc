package bo.aportaya.identidad.aplicacion;

import bo.aportaya.identidad.dominio.PermisosEfectivos;
import bo.aportaya.identidad.infraestructura.AccesosRepositorio;
import bo.aportaya.identidad.infraestructura.EmisorDeAcceso;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El token de acceso de una sesion recien abierta.
 *
 * <p>Los permisos se calculan **al emitir** y viajan dentro del token (ADR-024):
 * preguntarselos a identidad en cada peticion acoplaria la disponibilidad de los catorce
 * servicios a la de uno, y hacerlo contra su esquema violaria el invariante 11. El
 * precio es la ventana entre revocar un permiso y que el token venza, y por eso el token
 * dura quince minutos.
 *
 * <p>Se calculan los <b>efectivos</b> y no los del rol: una asignacion vencida o revocada
 * sigue en la tabla y deja de contar sola. Un token que se emite con permisos de una
 * asignacion vencida es un permiso que nadie recuerda haber dado.
 */
@Service
public class EmitirAcceso {

    private final Datos datos;
    private final AccesosRepositorio accesos;
    private final EmisorDeAcceso emisor;
    private final Reloj reloj;

    public EmitirAcceso(Datos datos, AccesosRepositorio accesos, EmisorDeAcceso emisor, Reloj reloj) {
        this.datos = datos;
        this.accesos = accesos;
        this.emisor = emisor;
        this.reloj = reloj;
    }

    /**
     * Las claves publicas con las que se verifica lo que este servicio firmo.
     *
     * <p>Pasa por aca y no directo desde la pagina porque la pagina no habla con
     * infraestructura: la direccion de dependencia se verifica, no se pide.
     */
    public java.util.Map<String, Object> clavesPublicas() {
        return emisor.jwks();
    }

    @Transactional(readOnly = true)
    public Acceso ejecutar(UUID usuarioId, String rol, String nivelDiligencia, String dispositivo) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        // El contexto es el del propio titular: leer sus asignaciones bajo su politica de
        // fila es lo que impide que emitir un token para otro sea posible por descuido.
        var ctx = ContextoSesion.de(usuarioId, rol, new Traza(UUID.randomUUID().toString()));

        List<String> permisos = datos.conContexto(
                ctx, dsl -> PermisosEfectivos.de(accesos.asignacionesDe(dsl, usuarioId), ahora).stream()
                        .sorted()
                        .toList());

        var emitido = emisor.emitir(usuarioId, rol, permisos, nivelDiligencia, dispositivo);
        return new Acceso(emitido.token(), emitido.expiraEn());
    }

    /**
     * El token y hasta cuando vale.
     *
     * <p>Es de esta capa y no de infraestructura porque lo consume la pagina, y la pagina
     * no depende de infraestructura. El detalle de como se firmo se queda del otro lado.
     */
    public record Acceso(String token, java.time.Instant expiraEn) {}
}
