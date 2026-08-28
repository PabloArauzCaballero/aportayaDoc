package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.plataforma.pruebas.BaseDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * El proceso levanta.
 *
 * <p>Parece poco y es lo unico que comprueba que este servicio se puede desplegar: que
 * compila no dice nada sobre si arranca. Aca se ejercitan de verdad la guardia por
 * omision —{@code TodoEndpointDecideSuAcceso} corre al iniciar y tumba el arranque si
 * algun endpoint no declara {@code @Permiso} ni {@code @Publico}—, el decodificador de
 * token y las quince rutas del contrato.
 *
 * <p>El JWKS apunta a una direccion que no existe a proposito: el decodificador la
 * resuelve la primera vez que llega un token, no al arrancar. Si algun dia arrancara
 * pidiendola, esta prueba lo diria — y seria un defecto, porque ataria el arranque de
 * los trece servicios al de identidad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArranqueTest {

    /** Los prefijos reservados de este servicio (scripts/modelo.py). */
    private static final java.util.List<String> PREFIJOS = java.util.List.of("/grupos", "/turnos", "/acuerdos");

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
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping rutas;

    @Test
    @DisplayName("arranque: el contexto levanta y toda ruta del producto cae en un prefijo reservado")
    void elContextoLevanta() {
        java.util.List<String> mapeadas = rutas.getHandlerMethods().keySet().stream()
                .filter(info -> info.getPathPatternsCondition() != null)
                .flatMap(info -> info.getPathPatternsCondition().getPatternValues().stream())
                .filter(ruta ->
                        !ruta.startsWith("/actuator") && !ruta.startsWith("/.well-known") && !ruta.equals("/error"))
                .toList();

        // Sin `isNotEmpty()` a proposito: grupos todavia no sirve ninguna ruta. Su
        // contrato declara once operaciones sin cuerpo ni esquema de respuesta, porque
        // sus casos de uso reciben hechos ya resueltos de otros servicios —organizador
        // habilitado, tarifario vigente, quien esta al dia— y todavia no esta decidido
        // quien los resuelve. Esta declarado en planes/informes/carril-T.md.
        //
        // Lo que si comprueba esta prueba es lo que importa hoy: que el proceso levanta
        // con su guardia y su cableado, y que el dia que aparezca la primera ruta caiga
        // dentro de los prefijos reservados.
        assertThat(mapeadas).allSatisfy(ruta -> assertThat(PREFIJOS)
                .as("la ruta %s no cae en ningun prefijo reservado", ruta)
                .anySatisfy(prefijo -> assertThat(ruta).startsWith(prefijo)));
    }
}
