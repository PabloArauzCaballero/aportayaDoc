package bo.aportaya.plataforma.dominio;

import java.util.Objects;

/**
 * El identificador que atraviesa los catorce servicios y llega hasta el consumidor de
 * Kafka.
 *
 * <p>Es lo que convierte «algo fallo en produccion» en «fallo esta operacion, de este
 * usuario, en este caso de uso». Sin el, catorce procesos escriben catorce registros
 * que nadie puede coser.
 */
public record Traza(String id) {

    public Traza {
        Objects.requireNonNull(id, "id de traza");
        if (id.isBlank()) {
            throw new ErrorDeDominio("Una traza en blanco no traza nada");
        }
    }

    public static Traza nueva(Ids ids) {
        return new Traza(ids.nuevo().toString());
    }

    @Override
    public String toString() {
        return id;
    }
}
