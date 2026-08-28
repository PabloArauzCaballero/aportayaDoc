package bo.aportaya.nucleofinanciero.web;

import bo.aportaya.nucleofinanciero.aplicacion.CU10RecargarSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.CU13RetenerSaldo;
import bo.aportaya.nucleofinanciero.aplicacion.CU14ReversarTransaccion;
import bo.aportaya.nucleofinanciero.aplicacion.CU15EmitirExtracto;
import bo.aportaya.nucleofinanciero.aplicacion.CU17BloquearPorAutoridad;
import bo.aportaya.nucleofinanciero.aplicacion.ConsultarSaldo;
import bo.aportaya.nucleofinanciero.web.generado.BilleteraApi;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaBloqueo;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaCierreRetencion;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaRecarga;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaRetencion;
import bo.aportaya.nucleofinanciero.web.generado.modelo.EntradaReverso;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SaldoBilletera;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaAcreditacion;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaBloqueo;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaCierreRetencion;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaExtracto;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaRecarga;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaRetencion;
import bo.aportaya.nucleofinanciero.web.generado.modelo.SalidaReverso;
import bo.aportaya.plataforma.web.seguridad.Permiso;
import bo.aportaya.plataforma.web.seguridad.SesionDeLaPeticion;
import bo.aportaya.plataforma.web.traza.Traza;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las paginas de {@code /billetera}.
 *
 * <p><b>Tres operaciones quedan sin implementar a proposito</b>, y estan declaradas en
 * {@code planes/informes/carril-T.md}:
 *
 * <ul>
 *   <li>{@code solicitarRetiro} necesita el costo del retiro, que lo cotiza
 *       {@code tarifas} y el contrato no trae. Ponerlo en cero cobraria de menos en
 *       silencio.
 *   <li>{@code solicitarCierreBilletera} necesita saber si el titular tiene
 *       obligaciones abiertas o participa en un grupo activo — hechos de
 *       {@code aportes} y de {@code grupos}.
 *   <li>{@code transferirSaldo} recibe el destino como {@code {tipo, valor}} y CU-12
 *       espera la cuenta ya resuelta. Resolver un alias exige leer
 *       {@code grupos.participante} (invariante 11).
 * </ul>
 *
 * <p>Los tres son huecos de contrato entre carriles y se cierran con un micro-PR
 * {@code [CONTRATO]}, no adivinando en la frontera.
 *
 * <p>El {@code @Permiso} de clase cubre esas dos rutas: sin decision de acceso
 * declarada el proceso no levanta, y una ruta sin implementar no es excusa para
 * dejarla abierta.
 */
@RestController
@Permiso("BILLETERA_OPERAR")
public class BilleteraController implements BilleteraApi {

    private final CU10RecargarSaldo cu10;
    private final CU13RetenerSaldo cu13;
    private final CU14ReversarTransaccion cu14;
    private final CU15EmitirExtracto cu15;
    private final CU17BloquearPorAutoridad cu17;
    private final ConsultarSaldo saldos;
    private final SesionDeLaPeticion sesion;

    public BilleteraController(
            CU10RecargarSaldo cu10,
            CU13RetenerSaldo cu13,
            CU14ReversarTransaccion cu14,
            CU15EmitirExtracto cu15,
            CU17BloquearPorAutoridad cu17,
            ConsultarSaldo saldos,
            SesionDeLaPeticion sesion) {
        this.cu10 = cu10;
        this.cu13 = cu13;
        this.cu14 = cu14;
        this.cu15 = cu15;
        this.cu17 = cu17;
        this.saldos = saldos;
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
}
