package bo.aportaya.plataforma.web.errores;

import bo.aportaya.plataforma.dominio.ErrorDeDominio;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.SinContextoDeSesion;
import bo.aportaya.plataforma.web.idempotencia.OperacionRepetida;
import bo.aportaya.plataforma.web.traza.Traza;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce, y no deja pasar nada crudo.
 *
 * <p>El mapeo es el de {@code errores-api}: {@code 400} para el esquema,
 * {@code 422} para la regla de negocio, {@code 409} para lo que rechaza la base,
 * {@code 403} sin permiso y {@code 500} con **solo** la traza.
 *
 * <p>Una restriccion que dispara y no esta en el catalogo NO se convierte en un
 * mensaje generico: es un caso que nadie previo, y sale como {@code 500} con alerta.
 * Improvisar ahi un texto amable es esconder un defecto.
 */
@RestControllerAdvice
public class ManejadorGlobalDeErrores {

    private static final Logger BITACORA = LoggerFactory.getLogger(ManejadorGlobalDeErrores.class);

    private final TraduccionDeRestricciones traduccion;

    public ManejadorGlobalDeErrores(TraduccionDeRestricciones traduccion) {
        this.traduccion = traduccion;
    }

    /** Regla del caso de uso: 422 con su AP-CU<NN>-<nn>. */
    @ExceptionHandler(ErrorDeNegocio.class)
    public ResponseEntity<ErrorApi> reglaDeNegocio(ErrorDeNegocio e) {
        BITACORA.info("regla de negocio {}: {}", e.codigo(), e.getMessage());
        return ResponseEntity.unprocessableEntity()
                .body(new ErrorApi(e.codigo().valor(), e.getMessage(), e.detalle(), Traza.actual()));
    }

    /**
     * Clave de idempotencia repetida: 200 con LA RESPUESTA ORIGINAL, integra.
     *
     * <p>No es un 409: el cliente hizo lo correcto al reintentar, y la red hizo lo
     * que hace. Devolverle un conflicto lo obligaria a distinguir un fallo real de
     * un duplicado, que es justo lo que la clave existe para ahorrarle.
     */
    @ExceptionHandler(OperacionRepetida.class)
    public ResponseEntity<String> operacionRepetida(OperacionRepetida e) {
        return ResponseEntity.status(e.codigoHttp())
                .header("Content-Type", "application/json")
                .body(e.cuerpo());
    }

    @ExceptionHandler(SinContextoDeSesion.class)
    public ResponseEntity<ErrorApi> sinSesion(SinContextoDeSesion e) {
        BITACORA.warn("consulta sin contexto de sesion");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorApi.de("AP-SES-01", "Inicia sesion para continuar.", Traza.actual()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorApi> sinPermiso(AccessDeniedException e) {
        // Sin detalles: decir por que se niega ya es contar que el recurso existe.
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorApi.de("AP-SEG-01", "No tenes acceso a este recurso.", Traza.actual()));
    }

    /** Lo que rechaza la BASE: 409 con la regla traducida. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorApi> restriccionDeLaBase(DataAccessException e) {
        String mensajeCrudo = raiz(e).getMessage();
        return traduccion
                .traducir(mensajeCrudo)
                .map(regla -> {
                    BITACORA.info("restriccion {} rechazo la escritura", regla.codigo());
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(ErrorApi.de(regla.codigo(), regla.mensaje() + ".", Traza.actual()));
                })
                .orElseGet(() -> {
                    BITACORA.error("restriccion sin traduccion en el catalogo: {}", mensajeCrudo, e);
                    return sinPrevision();
                });
    }

    /** Entrada invalida por el contrato: 400 con la lista de campos. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorApi> entradaInvalida(MethodArgumentNotValidException e) {
        Map<String, Object> campos = new LinkedHashMap<>();
        e.getBindingResult()
                .getFieldErrors()
                .forEach(error -> campos.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ErrorApi("AP-VAL-01", "Revisa los datos del formulario.", campos, Traza.actual()));
    }

    /**
     * Lo que el cliente mando mal, y **no es un fallo del servidor**.
     *
     * <p>Un JSON roto, una fecha que no es una fecha, un UUID que no es un UUID, un
     * parametro que falta. Sin esto todo eso caia en {@code falloNoPrevisto} y salia
     * como {@code 500}: se descubrio corriendo la coleccion de humo, mandando una
     * {@code fechaNacimiento} invalida al registro.
     *
     * <p>Importa por tres cosas distintas. Al cliente le decimos que se equivoco el, no
     * nosotros. La bitacora deja de llenarse de ERROR por peticiones malformadas, que
     * es como una alerta real se pierde entre el ruido. Y cualquiera que mande basura a
     * proposito deja de poder simular una caida.
     *
     * <p>El detalle es corto a proposito: **no se devuelve el mensaje del parser**.
     * Trae nombres de clases y de campos internos, y eso es contarle al que prueba como
     * esta hecho el servidor por dentro.
     */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        HandlerMethodValidationException.class
    })
    public ResponseEntity<ErrorApi> entradaMalFormada(Exception e) {
        BITACORA.info("entrada mal formada: {}", e.getClass().getSimpleName());
        return ResponseEntity.badRequest()
                .body(ErrorApi.de("AP-VAL-02", "Revisa el formato de los datos enviados.", Traza.actual()));
    }

    /** El verbo no existe para esa ruta. Es 405, no 500 ni 404. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorApi> metodoNoSoportado(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ErrorApi.de("AP-VAL-03", "Ese metodo no aplica a esta ruta.", Traza.actual()));
    }

    /** El cuerpo vino en un formato que no servimos. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorApi> tipoNoSoportado(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorApi.de("AP-VAL-04", "El cuerpo tiene que ser application/json.", Traza.actual()));
    }

    /**
     * La ruta no existe. Sale con el mismo cuerpo que todo lo demas.
     *
     * <p>Un 404 con la pagina de Tomcat cuenta la version del servidor y que hay un
     * Tomcat. No es grave por si solo; es un dato gratis para quien esta mirando.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorApi> rutaInexistente(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorApi.de("AP-VAL-05", "Esa ruta no existe.", Traza.actual()));
    }

    @ExceptionHandler(ErrorDeDominio.class)
    public ResponseEntity<ErrorApi> reglaDelDominio(ErrorDeDominio e) {
        return ResponseEntity.unprocessableEntity().body(ErrorApi.de("AP-DOM-01", e.getMessage(), Traza.actual()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorApi> falloNoPrevisto(Exception e) {
        BITACORA.error("fallo no previsto", e);
        return sinPrevision();
    }

    /** Solo la traza. Nada mas: el error no ensena la arquitectura. */
    private ResponseEntity<ErrorApi> sinPrevision() {
        return ResponseEntity.internalServerError()
                .body(ErrorApi.de("AP-INT-01", "No pudimos completar la operacion. Volve a intentar.", Traza.actual()));
    }

    private Throwable raiz(Throwable e) {
        Throwable actual = e;
        while (actual.getCause() != null && actual.getCause() != actual) {
            actual = actual.getCause();
        }
        return actual;
    }
}
