package bo.aportaya.erp.aplicacion;

import bo.aportaya.erp.infraestructura.ComprasRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-102 · Dar de alta un tercero comercial y su orden de compra.
 *
 * <p>El alta de un proveedor es la puerta de entrada del dinero que sale. Por eso:
 *
 * <ul>
 *   <li>**Un documento, un tercero.** El mismo NIT cargado dos veces permite pagarle dos
 *       veces a la misma empresa sin que el control de duplicados lo vea.
 *   <li>**La orden nace en BORRADOR y aprobar es un acto separado**
 *       ({@code ck_orden_compra_aprobacion}). Quien crea la orden no la aprueba solo por
 *       haberla escrito.
 *   <li>**Un tercero bloqueado no recibe ordenes.** Bloquearlo y seguir comprandole
 *       convierte el bloqueo en una nota de color.
 * </ul>
 */
@Service
public class CU102AltaDeTercero {

    private final Datos datos;
    private final ComprasRepositorio compras;
    private final Outbox outbox;

    public CU102AltaDeTercero(Datos datos, ComprasRepositorio compras, Outbox outbox) {
        this.datos = datos;
        this.compras = compras;
        this.outbox = outbox;
    }

    @Transactional
    public UUID darDeAlta(EntradaTercero entrada, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var existente = compras.terceroPorDocumento(dsl, entrada.numeroDocumento());
            if (existente.isPresent()) {
                // AP-CU102-02 · el mismo NIT con otro tipo es otro tercero, y devolverlo
                // como si fuera el pedido dejaria comprando a un cliente.
                if (!existente.get().tipo().equals(entrada.tipo())) {
                    throw new ErrorDeNegocio(
                            CodigoError.de(102, 2),
                            "El documento %s ya esta registrado como %s."
                                    .formatted(
                                            entrada.numeroDocumento(),
                                            existente.get().tipo()));
                }
                // El reintento es inocuo: devuelve el que hay en vez de crear un
                // duplicado que despues permitiria pagar dos veces.
                return existente.get().id();
            }
            UUID id = compras.altaDeTercero(
                    dsl,
                    entrada.tipo(),
                    entrada.razonSocial(),
                    entrada.numeroDocumento(),
                    entrada.email(),
                    entrada.cuentaContableId());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.tercero_dado_de_alta",
                            "tercero_comercial",
                            id,
                            Map.of("tipo", entrada.tipo(), "numeroDocumento", entrada.numeroDocumento()),
                            UUID.fromString(ctx.traza().id())));
            return id;
        });
    }

    @Transactional
    public SalidaOrden crearOrden(EntradaOrden entrada, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var tercero = compras.terceroPorId(dsl, entrada.terceroId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(102, 1), "Ese tercero no existe."));
            if (!"ACTIVO".equals(tercero.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(102, 1),
                        "El tercero esta " + tercero.estado() + ": no se le emiten ordenes de compra.");
            }
            if ("CLIENTE".equals(tercero.tipo())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(102, 1), "Ese tercero es cliente, no proveedor: no se le compra.");
            }

            UUID id = compras.crearOrden(
                    dsl,
                    entrada.terceroId(),
                    entrada.centroCostoId(),
                    entrada.numero(),
                    entrada.descripcion(),
                    entrada.monto(),
                    entrada.moneda());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.orden_compra_creada",
                            "orden_compra",
                            id,
                            Map.of(
                                    "numero", entrada.numero(),
                                    "monto", entrada.monto().toPlainString(),
                                    "estado", "BORRADOR"),
                            UUID.fromString(ctx.traza().id())));
            return new SalidaOrden(id, "BORRADOR", entrada.monto());
        });
    }

    @Transactional
    public SalidaOrden aprobarOrden(UUID ordenId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var orden = compras.orden(dsl, ordenId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(102, 3), "Esa orden no existe."));
            if (!compras.aprobarOrden(dsl, ordenId, ctx.usuarioId())) {
                throw new ErrorDeNegocio(CodigoError.de(102, 3), "Esa orden ya no esta en borrador.");
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.orden_compra_aprobada",
                            "orden_compra",
                            ordenId,
                            Map.of(
                                    "aprobadaPor", ctx.usuarioId().toString(),
                                    "monto", orden.montoTotal().toPlainString()),
                            UUID.fromString(ctx.traza().id())));
            return new SalidaOrden(ordenId, "APROBADA", orden.montoTotal());
        });
    }

    public record EntradaTercero(
            String tipo, String razonSocial, String numeroDocumento, String email, UUID cuentaContableId) {}

    public record EntradaOrden(
            UUID terceroId, UUID centroCostoId, String numero, String descripcion, BigDecimal monto, String moneda) {}

    public record SalidaOrden(UUID ordenId, String estado, BigDecimal monto) {}
}
