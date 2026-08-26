package bo.aportaya.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Unica entrada publica detras de NGINX.
 *
 * <p>No compone respuestas, no traduce errores y no consulta la base: un gateway con
 * logica es el monolito volviendo por la puerta de atras, y ademas compartido por
 * catorce carriles. Lo unico que hace es enrutar por prefijo, cortar por tasa y
 * propagar {@code x-request-id}.
 */
@SpringBootApplication
public class Aplicacion {

    public static void main(String[] argumentos) {
        SpringApplication.run(Aplicacion.class, argumentos);
    }
}
