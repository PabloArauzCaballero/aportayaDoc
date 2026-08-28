package bo.aportaya.transparencia.dominio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * El sello de un bloque de transparencia y su cadena.
 *
 * <p>La cadena existe para que el grupo pueda probar, sin confiar en la plataforma, que
 * lo que paso quedo registrado y no se toco despues. Cada bloque referencia el hash del
 * anterior: alterar un evento viejo obliga a recalcular todos los bloques posteriores, y
 * eso es visible.
 *
 * <p>El hash de un bloque se calcula sobre su **contenido**: numero, hash anterior, raiz
 * de Merkle y periodo. Si se calculara sobre el identificador o la fecha de sellado,
 * dos bloques con el mismo contenido darian hashes distintos y la cadena dejaria de
 * probar nada.
 */
public final class CadenaDeBloques {

    /** El primer bloque de un grupo no tiene anterior: 64 ceros. */
    public static final String GENESIS = "0".repeat(64);

    /**
     * El numero del primer bloque de un grupo. **Es 2, y no por gusto.**
     *
     * <p>{@code ck_bloque_genesis} exige que {@code numero_bloque = 1} lleve
     * {@code hash_bloque_anterior IS NULL}, pero la columna esta declarada
     * {@code NOT NULL}. Las dos condiciones juntas hacen que **ningun bloque numero 1
     * se pueda escribir jamas**: la base rechaza el genesis. Mientras la boveda no
     * arregle una de las dos, la cadena arranca en 2 con {@link #GENESIS} como hash
     * anterior, que es lo unico que la base acepta.
     *
     * <p>No es una preferencia de numeracion: esta declarado como hueco en
     * {@code planes/informes/carril-3B.md} y hay una prueba que demuestra el rechazo.
     */
    public static final long PRIMER_NUMERO = 2;

    private CadenaDeBloques() {}

    /**
     * La raiz de Merkle de los hashes de contenido.
     *
     * <p>Permite probar que **un evento concreto** esta en el bloque sin publicar todos
     * los demas: un participante puede verificar su propio pago sin ver los de los
     * otros. Con una lista simple habria que revelar todo el bloque para probar una
     * sola cosa.
     */
    public static String raizMerkle(List<String> hojas) {
        if (hojas.isEmpty()) {
            return GENESIS;
        }
        List<String> nivel = new ArrayList<>(hojas);
        while (nivel.size() > 1) {
            List<String> siguiente = new ArrayList<>();
            for (int i = 0; i < nivel.size(); i += 2) {
                // El ultimo impar se empareja consigo mismo: es la convencion habitual
                // y mantiene el arbol balanceado sin inventar una hoja.
                String izquierda = nivel.get(i);
                String derecha = i + 1 < nivel.size() ? nivel.get(i + 1) : izquierda;
                siguiente.add(sha256(izquierda + derecha));
            }
            nivel = siguiente;
        }
        return nivel.get(0);
    }

    /** El hash del bloque, sobre su contenido. Determinista y reproducible. */
    public static String hashDelBloque(
            long numero, String hashAnterior, String raizMerkle, String desde, String hasta) {
        return sha256("%d|%s|%s|%s|%s".formatted(numero, hashAnterior, raizMerkle, desde, hasta));
    }

    /** El hash del contenido de un registro sellado. */
    public static String sha256(String material) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder texto = new StringBuilder(resumen.length * 2);
            for (byte b : resumen) {
                texto.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return texto.toString();
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("Toda JVM trae SHA-256", imposible);
        }
    }

    /**
     * Un eslabon roto: **donde, que componente y por que**.
     *
     * <p>El componente importa tanto como el numero. «El bloque 3 falla» no le dice a
     * nadie si le movieron el contenido, si le cambiaron el enlace o si falta un
     * bloque entero antes; cada una de esas tres cosas se investiga distinto.
     */
    public record Rotura(long numeroBloque, String componente, String motivo) {}

    /**
     * Un bloque tal como se verifica.
     *
     * @param raizMerkle la raiz guardada al sellar
     * @param raizRecomputada la raiz que producen hoy los registros sellados de ese
     *     bloque. Si difiere de la guardada, el contenido fue alterado despues del
     *     sellado — que es justamente lo que la cadena existe para delatar. Puede ser
     *     {@code null} cuando el verificador no tiene las hojas a mano; en ese caso el
     *     contenido no se comprueba y **el veredicto lo dice**, no lo calla.
     */
    public record Bloque(
            long numero,
            String hashAnterior,
            String raizMerkle,
            String hash,
            String desde,
            String hasta,
            String raizRecomputada) {}

    /**
     * Recorre la cadena y devuelve donde se rompe.
     *
     * <p>Devuelve **todas** las roturas, no la primera: si alguien altero varios
     * bloques, saber solo del primero deja el resto sin revisar. Quien llama decide
     * si muestra la primera —que es la que importa para explicar— o todas.
     */
    public static List<Rotura> verificar(List<Bloque> cadena) {
        List<Rotura> roturas = new ArrayList<>();
        String esperadoAnterior = GENESIS;
        // La cadena se recorre entera y desde su principio. Empezar por donde empieza
        // lo que nos pasaron dejaria pasar una cadena a la que le cortaron la cabeza.
        long numeroEsperado = PRIMER_NUMERO;

        for (Bloque bloque : cadena) {
            // Un hueco en la numeracion es tan grave como una alteracion: significa
            // que un periodo entero desaparecio de la historia.
            if (bloque.numero() != numeroEsperado) {
                roturas.add(new Rotura(
                        bloque.numero(),
                        "SECUENCIA",
                        "Falta el bloque " + numeroEsperado + " o el orden esta alterado"));
            }
            if (!bloque.hashAnterior().equals(esperadoAnterior)) {
                roturas.add(new Rotura(bloque.numero(), "HASH_ANTERIOR", "El hash del bloque anterior no coincide"));
            }
            if (bloque.raizRecomputada() != null && !bloque.raizRecomputada().equals(bloque.raizMerkle())) {
                roturas.add(new Rotura(
                        bloque.numero(), "HASH_CONTENIDO", "Los hechos sellados ya no producen la raiz guardada"));
            }
            String recalculado = hashDelBloque(
                    bloque.numero(), bloque.hashAnterior(), bloque.raizMerkle(), bloque.desde(), bloque.hasta());
            if (!recalculado.equals(bloque.hash())) {
                roturas.add(new Rotura(bloque.numero(), "HASH_BLOQUE", "El contenido del bloque no produce su hash"));
            }
            esperadoAnterior = bloque.hash();
            numeroEsperado = bloque.numero() + 1;
        }
        return List.copyOf(roturas);
    }
}
