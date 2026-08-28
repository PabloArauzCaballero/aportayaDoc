package bo.aportaya.transparencia.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * El contenido de un certificado, su hash y su firma.
 *
 * <p>Tres decisiones que no son de estilo:
 *
 * <ol>
 *   <li>**El contenido lo elige el titular, campo por campo.** Un certificado que
 *       siempre muestra todo obliga a quien solo queria probar su antiguedad a revelar
 *       tambien su puntaje. Lo que no se pidio no entra, y por eso el hash se calcula
 *       sobre lo que se pidio.
 *   <li>**El codigo de verificacion es azar criptografico, no un correlativo.** Un
 *       correlativo se recorre: con un codigo se llega al siguiente, y los
 *       certificados de todo el mundo quedan a la vista.
 *   <li>**La firma es HMAC con una clave del almacen de secretos.** Sin clave no se
 *       firma: emitir sin firma es emitir un documento que cualquiera puede fabricar.
 * </ol>
 */
public final class CertificadoVerificable {

    /** El alfabeto excluye 0/O y 1/I/l: los codigos se dictan por telefono. */
    private static final char[] ALFABETO = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private CertificadoVerificable() {}

    /**
     * Arma el contenido con **solo** los campos pedidos, en forma canonica.
     *
     * @param disponibles todo lo que se podria mostrar
     * @param pedidos las claves que el titular eligio incluir
     */
    public static Map<String, String> contenido(Map<String, String> disponibles, java.util.Set<String> pedidos) {
        var elegido = new TreeMap<String, String>();
        for (var clave : pedidos) {
            String valor = disponibles.get(clave);
            if (valor != null) {
                elegido.put(clave, valor);
            }
        }
        return elegido;
    }

    public static String hash(Map<String, String> contenido) {
        return CadenaDeBloques.sha256(ContenidoCanonico.serializar(contenido));
    }

    /**
     * Firma el hash con la clave del almacen de secretos.
     *
     * @throws IllegalStateException si no hay clave. **Falla al emitir, no al
     *     verificar**: un certificado sin firma se descubriria recien cuando un tercero
     *     intenta confiar en el.
     */
    public static String firmar(String hashContenido, String clave) {
        if (clave == null || clave.isBlank()) {
            throw new IllegalStateException("Un certificado sin firma no prueba nada: falta la clave de firmado");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] firma = mac.doFinal(hashContenido.getBytes(StandardCharsets.UTF_8));
            var texto = new StringBuilder(firma.length * 2);
            for (byte b : firma) {
                texto.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return texto.toString();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el certificado", e);
        }
    }

    /** Comparacion en tiempo constante: comparar firmas con {@code equals} las filtra. */
    public static boolean firmaValida(String hashContenido, String clave, String firmaPublicada) {
        if (firmaPublicada == null) {
            return false;
        }
        return MessageDigest.isEqual(
                firmar(hashContenido, clave).getBytes(StandardCharsets.UTF_8),
                firmaPublicada.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * El codigo publico, a partir de azar criptografico. **Nunca de un contador ni de
     * {@code Math.random}**: los dos se adivinan.
     *
     * @param azar al menos 16 bytes de {@link java.security.SecureRandom}
     */
    public static String codigo(byte[] azar) {
        if (azar.length < 16) {
            throw new IllegalArgumentException("Un codigo publico exige al menos 16 bytes de azar");
        }
        var texto = new StringBuilder("AY-");
        for (int i = 0; i < 16; i++) {
            texto.append(ALFABETO[(azar[i] & 0xFF) % ALFABETO.length]);
            if (i == 3 || i == 7 || i == 11) {
                texto.append('-');
            }
        }
        return texto.toString();
    }

    /** El estado de un certificado ante un tercero. */
    public static String estado(
            boolean existe,
            java.time.OffsetDateTime revocadoEn,
            java.time.OffsetDateTime expiraEn,
            java.time.OffsetDateTime ahora) {
        // Un codigo inexistente responde igual que uno invalido: distinguirlos le diria
        // a quien prueba codigos al azar cuando acerto.
        if (!existe) {
            return "NO_VALIDO";
        }
        if (revocadoEn != null) {
            return "REVOCADO";
        }
        if (!expiraEn.isAfter(ahora)) {
            return "VENCIDO";
        }
        return "VIGENTE";
    }
}
