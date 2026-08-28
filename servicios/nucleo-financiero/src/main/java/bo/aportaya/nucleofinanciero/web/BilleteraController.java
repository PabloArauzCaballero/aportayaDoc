package bo.aportaya.nucleofinanciero.web;

import bo.aportaya.nucleofinanciero.aplicacion.CU10RecargarSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.CU11RetirarSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.CU12TransferirSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.CU14ReversarTransaccion;
import bo.aportaya.nucleofinanciero.aplicacion.CU15EmitirExtracto;
import bo.aportaya.nucleofinanciero.aplicacion.CU16CerrarBilletera;
import bo.aportaya.nucleofinanciero.aplicacion.CU17BloquearPorAutoridad;
import bo.aportaya.nucleofinanciero.aplicacion.ConsultarSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.ResolverDestino;
import bo.aportaya.nucleofinanciero.dominio.puertos.CotizadorDeComision;
import bo.aportaya.nucleofinanciero.dominio.puertos.HechosDeOtrosServicios;
import bo.aportaya.nucleofinanciero.dominio.puertos.SegundoFactor;
import bo.aportaya.nucleofinanciero.web.generado.BilleteraApi;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaBloqueo;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaCierre;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaCierreRetencion;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaRecarga;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaRetencion;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaRetiro;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaReverso;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaTransferencia;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SaldoBilletera;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaAcreditacion;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaBloqueo;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaCierreBilletera;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaCierreRetencion;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaExtracto;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaRecarga;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaRetencion;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaRetiro;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaReverso;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaTransferencia;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /billetera}.
 *
 * <p><b>Todo lo que este servicio no sabe se resuelve antes de abrir la transaccion.</b>
 * El costo de un retiro lo fija {@code tarifas}; a quien apunta un alias lo sabe
 * {@code grupos}; si quedan aportes pendientes lo sabe {@code aportes}. Los tres son
 * llamadas de red, y una llamada de red dentro de la transaccion que mueve plata deja
 * el dinero bloqueado esperando a un tercero (invariante 6).
 *
 * <p>Cuando alguna de esas preguntas no obtiene respuesta, la operacion se rechaza. No
 * es prudencia de mas: cobrar cero porque {@code tarifas} no contesto es regalar plata,
 * y cerrar una billetera porque {@code aportes} no contesto traslada el aporte impago a
 * los otros del pasanaku.
 */
@RestController
@Permiso("BILLETERA_OPERAR")
public class BilleteraController implements BilleteraApi {

    private final CU10RecargarSaldo cu10;
    private final CU13RetenerSaldo cu13;
    private final CU14ReversarTransaccion cu14;
    private final CU15EmitirExtracto cu15;
    private final CU17BloquearPorAutoridad cu17;
    private final CU11RetirarSaldo cu11;
    private final CU12TransferirSaldo cu12;
    private final CU16CerrarBilletera cu16;
    private final ConsultarSaldo saldos;
    private final ResolverDestino destinos;
    private final HechosDeOtrosServicios afuera;
    private final CotizadorDeComision cotizador;
    private final SegundoFactor segundoFactor;
    private final BigDecimal desdeCuandoSonDosFirmas;
    private final SesionDeLaPeticion sesion;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public BilleteraController(
            CU10RecargarSaldo cu10,
            CU13RetenerSaldo cu13,
            CU14ReversarTransaccion cu14,
            CU15EmitirExtracto cu15,
            CU17BloquearPorAutoridad cu17,
            CU11RetirarSaldo cu11,
            CU12TransferirSaldo cu12,
            CU16CerrarBilletera cu16,
            ConsultarSaldo saldos,
            ResolverDestino destinos,
            HechosDeOtrosServicios afuera,
            CotizadorDeComision cotizador,
            SegundoFactor segundoFactor,
            @Value("${aportaya.retiro.doble-aprobacion-desde}") BigDecimal desdeCuandoSonDosFirmas,
            SesionDeLaPeticion sesion) {
        this.cu10 = cu10;
        this.cu13 = cu13;
        this.cu14 = cu14;
        this.cu15 = cu15;
        this.cu17 = cu17;
        this.cu11 = cu11;
        this.cu12 = cu12;
        this.cu16 = cu16;
        this.saldos = saldos;
        this.destinos = destinos;
        this.afuera = afuera;
        this.cotizador = cotizador;
        this.segundoFactor = segundoFactor;
        this.desdeCuandoSonDosFirmas = desdeCuandoSonDosFirmas;
        this.sesion = sesion;
    }

    @Override
    @Permiso("BILLETERA_VER")
    public ResponseEntity<SaldoBilletera> consultarSaldo(UUID cuentaId) {
        Traza.marcarCasoDeUso("CU-13", cuentaId.toString());

        var salida = saldos.ejecutar(cuentaId, sesion.actual());

        var respuesta = new SaldoBilletera();
        respuesta.setCuentaId(salida.cuentaId());
        respuesta.setDisponible(MapeoDeBilletera.dinero(salida.disponible()));
        respuesta.setRetenido(MapeoDeBilletera.dinero(salida.retenido()));
        respuesta.setAlCorteDe(salida.alCorteDe());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaRecarga> solicitarRecarga(UUID idempotencyKey, EntradaRecarga cuerpo) {
        Traza.marcarCasoDeUso("CU-10", cuerpo.getCuentaBilleteraId().toString());

        var salida = cu10.solicitar(
                new CU10RecargarSaldo.EntradaSolicitud(
                        idempotencyKey.toString(),
                        cuerpo.getCuentaBilleteraId(),
                        MapeoDeBilletera.dinero(cuerpo.getMonto()),
                        MapeoDeBilletera.ceroSiFalta(cuerpo.getCostoProveedor(), cuerpo.getMonto()),
                        cuerpo.getMedio(),
                        Optional.ofNullable(cuerpo.getInstrumentoFondeoId())),
                sesion.actual());

        var respuesta = new SalidaRecarga();
        respuesta.setOrdenRecargaId(salida.ordenRecargaId());
        respuesta.setEstado(salida.estado());
        respuesta.setExpiraEn(salida.expiraEn());
        respuesta.setAcreditara(MapeoDeBilletera.dinero(salida.acreditara()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaAcreditacion> acreditarRecarga(UUID ordenId, UUID idempotencyKey) {
        Traza.marcarCasoDeUso("CU-10", ordenId.toString());

        var salida = cu10.acreditar(ordenId, sesion.actual());

        var respuesta = new SalidaAcreditacion();
        respuesta.setOrdenRecargaId(salida.ordenRecargaId());
        respuesta.setTransaccionId(salida.transaccionId());
        respuesta.setSaldoDespues(MapeoDeBilletera.dinero(salida.saldoDespues()));
        return ResponseEntity.ok(respuesta);
    }

    /**
     * El retiro, con las dos cosas que este servicio no sabe resueltas ANTES de abrir la
     * transaccion.
     *
     * <p>El costo lo fija {@code tarifas} y el segundo factor lo comprueba quien guarda
     * las credenciales: son dos llamadas de red, y una llamada de red dentro de la
     * transaccion es el invariante 6. Por eso salen aca y entran al caso de uso ya
     * resueltas, igual que hace CU-32 con el servicio fiscal.
     *
     * <p>Si el costo no se pudo cotizar, **se rechaza**. Cobrar cero porque tarifas no
     * respondio es regalar plata en silencio, y denegar por omision es el invariante 9.
     */
    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaRetiro> solicitarRetiro(UUID idempotencyKey, EntradaRetiro cuerpo) {
        Traza.marcarCasoDeUso("CU-11", cuerpo.getCuentaBilleteraId().toString());

        var monto = MapeoDeBilletera.dinero(cuerpo.getMonto());
        var costo = cotizador
                .costoDe("RETIRO_ACREDITADO", cuerpo.getCuentaBilleteraId(), monto, idempotencyKey.toString())
                .orElseThrow(() -> new ErrorDeNegocio(
                        CodigoError.de(11, 1), "No se pudo cotizar el costo del retiro: intentalo de nuevo."));

        var salida = cu11.solicitar(
                new CU11RetirarSaldo.EntradaRetiro(
                        idempotencyKey.toString(),
                        cuerpo.getCuentaBilleteraId(),
                        monto,
                        costo,
                        cuerpo.getInstrumentoDestinoId(),
                        segundoFactor.verificado(sesion.actual().usuarioId(), cuerpo.getFactorMfa()),
                        monto.monto().compareTo(desdeCuandoSonDosFirmas) >= 0),
                sesion.actual());

        var respuesta = new SalidaRetiro();
        respuesta.setOrdenRetiroId(salida.ordenRetiroId());
        respuesta.setEstado(SalidaRetiro.EstadoEnum.fromValue(salida.estado()));
        respuesta.setCostoRetiro(MapeoDeBilletera.dinero(salida.costoRetiro()));
        respuesta.setMontoNeto(MapeoDeBilletera.dinero(salida.montoNeto()));
        respuesta.setRetencionId(salida.retencionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaRetencion> retenerSaldo(UUID idempotencyKey, EntradaRetencion cuerpo) {
        Traza.marcarCasoDeUso("CU-13", cuerpo.getCuentaBilleteraId().toString());

        var salida = cu13.retener(
                new CU13RetenerSaldo.EntradaRetencion(
                        cuerpo.getCuentaBilleteraId(),
                        MapeoDeBilletera.dinero(cuerpo.getMonto()),
                        cuerpo.getMotivo(),
                        Optional.ofNullable(cuerpo.getTransaccionOrigenId()),
                        Optional.ofNullable(cuerpo.getReferenciaTipo()),
                        Optional.ofNullable(cuerpo.getReferenciaId()),
                        Optional.ofNullable(cuerpo.getExpiraEn())),
                sesion.actual());

        var respuesta = new SalidaRetencion();
        respuesta.setRetencionId(salida.retencionId());
        respuesta.setSaldoDisponible(MapeoDeBilletera.dinero(salida.saldoDisponible()));
        respuesta.setSaldoRetenido(MapeoDeBilletera.dinero(salida.saldoRetenido()));
        respuesta.setExpiraEn(salida.expiraEn());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaCierreRetencion> cerrarRetencion(
            UUID retencionId, UUID idempotencyKey, EntradaCierreRetencion cuerpo) {
        Traza.marcarCasoDeUso("CU-13", retencionId.toString());

        // Liberar devuelve el saldo al titular; ejecutar lo entrega. Son dos actos
        // distintos y el desenlace es lo unico que los distingue.
        var salida = cuerpo.getDesenlace() == EntradaCierreRetencion.DesenlaceEnum.LIBERADA
                ? cu13.liberar(retencionId, sesion.actual())
                : cu13.ejecutar(retencionId, sesion.actual());

        var respuesta = new SalidaCierreRetencion();
        respuesta.setRetencionId(salida.retencionId());
        respuesta.setEstado(salida.estado());
        respuesta.setSaldoDisponible(MapeoDeBilletera.dinero(salida.saldoDisponible()));
        respuesta.setSaldoRetenido(MapeoDeBilletera.dinero(salida.saldoRetenido()));
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("REVERSO_AUTORIZAR")
    public ResponseEntity<SalidaReverso> reversarTransaccion(UUID idempotencyKey, EntradaReverso cuerpo) {
        Traza.marcarCasoDeUso("CU-14", cuerpo.getTransaccionOriginalId().toString());

        var salida = cu14.ejecutar(
                new CU14ReversarTransaccion.EntradaReverso(
                        idempotencyKey.toString(),
                        cuerpo.getTransaccionOriginalId(),
                        cuerpo.getTipo().getValue(),
                        cuerpo.getMotivo(),
                        cuerpo.getAutorizadaPor()),
                sesion.actual());

        var respuesta = new SalidaReverso();
        respuesta.setReversoId(salida.reversoId());
        respuesta.setTransaccionReversoId(salida.transaccionReversoId());
        respuesta.setGeneraObligacionDeRestitucion(salida.generaObligacionDeRestitucion());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @Override
    @Permiso("BILLETERA_VER")
    public ResponseEntity<SalidaExtracto> emitirExtracto(UUID cuentaId, LocalDate desde, LocalDate hasta) {
        Traza.marcarCasoDeUso("CU-15", cuentaId.toString());

        // El permiso delegado sale del token, no del cuerpo: quien pide el extracto de
        // otro tiene que traerlo firmado, no afirmarlo.
        boolean delegado = sesion.permisos().contains("BILLETERA_VER_TERCEROS");

        var salida =
                cu15.emitir(new CU15EmitirExtracto.EntradaExtracto(cuentaId, desde, hasta, delegado), sesion.actual());

        var respuesta = new SalidaExtracto();
        respuesta.setCuentaBilleteraId(salida.cuentaBilleteraId());
        respuesta.setDesde(salida.desde());
        respuesta.setHasta(salida.hasta());
        respuesta.setSaldoFinal(MapeoDeBilletera.dinero(salida.saldoFinal()));
        respuesta.setCantidadMovimientos(salida.cantidadMovimientos());
        respuesta.setHashArchivo(salida.hashArchivo());
        respuesta.setEmitido(salida.emitido());
        respuesta.setMotivoDelBloqueo(salida.motivoDelBloqueo());
        return ResponseEntity.ok(respuesta);
    }

    @Override
    @Permiso("DATOS_SENSIBLES_LEER")
    public ResponseEntity<SalidaBloqueo> bloquearPorAutoridad(UUID idempotencyKey, EntradaBloqueo cuerpo) {
        Traza.marcarCasoDeUso("CU-17", cuerpo.getNumeroOficio());

        var salida = cu17.ejecutar(
                new CU17BloquearPorAutoridad.EntradaBloqueo(
                        cuerpo.getCuentaBilleteraId(),
                        cuerpo.getAutoridad().getValue(),
                        cuerpo.getTipoOrden().getValue(),
                        cuerpo.getNumeroOficio(),
                        MapeoDeBilletera.dineroOpcional(cuerpo.getMontoBloqueado()),
                        cuerpo.getAlcance().getValue(),
                        cuerpo.getDocumentoUrl().toString(),
                        cuerpo.getHashDocumento()),
                sesion.actual());

        var respuesta = new SalidaBloqueo();
        respuesta.setBloqueoId(salida.bloqueoId());
        respuesta.setRetencionId(salida.retencionId());
        respuesta.setMontoBloqueado(MapeoDeBilletera.dinero(salida.montoBloqueado()));
        respuesta.setSaldoDisponible(MapeoDeBilletera.dinero(salida.saldoDisponible()));
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    /**
     * La transferencia.
     *
     * <p>El destino llega como lo escribio una persona —un alias, un grupo— y el caso
     * de uso trabaja con la cuenta. Traducirlo es lo primero **y pasa afuera**: el
     * alias lo resuelve {@code grupos}, y esa llamada no puede estar dentro de la
     * transaccion que mueve el dinero.
     *
     * <p>Un destino que no se pudo resolver se rechaza. Mandar la plata a una cuenta
     * adivinada no tiene vuelta atras.
     */
    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaTransferencia> transferirSaldo(UUID idempotencyKey, EntradaTransferencia cuerpo) {
        Traza.marcarCasoDeUso("CU-12", cuerpo.getCuentaOrigenId().toString());

        var destino = cuerpo.getDestino();
        UUID cuentaDestino = destinos.cuenta(destino.getTipo().getValue(), destino.getValor(), sesion.actual())
                .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(12, 2), "Ese destino no existe."));

        var salida = cu12.ejecutar(
                new CU12TransferirSaldo.EntradaTransferencia(
                        idempotencyKey.toString(),
                        cuerpo.getCuentaOrigenId(),
                        cuentaDestino,
                        MapeoDeBilletera.dinero(cuerpo.getMonto()),
                        cuerpo.getConcepto(),
                        Optional.empty(),
                        Optional.ofNullable(cuerpo.getObligacionId())),
                sesion.actual());

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
     * lleva {@code aportes} y los pasanakus vivos los lleva {@code grupos}. Se
     * preguntan antes de abrir la transaccion y entran al caso de uso ya resueltas.
     *
     * <p>Si alguno de los dos no contesta, la respuesta que se asume es **que si hay
     * pendientes**. Cerrar por falta de respuesta le pasaria la deuda a los otros del
     * grupo, y la puerta existe para protegerlos a ellos.
     */
    @Override
    @Permiso("BILLETERA_OPERAR")
    public ResponseEntity<SalidaCierreBilletera> solicitarCierreBilletera(UUID idempotencyKey, EntradaCierre cuerpo) {
        var ctx = sesion.actual();
        Traza.marcarCasoDeUso("CU-16", cuerpo.getCuentaBilleteraId().toString());

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
