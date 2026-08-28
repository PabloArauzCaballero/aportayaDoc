package bo.aportaya.nucleofinanciero.aplicacion;

import bo.aportaya.nucleofinanciero.infraestructura.BloqueoRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.CierreRepositorio;
import bo.aportaya.nucleofinanciero.infraestructura.CuentaBilleteraRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
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
 * CU-16 · Cerrar la billetera y devolver el saldo.
 *
 * <p>Cerrar **no borra nada**. La cuenta queda CERRADA y sus movimientos siguen
 * disponibles durante todo el plazo de conservacion: alguien puede necesitar su
 * extracto dos años despues, y la ley obliga a poder darselo.
 *
 * <p>Cuatro puertas, y las cuatro protegen a terceros antes que a la empresa:
 * obligaciones abiertas, grupo activo, retencion vigente y bloqueo de autoridad. Un
 * cierre que deje una obligacion sin pagar traslada la perdida a los otros
 * participantes del pasanaku.
 */
@Service
public class CU16CerrarBilletera {

    private final Datos datos;
    private final CuentaBilleteraRepositorio cuentas;
    private final CierreRepositorio cierres;
    private final BloqueoRepositorio bloqueos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU16CerrarBilletera(
            Datos datos,
            CuentaBilleteraRepositorio cuentas,
            CierreRepositorio cierres,
            BloqueoRepositorio bloqueos,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.cuentas = cuentas;
        this.cierres = cierres;
        this.bloqueos = bloqueos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaSolicitud solicitar(EntradaCierre entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var cuenta = cuentas.bloquear(dsl, entrada.cuentaBilleteraId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(16, 1), "Esa billetera no existe."));

            // AP-CU16-04. Se mira PRIMERO: es el unico impedimento que la persona no
            // puede resolver por su cuenta, y decirselo al final seria hacerle
            // recorrer los otros tres para nada.
            var bloqueo = bloqueos.vigenteDe(dsl, cuenta.id());
            if (bloqueo.isPresent()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(16, 4),
                        "Hay un bloqueo de autoridad vigente: oficio "
                                + bloqueo.get().numeroOficio() + " de "
                                + bloqueo.get().autoridad() + ".");
            }

            // AP-CU16-03. Cualquier otra retencion vigente tambien frena: el saldo
            // apartado esta comprometido con algo que todavia no termino.
            if (cierres.hayRetencionVigente(dsl, cuenta.id())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(16, 3), "Hay saldo retenido: no se puede cerrar hasta que se libere.");
            }

            // AP-CU16-01 y AP-CU16-02 llegan resueltas desde afuera: las obligaciones
            // viven en `aportes` y los grupos en `grupos`, y este servicio no lee
            // esquemas ajenos (invariante 11).
            if (entrada.tieneObligacionesAbiertas()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(16, 1), "Quedan aportes pendientes: no se puede cerrar la billetera.");
            }
            if (entrada.participaEnGrupoActivo()) {
                throw new ErrorDeNegocio(CodigoError.de(16, 2), "Todavia participa en un pasanaku activo.");
            }

            // Una solicitud por cuenta, y la base lo garantiza. Se pregunta antes
            // para devolver el codigo del contrato en vez de una violacion cruda.
            var yaSolicitada = cierres.solicitudDe(dsl, cuenta.id());
            if (yaSolicitada.isPresent()) {
                return new SalidaSolicitud(yaSolicitada.get(), "SOLICITADA", cuenta.disponible());
            }

            UUID solicitudId = cierres.solicitar(
                    dsl,
                    cuenta.id(),
                    ctx.usuarioId(),
                    entrada.motivo(),
                    cuenta.disponible(),
                    entrada.destinoSaldo(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.cierre_solicitado",
                            "solicitud_cierre_billetera",
                            solicitudId,
                            Map.of(
                                    "cuentaBilleteraId", cuenta.id().toString(),
                                    "saldo", cuenta.disponible().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaSolicitud(solicitudId, "SOLICITADA", cuenta.disponible());
        });
    }

    /**
     * El saldo ya salio: la cuenta pasa a CERRADA.
     *
     * <p>Se exige saldo cero antes de cerrar. Cerrar con saldo dejaria plata de
     * alguien en una cuenta que ya no se puede operar, que es la peor forma de
     * perderla: sin aviso y sin nadie a quien reclamar.
     */
    @Transactional
    public SalidaCierre ejecutar(UUID solicitudId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            UUID cuentaId = cierres.cuentaDe(dsl, solicitudId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(16, 1), "Esa solicitud no existe."));
            var cuenta = cuentas.bloquear(dsl, cuentaId).orElseThrow();

            if (!cuenta.disponible().esCero() || !cuenta.retenido().esCero()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(16, 1),
                        "Todavia queda saldo: hay que devolverlo antes de cerrar. Disponible " + cuenta.disponible()
                                + ", retenido " + cuenta.retenido() + ".");
            }

            if (!cierres.marcarEjecutada(dsl, solicitudId, ctx.usuarioId(), ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(16, 1), "Esa solicitud ya no esta abierta.");
            }
            cierres.cerrarCuenta(dsl, cuentaId, ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "nucleo_financiero.billetera_cerrada",
                            "cuenta_billetera",
                            cuentaId,
                            Map.of("solicitudId", solicitudId.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCierre(solicitudId, cuentaId, "CERRADA");
        });
    }

    public record EntradaCierre(
            UUID cuentaBilleteraId,
            String motivo,
            String destinoSaldo,
            boolean tieneObligacionesAbiertas,
            boolean participaEnGrupoActivo) {}

    public record SalidaSolicitud(UUID solicitudId, String estado, Dinero saldoAlSolicitar) {}

    public record SalidaCierre(UUID solicitudId, UUID cuentaBilleteraId, String estado) {}
}
