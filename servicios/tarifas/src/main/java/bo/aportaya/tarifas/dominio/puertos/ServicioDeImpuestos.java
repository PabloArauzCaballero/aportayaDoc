package bo.aportaya.tarifas.dominio.puertos;

import java.time.OffsetDateTime;

/**
 * El servicio de impuestos, visto desde adentro.
 *
 * <p>Es un puerto y no una llamada directa por una razon concreta: **su caida no puede
 * detener la operacion**. Si emitir una factura dependiera de que el SIN responda, un
 * corte de su lado dejaria a la gente sin poder cobrar su turno.
 *
 * <p>El adaptador por omision es el simulador (contrato de implementacion §7). El real
 * se conecta cuando existan las credenciales, y no antes: programar contra un servicio
 * que no se puede probar produce codigo que nadie sabe si anda.
 */
public interface ServicioDeImpuestos {

    /** Lo que responde el servicio a un envio en linea. */
    record Respuesta(boolean aceptado, String codigoRecepcion, String motivoDelRechazo) {

        public static Respuesta aceptada(String codigo) {
            return new Respuesta(true, codigo, null);
        }

        public static Respuesta rechazada(String motivo) {
            return new Respuesta(false, null, motivo);
        }
    }

    /** Se lanza cuando el servicio no responde: es la senal para abrir contingencia. */
    class NoResponde extends RuntimeException {
        public NoResponde(String mensaje) {
            super(mensaje);
        }
    }

    /**
     * Envia el documento. Lanza {@link NoResponde} si el servicio no contesta.
     *
     * <p>Se llama **fuera** de la transaccion (invariante 6): una llamada de red
     * adentro deja la transaccion abierta el tiempo que el tercero tarde en contestar.
     */
    Respuesta enviar(String cuf, String documentoXml, OffsetDateTime momento);

    /** El codigo diario vigente para el punto de venta, si lo hay. */
    java.util.Optional<String> cufdVigente(int sucursal, int puntoVenta, OffsetDateTime momento);
}
