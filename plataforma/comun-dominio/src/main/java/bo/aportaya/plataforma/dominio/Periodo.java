package bo.aportaya.plataforma.dominio;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Un rango de fechas cerrado en los dos extremos.
 *
 * <p>El solape se pregunta aca y no en cada consulta: el modelo lo hace cumplir con
 * restricciones {@code EXCLUDE} y {@code btree_gist}, y esta clase es la que permite
 * llegar a la base sabiendo la respuesta en vez de descubrirla por rechazo.
 */
public record Periodo(LocalDate inicio, LocalDate fin) {

    public Periodo {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fin, "fin");
        if (fin.isBefore(inicio)) {
            throw new ErrorDeDominio("Un periodo no termina antes de empezar: %s a %s".formatted(inicio, fin));
        }
    }

    public static Periodo de(LocalDate inicio, LocalDate fin) {
        return new Periodo(inicio, fin);
    }

    public boolean contiene(LocalDate fecha) {
        Objects.requireNonNull(fecha, "fecha");
        return !fecha.isBefore(inicio) && !fecha.isAfter(fin);
    }

    public boolean seSolapaCon(Periodo otro) {
        Objects.requireNonNull(otro, "otro periodo");
        return !inicio.isAfter(otro.fin) && !otro.inicio.isAfter(fin);
    }

    /** Dias calendario, contando los dos extremos. */
    public long dias() {
        return ChronoUnit.DAYS.between(inicio, fin) + 1;
    }
}
