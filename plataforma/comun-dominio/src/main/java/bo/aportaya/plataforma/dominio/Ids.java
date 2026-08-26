package bo.aportaya.plataforma.dominio;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generar identificadores tambien es un borde: azar y estado del proceso.
 *
 * <p>Se inyecta por la misma razon que {@link Reloj}. Y el azar es criptografico
 * siempre, porque un identificador que se puede adivinar deja de ser solo un
 * identificador.
 */
public interface Ids {

    UUID nuevo();

    static Ids seguros() {
        SecureRandom azar = new SecureRandom();
        return () -> {
            byte[] bytes = new byte[16];
            azar.nextBytes(bytes);
            bytes[6] = (byte) ((bytes[6] & 0x0f) | 0x40);
            bytes[8] = (byte) ((bytes[8] & 0x3f) | 0x80);
            long alto = 0;
            long bajo = 0;
            for (int i = 0; i < 8; i++) {
                alto = (alto << 8) | (bytes[i] & 0xffL);
                bajo = (bajo << 8) | (bytes[i + 8] & 0xffL);
            }
            return new UUID(alto, bajo);
        };
    }
}
