package bo.aportaya.tarifas.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.tarifas.dominio.MaximoDevolvible;
import bo.aportaya.tarifas.infraestructura.DevengoRepositorio;
import bo.aportaya.tarifas.infraestructura.DevolucionRepositorio;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-33 · Devolver comision y emitir nota de credito.
 *
 * <p>Reparar un cobro indebido devolviendo el dinero **y** corrigiendo el documento
 * fiscal. Devolver sin nota de credito deja una factura declarando un ingreso que ya
 * no existe: la plataforma termina pagando impuestos sobre plata que devolvio.
 *
 * <p>La factura **nunca se edita** (R-TAR-10): se emite una nota de credito con su
 * propio CUF. Editar un documento fiscal ya validado es, literalmente, falsificarlo.
 */
@Service
public class CU33DevolverComision {

    private final Datos datos;
    private final DevengoRepositorio devengos;
    private final DevolucionRepositorio devoluciones;
    private final Outbox outbox;
    private final Reloj reloj;
    private final EmisorDeCuf emisorDeCuf;

    public CU33DevolverComision(
            Datos datos,
            DevengoRepositorio devengos,
            DevolucionRepositorio devoluciones,
            Outbox outbox,
            Reloj reloj,
            EmisorDeCuf emisorDeCuf) {
        this.datos = datos;
        this.devengos = devengos;
        this.devoluciones = devoluciones;
        this.outbox = outbox;
        this.reloj = reloj;
        this.emisorDeCuf = emisorDeCuf;
    }

    /** Quien arma el codigo unico del documento fiscal. Se inyecta: no es de este CU. */
    public interface EmisorDeCuf {
        String paraNota(UUID facturaId, OffsetDateTime momento);
    }

    @Transactional
    public SalidaDevolucion devolver(EntradaDevolucion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var devengo = devengos.ver(dsl, entrada.devengoId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(33, 2), "Ese devengo no existe."));

            // AP-CU33-02: si nunca se cobro, no hay nada que devolver. Devolver sobre un
            // devengo no cobrado seria regalar plata que nunca entro.
            Dinero cobrado =
                    devengos.cobradoDe(dsl, devengo.id(), devengo.montoTotal().moneda());
            if (cobrado.monto().signum() == 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(33, 2), "Ese devengo no tiene cobro: no hay nada que devolver.");
            }

            // AP-CU33-01 · R-TAR-11: contra lo YA devuelto, no contra lo cobrado a secas.
            var maximo = new MaximoDevolvible(cobrado, devoluciones.devueltoDe(dsl, devengo.id(), cobrado.moneda()));
            if (!maximo.admite(entrada.monto())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(33, 1),
                        "La devolucion excede lo cobrado: quedan " + maximo.disponible() + ".",
                        Map.of(
                                "cobrado",
                                cobrado.toString(),
                                "yaDevuelto",
                                maximo.yaDevuelto().toString()));
            }

            var factura = devoluciones.facturaDe(dsl, devengo.id());
            // AP-CU33-03: una factura emitida offline y sin enviar no admite nota de
            // credito todavia. Primero se envia el documento original; si no, la nota
            // corrige algo que para el servicio de impuestos no existe.
            if (factura.isPresent()
                    && "EMITIDA_OFFLINE".equals(factura.get().estadoFiscal())
                    && factura.get().loteEnvioId() == null) {
                throw new ErrorDeNegocio(
                        CodigoError.de(33, 3),
                        "La factura sigue offline sin enviar: primero se envia y despues se emite la nota.");
            }

            UUID devolucionId = devoluciones.registrar(
                    dsl,
                    devengo.id(),
                    entrada.autorizadaPor(),
                    entrada.motivo(),
                    entrada.detalle(),
                    entrada.monto(),
                    entrada.forma(),
                    entrada.reclamoId().orElse(null),
                    ahora);

            UUID notaId = null;
            String cuf = null;
            if (factura.isPresent()) {
                cuf = emisorDeCuf.paraNota(factura.get().id(), ahora);
                notaId = devoluciones.emitirNotaDeCredito(
                        dsl, factura.get().id(), devolucionId, entrada.motivo(), entrada.monto(), cuf, ahora);
            }

            // El devengo NO se edita: es append-only (R-AUD-01). La devolucion vive en
            // su propia fila y el estado corriente se DERIVA de ella. Borrar el ingreso
            // para «dejarlo en cero» perderia la prueba de que se cobro y se devolvio,
            // que es justo lo que hay que poder mostrar.
            boolean total = !maximo.disponible().esMayorQue(entrada.monto());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "tarifas.comision_devuelta",
                            "devolucion_comision",
                            devolucionId,
                            Map.of(
                                    "devengoId", devengo.id().toString(),
                                    "monto", entrada.monto().toString(),
                                    "motivo", entrada.motivo(),
                                    "total", Boolean.toString(total)),
                            UUID.fromString(ctx.traza().id())));

            // Un error de tarifa o una falla de servicio son perdida operativa: se
            // registran como evento de riesgo, no se archivan como «devolucion mas».
            if ("ERROR_DE_TARIFA".equals(entrada.motivo()) || "FALLA_DE_SERVICIO".equals(entrada.motivo())) {
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "tarifas.riesgo_operativo_detectado",
                                "devolucion_comision",
                                devolucionId,
                                Map.of(
                                        "motivo",
                                        entrada.motivo(),
                                        "monto",
                                        entrada.monto().toString()),
                                UUID.fromString(ctx.traza().id())));
            }

            return new SalidaDevolucion(
                    devolucionId, notaId, cuf, total, maximo.disponible().menos(entrada.monto()));
        });
    }

    public record EntradaDevolucion(
            UUID devengoId,
            String motivo,
            String detalle,
            Dinero monto,
            String forma,
            Optional<UUID> reclamoId,
            UUID autorizadaPor) {}

    public record SalidaDevolucion(
            UUID devolucionId, UUID notaCreditoId, String cuf, boolean devolucionTotal, Dinero disponibleRestante) {}
}
