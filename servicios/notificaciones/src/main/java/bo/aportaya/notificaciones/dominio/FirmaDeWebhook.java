package bo.aportaya.notificaciones.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * CU-82 · Verifica la firma del webhook del proveedor. Puro.
 *
 * <p>HMAC-SHA256, y la comparacion es **en tiempo constante**. Comparar con
 * {@code equals} filtra, por el tiempo que tarda en volver, cuantos bytes coincidian:
 * con suficientes intentos eso alcanza para construir una firma valida sin conocer el
 * secreto.
 */
public final class FirmaDeWebhook {

    private static final String ALGORITMO = "HmacSHA256";

    private FirmaDeWebhook() {}

    public static String firmar(String cargaUtil, String secreto) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), ALGORITMO));
            return enHexadecimal(mac.doFinal(cargaUtil.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar la carga util", e);
        }
    }

    public static boolean verifica(String cargaUtil, String firmaRecibida, String secreto) {
        if (firmaRecibida == null || secreto == null || secreto.isBlank()) {
            // Sin secreto configurado no se acepta nada: denegar por omision. Aceptar
            // «porque todavia no hay secreto» abriria el webhook a cualquiera.
            return false;
        }
        return MessageDigest.isEqual(
                firmar(cargaUtil, secreto).getBytes(StandardCharsets.UTF_8),
                firmaRecibida.getBytes(StandardCharsets.UTF_8));
    }

    private static String enHexadecimal(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            texto.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return texto.toString();
    }
}
