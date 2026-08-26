package bo.aportaya.identidad.infraestructura;

import bo.aportaya.identidad.dominio.puertos.HasheadorDeCredencial;
import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Argon2id con pimienta, que es lo unico admisible para una credencial.
 *
 * <p>Un hash rapido —SHA-256, incluso con sal— se rompe en una GPU a millones por
 * segundo: el punto de Argon2 es costar memoria, no ser veloz. La pimienta va aparte
 * de la base para que filtrar la tabla no alcance.
 */
@Component
public class Argon2Hasheador implements HasheadorDeCredencial {

    private static final int ITERACIONES = 3;
    private static final int MEMORIA_KIB = 65536;
    private static final int PARALELISMO = 2;

    private final Argon2 argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
    private final String pimienta;

    public Argon2Hasheador(@Value("${aportaya.seguridad.pimienta}") String pimienta) {
        this.pimienta = pimienta;
    }

    @Override
    public String hashear(char[] credencial) {
        char[] conPimienta = sazonar(credencial);
        try {
            return argon2.hash(ITERACIONES, MEMORIA_KIB, PARALELISMO, conPimienta, StandardCharsets.UTF_8);
        } finally {
            argon2.wipeArray(conPimienta);
        }
    }

    @Override
    public boolean coincide(char[] credencial, String hashGuardado) {
        char[] conPimienta = sazonar(credencial);
        try {
            // verify de Argon2 compara en tiempo constante.
            return argon2.verify(hashGuardado, conPimienta, StandardCharsets.UTF_8);
        } finally {
            argon2.wipeArray(conPimienta);
        }
    }

    private char[] sazonar(char[] credencial) {
        char[] pimientaChars = pimienta.toCharArray();
        char[] resultado = new char[credencial.length + pimientaChars.length];
        System.arraycopy(credencial, 0, resultado, 0, credencial.length);
        System.arraycopy(pimientaChars, 0, resultado, credencial.length, pimientaChars.length);
        return resultado;
    }
}
