package bo.aportaya.auditoria.infraestructura;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Corre la {@code consulta_base} de un reporte.
 *
 * <p>Una sola cosa importa en esta clase, y es la que decide si el reporte es una
 * herramienta o un agujero: <b>el valor del parametro nunca entra en el texto de la
 * consulta</b>. Los marcadores {@code :nombre} se reemplazan por {@code ?} y los
 * valores viajan como ligaduras. Concatenar aunque sea una vez —«total, es una fecha
 * que ya validamos»— es como se escriben las inyecciones: la validacion siempre deja
 * pasar un caso, la ligadura no deja pasar ninguno.
 *
 * <p>Y corre con la sesion del solicitante, sobre el {@code DSLContext} de la
 * transaccion en curso, de modo que las politicas de fila siguen rigiendo
 * (`R-SEG-03`). <b>Un reporte no es una puerta trasera al RLS</b>: si lo fuera, bastaria
 * definir un reporte para leer lo que la politica niega.
 */
@Component
public class EjecutorDeConsulta {

    /** {@code :nombre} — el marcador que usan las definiciones sembradas. */
    private static final Pattern MARCADOR = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    /**
     * Solo lectura, y verificado antes de correr. La definicion vive en una tabla que
     * un operador puede editar; si esa tabla admitiera un {@code DELETE}, el circuito
     * de reportes seria una via para escribir con la sesion de cualquiera que ejecute.
     */
    private static final Pattern SOLO_LECTURA = Pattern.compile("^\\s*(select|with)\\b", Pattern.CASE_INSENSITIVE);

    private final int segundosDeTope;

    public EjecutorDeConsulta(@Value("${auditoria.reportes.tope-de-segundos:30}") int segundosDeTope) {
        this.segundosDeTope = segundosDeTope;
    }

    /** El resultado: las filas ya como cadenas, y cuanto tardo. */
    public record Resultado(List<List<String>> filas, int duracionMs) {}

    /**
     * @throws ConsultaNoEsDeLectura si la definicion trae algo que no es una lectura
     */
    public Resultado correr(DSLContext dsl, String consultaBase, Map<String, String> parametros) {
        if (!SOLO_LECTURA.matcher(consultaBase).find()) {
            throw new ConsultaNoEsDeLectura();
        }

        List<Object> ligaduras = new ArrayList<>();
        String sql = ligar(consultaBase, parametros, ligaduras);

        long comienzo = System.nanoTime();
        Result<Record> filas = dsl.resultQuery(sql, ligaduras.toArray())
                // El tope no es una cortesia: sin el, un reporte mal acotado deja una
                // consulta corriendo y la base de produccion se cuelga por un pedido
                // que a nadie le urgia (AP-CU58-04).
                .queryTimeout(segundosDeTope)
                .fetch();
        int duracionMs = (int) ((System.nanoTime() - comienzo) / 1_000_000L);

        return new Resultado(comoCadenas(filas), duracionMs);
    }

    /**
     * Reemplaza cada {@code :nombre} por {@code ?} y apila su valor.
     *
     * <p>El mismo parametro puede aparecer varias veces —un rango que se compara dos
     * veces— y cada aparicion apila su propia ligadura: JDBC posiciona por orden, no
     * por nombre.
     */
    private static String ligar(String consulta, Map<String, String> parametros, List<Object> ligaduras) {
        Matcher marcador = MARCADOR.matcher(consulta);
        StringBuilder sql = new StringBuilder();
        while (marcador.find()) {
            String nombre = marcador.group(1);
            if (!parametros.containsKey(nombre)) {
                // No se completa con nulo: una consulta a la que le falta un parametro
                // devuelve un resultado que parece valido y no lo es. Que falle.
                throw new ParametroSinValor(nombre);
            }
            ligaduras.add(parametros.get(nombre));
            marcador.appendReplacement(sql, "?");
        }
        marcador.appendTail(sql);
        return sql.toString();
    }

    /**
     * Todo sale como cadena.
     *
     * <p>No es pereza: es lo que hace estable la huella. Si cada tipo se serializara a
     * su manera, el mismo resultado daria hashes distintos segun el driver, y
     * {@code hash_resultado} dejaria de probar nada. {@code toString} de
     * {@code BigDecimal} conserva la escala, que es lo que importa en una columna de
     * dinero.
     */
    private static List<List<String>> comoCadenas(Result<Record> filas) {
        List<List<String>> salida = new ArrayList<>(filas.size());
        for (Record fila : filas) {
            List<String> campos = new ArrayList<>(fila.size());
            for (int i = 0; i < fila.size(); i++) {
                Object valor = fila.get(i);
                campos.add(valor == null ? "" : String.valueOf(valor));
            }
            salida.add(List.copyOf(campos));
        }
        return List.copyOf(salida);
    }

    /** La definicion trae algo que no es una lectura. */
    public static class ConsultaNoEsDeLectura extends RuntimeException {
        public ConsultaNoEsDeLectura() {
            super("La consulta base de un reporte solo puede ser una lectura.");
        }
    }

    /** La consulta pide un parametro que la ejecucion no trajo. */
    public static class ParametroSinValor extends RuntimeException {
        public ParametroSinValor(String nombre) {
            super("La consulta necesita el parametro '%s' y no llego.".formatted(nombre.toLowerCase(Locale.ROOT)));
        }
    }
}
