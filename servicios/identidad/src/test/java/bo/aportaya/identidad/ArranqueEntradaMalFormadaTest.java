package bo.aportaya.identidad;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * **Lo que el cliente manda mal es culpa del cliente, y se responde como tal.**
 *
 * <p>Antes no era asi: una {@code fechaNacimiento} que no era una fecha caia en el
 * manejador de ultimo recurso y salia como {@code 500}. Se descubrio corriendo la
 * coleccion de humo —el caso «limite» del registro— y vale por tres razones que no son
 * la misma:
 *
 * <ol>
 *   <li>Un {@code 500} le dice al cliente «se rompio el servidor» cuando lo que paso es
 *       que mando una fecha invalida. Es informacion falsa.
 *   <li>Cada peticion malformada escribia una linea ERROR en la bitacora. Asi es como
 *       una alerta de verdad se pierde entre el ruido de gente equivocandose.
 *   <li>Cualquiera puede simular una caida mandando basura, y los tableros la creen.
 * </ol>
 *
 * <p>Se prueba sobre {@code POST /usuarios} porque es publica: no hace falta sesion
 * para llegar al fallo, que es exactamente lo que lo hacia util para quien lo quisiera
 * usar mal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArranqueEntradaMalFormadaTest {

    @DynamicPropertySource
    static void configuracion(DynamicPropertyRegistry registro) {
        var contenedor = BaseDePrueba.contenedor();
        registro.add("spring.datasource.url", contenedor::getJdbcUrl);
        registro.add("spring.datasource.username", contenedor::getUsername);
        registro.add("spring.datasource.password", contenedor::getPassword);
        registro.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registro.add("aportaya.jwt.jwks-uri", () -> "http://identidad:8080/.well-known/jwks.json");
        registro.add("SEGURIDAD_PIMIENTA", () -> "pimienta-de-prueba");
    }

    @Autowired
    private TestRestTemplate cliente;

    private HttpEntity<String> json(String cuerpo) {
        var cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        cabeceras.set("Idempotency-Key", java.util.UUID.randomUUID().toString());
        return new HttpEntity<>(cuerpo, cabeceras);
    }

    private org.springframework.http.ResponseEntity<String> registrar(String cuerpo) {
        return cliente.exchange("/usuarios", HttpMethod.POST, json(cuerpo), String.class);
    }

    @Test
    @DisplayName("entrada: una fecha que no es una fecha da 400, no 500")
    void laFechaInvalidaNoEsUnFalloDelServidor() {
        var respuesta = registrar(
                """
                {"telefonoE164":"+59178123456","nombres":"Rosa","apellidos":"Mamani",
                 "fechaNacimiento":"no-soy-una-fecha",
                 "documento":{"tipo":"CI","numero":"9988776"},
                 "aceptaContratos":["00000000-0000-0000-0000-000000000001"]}
                """);

        assertThat(respuesta.getStatusCode())
                .as("el cliente mando mal una fecha; eso no es una caida del servidor")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("entrada: un JSON roto da 400, no 500")
    void elJsonRotoNoEsUnFalloDelServidor() {
        assertThat(registrar("{esto no es json").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("entrada: un UUID que no es un UUID da 400, no 500")
    void elUuidInvalidoNoEsUnFalloDelServidor() {
        var respuesta = registrar(
                """
                {"telefonoE164":"+59178123456","nombres":"Rosa","apellidos":"Mamani",
                 "fechaNacimiento":"1990-05-12",
                 "documento":{"tipo":"CI","numero":"9988776"},
                 "aceptaContratos":["no-soy-un-uuid"]}
                """);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("entrada: a quien no tiene sesion no se le dice que rutas existen")
    void elVerboEquivocadoNoRevelaElMapa() {
        // `GET /usuarios` no esta mapeado —solo POST— y la respuesta es **401, no 405**.
        //
        // Se comprobo contra el proceso levantado, y esta bien que sea asi: un 405
        // le confirma a quien prueba que la ruta existe y que el verbo era otro, y un
        // 404 le confirma que no existe. Las dos cosas son un mapa gratis del sistema
        // para alguien sin sesion. Un 401 uniforme no le dice nada.
        //
        // Los codigos 405 y 404 del manejador global siguen valiendo para quien SI
        // trae sesion, que es donde sirven: ahi la informacion ya no es un regalo.
        assertThat(cliente.getForEntity("/usuarios", String.class).getStatusCode())
                .as("un anonimo no puede deducir que rutas hay probando verbos")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("entrada: el 400 no cuenta como esta hecho el servidor por dentro")
    void elRechazoNoFiltraLaArquitectura() {
        String cuerpo = registrar("{esto no es json").getBody();

        assertThat(cuerpo).isNotNull();
        // El mensaje del parser trae nombres de clases y de campos internos. Es un dato
        // gratis para quien esta probando por donde entrar.
        assertThat(cuerpo)
                .doesNotContain("com.fasterxml")
                .doesNotContain("bo.aportaya")
                .doesNotContain("java.lang")
                .doesNotContain("JsonParseException");
    }
}
