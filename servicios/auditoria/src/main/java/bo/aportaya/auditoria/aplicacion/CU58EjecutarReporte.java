package bo.aportaya.auditoria.aplicacion;

import bo.aportaya.auditoria.dominio.HuellaDeResultado;
import bo.aportaya.auditoria.dominio.ParametrosDeReporte;
import bo.aportaya.auditoria.infraestructura.EjecutorDeConsulta;
import bo.aportaya.auditoria.infraestructura.ReporteRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.datos.TransaccionAparte;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeDominio;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-58 · Definir, programar y exportar un reporte.
 *
 * <p>Que sacar informacion del sistema sea una operacion <b>con permiso, huella y
 * vencimiento</b> — y no una consulta suelta que alguien corre contra produccion y
 * manda por correo.
 *
 * <p>Tres decisiones que este caso de uso toma y que casi nunca se toman:
 *
 * <ul>
 *   <li><b>Los parametros van por lista blanca.</b> Lo que no esta declarado no entra,
 *       y el valor viaja como ligadura, nunca concatenado.
 *   <li><b>El resultado deja huella.</b> Dos ejecuciones con los mismos parametros y
 *       los mismos datos dan el mismo hash: es como se prueba que el archivo que
 *       alguien presenta es el que salio de aca.
 *   <li><b>La exportacion caduca.</b> Un enlace eterno a un archivo con datos
 *       personales es una fuga futura con fecha abierta.
 * </ul>
 *
 * <p><b>Divergencia con el texto del CU, resuelta a favor de la tabla.</b> El contrato
 * escrito en CU-58 enumera el estado {@code LISTA}; el CHECK de
 * {@code ejecucion_reporte} admite {@code COMPLETADA}. Manda la tabla, que es la que
 * rechaza.
 */
@Service
public class CU58EjecutarReporte {

    /** El estado terminal exitoso, tal como lo nombra {@code ck_ejecucion_reporte_estado}. */
    private static final String COMPLETADA = "COMPLETADA";

    private final Datos datos;
    private final ReporteRepositorio reportes;
    private final EjecutorDeConsulta ejecutor;
    private final TransaccionAparte aparte;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int horasDeVigencia;

    public CU58EjecutarReporte(
            Datos datos,
            ReporteRepositorio reportes,
            EjecutorDeConsulta ejecutor,
            TransaccionAparte aparte,
            Outbox outbox,
            Reloj reloj,
            @Value("${auditoria.reportes.horas-de-vigencia:72}") int horasDeVigencia) {
        this.datos = datos;
        this.reportes = reportes;
        this.ejecutor = ejecutor;
        this.aparte = aparte;
        this.outbox = outbox;
        this.reloj = reloj;
        this.horasDeVigencia = horasDeVigencia;
    }

    /**
     * Ejecuta el reporte y, si se pidio formato, lo exporta.
     *
     * <p>Todo ocurre con la sesion del solicitante para que las politicas de fila sigan
     * rigiendo (`R-SEG-03`): <b>un reporte no es una puerta trasera al RLS</b>.
     */
    @Transactional
    public SalidaReporte ejecutar(EntradaReporte entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var definicion = reportes.definicion(dsl, entrada.definicionId())
                    .filter(ReporteRepositorio.Definicion::activa)
                    .orElseThrow(() ->
                            new ErrorDeNegocio(CodigoError.de(58, 1), "Ese reporte no existe o esta dado de baja."));

            // AP-CU58-01. El permiso lo decide la definicion, no el llamador.
            if (!entrada.permisos().contains(definicion.permisoRequerido())) {
                // «Queda registrado el intento» — y por eso se escribe EN OTRA
                // TRANSACCION. Escribirlo en esta y despues lanzar el error de negocio
                // haria que el ROLLBACK se llevara la constancia: el rechazo quedaria
                // registrado en ningun lado, que es lo contrario de lo que pide el
                // criterio. Quien pide lo que no puede ver es informacion de seguridad,
                // no ruido, y un log de aplicacion no sobrevive a la rotacion.
                anotarElRechazo(entrada, ctx, definicion, "SIN_PERMISO: falta " + definicion.permisoRequerido(), ahora);
                throw new ErrorDeNegocio(
                        CodigoError.de(58, 1),
                        "Necesitas el permiso " + definicion.permisoRequerido() + " para ese reporte.");
            }

            // AP-CU58-03. Con datos sensibles, sin justificacion no se ejecuta: un
            // registro de acceso sin motivo no sirve para auditar. Al auditar la
            // pregunta no es quien miro sino por que (`R-SEG-02`), y la base exige la
            // respuesta en `ck_acceso_justificacion`.
            String justificacion = entrada.justificacion().map(String::trim).orElse("");
            if (definicion.contieneDatosSensibles() && justificacion.isBlank()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(58, 3),
                        "Ese reporte tiene datos sensibles: hace falta justificar, y con algo que se pueda leer"
                                + " dentro de un ano.");
            }
            // El LARGO minimo no se repite aca. Vive en dos lugares que ya lo hacen
            // cumplir —`minLength` del contrato, en el borde, y `ck_acceso_justificacion`
            // en la base, abajo— y copiarlo a un tercero solo garantiza que algun dia
            // los tres digan cosas distintas.

            // AP-CU58-02. Lista blanca: lo no declarado no entra, y nada se concatena.
            try {
                ParametrosDeReporte.validar(definicion.esperados(), entrada.parametros());
            } catch (ErrorDeDominio invalido) {
                throw new ErrorDeNegocio(CodigoError.de(58, 2), invalido.getMessage());
            }

            UUID ejecucionId =
                    reportes.abrirEjecucion(dsl, definicion.id(), ctx.usuarioId(), entrada.parametros(), ahora);

            EjecutorDeConsulta.Resultado resultado;
            try {
                resultado = ejecutor.correr(dsl, definicion.consultaBase(), entrada.parametros());
            } catch (org.jooq.exception.DataAccessException seCorto) {
                // AP-CU58-04. Tambien aparte, y por una razon mas dura que la anterior:
                // una consulta cortada por tiempo deja la transaccion ABORTADA, y en una
                // transaccion abortada PostgreSQL no acepta ni un INSERT. El rastro del
                // reporte que hay que acotar solo se puede escribir desde afuera.
                anotarElRechazo(entrada, ctx, definicion, "TIEMPO_EXCEDIDO", ahora);
                throw new ErrorDeNegocio(
                        CodigoError.de(58, 4),
                        "La consulta tardo mas de lo permitido y se cancelo: acota el rango y volve a pedirla.");
            }

            List<List<String>> filas = resultado.filas();
            String huella = HuellaDeResultado.de(filas);
            reportes.cerrarEjecucion(dsl, ejecucionId, COMPLETADA, filas.size(), resultado.duracionMs(), huella, ahora);

            // El reporte vacio se entrega igual, con cero filas: «no hubo» es una
            // respuesta, y para los regulatorios es la respuesta obligatoria
            // (`R-UIF-06`). Devolver un error por lista vacia obligaria a quien reporta
            // a distinguir «no hubo» de «fallo», y ahi es donde se pierde el envio.

            Optional<UUID> exportacionId = entrada.formato().map(formato -> {
                // Con datos sensibles el archivo va SIEMPRE cifrado y SIEMPRE con
                // caducidad. No es una opcion del solicitante: quien exporta no decide
                // el nivel de proteccion de datos que no son suyos.
                boolean cifrado = definicion.contieneDatosSensibles();
                return reportes.exportar(
                        dsl,
                        ejecucionId,
                        formato,
                        "reportes/" + ejecucionId + "." + formato.toLowerCase(Locale.ROOT),
                        huella,
                        (long) filas.size() * BYTES_POR_FILA,
                        cifrado,
                        ahora.plusHours(horasDeVigencia),
                        ahora);
            });

            if (definicion.contieneDatosSensibles()) {
                registrarElAcceso(dsl, entrada, ctx, justificacion, filas.size(), ahora);
            }

            outbox.emitir(dsl, evento("auditoria.reporte_ejecutado", ejecucionId, definicion.nombre(), ctx));

            return new SalidaReporte(ejecucionId, COMPLETADA, filas.size(), huella, exportacionId);
        });
    }

    /**
     * Anota el intento fallido en una transaccion propia, que confirma aunque la del
     * caso de uso se revierta.
     *
     * <p>El evento va con la constancia y no con el caso de uso por lo mismo: el outbox
     * escribe en la transaccion en curso, y esa transaccion es la que se va a revertir.
     */
    private void anotarElRechazo(
            EntradaReporte entrada,
            ContextoSesion ctx,
            ReporteRepositorio.Definicion definicion,
            String motivo,
            OffsetDateTime ahora) {
        aparte.en(() -> datos.conContexto(ctx, otro -> {
            UUID intento = reportes.registrarIntentoFallido(
                    otro, definicion.id(), ctx.usuarioId(), entrada.parametros(), motivo, ahora);
            outbox.emitir(otro, evento("auditoria.reporte_denegado", intento, definicion.nombre(), ctx));
            return intento;
        }));
    }

    /**
     * Deja el rastro de `R-SEG-02` en {@code comun.registro_acceso_datos}.
     *
     * <p><b>Hueco declarado.</b> Esa tabla exige {@code usuario_afectado_id} NOT NULL:
     * esta pensada para el acceso a UNA persona, y un reporte alcanza a muchas. Cuando
     * los parametros nombran al titular —que es el caso de los reportes por
     * participante, los sensibles de verdad— se registra con su identificador. Cuando
     * no lo nombran, NO se inventa un afectado: una fila con un identificador de relleno
     * es peor que ninguna, porque una auditoria la leeria como cierta. Queda entonces la
     * ejecucion, con solicitante, parametros y huella.
     *
     * <p>Se cierra cuando el modelo admita un acceso masivo sin titular unico; hasta
     * entonces esta escrito aca y no en un comentario suelto.
     */
    private void registrarElAcceso(
            org.jooq.DSLContext dsl,
            EntradaReporte entrada,
            ContextoSesion ctx,
            String justificacion,
            int registros,
            OffsetDateTime ahora) {
        titularDe(entrada.parametros())
                .ifPresent(afectado ->
                        reportes.registrarAcceso(dsl, ctx.usuarioId(), afectado, justificacion, registros, ahora));
    }

    /** El titular, si los parametros lo nombran con alguno de los nombres acordados. */
    private static Optional<UUID> titularDe(Map<String, String> parametros) {
        for (String nombre : NOMBRES_DEL_TITULAR) {
            String valor = parametros.get(nombre);
            if (valor != null && !valor.isBlank()) {
                try {
                    return Optional.of(UUID.fromString(valor.trim()));
                } catch (IllegalArgumentException noEsUuid) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static EventoDominio evento(String tipo, UUID agregadoId, String nombre, ContextoSesion ctx) {
        return new EventoDominio(
                tipo,
                "ejecucion_reporte",
                agregadoId,
                Map.of("reporte", nombre, "solicitadoPor", ctx.usuarioId().toString()),
                UUID.fromString(ctx.traza().id()));
    }

    /** Estimacion para {@code tamano_bytes}; el exportador real la reemplaza. */
    private static final int BYTES_POR_FILA = 64;

    private static final List<String> NOMBRES_DEL_TITULAR = List.of("usuarioId", "usuario_id", "titularId");

    /**
     * @param permisos los del token de quien pide. No se consultan a `identidad` en cada
     *     peticion: eso ataria la disponibilidad de este servicio a la de otro, y
     *     hacerlo contra su esquema violaria el invariante 11.
     */
    public record EntradaReporte(
            UUID definicionId,
            Map<String, String> parametros,
            Optional<String> formato,
            Optional<String> justificacion,
            Set<String> permisos) {}

    public record SalidaReporte(
            UUID ejecucionId, String estado, int filasGeneradas, String hashResultado, Optional<UUID> exportacionId) {}
}
