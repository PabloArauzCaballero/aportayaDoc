package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.ClasificacionPep.NivelRiesgo;
import bo.aportaya.cumplimiento.dominio.DesvioDePerfil;
import bo.aportaya.cumplimiento.dominio.DesvioDePerfil.Desvio;
import bo.aportaya.cumplimiento.dominio.DesvioDePerfil.Umbrales;
import bo.aportaya.cumplimiento.dominio.PeriodicidadDeRevision;
import bo.aportaya.cumplimiento.infraestructura.CalificacionRiesgoRepositorio;
import bo.aportaya.cumplimiento.infraestructura.PerfilTransaccionalRepositorio;
import bo.aportaya.cumplimiento.infraestructura.RevisionKycRepositorio;
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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-06 · Revision periodica de conocimiento del cliente.
 *
 * <p>Trabajo programado, **idempotente por usuario y periodo**: si el planificador
 * dispara dos veces el mismo dia, o si se reintenta tras un fallo, no se abren dos
 * revisiones del mismo periodo. La idempotencia no la da una clave que manda el
 * llamador —no hay llamador— sino la fecha programada, que es lo unico estable.
 *
 * <p>Compara lo declarado contra lo observado. El monto observado **llega desde
 * afuera**: vive en el esquema de la billetera y cumplimiento no lo lee (invariante
 * 11). Lo aporta el evento o el proceso que dispara la revision.
 */
@Service
public class CU06RevisarConocimiento {

    private static final String PERFIL_DECLARADO = "DECLARADO";
    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyyMM");

    private final Datos datos;
    private final CalificacionRiesgoRepositorio calificaciones;
    private final PerfilTransaccionalRepositorio perfiles;
    private final RevisionKycRepositorio revisiones;
    private final Outbox outbox;
    private final Reloj reloj;
    private final PeriodicidadDeRevision periodicidad;
    private final Umbrales umbrales;
    private final String codigoReglaDesvio;

    public CU06RevisarConocimiento(
            Datos datos,
            CalificacionRiesgoRepositorio calificaciones,
            PerfilTransaccionalRepositorio perfiles,
            RevisionKycRepositorio revisiones,
            Outbox outbox,
            Reloj reloj,
            PeriodicidadDeRevision periodicidad,
            Umbrales umbrales,
            String codigoReglaDesvio) {
        this.datos = datos;
        this.calificaciones = calificaciones;
        this.perfiles = perfiles;
        this.revisiones = revisiones;
        this.outbox = outbox;
        this.reloj = reloj;
        this.periodicidad = periodicidad;
        this.umbrales = umbrales;
        this.codigoReglaDesvio = codigoReglaDesvio;
    }

    @Transactional
    public SalidaRevision ejecutar(EntradaRevision entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        LocalDate hoy = ahora.toLocalDate();

        return datos.conContexto(ctx, dsl -> {
            // AP-CU06-01: sin calificacion no hay periodicidad que aplicar ni
            // referencia contra la que comparar.
            var calificacion = calificaciones
                    .vigenteDe(dsl, entrada.usuarioId())
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(6, 1),
                            "Ese cliente no tiene calificacion de riesgo vigente: no hay nada que revisar."));

            // AP-CU06-02: un alta sin perfil declarado deja al monitoreo sin
            // referencia. No es burocracia: es contra lo que se compara despues.
            var declarado = perfiles.masReciente(dsl, entrada.usuarioId(), PERFIL_DECLARADO)
                    .orElseThrow(() -> new ErrorDeNegocio(
                            CodigoError.de(6, 2), "Falta el perfil transaccional declarado de ese cliente."));

            LocalDate fechaProgramada = calificacion.proximaRevision();
            UUID revisionId = revisiones
                    .deLaFecha(dsl, entrada.usuarioId(), fechaProgramada)
                    .orElseGet(() -> revisiones.programar(
                            dsl, entrada.usuarioId(), Optional.of(calificacion.id()), fechaProgramada));

            String resultado = "RATIFICADA";
            String desvioTexto = null;

            if (entrada.montoObservado().isPresent()) {
                Desvio desvio =
                        DesvioDePerfil.calcular(entrada.montoObservado().get(), declarado.montoMensual(), umbrales);
                // Copia final: la lambda del `map` de abajo la captura, y el campo
                // externo se reasigna despues.
                final String porcentaje = desvio.porcentaje().toPlainString();
                desvioTexto = porcentaje;

                if (desvio.ameritaAlerta()) {
                    Optional<UUID> alerta = revisiones
                            .reglaDeDesvio(dsl, codigoReglaDesvio)
                            .map(regla -> revisiones.abrirAlerta(
                                    dsl,
                                    regla,
                                    entrada.usuarioId(),
                                    entrada.montoObservado().get(),
                                    "{\"motivo\": \"DESVIO_PERFIL\", \"desvioPorcentual\": \"" + porcentaje + "\"}",
                                    desvio.severidad().name(),
                                    ahora));

                    revisiones.registrarDesvio(
                            dsl,
                            entrada.usuarioId(),
                            declarado.id(),
                            alerta,
                            hoy.format(PERIODO),
                            entrada.montoObservado().get(),
                            declarado.montoMensual(),
                            desvio.porcentaje(),
                            desvio.severidad().name(),
                            ahora);

                    resultado = desvio.severidad() == DesvioDePerfil.Severidad.CRITICA ? "ESCALADA" : "OBSERVADA";

                    outbox.emitir(
                            dsl,
                            new EventoDominio(
                                    "cumplimiento.perfil_desviado",
                                    "desvio_perfil",
                                    revisionId,
                                    Map.of(
                                            "usuarioId", entrada.usuarioId().toString(),
                                            "desvioPorcentual", porcentaje,
                                            "severidad", desvio.severidad().name()),
                                    UUID.fromString(ctx.traza().id())));
                }
            }

            revisiones.ejecutar(dsl, revisionId, resultado, ctx.usuarioId(), hoy);

            NivelRiesgo riesgo = NivelRiesgo.valueOf(calificacion.nivel());
            LocalDate proxima = periodicidad.proximaDesde(hoy, riesgo);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.kyc_revisado",
                            "revision_periodica_kyc",
                            revisionId,
                            Map.of(
                                    "usuarioId", entrada.usuarioId().toString(),
                                    "resultado", resultado,
                                    "proximaRevision", proxima.toString()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaRevision(revisionId, resultado, desvioTexto, proxima);
        });
    }

    /**
     * Marca vencidas las revisiones que nadie ejecuto y pide limitar esas cuentas.
     *
     * <p>El estado LIMITADA vive en {@code nucleo_financiero.cuenta_billetera}:
     * cumplimiento lo **pide por evento**, no lo escribe (invariante 11).
     */
    @Transactional
    public int vencerYPedirLimitacion(ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            int vencidas = revisiones.marcarVencidas(dsl, ahora.toLocalDate());
            if (vencidas > 0) {
                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "cumplimiento.revision_vencida",
                                "revision_periodica_kyc",
                                UUID.fromString(ctx.traza().id()),
                                Map.of("cantidad", Integer.toString(vencidas)),
                                UUID.fromString(ctx.traza().id())));
            }
            return vencidas;
        });
    }

    public record EntradaRevision(UUID usuarioId, String motivo, Optional<BigDecimal> montoObservado) {

        public static EntradaRevision programada(UUID usuarioId) {
            return new EntradaRevision(usuarioId, "PROGRAMADA", Optional.empty());
        }
    }

    public record SalidaRevision(
            UUID revisionId, String resultado, String desvioPorcentual, LocalDate proximaRevision) {}
}
