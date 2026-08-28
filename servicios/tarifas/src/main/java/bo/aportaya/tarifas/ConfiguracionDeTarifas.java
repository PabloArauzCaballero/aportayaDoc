package bo.aportaya.tarifas;

import bo.aportaya.tarifas.aplicacion.CU33DevolverComision;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Las piezas de tarifas que no son ni repositorio ni caso de uso. */
@Configuration
public class ConfiguracionDeTarifas {

    /**
     * El codigo unico de una nota de credito.
     *
     * <p>Se deriva del documento que corrige **y del momento**: determinista para un
     * reintento —el mismo pedido produce el mismo codigo—, y distinto entre dos notas
     * sobre la misma factura. Solo con la factura, dos devoluciones parciales darian el
     * mismo codigo y {@code uq_nota_cuf} rechazaria la segunda.
     *
     * <p>Es el emisor local, el que manda el contrato de implementacion mientras el SIN
     * no este integrado. Cuando lo este, se cambia este bean y ningun caso de uso.
     */
    @Bean
    public CU33DevolverComision.EmisorDeCuf emisorDeCuf() {
        return (facturaId, momento) -> "NC%s%d"
                .formatted(
                        Integer.toHexString(facturaId.hashCode()).toUpperCase(Locale.ROOT),
                        momento.toInstant().toEpochMilli());
    }
}
