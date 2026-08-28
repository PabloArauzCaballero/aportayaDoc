package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.ArchivoRegulatorio;
import bo.aportaya.cumplimiento.infraestructura.GobiernoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.OperacionRelevanteRepositorio;
import bo.aportaya.cumplimiento.infraestructura.ReporteRegulatorioRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-43 · Remitir los reportes mensuales a la UIF.
 *
 * <p>Tres cosas que este caso de uso no negocia:
 *
 * <ul>
 *   <li>**Quien aprueba no es quien genero** (R-SEG-04). Es la unica defensa real contra
 *       un reporte armado a medida: si la misma persona lo hace y lo firma, la firma no
 *       agrega nada.
 *   <li>**Un mes sin operaciones se informa en cero** (R-UIF-06). No mandar nada y
 *       mandar cero son cosas distintas: la primera parece un olvido y el regulador la
 *       trata como tal.
 *   <li>**Fuera de plazo se envia igual**, y se abre hallazgo. Callar un envio tardio
 *       agrega un encubrimiento al retraso.
 * </ul>
 */
@Service
public class CU43RemitirReportes {

    private final Datos datos;
    private final ReporteRegulatorioRepositorio reportes;
    private final OperacionRelevanteRepositorio operaciones;
    private final GobiernoRepositorio gobierno;
    private final Outbox outbox;
    private final Reloj reloj;
    private final String baseUrlDeArchivos;

    public CU43RemitirReportes(
            Datos datos,
            ReporteRegulatorioRepositorio reportes,
            OperacionRelevanteRepositorio operaciones,
            GobiernoRepositorio gobierno,
            Outbox outbox,
            Reloj reloj,
            @Value("${aportaya.publicacion.base-url}") String baseUrlDeArchivos) {
        this.datos = datos;
        this.reportes = reportes;
        this.operaciones = operaciones;
        this.gobierno = gobierno;
        this.outbox = outbox;
        this.reloj = reloj;
        this.baseUrlDeArchivos = baseUrlDeArchivos;
    }

    @Transactional
    public SalidaReporte generar(EntradaReporte entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var catalogo = reportes.catalogoPorCodigo(dsl, entrada.catalogoCodigo())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(43, 1),
                            "No hay catalogo activo para el reporte " + entrada.catalogoCodigo() + "."));

            var yaGenerado = reportes.reporteDe(dsl, catalogo.id(), entrada.periodo());
            if (yaGenerado.isPresent()) {
                var r = yaGenerado.get();
                return new SalidaReporte(
                        r.id(), r.cantidadRegistros(), r.enCero(), urlDe(r.id()), r.hashArchivo(), null, false);
            }

            var pendientes = operaciones.pendientesDelPeriodo(dsl, entrada.periodo(), entrada.filtroDeFormulario());
            BigDecimal total = pendientes.stream()
                    .map(OperacionRelevanteRepositorio.Pendiente::montoUsd)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // El plazo sale del catalogo y SE GUARDA. Recalcularlo al consultar dejaria
            // que un cambio de plazo reescribiera si un envio viejo llego a tiempo.
            YearMonth mes = YearMonth.parse(entrada.periodo());
            LocalDate corte = mes.atEndOfMonth();
            LocalDate limite = corte.plusDays(catalogo.plazoDias());

            String hash = ArchivoRegulatorio.hashDe(ArchivoRegulatorio.armar(
                    catalogo.codigo(),
                    entrada.periodo(),
                    pendientes.stream()
                            .map(r -> new ArchivoRegulatorio.Linea(r.id().toString(), r.formulario(), r.montoUsd()))
                            .toList()));

            UUID reporteId = reportes.generar(
                    dsl,
                    catalogo.id(),
                    ctx.usuarioId(),
                    entrada.periodo(),
                    corte,
                    limite,
                    pendientes.size(),
                    total,
                    urlDeCodigo(catalogo.codigo(), entrada.periodo()),
                    hash,
                    ahora);

            // HUECO: `registro_operacion_relevante.reporte_regulatorio_id` no se puede
            // escribir nunca —la tabla es append-only (R-AUD-01)—, asi que el enlace
            // entre reporte y registros se deriva del periodo. Lo que impide reportar
            // dos veces el mismo mes es uq_reporte_catalogo_periodo.

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.reporte_generado",
                            "reporte_regulatorio",
                            reporteId,
                            Map.of(
                                    "catalogoCodigo", catalogo.codigo(),
                                    "periodo", entrada.periodo(),
                                    "cantidadRegistros", Integer.toString(pendientes.size()),
                                    "reporteEnCero", Boolean.toString(pendientes.isEmpty()),
                                    "fechaLimite", limite.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaReporte(
                    reporteId,
                    pendientes.size(),
                    pendientes.isEmpty(),
                    urlDeCodigo(catalogo.codigo(), entrada.periodo()),
                    hash,
                    null,
                    true);
        });
    }

    /** Aprueba y envia. Aca vive la segregacion: aprobar exige no haber generado. */
    @Transactional
    public SalidaReporte aprobarYEnviar(EntradaEnvio entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            var catalogo = reportes.catalogoPorCodigo(dsl, entrada.catalogoCodigo())
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(43, 1), "Catalogo de reporte inexistente."));
            var reporte = reportes.reporteDe(dsl, catalogo.id(), entrada.periodo())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(43, 1), "El periodo " + entrada.periodo() + " todavia no se genero."));

            // AP-CU43-02 · R-SEG-04.
            if (entrada.aprobadoPor().equals(reporte.generadoPor())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(43, 2), "Quien aprueba el reporte no puede ser quien lo genero (R-SEG-04).");
            }
            if (!reportes.aprobar(dsl, reporte.id(), entrada.aprobadoPor())) {
                throw new ErrorDeNegocio(CodigoError.de(43, 3), "Ese reporte ya no esta en estado GENERADO.");
            }

            boolean fueraDePlazo = ahora.toLocalDate().isAfter(reporte.fechaLimite());
            String estadoEnvio = entrada.aceptadoPorElOrganismo() ? "ACEPTADO" : "ENVIADO";
            reportes.registrarEnvio(
                    dsl,
                    reporte.id(),
                    entrada.aprobadoPor(),
                    catalogo.organismo(),
                    entrada.canal(),
                    estadoEnvio,
                    entrada.numeroConstancia(),
                    entrada.reintentos(),
                    ahora);

            if (fueraDePlazo) {
                // Se envia igual. Y se deja constancia: un envio tardio silencioso
                // agrega un encubrimiento al retraso.
                gobierno.abrirHallazgo(
                        dsl,
                        // `hallazgo_auditoria.codigo` es VARCHAR(20): el codigo lleva el
                        // periodo, que es lo que distingue un vencimiento de otro, y del
                        // catalogo solo lo que entra.
                        codigoDeHallazgo(catalogo.codigo(), entrada.periodo()),
                        "AUTOEVALUACION",
                        "El reporte " + catalogo.codigo() + " del periodo " + entrada.periodo()
                                + " se envio despues de su fecha limite (" + reporte.fechaLimite() + ").",
                        "ALTA",
                        "REPORTES_REGULATORIOS",
                        ahora.toLocalDate(),
                        ahora.toLocalDate().plusDays(30));
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "cumplimiento.reporte_vencido",
                                "reporte_regulatorio",
                                reporte.id(),
                                Map.of(
                                        "periodo",
                                        entrada.periodo(),
                                        "fechaLimite",
                                        reporte.fechaLimite().toString()),
                                UUID.fromString(ctx.traza().id())));
            }

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.reporte_enviado",
                            "reporte_regulatorio",
                            reporte.id(),
                            Map.of(
                                    "organismo", catalogo.organismo(),
                                    "numeroConstancia",
                                            entrada.numeroConstancia() == null ? "" : entrada.numeroConstancia(),
                                    "fueraDePlazo", Boolean.toString(fueraDePlazo)),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaReporte(
                    reporte.id(),
                    reporte.cantidadRegistros(),
                    reporte.enCero(),
                    urlDe(reporte.id()),
                    reporte.hashArchivo(),
                    entrada.numeroConstancia(),
                    fueraDePlazo);
        });
    }

    /** «REP-» + periodo (7) + lo que quede del codigo, dentro de los 20 que la base da. */
    private String codigoDeHallazgo(String codigoCatalogo, String periodo) {
        String prefijo = "REP-" + periodo + "-";
        int disponible = 20 - prefijo.length();
        return prefijo + codigoCatalogo.substring(0, Math.min(disponible, codigoCatalogo.length()));
    }

    private String urlDe(UUID reporteId) {
        return baseUrlDeArchivos + "/reportes/" + reporteId;
    }

    private String urlDeCodigo(String codigo, String periodo) {
        return baseUrlDeArchivos + "/reportes/" + codigo + "-" + periodo;
    }

    /**
     * @param filtroDeFormulario patron SQL: {@code 'PCC-01'} o {@code 'ROG-%'}. Es
     *     catalogo del reporte, no una constante escondida
     */
    public record EntradaReporte(String periodo, String catalogoCodigo, String filtroDeFormulario) {}

    public record EntradaEnvio(
            String periodo,
            String catalogoCodigo,
            UUID aprobadoPor,
            String canal,
            String numeroConstancia,
            boolean aceptadoPorElOrganismo,
            int reintentos) {}

    public record SalidaReporte(
            UUID reporteId,
            int cantidadRegistros,
            boolean reporteEnCero,
            String urlArchivo,
            String hashArchivo,
            String numeroConstancia,
            boolean fueraDePlazo) {}
}
