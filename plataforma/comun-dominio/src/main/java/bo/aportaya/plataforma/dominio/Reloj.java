package bo.aportaya.plataforma.dominio;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * El reloj se inyecta. Un {@code Instant.now()} dentro de un calculo es una prueba no
 * determinista esperando fecha: el dia que el plazo caiga en el borde, la suite falla
 * en la maquina de otro y nadie sabe por que.
 */
public interface Reloj {

    /** Bolivia no tiene horario de verano, pero el plazo legal se cuenta aca. */
    ZoneId ZONA = ZoneId.of("America/La_Paz");

    Instant ahora();

    default LocalDate hoy() {
        return LocalDate.ofInstant(ahora(), ZONA);
    }

    static Reloj delSistema() {
        Clock reloj = Clock.system(ZONA);
        return reloj::instant;
    }

    /** Para las pruebas: el tiempo se para donde uno lo deja. */
    static Reloj fijo(Instant momento) {
        return () -> momento;
    }
}
