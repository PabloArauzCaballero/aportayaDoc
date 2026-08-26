package bo.aportaya.identidad.dominio;

import bo.aportaya.plataforma.dominio.ErrorDeDominio;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * El documento del titular: **cifrado para guardarlo, hasheado para buscarlo**.
 *
 * <p>El numero nunca viaja ni se guarda en claro. El hash con pimienta permite
 * responder «¿este documento ya esta registrado?» sin poder reconstruirlo, que es
 * exactamente lo que hace falta para detectar duplicados sin crear un padron de
 * documentos legible por quien acceda a la base.
 */
public record DocumentoDeIdentidad(Tipo tipo, String hashNumero, String paisEmision) {

    public DocumentoDeIdentidad {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(paisEmision, "pais de emision");
        if (hashNumero == null || hashNumero.length() != 64) {
            throw new ErrorDeDominio("El hash del documento tiene 64 caracteres o no es un hash");
        }
    }

    public static DocumentoDeIdentidad de(Tipo tipo, String numero, String pimienta, String paisEmision) {
        return new DocumentoDeIdentidad(tipo, hashear(numero + pimienta), paisEmision);
    }

    private static String hashear(String texto) {
        try {
            byte[] digestion = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexadecimal = new StringBuilder(digestion.length * 2);
            for (byte b : digestion) {
                hexadecimal.append("%02x".formatted(b));
            }
            return hexadecimal.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tiene que existir en cualquier JVM", e);
        }
    }

    /** Los del modelo. `CEX` del caso de uso es `CARNET_EXTRANJERIA` en el `.puml`. */
    public enum Tipo {
        CI,
        CARNET_EXTRANJERIA,
        PASAPORTE
    }
}
