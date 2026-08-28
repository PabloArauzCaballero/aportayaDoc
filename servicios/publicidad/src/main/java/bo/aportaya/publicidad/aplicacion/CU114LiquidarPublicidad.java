package bo.aportaya.publicidad.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.publicidad.infraestructura.AnuncianteRepositorio;
import bo.aportaya.publicidad.infraestructura.FacturacionRepositorio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-114 · Liquidar y facturar el gasto publicitario.
 *
 * <p>El gasto de un mes se cobra por el mismo camino que cualquier otro ingreso de la
 * empresa: comprobante fiscal de modulo 11 y cuenta por cobrar de modulo 13. Publicidad
 * **no escribe ninguna de las dos** — viven en otros esquemas (invariante 11) — y
 * tampoco puede pedirlas por red dentro de esta transaccion (invariante 6).
 *
 * <p>Por eso los dos identificadores **entran como dato**: quien orquesta la corrida
 * mensual pide el comprobante y la cuenta por cobrar antes, y recien despues llama
 * aca. No es una comodidad: {@code factura_publicidad} es append-only, asi que si la
 * fila nace sin ellos, nunca los va a tener. Queda declarado como hueco del carril.
 *
 * <p>Un mes sin consumo no se factura. No hay obligacion de emitir un comprobante por
 * cero, y {@code ck_factura_publicidad_monto} —{@code monto_total > 0}— tampoco lo
 * admitiria.
 */
@Service
public class CU114LiquidarPublicidad {

    private final Datos datos;
    private final FacturacionRepositorio facturacion;
    private final AnuncianteRepositorio anunciantes;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU114LiquidarPublicidad(
            Datos datos,
            FacturacionRepositorio facturacion,
            AnuncianteRepositorio anunciantes,
            Outbox outbox,
            Reloj reloj) {
        this.datos = datos;
        this.facturacion = facturacion;
        this.anunciantes = anunciantes;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public Salida liquidar(Entrada entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> {
            var cuenta = anunciantes
                    .cuentaBloqueada(dsl, entrada.cuentaPublicitariaId())
                    .orElseThrow(
                            () -> new ErrorDeNegocio(CodigoError.de(114, 1), "Esa cuenta publicitaria no existe."));

            // AP-CU114-02 · R-PUB-06: un periodo por cuenta. La lectura va despues del
            // FOR UPDATE de la cuenta, que es lo que serializa dos corridas del mismo mes.
            var yaFacturado = facturacion.facturaDe(dsl, cuenta.id(), entrada.periodo());
            if (yaFacturado.isPresent()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(114, 2),
                        "El periodo " + entrada.periodo() + " ya esta liquidado.",
                        Map.of("facturaPublicidadId", yaFacturado.get().toString()));
            }

            var consumo = facturacion.consumo(dsl, cuenta.id(), entrada.periodo());
            // AP-CU114-01 · no hay obligacion de facturar cero.
            if (consumo.estaVacio()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(114, 1),
                        "La cuenta no consumio nada en " + entrada.periodo() + ": no se factura cero.");
            }

            BigDecimal total = consumo.aFacturar();
            String estado = entrada.facturaElectronicaId() == null ? "GENERADA" : "FACTURADA";
            UUID facturaId = facturacion.emitir(
                    dsl,
                    cuenta.id(),
                    entrada.periodo(),
                    total,
                    cuenta.moneda(),
                    entrada.facturaElectronicaId(),
                    entrada.cuentaPorCobrarId(),
                    estado,
                    ahora);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "publicidad.factura_publicidad_generada",
                            "factura_publicidad",
                            facturaId,
                            Map.of(
                                    "cuentaPublicitariaId", cuenta.id().toString(),
                                    "periodo", entrada.periodo(),
                                    "montoTotal", total.toPlainString(),
                                    "estado", estado),
                            UUID.fromString(ctx.traza().id())));

            return new Salida(facturaId, total, cuenta.moneda(), estado, entrada.cuentaPorCobrarId());
        });
    }

    /** Lo consumido en el mes, sin emitir nada: es lo que mira Contabilidad antes de correr. */
    @Transactional(readOnly = true)
    public BigDecimal consumoDelPeriodo(UUID cuentaId, String periodo, ContextoSesion ctx) {
        return datos.conContexto(
                ctx, dsl -> facturacion.consumo(dsl, cuentaId, periodo).aFacturar());
    }

    public record Entrada(
            UUID cuentaPublicitariaId, String periodo, UUID facturaElectronicaId, UUID cuentaPorCobrarId) {}

    public record Salida(
            UUID facturaPublicidadId, BigDecimal montoTotal, String moneda, String estado, UUID cuentaPorCobrarId) {}
}
