package bo.aportaya.identidad.aplicacion;

import bo.aportaya.identidad.dominio.ExigeSegundoFactor;
import bo.aportaya.identidad.dominio.FactorAdmisible;
import bo.aportaya.identidad.dominio.PerfilDeAcceso;
import bo.aportaya.identidad.dominio.PoliticaDeIntentos;
import bo.aportaya.identidad.dominio.ResultadoDeAutenticacion;
import bo.aportaya.identidad.dominio.puertos.DesafioDeFactor;
import bo.aportaya.identidad.dominio.puertos.HasheadorDeCredencial;
import bo.aportaya.identidad.infraestructura.AccesoRepositorio;
import bo.aportaya.identidad.infraestructura.UsuarioRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-04 · Autenticar con MFA y registrar dispositivo.
 *
 * <p>Una sola transaccion, y **no lanza para rechazar**: el intento fallido tiene que
 * quedar escrito, y una excepcion lo revertiria junto con todo lo demas. Devuelve un
 * {@link ResultadoDeAutenticacion} y la pagina lo traduce despues del {@code COMMIT}.
 *
 * <p>El ingreso no lleva clave de idempotencia: cada intento ES un hecho distinto y
 * queda registrado. Reintentar no debe colapsar dos intentos en uno — al reves, es
 * justo lo que hay que poder contar.
 */
@Service
public class CU04Autenticar {

    /** Ventana en la que se cuentan los fallidos consecutivos. */
    private static final Duration VENTANA_DE_INTENTOS = Duration.ofMinutes(15);

    private final Datos datos;
    private final UsuarioRepositorio usuarios;
    private final AccesoRepositorio accesos;
    private final HasheadorDeCredencial hasheador;
    private final DesafioDeFactor desafio;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU04Autenticar(
            Datos datos,
            UsuarioRepositorio usuarios,
            AccesoRepositorio accesos,
            HasheadorDeCredencial hasheador,
            DesafioDeFactor desafio,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.usuarios = usuarios;
        this.accesos = accesos;
        this.hasheador = hasheador;
        this.desafio = desafio;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public ResultadoDeAutenticacion ejecutar(EntradaAutenticacion entrada, PoliticaDeIntentos politica) {
        OffsetDateTime ahora = reloj.ahora().atOffset(java.time.ZoneOffset.UTC);
        // El ingreso es la unica operacion que corre SIN sesion previa: el contexto
        // es el del sistema, y las politicas de fila del rol `sistema` son las que
        // permiten leer la fila del usuario que todavia no se autentico.
        ContextoSesion ctx =
                ContextoSesion.deSistema(EntradaAutenticacion.PROCESO_INGRESO, new Traza(entrada.trazaId()));

        return datos.conContexto(ctx, dsl -> autenticar(dsl, entrada, politica, ahora));
    }

    private ResultadoDeAutenticacion autenticar(
            DSLContext dsl, EntradaAutenticacion entrada, PoliticaDeIntentos politica, OffsetDateTime ahora) {
        Optional<UUID> usuarioId = usuarios.porTelefono(dsl, entrada.telefonoE164());
        UUID intentoId = accesos.registrarIntento(
                dsl, usuarioId, entrada.telefonoE164(), entrada.ip(), entrada.agente(), entrada.huella(), ahora);

        if (usuarioId.isEmpty()) {
            // El mismo mensaje que para una credencial mala: decir «ese numero no
            // existe» convierte el ingreso en un buscador de usuarios registrados.
            return cerrar(dsl, intentoId, "CREDENCIAL_INVALIDA", credencialInvalida());
        }
        UUID usuario = usuarioId.get();

        if (usuarios.tieneBloqueoVigente(dsl, usuario, ahora)) {
            return cerrar(
                    dsl,
                    intentoId,
                    "CUENTA_BLOQUEADA",
                    ResultadoDeAutenticacion.rechazado(
                            CodigoError.de(4, 2), "Tu cuenta esta bloqueada temporalmente por seguridad."));
        }

        if (!credencialCorrecta(dsl, usuario, entrada)) {
            int fallidos = accesos.fallidosConsecutivos(dsl, usuario, ahora.minus(VENTANA_DE_INTENTOS));
            if (politica.debeBloquear(fallidos)) {
                accesos.bloquear(dsl, usuario, politica.duracionDelBloqueo(), ahora);
                outbox.emitir(dsl, evento("identidad.acceso_bloqueado", usuario, entrada));
                return cerrar(
                        dsl,
                        intentoId,
                        "DEMASIADOS_INTENTOS",
                        ResultadoDeAutenticacion.rechazado(
                                CodigoError.de(4, 5), "Demasiados intentos. Volve a probar mas tarde."));
            }
            return cerrar(dsl, intentoId, "CREDENCIAL_INVALIDA", credencialInvalida());
        }

        PerfilDeAcceso perfil = usuarios.perfilDe(dsl, usuario);
        var dispositivo = accesos.dispositivoPorHuella(dsl, usuario, entrada.huella(), entrada.plataforma(), ahora);
        Optional<String> factor = accesos.factorActivo(dsl, usuario);

        if (perfil.esOperador() && factor.isEmpty()) {
            // Una asignacion de rol sin factor deja al operador afuera, y eso es lo
            // correcto: entrar sin segundo factor seria peor que no entrar.
            return cerrar(
                    dsl,
                    intentoId,
                    "FACTOR_NO_ENROLADO",
                    ResultadoDeAutenticacion.rechazado(
                            CodigoError.de(4, 6), "Tenes que enrolar tu segundo factor antes de entrar."));
        }
        if (factor.isPresent() && !FactorAdmisible.para(perfil, factor.get())) {
            return cerrar(
                    dsl,
                    intentoId,
                    "FACTOR_NO_ADMISIBLE",
                    ResultadoDeAutenticacion.rechazado(
                            CodigoError.de(4, 7), "Ese tipo de factor no se admite para tu rol."));
        }

        if (ExigeSegundoFactor.decidir(perfil, dispositivo.confiable(), entrada.operacionSensible())) {
            if (entrada.factorPresentado().isEmpty()) {
                factor.ifPresent(tipo -> desafio.emitir(usuario, tipo));
                return cerrar(
                        dsl,
                        intentoId,
                        "FACTOR_REQUERIDO",
                        ResultadoDeAutenticacion.faltaSegundoFactor(dispositivo.confiable()));
            }
            boolean valido = factor.map(tipo -> desafio.validar(
                            usuario, tipo, entrada.factorPresentado().get()))
                    .orElse(false);
            if (!valido) {
                return cerrar(
                        dsl,
                        intentoId,
                        "TOKEN_VENCIDO",
                        ResultadoDeAutenticacion.rechazado(
                                CodigoError.de(4, 4), "Ese codigo ya no sirve. Pedi uno nuevo."));
            }
        }

        var sesion =
                accesos.abrirSesion(dsl, usuario, dispositivo.id(), entrada.ip(), entrada.vigenciaDeSesion(), ahora);
        accesos.cerrarIntento(dsl, intentoId, true, null);
        outbox.emitir(dsl, evento("identidad.sesion_iniciada", usuario, entrada));

        return ResultadoDeAutenticacion.sesionAbierta(sesion.id(), sesion.expiraEn(), dispositivo.confiable());
    }

    private boolean credencialCorrecta(DSLContext dsl, UUID usuario, EntradaAutenticacion entrada) {
        return usuarios.hashDeCredencial(dsl, usuario)
                .map(hash -> hasheador.coincide(entrada.credencial(), hash))
                .orElse(false);
    }

    private ResultadoDeAutenticacion credencialInvalida() {
        return ResultadoDeAutenticacion.rechazado(CodigoError.de(4, 1), "El telefono o la credencial no coinciden.");
    }

    private ResultadoDeAutenticacion cerrar(
            DSLContext dsl, UUID intentoId, String motivo, ResultadoDeAutenticacion resultado) {
        accesos.cerrarIntento(dsl, intentoId, false, motivo);
        return resultado;
    }

    private EventoDominio evento(String tipo, UUID usuario, EntradaAutenticacion entrada) {
        // Identificadores, no datos derivados: el consumidor pregunta y obtiene la
        // verdad de ahora, no una copia que quedo vieja entre el commit y el relevo.
        return new EventoDominio(
                tipo, "usuario", usuario, Map.of("usuarioId", usuario.toString()), UUID.fromString(entrada.trazaId()));
    }
}
