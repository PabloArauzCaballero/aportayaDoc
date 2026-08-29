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
 * **Se puede entrar al sistema.**
 *
 * <p>Esta prueba existe porque durante un tiempo no se podia. La guardia abria cuatro
 * patrones escritos a mano —las sondas, el JWKS y {@code /publico}— y {@code @Publico}
 * solo se comprobaba al arrancar. El registro (CU-01) y el ingreso (CU-04) estaban
 * anotados y aun asi devolvian 401: **no habia forma de conseguir un token**, y por lo
 * tanto no habia forma de usar ninguna de las 151 operaciones. Todo compilaba, todas
 * las pruebas de caso de uso pasaban, y el sistema entero era inalcanzable desde
 * afuera.
 *
 * <p>Se descubrio armando la coleccion de Postman, que es lo primero que ejercito el
 * sistema por HTTP de punta a punta.
 *
 * <p>Las dos mitades importan igual. Que lo publico abra **y** que lo demas siga
 * cerrado: una guardia que se arregla abriendo todo no es una guardia.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArranquePuertaDeEntradaTest {

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

    private HttpEntity<String> cuerpo(String json) {
        var cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        cabeceras.set("Idempotency-Key", java.util.UUID.randomUUID().toString());
        return new HttpEntity<>(json, cabeceras);
    }

    @Test
    @DisplayName("seguridad: el registro (CU-01) es alcanzable sin token")
    void elRegistroNoPideSesion() {
        // El cuerpo esta incompleto a proposito: lo que se comprueba es que la
        // peticion LLEGA al controlador. Un 400 o un 422 significa que llego y que
        // el contrato la valido; un 401 significaria que la guardia la freno antes.
        var respuesta = cliente.exchange(
                "/usuarios", HttpMethod.POST, cuerpo("{\"telefonoE164\":\"+59178123456\"}"), String.class);

        assertThat(respuesta.getStatusCode())
                .as("el registro es la unica puerta de alta y no puede pedir la sesion que todavia no existe")
                .isNotEqualTo(HttpStatus.UNAUTHORIZED)
                .isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("seguridad: el ingreso (CU-04) es alcanzable sin token")
    void elIngresoNoPideSesion() {
        var respuesta = cliente.exchange(
                "/sesiones",
                HttpMethod.POST,
                cuerpo("{\"telefonoE164\":\"+59171000001\",\"huellaDispositivo\":\"prueba\"}"),
                String.class);

        // Un 401 aca podria ser legitimo —credencial invalida— pero no puede venir de
        // la guardia. Se distingue por el cuerpo: el rechazo del caso de uso trae su
        // codigo AP-CU; el de la guardia viene vacio.
        if (respuesta.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            assertThat(respuesta.getBody())
                    .as("la guardia freno el ingreso antes de que el caso de uso decidiera")
                    .isNotNull()
                    .contains("AP-CU");
        }
    }

    @Test
    @DisplayName("seguridad: el JWKS se sirve sin token, o nadie puede validar ninguna firma")
    void elJwksNoPideSesion() {
        var respuesta = cliente.getForEntity("/.well-known/jwks.json", String.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).contains("keys");
        // La clave PUBLICA sale; la privada no sale nunca de identidad.
        assertThat(respuesta.getBody())
                .as("la clave privada no puede salir del emisor")
                .doesNotContain("\"d\"")
                .doesNotContain("\"p\"");
    }

    @Test
    @DisplayName("seguridad: una ruta con @Permiso sigue cerrada sin token")
    void loDemasSigueCerrado() {
        var respuesta = cliente.getForEntity("/usuarios/por-telefono?telefono=%2B59171000001", String.class);

        // La otra mitad del arreglo. Abrir lo publico no puede abrir lo demas:
        // denegar por omision es el invariante 9.
        assertThat(respuesta.getStatusCode())
                .as("una ruta con permiso declarado contesto sin sesion")
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
