package bo.aportaya.entregas.aplicacion;

import bo.aportaya.entregas.dominio.CuentaEnmascarada;
import bo.aportaya.entregas.dominio.TitularidadDeCuenta;
import bo.aportaya.entregas.dominio.VentanaDeEnfriamiento;
import bo.aportaya.entregas.infraestructura.CuentaDestinoRepositorio;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-18 · Registrar y verificar una cuenta bancaria de destino.
 *
 * <p>Dos cosas que no se negocian. **El numero nunca se guarda en claro** (R-SEG-01):
 * la fila lleva el cifrado, un hash con pimienta para buscar, y una version
 * enmascarada. Y **la cuenta esta a nombre de quien la registra** (R-SEG-02): una
 * cuenta de destino ajena es la forma mas simple de sacar plata de un grupo hacia
 * afuera.
 *
 * <p>La ventana de enfriamiento tras verificar existe porque si alguien toma una cuenta
 * ajena, lo primero que hace es cambiar el destino y retirar. El plazo le da al titular
 * real el tiempo de enterarse y frenarlo.
 */
@Service
public class CU18RegistrarCuentaDestino {

    private final Datos datos;
    private final CuentaDestinoRepositorio cuentas;
    private final Outbox outbox;
    private final Reloj reloj;
    private final String pimienta;
    private final int versionLlave;
    private final int maximoDeCuentas;
    private final Duration ventanaDeEnfriamiento;

    public CU18RegistrarCuentaDestino(
            Datos datos,
            CuentaDestinoRepositorio cuentas,
            Outbox outbox,
            Reloj reloj,
            String pimienta,
            int versionLlave,
            int maximoDeCuentas,
            Duration ventanaDeEnfriamiento) {
        this.datos = datos;
        this.cuentas = cuentas;
        this.outbox = outbox;
        this.reloj = reloj;
        this.pimienta = pimienta;
        this.versionLlave = versionLlave;
        this.maximoDeCuentas = maximoDeCuentas;
        this.ventanaDeEnfriamiento = ventanaDeEnfriamiento;
    }

    @Transactional
    public SalidaRegistro registrar(EntradaRegistro entrada, ContextoSesion ctx) {
        // El enmascarado y el hash se calculan ANTES de tocar la base: el numero en
        // claro no entra a ninguna consulta, ni siquiera a una que vaya a fallar.
        var enmascarada = CuentaEnmascarada.de(entrada.numeroEnClaro(), pimienta, versionLlave);

        // AP-CU18-01 · R-SEG-02: la cuenta es de quien la registra.
        if (!TitularidadDeCuenta.coincide(
                entrada.titularNombre(),
                entrada.titularDocumento(),
                entrada.nombreDelUsuario(),
                entrada.documentoDelUsuario())) {
            throw new ErrorDeNegocio(
                    CodigoError.de(18, 1), "El titular de la cuenta no coincide con quien la registra.");
        }

        return datos.conContexto(ctx, dsl -> {
            // AP-CU18-02: la misma cuenta dos veces no es un error del usuario; se le
            // devuelve la que ya tiene.
            var repetida = cuentas.porHash(dsl, ctx.usuarioId(), enmascarada.hash());
            if (repetida.isPresent()) {
                var previa = repetida.get();
                return new SalidaRegistro(previa.id(), previa.enmascarado(), previa.estadoVerificacion(), false);
            }
            // AP-CU18-06: un tope de cuentas por usuario. Diez destinos distintos para
            // la misma persona es un patron, no una comodidad.
            if (cuentas.cuantasTiene(dsl, ctx.usuarioId()) >= maximoDeCuentas) {
                throw new ErrorDeNegocio(
                        CodigoError.de(18, 6),
                        "Ya tiene " + maximoDeCuentas + " cuentas registradas: no se admiten mas.");
            }

            boolean primera = cuentas.cuantasTiene(dsl, ctx.usuarioId()) == 0;
            UUID cuentaId = cuentas.registrar(
                    dsl,
                    ctx.usuarioId(),
                    entrada.tipoCuenta(),
                    entrada.entidadFinanciera(),
                    entrada.numeroCifrado(),
                    enmascarada,
                    entrada.titularNombre(),
                    entrada.titularDocumento(),
                    entrada.moneda(),
                    primera);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "entregas.cuenta_destino_registrada",
                            "cuenta_bancaria_beneficiario",
                            cuentaId,
                            // El evento lleva el ENMASCARADO, nunca el numero: un evento
                            // se replica, se archiva y se lee en veinte lugares.
                            Map.of(
                                    "usuarioId", ctx.usuarioId().toString(),
                                    "numeroEnmascarado", enmascarada.enmascarado(),
                                    "entidad", entrada.entidadFinanciera()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaRegistro(cuentaId, enmascarada.enmascarado(), "PENDIENTE", true);
        });
    }

    /**
     * Verifica la cuenta y **guarda** cuando queda disponible.
     *
     * <p>Verificada no es utilizable: empieza la ventana de enfriamiento, y su fin se
     * persiste (invariante 8).
     */
    @Transactional
    public SalidaVerificacion verificar(UUID cuentaId, String metodo, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var cuenta = cuentas.ver(dsl, cuentaId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(18, 4), "Esa cuenta no existe."));
            if (!cuenta.usuarioId().equals(ctx.usuarioId())) {
                throw new ErrorDeNegocio(CodigoError.de(18, 1), "Esa cuenta no es de quien la esta verificando.");
            }

            var ventana = new VentanaDeEnfriamiento(ahora, ventanaDeEnfriamiento);
            if (!cuentas.verificar(dsl, cuentaId, metodo, ahora, ventana.disponibleDesde())) {
                return new SalidaVerificacion(cuentaId, cuenta.estadoVerificacion(), cuenta.bloqueadaHasta(), false);
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "entregas.cuenta_destino_verificada",
                            "cuenta_bancaria_beneficiario",
                            cuentaId,
                            Map.of(
                                    "usuarioId", cuenta.usuarioId().toString(),
                                    "numeroEnmascarado", cuenta.enmascarado(),
                                    "disponibleDesde", ventana.disponibleDesde().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaVerificacion(cuentaId, "VERIFICADA", ventana.disponibleDesde(), true);
        });
    }

    /** Si la cuenta puede recibir plata ahora, y si no, cuanto falta. */
    @Transactional(readOnly = true)
    public Disponibilidad disponibilidad(UUID cuentaId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var cuenta = cuentas.ver(dsl, cuentaId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(18, 4), "Esa cuenta no existe."));
            if (!cuenta.estaVerificada()) {
                return new Disponibilidad(false, Duration.ZERO, "La cuenta todavia no esta verificada");
            }
            if (cuenta.bloqueadaHasta() != null && ahora.isBefore(cuenta.bloqueadaHasta())) {
                Duration falta = Duration.between(ahora, cuenta.bloqueadaHasta());
                // Se le dice CUANTO falta, no solo que no puede: negar sin explicar
                // convierte una medida de seguridad en una falla del sistema.
                return new Disponibilidad(
                        false, falta, "Faltan " + falta.toHours() + " horas para que la cuenta quede disponible");
            }
            return new Disponibilidad(true, Duration.ZERO, "Disponible");
        });
    }

    /** Una sola principal por usuario (R-BIL-17). */
    @Transactional
    public boolean designarPrincipal(UUID cuentaId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var cuenta = cuentas.ver(dsl, cuentaId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(18, 4), "Esa cuenta no existe."));
            if (!cuenta.usuarioId().equals(ctx.usuarioId())) {
                throw new ErrorDeNegocio(CodigoError.de(18, 1), "Esa cuenta no es suya.");
            }
            // Se baja la anterior ANTES: si no, el indice unico parcial rechaza la
            // segunda y el usuario ve un error donde deberia ver un cambio.
            cuentas.quitarPrincipal(dsl, ctx.usuarioId());
            return cuentas.designarPrincipal(dsl, cuentaId);
        });
    }

    public record EntradaRegistro(
            String tipoCuenta,
            String entidadFinanciera,
            String numeroEnClaro,
            String numeroCifrado,
            String titularNombre,
            String titularDocumento,
            String nombreDelUsuario,
            String documentoDelUsuario,
            String moneda) {}

    public record SalidaRegistro(UUID cuentaId, String numeroEnmascarado, String estado, boolean esNueva) {}

    public record SalidaVerificacion(UUID cuentaId, String estado, OffsetDateTime disponibleDesde, boolean esNueva) {}

    public record Disponibilidad(boolean disponible, Duration restante, String motivo) {}
}
