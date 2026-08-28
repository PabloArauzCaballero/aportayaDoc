package bo.aportaya.notificaciones.infraestructura.simulado;

import bo.aportaya.notificaciones.aplicacion.CU83DespacharLote.AdaptadorMensajeria;
import bo.aportaya.notificaciones.dominio.Canal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * El adaptador de mensajeria por omision: acepta y deja constancia, sin salir a la red.
 *
 * <p>Es el default que fija el contrato de implementacion —bandeja interna y correo, con
 * push como aviso; WhatsApp y SMS apagados— mientras no haya proveedor integrado. Que el
 * simulador sea el adaptador por omision es deliberado: apartarse de eso es un ADR, no
 * una decision de implementacion.
 *
 * <p><b>No registra el destinatario en el log.</b> Un telefono o un correo en una linea
 * de log es un dato personal que se replica en cada agregador por el que pase; lo que se
 * deja es el identificador del envio, que sirve para rastrear sin exponer a nadie.
 */
@Component
public class MensajeriaSimulada implements AdaptadorMensajeria {

    private static final Logger LOG = LoggerFactory.getLogger(MensajeriaSimulada.class);

    @Override
    public Resultado enviar(String proveedorCodigo, Canal canal, String destinatario, UUID envioId) {
        LOG.info("envio simulado · proveedor={} canal={} envio={}", proveedorCodigo, canal, envioId);
        return new Resultado(true, "SIM-" + envioId, null);
    }
}
