package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.ExpresionDeRegla;
import bo.aportaya.cumplimiento.infraestructura.MonitoreoLftRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-48 · Calibrar reglas de cumplimiento y triar sus alertas.
 *
 * <p>Una regla mal calibrada hace mas daño que ninguna: marca el 40% del trafico, la
 * bandeja se llena, y el analista empieza a cerrar alertas sin mirarlas. Ahi es cuando
 * la que importaba pasa de largo. De ahi las tres puertas antes de activar:
 *
 * <ul>
 *   <li>**El umbral apunta al catalogo, no va escrito** (R-UIF-01). Un numero dentro de
 *       la expresion obliga a desplegar para cumplir una circular.
 *   <li>**Simulacion previa, bajo el techo de trafico.** Activar sin simular es apostar
 *       con la bandeja de otro.
 *   <li>**La accion automatica no excede lo que la severidad habilita.** Bloquear una
 *       cuenta por una alerta de severidad baja es un daño cierto por una sospecha
 *       debil.
 * </ul>
 */
@Service
public class CU48CalibrarReglas {

    private final Datos datos;
    private final MonitoreoLftRepositorio monitoreo;
    private final Outbox outbox;
    private final Reloj reloj;

    /** Cuanto trafico puede marcar una regla antes de ser inaceptable. Es politica. */
    private final BigDecimal techoDeTrafico;

    public CU48CalibrarReglas(
            Datos datos, MonitoreoLftRepositorio monitoreo, Outbox outbox, Reloj reloj, BigDecimal techoDeTrafico) {
        this.datos = datos;
        this.monitoreo = monitoreo;
        this.outbox = outbox;
        this.reloj = reloj;
        this.techoDeTrafico = techoDeTrafico;
    }

    @Transactional
    public UUID crear(EntradaRegla entrada, ContextoSesion ctx) {
        var veredicto = ExpresionDeRegla.revisar(entrada.expresion(), entrada.umbralReferencia());
        if (veredicto.umbralCableado()) {
            throw new ErrorDeNegocio(CodigoError.de(48, 2), veredicto.motivo());
        }
        if (!veredicto.valida()) {
            throw new ErrorDeNegocio(CodigoError.de(48, 1), veredicto.motivo());
        }
        if (!ExpresionDeRegla.accionProporcionada(entrada.severidad(), entrada.accionAutomatica())) {
            throw new ErrorDeNegocio(
                    CodigoError.de(48, 4),
                    "La accion " + entrada.accionAutomatica() + " excede lo que la severidad " + entrada.severidad()
                            + " habilita.");
        }

        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        return datos.conContexto(ctx, dsl -> {
            if (monitoreo.reglaPorCodigo(dsl, entrada.codigo()).isPresent()) {
                throw new ErrorDeNegocio(
                        CodigoError.de(48, 6), "Ya existe una regla con el codigo " + entrada.codigo() + ".");
            }
            UUID id = monitoreo.crearRegla(
                    dsl,
                    entrada.codigo(),
                    entrada.tipologia(),
                    entrada.descripcion(),
                    entrada.expresionJson(),
                    entrada.ventana(),
                    entrada.umbralMonto(),
                    entrada.umbralCantidad(),
                    entrada.severidad(),
                    entrada.accionAutomatica(),
                    entrada.fuenteNormativa(),
                    ahora);
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.regla_monitoreo_creada",
                            "regla_monitoreo_lft",
                            id,
                            Map.of("codigo", entrada.codigo(), "severidad", entrada.severidad(), "activa", "false"),
                            UUID.fromString(ctx.traza().id())));
            return id;
        });
    }

    /**
     * Mide una regla contra el trafico historico **antes** de encenderla.
     *
     * <p>El conteo lo hace quien posee las operaciones y llega resuelto (invariante 11):
     * aca se decide si el porcentaje es tolerable, que es la parte que no es una
     * consulta.
     */
    public SalidaSimulacion simular(int operacionesEvaluadas, int operacionesMarcadas) {
        if (operacionesEvaluadas == 0) {
            return new SalidaSimulacion(0, 0, "0.0000", false);
        }
        BigDecimal porcentaje = BigDecimal.valueOf(operacionesMarcadas)
                .divide(BigDecimal.valueOf(operacionesEvaluadas), 4, RoundingMode.HALF_EVEN);
        return new SalidaSimulacion(
                operacionesEvaluadas,
                operacionesMarcadas,
                porcentaje.toPlainString(),
                porcentaje.compareTo(techoDeTrafico) > 0);
    }

    @Transactional
    public void activar(EntradaActivacion entrada, ContextoSesion ctx) {
        // AP-CU48-03. Sin simulacion, o con simulacion que supera el techo, no se
        // enciende: la bandeja no es un campo de pruebas.
        if (!entrada.hubosimulacion()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(48, 3), "Activar una regla exige simulacion previa sobre trafico historico.");
        }
        if (entrada.superaTecho()) {
            throw new ErrorDeNegocio(
                    CodigoError.de(48, 3),
                    "La simulacion marca mas trafico del que el techo admite: la regla saturaria la bandeja.");
        }

        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        datos.conContexto(ctx, dsl -> {
            if (!monitoreo.activar(dsl, entrada.reglaId(), entrada.aprobadaPor(), ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(48, 6), "Esa regla ya estaba activa.");
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.regla_monitoreo_activada",
                            "regla_monitoreo_lft",
                            entrada.reglaId(),
                            Map.of(
                                    "aprobadaPor", entrada.aprobadaPor().toString(),
                                    "porcentajeTrafico", entrada.porcentajeTrafico()),
                            UUID.fromString(ctx.traza().id())));
            return null;
        });
    }

    /** El triaje del analista. **Nunca sin fundamento** (R-UIF-07). */
    @Transactional
    public void triar(EntradaTriaje entrada, ContextoSesion ctx) {
        if (entrada.fundamento() == null || entrada.fundamento().trim().length() < 20) {
            throw new ErrorDeNegocio(
                    CodigoError.de(48, 5), "Una alerta no se cierra sin fundamento escrito (R-UIF-07).");
        }
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);
        String estado = "SIN_MERITO".equals(entrada.conclusion()) ? "DESCARTADA" : "ESCALADA";

        datos.conContexto(ctx, dsl -> {
            if (!monitoreo.cerrar(dsl, entrada.alertaId(), estado, entrada.fundamento(), null, ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(48, 5), "Esa alerta ya fue cerrada.");
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "cumplimiento.alerta_triada",
                            "alerta_monitoreo_lft",
                            entrada.alertaId(),
                            Map.of("conclusion", entrada.conclusion(), "estado", estado),
                            UUID.fromString(ctx.traza().id())));
            return null;
        });
    }

    public record EntradaRegla(
            String codigo,
            String tipologia,
            String descripcion,
            String expresion,
            String expresionJson,
            String umbralReferencia,
            String ventana,
            BigDecimal umbralMonto,
            Integer umbralCantidad,
            String severidad,
            String accionAutomatica,
            String fuenteNormativa) {}

    public record EntradaActivacion(
            UUID reglaId, UUID aprobadaPor, boolean hubosimulacion, boolean superaTecho, String porcentajeTrafico) {}

    public record EntradaTriaje(UUID alertaId, String conclusion, String fundamento) {}

    public record SalidaSimulacion(
            int operacionesEvaluadas, int operacionesMarcadas, String porcentajeTrafico, boolean superaTecho) {}
}
