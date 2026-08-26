package bo.aportaya.identidad.aplicacion;

import bo.aportaya.identidad.dominio.CorteDeCredencial;
import bo.aportaya.identidad.dominio.PerfilDeAcceso;
import bo.aportaya.identidad.dominio.PoliticaDeClave;
import bo.aportaya.identidad.dominio.VentanaDeEnfriamiento;
import bo.aportaya.identidad.dominio.puertos.HasheadorDeCredencial;
import bo.aportaya.identidad.infraestructura.CredencialRepositorio;
import bo.aportaya.identidad.infraestructura.UsuarioRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-09 · Cambiar la credencial.
 *
 * <p>Todo en una transaccion: la clave vieja al historial, el hash nuevo, el corte de
 * sesiones y el evento. Si el corte quedara fuera del commit habria un instante con
 * la clave nueva puesta y las sesiones viejas vivas — y ese instante es justo el que
 * un atacante necesita.
 */
@Service
public class CU09CambiarCredencial {

    private final Datos datos;
    private final UsuarioRepositorio usuarios;
    private final CredencialRepositorio credenciales;
    private final HasheadorDeCredencial hasheador;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU09CambiarCredencial(
            Datos datos,
            UsuarioRepositorio usuarios,
            CredencialRepositorio credenciales,
            HasheadorDeCredencial hasheador,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.usuarios = usuarios;
        this.credenciales = credenciales;
        this.hasheador = hasheador;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaCambio ejecutar(EntradaCambio entrada, ContextoSesion ctx, PoliticaDeClave politica) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        UUID usuario = ctx.usuarioId();

        return datos.conContexto(ctx, dsl -> {
            String hashActual = usuarios.hashDeCredencial(dsl, usuario)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(9, 1), "No encontramos tu credencial."));

            if (!entrada.esRestablecimientoAsistido() && !hasheador.coincide(entrada.claveActual(), hashActual)) {
                throw new ErrorDeNegocio(CodigoError.de(9, 2), "La clave actual no coincide.");
            }

            politica.evaluar(
                            entrada.claveNueva(),
                            credenciales.historialDe(dsl, usuario),
                            hasheador::coincide,
                            credenciales.telefonoDe(dsl, usuario).orElse(null),
                            entrada.documento().orElse(null))
                    .ifPresent(motivo -> {
                        throw new ErrorDeNegocio(CodigoError.de(9, 3), mensajeDe(motivo));
                    });

            PerfilDeAcceso perfil = usuarios.perfilDe(dsl, usuario);
            CorteDeCredencial corte = CorteDeCredencial.para(perfil);

            // El operador no completa su restablecimiento con el token: lo abre. Sin
            // la aprobacion de otra identidad, no hay cambio.
            if (perfil.esOperador()
                    && entrada.esRestablecimientoAsistido()
                    && entrada.aprobadoPor().isEmpty()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(9, 5), "Este restablecimiento necesita la aprobacion de otra persona.");
            }

            credenciales.archivar(dsl, usuario, hashActual, ahora);
            credenciales.reemplazar(dsl, usuario, hasheador.hashear(entrada.claveNueva()), ahora);

            Optional<UUID> sobreviviente = corte.alcanzaALaSesionActual() ? Optional.empty() : entrada.sesionActual();
            int cerradas = credenciales.cerrarSesiones(dsl, usuario, sobreviviente, corte.motivo(), ahora);
            if (corte.quitaLaConfianzaDeLosDispositivos()) {
                credenciales.quitarConfianza(dsl, usuario);
            }

            VentanaDeEnfriamiento enfriamiento = VentanaDeEnfriamiento.desde(ahora, entrada.duracionDelEnfriamiento());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "identidad.credencial_cambiada",
                            "usuario",
                            usuario,
                            Map.of("usuarioId", usuario.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCambio(cerradas, enfriamiento, corte);
        });
    }

    private String mensajeDe(PoliticaDeClave.MotivoDeRechazo motivo) {
        return switch (motivo) {
            case DEMASIADO_CORTA -> "Esa clave es demasiado corta.";
            case DERIVADA_DE_DATOS_PERSONALES -> "No uses tu telefono ni tu documento dentro de la clave.";
            case REUTILIZADA -> "Ya usaste esa clave antes. Elegi una nueva.";
        };
    }

    public record EntradaCambio(
            char[] claveActual,
            char[] claveNueva,
            Optional<UUID> sesionActual,
            Optional<UUID> aprobadoPor,
            Optional<String> documento,
            boolean esRestablecimientoAsistido,
            Duration duracionDelEnfriamiento) {}

    public record SalidaCambio(int sesionesCerradas, VentanaDeEnfriamiento enfriamiento, CorteDeCredencial corte) {}
}
