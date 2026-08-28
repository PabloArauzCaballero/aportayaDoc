package bo.aportaya.nucleofinanciero.infraestructura;

import bo.aportaya.nucleofinanciero.dominio.puertos.CotizadorDeComision;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.web.clientes.ClienteDeServicio;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Le pregunta el precio a {@code tarifas}, por su contrato.
 *
 * <p>Cotizar **deja la cotizacion escrita**, y eso es deliberado: R-TAR-02 dice que lo
 * que se cotiza es lo que se cobra, asi que el retiro que se autoriza despues queda
 * atado a un precio que alguien puede volver a mirar.
 *
 * <p>Cuando el tarifario no tiene concepto para el hecho, {@code tarifas} responde
 * {@code AP-CU30-01} con {@code gratuita=true}. Eso <b>no</b> es una falla: es el
 * catalogo diciendo que la operacion no cobra. Cualquier otra respuesta —incluida la
 * ausencia de respuesta— se traduce a vacio, y quien pregunta rechaza.
 */
@Component
public class CotizadorPorHttp implements CotizadorDeComision {

    private static final String SIN_CONCEPTO = "AP-CU30-01";

    private final RestClient rest;
    private final String codigoTarifario;

    public CotizadorPorHttp(
            RestClient.Builder constructor,
            @Value("${aportaya.servicios.tarifas}") String urlDeTarifas,
            @Value("${aportaya.tarifas.codigo-tarifario}") String codigoTarifario) {
        this.rest = constructor.baseUrl(urlDeTarifas).build();
        this.codigoTarifario = codigoTarifario;
    }

    @Override
    public Optional<Dinero> costoDe(
            String hechoGenerador, UUID referenciaId, Dinero montoBase, String claveIdempotencia) {

        Map<String, Object> cuerpo = Map.of(
                "codigoTarifario",
                codigoTarifario,
                "hechoGenerador",
                hechoGenerador,
                "referenciaTipo",
                "OPERACION",
                "referenciaId",
                referenciaId.toString(),
                "montoBase",
                Map.of(
                        "monto", montoBase.monto().toPlainString(),
                        "moneda", montoBase.moneda().name()));
        try {
            var salida = rest.post()
                    .uri("/comisiones/cotizaciones")
                    .header("Idempotency-Key", claveIdempotencia)
                    .headers(ClienteDeServicio::propagarElToken)
                    .body(cuerpo)
                    .retrieve()
                    .body(Cotizacion.class);
            return Optional.ofNullable(salida)
                    .map(c -> Dinero.de(new BigDecimal(c.montoTotal().monto()), montoBase.moneda()));
        } catch (RestClientResponseException respondio) {
            return esGratuita(respondio) ? Optional.of(Dinero.cero(montoBase.moneda())) : Optional.empty();
        } catch (RuntimeException noRespondio) {
            return Optional.empty();
        }
    }

    /** «No hay concepto para ese hecho» es un precio, no un error de transporte. */
    private boolean esGratuita(RestClientResponseException respondio) {
        String cuerpo = respondio.getResponseBodyAsString();
        return cuerpo.contains(SIN_CONCEPTO) && cuerpo.contains("\"gratuita\"");
    }

    private record Cotizacion(Importe montoTotal) {
        private record Importe(String monto, String moneda) {}
    }
}
