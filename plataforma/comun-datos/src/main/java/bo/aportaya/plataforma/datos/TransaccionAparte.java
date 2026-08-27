package bo.aportaya.plataforma.datos;

import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Una transaccion que sobrevive al rechazo de la que la contiene.
 *
 * <p>Existe por un problema concreto que aparece en cuanto un caso de uso tiene que
 * <b>dejar constancia de que rechazo algo</b>: si la constancia se escribe en la misma
 * transaccion y despues se lanza el error de negocio, el {@code ROLLBACK} se lleva
 * puesta la constancia. El rechazo queda registrado en ningun lado — que es
 * exactamente lo contrario de lo que pedia el criterio de aceptacion.
 *
 * <p>Sirve para el rastro del intento denegado (CU-58), y para nada mas. <b>No es una
 * forma de partir un caso de uso en dos transacciones</b>: el invariante 2 sigue
 * valiendo, y lo que va aca adentro no es parte del efecto — es la anotacion de que el
 * efecto no ocurrio.
 *
 * <p>Se inyecta un {@code TransactionTemplate} propio y no se usa
 * {@code @Transactional(REQUIRES_NEW)} porque la anotacion sobre un metodo de la misma
 * clase no hace nada: el proxy de Spring no se aplica a una llamada interna, y el
 * codigo pareceria correcto mientras corre dentro de la transaccion equivocada.
 */
@Component
public class TransaccionAparte {

    private final TransactionTemplate propia;

    public TransaccionAparte(PlatformTransactionManager gestor) {
        this.propia = new TransactionTemplate(Objects.requireNonNull(gestor, "gestor"));
        // Suspende la de afuera y abre una nueva conexion. Con la de afuera ya abortada
        // —una consulta que se corto por tiempo deja la transaccion invalida— es la
        // unica forma de escribir algo.
        this.propia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T en(Supplier<T> trabajo) {
        return propia.execute(estado -> trabajo.get());
    }
}
