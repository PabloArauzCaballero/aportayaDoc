package bo.aportaya.cumplimiento.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * CU-05 · El hash que prueba QUE se acepto, QUIEN acepto y DESDE DONDE. Puro.
 *
 * <p>Guardar solo «acepto: si» no prueba nada seis meses despues, cuando alguien
 * discuta una comision. La evidencia ata cuatro cosas que no se pueden reconstruir
 * despues: el hash del documento que estaba publicado en ese momento, quien lo
 * acepto, desde que dispositivo e IP, y cuando. Cambiar cualquiera cambia el hash.
 *
 * <p>SHA-256 y no una funcion de contrasena: aca no se protege un secreto, se sella
 * un hecho. Lo que hace falta es que cualquiera pueda recomputarlo con los mismos
 * datos y llegar al mismo valor — incluido un perito que no tenga nuestro codigo.
 */
public final class EvidenciaDeAceptacion {

    private static final String SEPARADOR = "|";

    private EvidenciaDeAceptacion() {}

    public static String armar(
            String hashDocumento,
            UUID usuarioId,
            int version,
            Optional<String> ip,
            Optional<UUID> dispositivoId,
            OffsetDateTime momento) {

        String material = String.join(
                SEPARADOR,
                hashDocumento,
                usuarioId.toString(),
                Integer.toString(version),
                ip.orElse(""),
                dispositivoId.map(UUID::toString).orElse(""),
                // Instante en UTC: la misma aceptacion leida desde otra zona horaria
                // tiene que dar el mismo hash.
                momento.toInstant().toString());

        return enHexadecimal(sha256(material));
    }

    private static byte[] sha256(String material) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("Toda JVM trae SHA-256", imposible);
        }
    }

    private static String enHexadecimal(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            texto.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return texto.toString();
    }
}
