package bo.aportaya.entregas.dominio.puertos;

import java.util.UUID;

/**
 * Quien confirma que una cuenta bancaria es de quien la registra.
 *
 * <p>R-SEG-02 exige que el titular de la cuenta sea el titular de la billetera, y ese
 * nombre y ese documento viven en {@code identidad}. Este servicio no los puede leer
 * (invariante 11) ni le sirve que se los declare el mismo que registra la cuenta: seria
 * compararlo consigo mismo, y la comprobacion pasaria siempre.
 *
 * <p>Por eso se pregunta, y se pregunta **fuera de la transaccion** (invariante 6).
 * {@code identidad} contesta si coinciden; nunca dice cuales son.
 */
public interface TitularDeLaBilletera {

    boolean esElMismo(UUID usuarioId, String nombreDeclarado, String documentoDeclarado);
}
