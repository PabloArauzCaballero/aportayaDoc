package bo.aportaya.tarifas.infraestructura.simulado;

import bo.aportaya.tarifas.dominio.puertos.ServicioDeImpuestos;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * El adaptador por omision del servicio de impuestos.
 *
 * <p>No es un atajo: es el default declarado del proyecto (contrato de implementacion
 * §7). Conectar el SIN real exige credenciales, certificado de firma y punto de venta
 * habilitado; hasta entonces, programar contra el produce codigo que nadie puede
 * probar.
 *
 * <p>Acepta todo salvo que se le pida fallar. Sirve para dos cosas: que el flujo
 * completo se pueda ejercitar, y que la contingencia se pueda probar **de verdad**,
 * porque un camino de contingencia que nunca se ejecuto es un camino que no existe.
 */
@Component
public class ServicioDeImpuestosSimulado implements ServicioDeImpuestos {

    /** El CUFD dura 24 horas, extensible a 72 en los casos previstos. */
    private static final Duration VIGENCIA_DEL_CUFD = Duration.ofHours(24);

    private volatile boolean caido = false;
    private volatile String motivoDeRechazo = null;

    /** Para las pruebas: simula que el servicio no responde. */
    public void simularCaida(boolean caido) {
        this.caido = caido;
    }

    /** Para las pruebas: simula que el servicio rechaza el documento. */
    public void simularRechazo(String motivo) {
        this.motivoDeRechazo = motivo;
    }

    @Override
    public Respuesta enviar(String cuf, String documentoXml, OffsetDateTime momento) {
        if (caido) {
            throw new NoResponde("El servicio de impuestos no responde");
        }
        if (motivoDeRechazo != null) {
            return Respuesta.rechazada(motivoDeRechazo);
        }
        return Respuesta.aceptada("REC-" + Integer.toHexString(cuf.hashCode()).toUpperCase(java.util.Locale.ROOT));
    }

    @Override
    public Optional<String> cufdVigente(int sucursal, int puntoVenta, OffsetDateTime momento) {
        if (caido) {
            return Optional.empty();
        }
        // El codigo depende del dia y del punto de venta: dos puntos de venta no
        // comparten CUFD, y el de ayer no sirve hoy.
        long dia = momento.toEpochSecond() / VIGENCIA_DEL_CUFD.toSeconds();
        return Optional.of("CUFD%d%d%d".formatted(sucursal, puntoVenta, dia));
    }
}
