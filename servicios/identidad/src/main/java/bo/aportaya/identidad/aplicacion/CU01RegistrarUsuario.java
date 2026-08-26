package bo.aportaya.identidad.aplicacion;

import bo.aportaya.identidad.dominio.AperturaDeCuenta;
import bo.aportaya.identidad.dominio.DocumentoDeIdentidad;
import bo.aportaya.identidad.infraestructura.RegistroRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Ids;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.dominio.Traza;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-01 · Registro y apertura de billetera — <b>la parte que es de {@code identidad}</b>.
 *
 * <h2>Por que no es una saga</h2>
 *
 * El caso de uso toca cuatro esquemas: {@code identidad} (usuario, documento,
 * verificacion), {@code cumplimiento} (debida diligencia, calificacion de riesgo,
 * expediente), {@code nucleo_financiero} (cuenta de billetera) y {@code auditoria}
 * (listas restrictivas). Ninguno es dueño de todo, y {@code svc_identidad} no puede
 * escribir en los otros tres — invariante 11.
 *
 * <p>La tentacion es llamarlo saga. <b>No lo es, y el propio proyecto lo dice:</b>
 * {@code estado_saga} existe solo en {@code aportes}, {@code entregas},
 * {@code garantia} y {@code tarifas}, y el recetario §8b acota la saga a «cuando hay
 * dinero en vuelo». En una apertura de cuenta no hay dinero en vuelo: hay una cuenta
 * que se abre en cero.
 *
 * <p>Lo que si hay es <b>coreografia por eventos</b>, que es para lo que existe el
 * outbox: {@code identidad} crea al usuario en una transaccion local y emite
 * {@code identidad.usuario_registrado}; {@code cumplimiento} y
 * {@code nucleo-financiero} lo consumen y hacen lo suyo. Cada uno escribe en su
 * esquema, cada uno con su transaccion, y ninguno necesita permiso sobre el ajeno.
 *
 * <p>El usuario nace {@code PENDIENTE_VERIFICACION} y <b>no opera</b> hasta que esos
 * dos respondan. Esa espera no es una limitacion del diseño: es lo que el caso de uso
 * pide cuando dice que queda habilitado «solo dentro de los limites que le
 * corresponden por ese nivel de conocimiento». Marcarlo activo antes seria prometer
 * una habilitacion que todavia nadie evaluo.
 */
@Service
public class CU01RegistrarUsuario {

    /**
     * Las finalidades se consienten POR SEPARADO: aceptar los terminos no arrastra el
     * tratamiento de datos, y ninguno de los dos arrastra {@code MARKETING} — que a
     * proposito no esta en esta lista. Un consentimiento agrupado no es
     * consentimiento: es una casilla que nadie leyo.
     */
    private static final List<String> FINALIDADES = List.of("TERMINOS", "PRIVACIDAD", "TRATAMIENTO_DATOS");

    private final Datos datos;
    private final RegistroRepositorio registros;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Ids ids;

    public CU01RegistrarUsuario(Datos datos, RegistroRepositorio registros, Outbox outbox, Reloj reloj, Ids ids) {
        this.datos = datos;
        this.registros = registros;
        this.outbox = outbox;
        this.reloj = reloj;
        this.ids = ids;
    }

    @Transactional
    public SalidaRegistro ejecutar(EntradaRegistro entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            if (registros.telefonoYaRegistrado(dsl, entrada.telefonoE164())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(1, 3), "Ya hay una cuenta con ese telefono. Podes recuperar el acceso.");
            }
            if (registros.documentoYaRegistrado(dsl, entrada.documento().hashNumero())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(1, 3), "Ya hay una cuenta con ese documento. Podes recuperar el acceso.");
            }
            if (entrada.aceptaContratos().isEmpty()) {
                throw new ErrorDeNegocio(CodigoError.de(1, 4), "Hace falta aceptar el contrato para abrir la cuenta.");
            }
            if (!entrada.licenciaHabilitaBilletera()) {
                // Denegar por omision: sin licencia vigente que habilite el servicio,
                // no se abre nada. R-LIC-01 lo hace cumplir del otro lado tambien.
                throw new ErrorDeNegocio(CodigoError.de(1, 5), "El servicio no esta habilitado en este momento.");
            }

            UUID usuario = registros.crearUsuario(
                    dsl,
                    codigoPublico(),
                    entrada.nombres(),
                    entrada.apellidos(),
                    entrada.telefonoE164(),
                    entrada.fechaNacimiento(),
                    AperturaDeCuenta.PENDIENTE_VERIFICACION.name(),
                    ahora);

            UUID documento = registros.guardarDocumento(
                    dsl, usuario, entrada.documento(), entrada.numeroCifrado(), entrada.hashDelArchivo());
            registros.iniciarVerificacion(dsl, usuario, documento, "BASICO", ahora);
            registros.registrarConsentimientos(dsl, usuario, FINALIDADES, entrada.ip(), entrada.agente(), ahora);

            // Lo que sigue —diligencia, calificacion, expediente y billetera— lo hacen
            // `cumplimiento` y `nucleo-financiero` al consumir este evento. Cada uno en
            // su esquema y con su transaccion.
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "identidad.usuario_registrado",
                            "usuario",
                            usuario,
                            Map.of("usuarioId", usuario.toString(), "nivelSolicitado", "BASICO"),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaRegistro(usuario, AperturaDeCuenta.PENDIENTE_VERIFICACION);
        });
    }

    private String codigoPublico() {
        return "AY-" + ids.nuevo().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    public record EntradaRegistro(
            String telefonoE164,
            String nombres,
            String apellidos,
            LocalDate fechaNacimiento,
            DocumentoDeIdentidad documento,
            String numeroCifrado,
            String hashDelArchivo,
            List<UUID> aceptaContratos,
            boolean licenciaHabilitaBilletera,
            String ip,
            String agente) {}

    public record SalidaRegistro(UUID usuarioId, AperturaDeCuenta estado) {}

    /** Para que la traza del alta se pueda seguir desde el primer registro. */
    public static Traza trazaNueva(Ids ids) {
        return Traza.nueva(ids);
    }
}
