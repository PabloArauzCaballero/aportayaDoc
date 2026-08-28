package bo.aportaya.tarifas.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.tarifas.dominio.CalculoDeComision;
import bo.aportaya.tarifas.dominio.MetodoDeCalculo;
import bo.aportaya.tarifas.dominio.PoliticaDeRedondeo;
import bo.aportaya.tarifas.dominio.SegmentoAplicable;
import bo.aportaya.tarifas.dominio.VigenciaDeCotizacion;
import bo.aportaya.tarifas.infraestructura.CotizacionRepositorio;
import bo.aportaya.tarifas.infraestructura.TarifarioRepositorio;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-30 · Cotizar la comision antes de operar.
 *
 * <p>Que el usuario vea el numero final **antes** de aceptar, y que ese numero quede
 * guardado con su desglose. El reclamo tipico no es «me cobraron de mas»: es «nadie me
 * aviso».
 *
 * <p><b>Hueco declarado.</b> El CU pide que, cuando no hay concepto para el hecho, se
 * registre una cotizacion en cero «y no se omita». No se puede:
 * {@code cotizacion_comision.concepto_tarifa_id} es NOT NULL y sin concepto no hay
 * fila que guardar. Manda la DDL. Se responde AP-CU30-01 diciendo que la operacion es
 * gratuita, y no se inventa un concepto solo para poder escribir la fila — un concepto
 * inventado despues cobra.
 */
@Service
public class CU30CotizarComision {

    private final Datos datos;
    private final TarifarioRepositorio tarifarios;
    private final CotizacionRepositorio cotizaciones;
    private final Outbox outbox;
    private final Reloj reloj;
    private final Duration vigenciaDeLaCotizacion;

    public CU30CotizarComision(
            Datos datos,
            TarifarioRepositorio tarifarios,
            CotizacionRepositorio cotizaciones,
            Outbox outbox,
            Reloj reloj,
            Duration vigenciaDeLaCotizacion) {
        this.datos = datos;
        this.tarifarios = tarifarios;
        this.cotizaciones = cotizaciones;
        this.outbox = outbox;
        this.reloj = reloj;
        this.vigenciaDeLaCotizacion = vigenciaDeLaCotizacion;
    }

    @Transactional
    public SalidaCotizacion cotizar(EntradaCotizacion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            // Invariante 7: la clave se valida ANTES de escribir.
            var repetida = cotizaciones.porClave(dsl, entrada.referenciaId(), entrada.claveIdempotencia());
            if (repetida.isPresent()) {
                var previa = repetida.get();
                return new SalidaCotizacion(
                        previa.id(),
                        previa.montoComision(),
                        previa.montoImpuesto(),
                        previa.montoTotal(),
                        List.of(),
                        previa.validaHasta(),
                        false);
            }

            // R-TAR-07: si el grupo tiene tarifa congelada manda el snapshot. Perder el
            // precio pactado a mitad del pasanaku es cambiarle las reglas a alguien que
            // ya no se puede ir sin perder lo que puso.
            UUID tarifarioId = entrada.grupoId()
                    .flatMap(grupo -> tarifarios.tarifarioCongelado(dsl, grupo))
                    .or(() -> tarifarios.vigente(dsl, entrada.codigoTarifario(), ahora))
                    // AP-CU30-02 · R-CON-07: sin tarifario publicado no se cobra nada.
                    // Denegar por omision (invariante 9).
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(30, 2), "No hay tarifario vigente para " + entrada.codigoTarifario() + "."));

            // AP-CU30-01. El CU pide registrar una cotizacion en cero «y no omitirla»,
            // pero `cotizacion_comision.concepto_tarifa_id` es NOT NULL: sin concepto
            // la fila no se puede guardar. Manda la DDL; el hueco esta declarado en
            // planes/informes/carril-2B.md. Se responde que es gratuita, sin inventar
            // un concepto para poder escribir la fila.
            var elegido = tarifarios
                    .concepto(dsl, tarifarioId, entrada.hechoGenerador())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(30, 1),
                            "No hay concepto para el hecho «" + entrada.hechoGenerador()
                                    + "»: esa operacion no tiene comision.",
                            Map.of("tarifarioId", tarifarioId.toString(), "gratuita", "true")));
            MetodoDeCalculo.exigirCoherencia(elegido.metodoCalculo(), elegido.valorFijo(), elegido.valorPorcentual());

            var regla = tarifarios
                    .regla(dsl, elegido.id(), entrada.montoBase().monto(), ahora)
                    .orElse(null);
            PoliticaDeRedondeo redondeo =
                    tarifarios.redondeo(dsl, elegido.politicaRedondeoId()).orElseGet(PoliticaDeRedondeo::alCentavo);

            var calculo = CalculoDeComision.calcular(
                    entrada.montoBase(),
                    elegido.paraCalculo(regla),
                    tarifarios.impuestosVigentes(dsl, ahora.toLocalDate()),
                    entrada.descuento().orElse(Dinero.cero(entrada.montoBase().moneda())),
                    redondeo);

            // El segmento aplicado se guarda en el DESGLOSE, no en una columna:
            // `cotizacion_comision` no tiene `segmento_id` y no se inventa una.
            // Seis meses despues hay que poder decir por que pago eso, y la respuesta
            // vive en la fila, no en que los criterios no hayan cambiado desde entonces.
            var desglose = new java.util.ArrayList<>(calculo.desglose());
            entrada.segmentoAplicado()
                    .ifPresent(segmento -> desglose.add(new CalculoDeComision.LineaDesglose(
                            "SEGMENTO",
                            segmento.motivo(),
                            entrada.descuento()
                                    .orElse(Dinero.cero(entrada.montoBase().moneda())))));

            var vigencia = VigenciaDeCotizacion.desde(ahora, vigenciaDeLaCotizacion);
            UUID cotizacionId = cotizaciones.guardar(
                    dsl,
                    elegido.id(),
                    tarifarioId,
                    entrada.referenciaTipo(),
                    entrada.referenciaId(),
                    calculo,
                    aJson(desglose),
                    vigencia.validaHasta(),
                    ahora,
                    entrada.claveIdempotencia());

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "tarifas.comision_cotizada",
                            "cotizacion_comision",
                            cotizacionId,
                            Map.of(
                                    "conceptoCodigo", elegido.codigo(),
                                    "montoTotal", calculo.montoTotal().toString(),
                                    "validaHasta", vigencia.validaHasta().toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaCotizacion(
                    cotizacionId,
                    calculo.montoComision(),
                    calculo.montoImpuesto(),
                    calculo.montoTotal(),
                    List.copyOf(desglose),
                    vigencia.validaHasta(),
                    true);
        });
    }

    /** El usuario acepto el numero que se le mostro. Sin esto no hay consentimiento. */
    @Transactional
    public boolean aceptar(UUID cotizacionId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        return datos.conContexto(ctx, dsl -> {
            var cotizacion = cotizaciones
                    .ver(dsl, cotizacionId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(30, 2), "Esa cotizacion no existe."));
            // AP-CU30-03: nunca se acepta una cotizacion vencida. El precio que se
            // acepta tiene que ser el que se vio, y el que se vio ya caduco.
            if (new VigenciaDeCotizacion(cotizacion.validaHasta()).vencidaEn(ahora)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(30, 3), "Esa cotizacion vencio: hay que recalcular antes de cobrar.");
            }
            return cotizaciones.aceptar(dsl, cotizacionId, ahora);
        });
    }

    private String aJson(List<CalculoDeComision.LineaDesglose> desglose) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < desglose.size(); i++) {
            var linea = desglose.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"concepto\":\"")
                    .append(escapar(linea.concepto()))
                    .append("\",\"detalle\":\"")
                    .append(escapar(linea.detalle()))
                    .append("\",\"monto\":\"")
                    .append(linea.monto())
                    .append("\"}");
        }
        return json.append(']').toString();
    }

    private String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record EntradaCotizacion(
            String claveIdempotencia,
            String codigoTarifario,
            String hechoGenerador,
            String referenciaTipo,
            UUID referenciaId,
            Dinero montoBase,
            Optional<UUID> grupoId,
            Optional<Dinero> descuento,
            /** El segmento que rige para este usuario, ya resuelto por CU-36. */
            Optional<SegmentoAplicable.Eleccion> segmentoAplicado) {}

    public record SalidaCotizacion(
            UUID cotizacionId,
            Dinero montoComision,
            Dinero montoImpuesto,
            Dinero montoTotal,
            List<CalculoDeComision.LineaDesglose> desglose,
            OffsetDateTime validaHasta,
            boolean esNueva) {}
}
