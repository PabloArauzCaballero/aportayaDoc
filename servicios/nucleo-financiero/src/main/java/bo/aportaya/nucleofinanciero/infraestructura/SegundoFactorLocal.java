package bo.aportaya.nucleofinanciero.infraestructura;

import bo.aportaya.nucleofinanciero.dominio.puertos.SegundoFactor;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * El adaptador por omision del segundo factor: **deniega salvo que se lo apague a
 * proposito**.
 *
 * <p>Es el «adaptador local primero» que manda el contrato de carril: la operacion que
 * verificaria el factor todavia no existe en el contrato de {@code identidad}, y
 * mientras no exista este servicio no puede afirmar que alguien presento su segundo
 * factor. Lo unico honesto es negarlo.
 *
 * <p>{@code aportaya.mfa.exigido=false} lo apaga, y solo tiene sentido en desarrollo y
 * en las pruebas de punta a punta. **En cualquier entorno que maneje plata real queda
 * encendido**: apagarlo es habilitar retiros sin segundo factor, que es justo lo que
 * R-BIL-09 prohibe. El valor por omision es exigirlo.
 */
@Component
public class SegundoFactorLocal implements SegundoFactor {

    private final boolean exigido;

    public SegundoFactorLocal(@Value("${aportaya.mfa.exigido:true}") boolean exigido) {
        this.exigido = exigido;
    }

    @Override
    public boolean verificado(UUID usuarioId, String factor) {
        if (!exigido) {
            // Sin exigencia, cualquier sesion autenticada alcanza. Se sigue pidiendo
            // que el campo venga: un cliente que no lo manda no esta preparado para
            // el entorno donde si se exige.
            return factor != null && !factor.isBlank();
        }
        return false;
    }
}
