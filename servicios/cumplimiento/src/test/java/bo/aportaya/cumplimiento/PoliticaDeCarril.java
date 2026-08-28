package bo.aportaya.cumplimiento;

import bo.aportaya.cumplimiento.dominio.RiesgoDelProducto;
import bo.aportaya.plataforma.dominio.CalendarioHabil;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * La politica con la que se arman los casos de uso en las pruebas.
 *
 * <p>Vive aparte de {@link BaseDeCumplimiento} porque **son cifras de politica, no
 * cableado de pruebas**: en produccion salen de la configuracion del servicio, y
 * tenerlas juntas y con nombre hace evidente cuales son las palancas que el area de
 * cumplimiento puede mover sin desplegar (invariante 10).
 */
final class PoliticaDeCarril {

    /** El techo de trafico que una regla de monitoreo puede marcar antes de saturar. */
    static final BigDecimal TECHO_DE_TRAFICO = new BigDecimal("0.0500");

    /** Donde empieza cada nivel de riesgo de producto, sobre probabilidad x impacto. */
    static final RiesgoDelProducto.Escala ESCALA_DE_RIESGO_DE_PRODUCTO = new RiesgoDelProducto.Escala(15, 8);

    /** Dias habiles de primera respuesta a un reclamo (ASFI Libro 4 Titulo I). */
    static final int DIAS_DE_RESPUESTA = 5;

    /** Tope de la prorroga, medido desde el ingreso. */
    static final int MAXIMO_DIAS_DE_PRORROGA = 10;

    /**
     * Todos los dias son habiles en las pruebas.
     *
     * <p>El calendario real es catalogo sembrado y se prueba en CU-59: mezclarlo aca
     * haria que estas pruebas fallaran cada vez que cambia un feriado, que es
     * exactamente el ruido que hace que nadie las mire.
     */
    static final CalendarioHabil TODOS_HABILES = fecha -> false;

    /** Roles operativos incompatibles con ser oficial de cumplimiento (R-SEG-04). */
    static final Set<String> ROLES_INCOMPATIBLES = Set.of("TESORERIA", "OPERACIONES", "COMERCIAL");

    /** Plazo de investigacion de un caso segun su severidad (R-UIF-08). */
    static final Map<String, Duration> PLAZOS_DE_CASO = Map.of(
            "CRITICA", Duration.ofDays(5),
            "ALTA", Duration.ofDays(15),
            "MEDIA", Duration.ofDays(30),
            "BAJA", Duration.ofDays(45));

    private PoliticaDeCarril() {}
}
