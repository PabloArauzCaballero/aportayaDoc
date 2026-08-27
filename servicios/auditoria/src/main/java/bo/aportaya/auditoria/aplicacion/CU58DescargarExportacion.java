package bo.aportaya.auditoria.aplicacion;

import bo.aportaya.auditoria.infraestructura.ReporteRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-58 · la otra mitad: entregar el archivo, o negarse.
 *
 * <p>Vive aparte de {@link CU58EjecutarReporte} porque es otra operacion y otra
 * transaccion: ejecutar produce el resultado, descargar decide si el archivo todavia se
 * puede entregar. Meterlas juntas solo porque comparten el numero de caso de uso
 * empezaba a esconder que son dos decisiones distintas, tomadas en momentos distintos y
 * con reglas distintas.
 *
 * <p>Las dos reglas, y las dos existen por lo mismo — <b>un archivo entregado deja de
 * estar bajo control</b>:
 *
 * <ul>
 *   <li><b>Caduca.</b> Un enlace eterno a datos personales es una fuga futura con fecha
 *       abierta.
 *   <li><b>Se cuenta.</b> Lo que se descarga sin limite deja de ser una entrega y pasa a
 *       ser una copia publicada. Agotado el tope, hay que volver a pedir el reporte, con
 *       justificacion nueva — que es justo el rastro que una auditoria busca.
 * </ul>
 */
@Service
public class CU58DescargarExportacion {

    private final Datos datos;
    private final ReporteRepositorio reportes;
    private final Outbox outbox;
    private final Reloj reloj;
    private final int topeDeDescargas;

    public CU58DescargarExportacion(
            Datos datos,
            ReporteRepositorio reportes,
            Outbox outbox,
            Reloj reloj,
            @Value("${auditoria.reportes.tope-de-descargas:3}") int topeDeDescargas) {
        this.datos = datos;
        this.reportes = reportes;
        this.outbox = outbox;
        this.reloj = reloj;
        this.topeDeDescargas = topeDeDescargas;
    }

    /**
     * Autoriza una descarga y la cuenta.
     *
     * <p>Se cuenta <b>antes</b> de entregar. Contar despues significa no contar la vez
     * que fallo a mitad, y entonces el tope no es un tope.
     */
    @Transactional
    public void ejecutar(UUID exportacionId, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        datos.conContexto(ctx, dsl -> {
            // La fila se toma para actualizar: dos descargas simultaneas contarian una
            // sola y el tope dejaria de ser tope justo cuando mas importa.
            var exportacion = reportes.exportacion(dsl, exportacionId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(58, 5), "Esa exportacion no existe."));

            // AP-CU58-05
            if (!exportacion.expiraEn().isAfter(ahora)) {
                throw new ErrorDeNegocio(
                        CodigoError.de(58, 5), "Esa exportacion vencio: hay que volver a pedir el reporte.");
            }
            // AP-CU58-06
            if (exportacion.descargas() >= topeDeDescargas) {
                throw new ErrorDeNegocio(
                        CodigoError.de(58, 6),
                        "Se agotaron las descargas de ese archivo: hay que pedirlo de nuevo, con justificacion.");
            }

            reportes.contarDescarga(dsl, exportacionId);
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "auditoria.reporte_descargado",
                            "exportacion_reporte",
                            exportacionId,
                            Map.of("descargadoPor", ctx.usuarioId().toString()),
                            UUID.fromString(ctx.traza().id())));
            return null;
        });
    }
}
