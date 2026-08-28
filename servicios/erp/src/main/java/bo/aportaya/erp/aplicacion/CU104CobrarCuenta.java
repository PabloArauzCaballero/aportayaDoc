package bo.aportaya.erp.aplicacion;

import bo.aportaya.erp.infraestructura.CobranzasRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-104 · Cobrar una cuenta por cobrar.
 *
 * <p>**No se cobra por encima del saldo** (R-CTB-06). Cobrar de mas no es un error
 * contable menor: es plata que el cliente va a reclamar y que ya no figura como deuda,
 * asi que nadie la ve hasta que llama.
 *
 * <p>La cuenta se lee con {@code FOR UPDATE} por lo mismo que la factura: dos cobros
 * simultaneos leen el mismo saldo y los dos pasan.
 */
@Service
public class CU104CobrarCuenta {

    private final Datos datos;
    private final CobranzasRepositorio cobranzas;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU104CobrarCuenta(Datos datos, CobranzasRepositorio cobranzas, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.cobranzas = cobranzas;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public UUID abrir(EntradaCuenta entrada, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            UUID id = cobranzas.abrir(
                    dsl,
                    entrada.origenTipo(),
                    entrada.origenId(),
                    entrada.terceroId(),
                    entrada.monto(),
                    entrada.moneda(),
                    entrada.vencimiento());
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.cuenta_por_cobrar_abierta",
                            "cuenta_por_cobrar",
                            id,
                            Map.of(
                                    "origenTipo", entrada.origenTipo(),
                                    "monto", entrada.monto().toPlainString(),
                                    "vencimiento", entrada.vencimiento().toString()),
                            UUID.fromString(ctx.traza().id())));
            return id;
        });
    }

    @Transactional
    public SalidaCobro cobrar(EntradaCobro entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var cuenta = cobranzas
                    .bloqueada(dsl, entrada.cuentaPorCobrarId())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(104, 1), "Esa cuenta por cobrar no existe."));

            if ("INCOBRABLE".equals(cuenta.estado())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(104, 3), "Esa cuenta esta dada por incobrable: primero se rehabilita.");
            }

            BigDecimal cobradoNuevo = cuenta.montoCobrado().add(entrada.monto());
            // AP-CU104-02 · R-CTB-06.
            if (cobradoNuevo.compareTo(cuenta.monto()) > 0) {
                throw new ErrorDeNegocio(
                        CodigoError.de(104, 2),
                        "El cobro (%s) excede el saldo pendiente (%s)."
                                .formatted(
                                        entrada.monto().toPlainString(),
                                        cuenta.monto()
                                                .subtract(cuenta.montoCobrado())
                                                .toPlainString()));
            }

            UUID cobroId = cobranzas.cobrar(
                    dsl,
                    entrada.cuentaPorCobrarId(),
                    entrada.monto(),
                    cuenta.moneda(),
                    entrada.formaCobro(),
                    cobradoNuevo,
                    cuenta.monto(),
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "erp.cobro_registrado",
                            "cobro_cuenta_por_cobrar",
                            cobroId,
                            Map.of(
                                    "cuentaPorCobrarId",
                                            entrada.cuentaPorCobrarId().toString(),
                                    "monto", entrada.monto().toPlainString(),
                                    "saldoPendiente",
                                            cuenta.monto()
                                                    .subtract(cobradoNuevo)
                                                    .toPlainString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCobro(
                    cobroId,
                    entrada.cuentaPorCobrarId(),
                    cobradoNuevo.compareTo(cuenta.monto()) >= 0 ? "COBRADA" : "COBRADA_PARCIAL",
                    cobradoNuevo,
                    cuenta.monto().subtract(cobradoNuevo));
        });
    }

    public record EntradaCuenta(
            String origenTipo, UUID origenId, UUID terceroId, BigDecimal monto, String moneda, LocalDate vencimiento) {}

    public record EntradaCobro(UUID cuentaPorCobrarId, BigDecimal monto, String formaCobro) {}

    public record SalidaCobro(
            UUID cobroId, UUID cuentaPorCobrarId, String estado, BigDecimal cobrado, BigDecimal saldoPendiente) {}
}
