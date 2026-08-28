package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.infraestructura.OperacionRelevanteRepositorio;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-41 · Detectar umbral y registrar el formulario PCC-01.
 *
 * <p>Detecta cuando una operacion —o una acumulacion— obliga a pedir la declaracion de
 * origen y destino de fondos, **de forma reproducible**: se guarda el tipo de cambio
 * aplicado y el umbral vigente al momento (R-UIF-04, R-UIF-01). Revisar el registro dos
 * años despues tiene que dar el mismo resultado que se reporto.
 *
 * <p>**La ventana reinicia tras superar el umbral** (R-UIF-03). Sin reinicio, un usuario
 * que cruza el umbral una vez quedaria cruzandolo todos los dias por arrastre, y el
 * formulario perderia su sentido: dejaria de señalar el hecho y pasaria a señalar a la
 * persona.
 *
 * <p>Una operacion exenta **queda registrada igual**, con su motivo. No registrarla
 * seria no poder demostrar despues por que no se reporto.
 */
@Service
public class CU41RegistrarPcc01 {

    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyy-MM");

    private final Datos datos;
    private final OperacionRelevanteRepositorio operaciones;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU41RegistrarPcc01(Datos datos, OperacionRelevanteRepositorio operaciones, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.operaciones = operaciones;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    @Transactional
    public SalidaUmbral registrar(EntradaUmbral entrada, ContextoSesion ctx) {
        // AP-CU41-02 · operativa propia: sin titular identificado la operacion es de la
        // entidad consigo misma y queda exenta del formulario.
        if (entrada.usuarioId() == null) {
            throw new ErrorDeNegocio(
                    CodigoError.de(41, 2), "La operacion no tiene titular: es operativa propia y queda exenta.");
        }
        // AP-CU41-01 · R-UIF-04. Sin cotizacion no se convierte, y un umbral que no se
        // puede reproducir no se puede defender.
        if (!"USD".equals(entrada.moneda()) && entrada.tipoDeCambio() == null) {
            throw new ErrorDeNegocio(
                    CodigoError.de(41, 1),
                    "No hay tipo de cambio " + entrada.moneda() + " -> USD al " + entrada.fecha() + ".");
        }

        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        BigDecimal tipoDeCambio = "USD".equals(entrada.moneda()) ? BigDecimal.ONE : entrada.tipoDeCambio();
        BigDecimal montoUsd = entrada.monto().multiply(tipoDeCambio).setScale(2, java.math.RoundingMode.HALF_EVEN);

        return datos.conContexto(ctx, dsl -> {
            var umbrales = operaciones.umbralesVigentes(dsl, entrada.concepto(), entrada.fecha());
            var registros = new ArrayList<Registro>();
            boolean requiereDeclaracion = false;

            for (var umbral : umbrales) {
                if (!formularioDeEsteCasoDeUso(umbral.formulario())) {
                    continue;
                }
                BigDecimal medido =
                        umbral.esAcumulado() ? entrada.acumuladoEnVentana().add(montoUsd) : montoUsd;
                if (medido.compareTo(umbral.umbralUsd()) < 0) {
                    continue;
                }
                // R-UIF-13 · invariante 7: la clave se valida antes de escribir.
                var yaEsta = operaciones.registroDe(dsl, entrada.transaccionId(), umbral.id());
                if (yaEsta.isPresent()) {
                    registros.add(new Registro(yaEsta.get(), umbral.formulario(), umbral.esAcumulado(), medido, false));
                    continue;
                }

                LocalDate desde = umbral.esAcumulado() ? entrada.fecha().minusDays(umbral.ventanaDias() - 1L) : null;
                // HUECO H-1: un PCC-01 NO se puede escribir sin origen y destino
                // (ck_operelev_declaracion) y la tabla es append-only (R-AUD-01), asi
                // que tampoco se puede completar despues. El CU pide crearlo en el paso
                // 4 y pedir la declaracion en el 5; la boveda solo admite el orden
                // inverso. Se detecta el umbral, se pide la declaracion, y el registro
                // se escribe COMPLETO cuando llega. Es lo mismo que hace la boveda con
                // toda evidencia que no admite estados intermedios.
                if (umbral.exigeDeclaracion() && !entrada.exento() && entrada.origenDeclarado() == null) {
                    requiereDeclaracion = true;
                    outbox.emitir(
                            dsl,
                            new EventoDominio(
                                    "cumplimiento.uif_umbral_alcanzado",
                                    "umbral_reporte_uif",
                                    umbral.id(),
                                    Map.of(
                                            "usuarioId", entrada.usuarioId().toString(),
                                            "transaccionId",
                                                    entrada.transaccionId().toString(),
                                            "formulario", umbral.formulario(),
                                            "montoAcumuladoUsd", medido.toPlainString(),
                                            "umbralUsd", umbral.umbralUsd().toPlainString(),
                                            "baseNormativa", umbral.baseNormativa(),
                                            "exigeDeclaracion", "true"),
                                    UUID.fromString(ctx.traza().id())));
                    continue;
                }

                UUID id = operaciones.registrar(
                        dsl,
                        new OperacionRelevanteRepositorio.Registro(
                                entrada.usuarioId(),
                                entrada.transaccionId(),
                                umbral.id(),
                                umbral.esAcumulado() ? entrada.inicioDeVentanaId() : null,
                                umbral.formulario(),
                                umbral.concepto(),
                                umbral.esAcumulado(),
                                entrada.ventanaDesde() != null ? entrada.ventanaDesde() : desde,
                                umbral.esAcumulado() ? entrada.fecha() : null,
                                entrada.monto(),
                                entrada.moneda(),
                                medido,
                                tipoDeCambio,
                                montoUsd,
                                umbral.umbralUsd(),
                                entrada.exento(),
                                entrada.exento() ? entrada.motivoDeExencion() : null,
                                entrada.origenDeclarado(),
                                entrada.destinoDeclarado(),
                                entrada.fecha().format(PERIODO),
                                entrada.ocurridaEn()));

                registros.add(new Registro(id, umbral.formulario(), umbral.esAcumulado(), medido, true));

                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "cumplimiento.uif_umbral_alcanzado",
                                "registro_operacion_relevante",
                                id,
                                Map.of(
                                        "usuarioId", entrada.usuarioId().toString(),
                                        "formulario", umbral.formulario(),
                                        "montoAcumuladoUsd", medido.toPlainString(),
                                        "umbralUsd", umbral.umbralUsd().toPlainString(),
                                        "baseNormativa", umbral.baseNormativa(),
                                        "exigeDeclaracion", Boolean.toString(umbral.exigeDeclaracion())),
                                UUID.fromString(ctx.traza().id())));
            }

            return new SalidaUmbral(
                    List.copyOf(registros), requiereDeclaracion, entrada.fecha().format(PERIODO));
        });
    }

    /** CU-41 se ocupa del PCC-01; los ROG son de CU-42, con su propia clasificacion. */
    private boolean formularioDeEsteCasoDeUso(String formulario) {
        return "PCC-01".equals(formulario);
    }

    /**
     * El titular declara, y **recien ahi nace el registro**, completo.
     *
     * <p>No es el orden que el CU describe, es el unico que la boveda admite: la fila
     * no puede existir sin origen y destino, y tampoco se puede completar despues porque
     * la tabla es append-only. Lo que si queda desde el primer momento es el evento que
     * pidio la declaracion, con su fecha: el rastro de cuando se detecto el umbral no se
     * pierde.
     */
    @Transactional
    public SalidaUmbral declarar(EntradaDeclaracion entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
                            UUID declaracionId = operaciones.declararOrigen(
                                    dsl,
                                    entrada.umbral().usuarioId(),
                                    entrada.umbral().transaccionId(),
                                    entrada.umbral().monto(),
                                    entrada.umbral().moneda(),
                                    entrada.origen(),
                                    entrada.descripcion(),
                                    ahora);

                            outbox.emitir(
                                    dsl,
                                    new EventoDominio(
                                            "cumplimiento.uif_declaracion_recibida",
                                            "declaracion_origen_fondos",
                                            declaracionId,
                                            Map.of(
                                                    "usuarioId",
                                                            entrada.umbral()
                                                                    .usuarioId()
                                                                    .toString(),
                                                    "transaccionId",
                                                            entrada.umbral()
                                                                    .transaccionId()
                                                                    .toString()),
                                            UUID.fromString(ctx.traza().id())));
                            return null;
                        })
                        == null
                ? registrar(
                        new EntradaUmbral(
                                entrada.umbral().usuarioId(),
                                entrada.umbral().transaccionId(),
                                entrada.umbral().concepto(),
                                entrada.umbral().monto(),
                                entrada.umbral().moneda(),
                                entrada.umbral().tipoDeCambio(),
                                entrada.umbral().acumuladoEnVentana(),
                                entrada.umbral().inicioDeVentanaId(),
                                entrada.umbral().ventanaDesde(),
                                entrada.umbral().fecha(),
                                entrada.umbral().ocurridaEn(),
                                false,
                                null,
                                entrada.origen(),
                                entrada.destino()),
                        ctx)
                : null;
    }

    public record EntradaDeclaracion(EntradaUmbral umbral, String origen, String destino, String descripcion) {}

    /**
     * @param acumuladoEnVentana lo que ya suma la ventana SIN esta operacion. Lo calcula
     *     quien posee las transacciones (invariante 11) y llega resuelto
     * @param inicioDeVentanaId la primera operacion de la ventana, que es la que el
     *     formulario tiene que citar
     */
    public record EntradaUmbral(
            UUID usuarioId,
            UUID transaccionId,
            String concepto,
            BigDecimal monto,
            String moneda,
            BigDecimal tipoDeCambio,
            BigDecimal acumuladoEnVentana,
            UUID inicioDeVentanaId,
            LocalDate ventanaDesde,
            LocalDate fecha,
            OffsetDateTime ocurridaEn,
            boolean exento,
            String motivoDeExencion,
            String origenDeclarado,
            String destinoDeclarado) {}

    public record Registro(
            UUID registroId, String formulario, boolean esAcumulada, BigDecimal montoAcumuladoUsd, boolean esNuevo) {}

    public record SalidaUmbral(List<Registro> registros, boolean requiereDeclaracion, String periodoRemision) {}
}
