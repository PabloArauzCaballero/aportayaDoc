package bo.aportaya.cumplimiento.dominio;

import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeDominio;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * La taxonomia del riesgo operativo, y las tres cosas que tienen que cuadrar.
 *
 * <p>Las seis categorias y los cinco factores no son una lista de estilo: los fija la
 * norma, y son <b>lo que permite comparar la base de perdidas con la de cualquier otra
 * entidad</b>. Una septima categoria «otros» convertiria el registro en un cajon: todo
 * lo incomodo termina ahi y el analisis por factor deja de decir nada.
 *
 * <p>Es un atomo puro. Sin Spring, sin jOOQ y sin reloj: dadas las mismas entradas
 * decide siempre lo mismo, que es lo que permite probar la clasificacion sin base.
 */
public final class ClasificacionDeEvento {

    /** Las seis de `ck_evento_categoria`. Se escriben aca para poder rechazar antes. */
    private static final Set<String> CATEGORIAS = Set.of(
            "FRAUDE_INTERNO",
            "FRAUDE_EXTERNO",
            "RELACIONES_LABORALES",
            "CLIENTES_PRODUCTOS_PRACTICAS",
            "DANOS_ACTIVOS",
            "FALLAS_SISTEMAS");

    /** Los cinco de `ck_evento_factor`. */
    private static final Set<String> FACTORES =
            Set.of("PROCESOS_INTERNOS", "PERSONAS", "TECNOLOGIA_INFORMACION", "EVENTOS_EXTERNOS", "INFRAESTRUCTURA");

    private ClasificacionDeEvento() {}

    /** Lo que quedo clasificado, con la perdida neta ya derivada. */
    public record Clasificado(String categoria, String factor, Dinero perdidaNeta) {}

    /**
     * @throws TaxonomiaInvalida si la categoria o el factor no son de la norma
     * @throws FechasIncoherentes si la deteccion es anterior a la ocurrencia
     * @throws RecuperacionExcesiva si se recupera mas de lo que se perdio
     */
    public static Clasificado clasificar(
            String categoria,
            String factor,
            OffsetDateTime ocurrencia,
            OffsetDateTime deteccion,
            Dinero perdidaBruta,
            Dinero recuperacion) {

        String categoriaNormal = normalizar(categoria);
        String factorNormal = normalizar(factor);
        if (!CATEGORIAS.contains(categoriaNormal)) {
            throw new TaxonomiaInvalida("categoria", categoria);
        }
        if (!FACTORES.contains(factorNormal)) {
            throw new TaxonomiaInvalida("factor", factor);
        }

        // La deteccion nunca precede a la ocurrencia. Suena obvio; en la practica es
        // el error de carga mas comun, y una fecha invertida rompe el indicador que de
        // verdad importa: cuanto tardamos en darnos cuenta.
        if (deteccion.isBefore(ocurrencia)) {
            throw new FechasIncoherentes();
        }

        // La recuperacion no puede superar la perdida. Si la supera, o se cargo dos
        // veces o se esta contando un ingreso como recuperacion; en cualquiera de los
        // dos casos la base de perdidas quedaria mostrando una ganancia.
        if (recuperacion.compareTo(perdidaBruta) > 0) {
            throw new RecuperacionExcesiva();
        }

        // Neta = bruta - recuperacion. La columna es GENERATED en la base y este
        // calculo NO la reemplaza: la devuelve para poder responder sin releer. La
        // verdad sigue siendo la del motor (`R-RIS-02`).
        return new Clasificado(categoriaNormal, factorNormal, perdidaBruta.menos(recuperacion));
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }

    /** Categoria o factor fuera de la taxonomia de la norma (`R-RIS-01`). */
    public static class TaxonomiaInvalida extends ErrorDeDominio {
        public TaxonomiaInvalida(String campo, String valor) {
            super("El %s '%s' no esta en la taxonomia de la norma.".formatted(campo, valor));
        }
    }

    /** La deteccion no puede ser anterior a la ocurrencia. */
    public static class FechasIncoherentes extends ErrorDeDominio {
        public FechasIncoherentes() {
            super("La deteccion no puede ser anterior a la ocurrencia.");
        }
    }

    /** La recuperacion no puede superar la perdida bruta. */
    public static class RecuperacionExcesiva extends ErrorDeDominio {
        public RecuperacionExcesiva() {
            super("La recuperacion no puede superar la perdida bruta: quedaria una perdida negativa.");
        }
    }
}
