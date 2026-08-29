package bo.aportaya.nucleofinanciero.web;

import bo.aportaya.nucleofinanciero.aplicacion.CU12TransferirSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.CU16CerrarBilletera;
import bo.aportaya.nucleofinanciero.aplicacion.ResolverDestino;
import bo.aportaya.nucleofinanciero.dominio.puertos.HechosDeOtrosServicios;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaCierre;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaTransferencia;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaCierreBilletera;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaTransferencia;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Las dos operaciones que dependen de hechos de otros servicios.
 *
 * <p>Transferir a un alias y cerrar una billetera tienen la misma forma: **antes** de
 * abrir la transaccion hay que preguntarle algo a {@code grupos} o a {@code aportes},
 * porque una llamada de red adentro deja el dinero bloqueado esperando a un tercero
 * (invariante 6).
 *
 * <p>Viven aparte del controlador porque el generador agrupa las once operaciones de
 * {@code /billetera} en una sola interfaz: dos {@code @RestController} registrarian dos
 * veces cada mapeo. Lo que si se puede separar es a donde delega.
 */
@Component
class MovimientosDeLaBilletera {

    private final CU12TransferirSaldo cu12;
    private final CU16CerrarBilletera cu16;
    private final ResolverDestino destinos;
    private final HechosDeOtrosServicios afuera;

    MovimientosDeLaBilletera(
            CU12TransferirSaldo cu12,
            CU16CerrarBilletera cu16,
            ResolverDestino destinos,
            HechosDeOtrosServicios afuera) {
        this.cu12 = cu12;
        this.cu16 = cu16;
        this.destinos = destinos;
        this.afuera = afuera;
    }

    /**
     * La transferencia.
     *
     * <p>El destino llega como lo escribio una persona —un alias, un grupo— y el caso
     * de uso trabaja con la cuenta. Traducirlo es lo primero **y pasa afuera**: el
     * alias lo resuelve {@code grupos}.
     *
     * <p>Un destino que no se pudo resolver se rechaza. Mandar la plata a una cuenta
     * adivinada no tiene vuelta atras.
     */
    ResponseEntity<SalidaTransferencia> transferir(
            UUID claveIdempotencia, EntradaTransferencia cuerpo, ContextoSesion ctx) {
        var destino = cuerpo.getDestino();
        UUID cuentaDestino = destinos.cuenta(destino.getTipo().getValue(), destino.getValor(), ctx)
                .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(12, 2), "Ese destino no existe."));

        var salida = cu12.ejecutar(
                new CU12TransferirSaldo.EntradaTransferencia(
                        claveIdempotencia.toString(),
                        cuerpo.getCuentaOrigenId(),
                        cuentaDestino,
                        MapeoDeBilletera.dinero(cuerpo.getMonto()),
                        cuerpo.getConcepto(),
                        Optional.empty(),
                        Optional.ofNullable(cuerpo.getObligacionId())),
                ctx);

        var respuesta = new SalidaTransferencia();
        respuesta.setTransaccionId(salida.transaccionId());
        respuesta.setSaldoDespues(MapeoDeBilletera.dinero(salida.saldoDespues()));
        respuesta.setDestinatarioId(salida.destinatarioId());
        respuesta.setObligacionSaldada(salida.obligacionSaldada());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * El cierre de la billetera.
     *
     * <p>Dos de las cuatro puertas no son de este servicio: los aportes pendientes los
     * lleva {@code aportes} y los pasanakus vivos los lleva {@code grupos}.
     *
     * <p>Si alguno de los dos no contesta, la respuesta que se asume es **que si hay
     * pendientes**. Cerrar por falta de respuesta le pasaria la deuda a los otros del
     * grupo, y la puerta existe para protegerlos a ellos (invariante 9).
     */
    ResponseEntity<SalidaCierreBilletera> cerrar(EntradaCierre cuerpo, ContextoSesion ctx) {
        UUID titular = ctx.usuarioId();

        var salida = cu16.solicitar(
                new CU16CerrarBilletera.EntradaCierre(
                        cuerpo.getCuentaBilleteraId(),
                        cuerpo.getMotivo(),
                        cuerpo.getDestinoSaldo().getValue(),
                        afuera.tieneObligacionesAbiertas(titular),
                        afuera.participaEnGrupoActivo(titular)),
                ctx);

        var respuesta = new SalidaCierreBilletera();
        respuesta.setSolicitudId(salida.solicitudId());
        respuesta.setEstado(SalidaCierreBilletera.EstadoEnum.fromValue(salida.estado()));
        respuesta.setSaldoAlSolicitar(MapeoDeBilletera.dinero(salida.saldoAlSolicitar()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
