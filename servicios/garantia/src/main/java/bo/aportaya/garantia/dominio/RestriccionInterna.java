package bo.aportaya.garantia.dominio;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Que se le restringe a alguien que dejo una deuda, y por cuanto tiempo.
 *
 * <p>Una restriccion vigente por usuario y tipo (R-GAR-05), y **su levantamiento se
 * motiva**. La restriccion no es un castigo permanente: es una medida con plazo, y
 * quien la levanta antes tiene que dejar escrito por que — sin eso, levantarla se
 * convierte en un favor que nadie puede auditar.
 *
 * <p>La restriccion **no impide pagar la deuda**. Bloquear al deudor de todo, incluido
 * el camino para regularizarse, garantiza que no se regularice.
 */
public final class RestriccionInterna {

    /**
     * Lo que se restringe.
     *
     * <p>Deliberadamente **no incluye pagar ni ver el estado de la deuda**: cerrarle esa
     * puerta a quien debe es asegurarse de que no vuelva.
     */
    public static final Set<String> TIPOS =
            Set.of("CREAR_GRUPO", "UNIRSE_A_GRUPO", "SER_ORGANIZADOR", "SER_AVALISTA", "RETIRAR_SALDO");

    private RestriccionInterna() {}

    public static boolean esTipoValido(String tipo) {
        return TIPOS.contains(tipo);
    }

    /** Si la restriccion sigue vigente en un momento dado. */
    public static boolean vigenteEn(OffsetDateTime desde, OffsetDateTime hasta, OffsetDateTime momento) {
        if (momento.isBefore(desde)) {
            return false;
        }
        // Sin fecha de fin es indefinida: solo se levanta con motivo escrito.
        return hasta == null || momento.isBefore(hasta);
    }

    /**
     * Cuando vence una restriccion de severidad dada.
     *
     * @param duracion sale de la matriz de sanciones, que es catalogo. Devuelve nulo
     *     para las indefinidas, que exigen levantamiento motivado
     */
    public static OffsetDateTime venceEn(OffsetDateTime desde, Duration duracion) {
        return duracion == null ? null : desde.plus(duracion);
    }
}
