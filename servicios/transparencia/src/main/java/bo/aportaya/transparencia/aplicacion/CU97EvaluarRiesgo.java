package bo.aportaya.transparencia.aplicacion;

import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import bo.aportaya.transparencia.dominio.ContenidoCanonico;
import bo.aportaya.transparencia.dominio.SenalDeRiesgo;
import bo.aportaya.transparencia.infraestructura.ModeloRepositorio;
import bo.aportaya.transparencia.infraestructura.ReputacionRepositorio;
import bo.aportaya.transparencia.infraestructura.RiesgoRepositorio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-97 · Anticipar el riesgo con alertas tempranas.
 *
 * <p>Anticipar sirve para **acompanar**, no para etiquetar. De ahi las tres reglas que
 * gobiernan este caso de uso:
 *
 * <ul>
 *   <li>**Sin historial el nivel es {@code SIN_DATOS}, nunca riesgo alto.** Tratar a
 *       quien recien llega como probable incumplidor es la exclusion que este producto
 *       existe para no repetir.
 *   <li>**Al participante nunca se le muestra un puntaje.** El mensaje habla de
 *       hechos: que vence, cuanto, y que puede hacer. Un numero de riesgo no le sirve
 *       para nada y lo marca.
 *   <li>**Una alerta abierta por causa** (R-GAR-07), y **no se cierra sin desenlace**:
 *       una alerta que se cierra sin decir si era cierta no deja calibrar nada, y el
 *       modelo se queda con el mismo error para siempre.
 * </ul>
 */
@Service
public class CU97EvaluarRiesgo {

    private final Datos datos;
    private final RiesgoRepositorio riesgos;
    private final ReputacionRepositorio reputaciones;
    private final ModeloRepositorio modelos;
    private final Outbox outbox;
    private final Reloj reloj;

    /**
     * Donde empieza cada nivel de riesgo y cada severidad. Es politica, no constante
     * (invariante 10): decide a quien se acompaña y a quien se restringe.
     */
    private final SenalDeRiesgo.Escala escala;

    public CU97EvaluarRiesgo(
            Datos datos,
            RiesgoRepositorio riesgos,
            ReputacionRepositorio reputaciones,
            ModeloRepositorio modelos,
            Outbox outbox,
            Reloj reloj,
            SenalDeRiesgo.Escala escala) {
        this.datos = datos;
        this.riesgos = riesgos;
        this.reputaciones = reputaciones;
        this.modelos = modelos;
        this.outbox = outbox;
        this.reloj = reloj;
        this.escala = escala;
    }

    @Transactional
    public SalidaRiesgo evaluar(EntradaRiesgo entrada, ContextoSesion ctx) {
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        // AP-CU97-03: las metricas del grupo se calculan sobre un periodo cerrado. Con
        // el periodo abierto la tasa de pago en termino todavia va a subir, y alertar
        // sobre ella es alarmar por algo que aun no paso.
        if (entrada.ambito().equals("GRUPO") && entrada.periodoId() != null && !entrada.periodoCerrado()) {
            throw new ErrorDeNegocio(CodigoError.de(97, 3), "Las metricas del grupo exigen el periodo cerrado.");
        }

        return datos.conContexto(ctx, dsl -> {
            var modelo = modelos.modeloVigente(dsl, ahora)
                    .orElseThrow(() ->
                            new ErrorDeNegocio(CodigoError.de(97, 2), "No hay modelo de scoring vigente a la fecha."));

            // Las metricas se guardan siempre, alerten o no: la serie es lo que despues
            // permite ver que venia empeorando desde hace tres meses.
            for (var m : entrada.metricas()) {
                if (entrada.ambitoId() != null && entrada.periodoId() != null) {
                    riesgos.registrarMetrica(
                            dsl,
                            entrada.ambitoId(),
                            entrada.periodoId(),
                            m.codigo(),
                            m.valor(),
                            m.unidad(),
                            m.umbral(),
                            m.umbral() != null && m.superaUmbral(),
                            ahora);
                }
            }

            var enAlerta = SenalDeRiesgo.enAlerta(entrada.metricas());
            String nivel = SenalDeRiesgo.nivel(
                    entrada.observaciones(), modelo.minimoDeEventos(), entrada.puntajeDeRiesgo(), escala);

            var alertas = new ArrayList<Alerta>();
            for (var m : enAlerta) {
                String codigo = entrada.codigoDeAlerta().get(m.codigo());
                if (codigo == null) {
                    // ck_alerta_riesgo_codigo es cerrado: una metrica sin codigo de
                    // alerta declarado no se puede guardar, y no se inventa uno.
                    continue;
                }
                if (entrada.ambitoId() == null) {
                    continue;
                }
                var abierta = riesgos.alertaAbierta(dsl, entrada.ambito(), entrada.ambitoId(), codigo);
                if (abierta.isPresent()) {
                    // AP-CU97-04 · R-GAR-07. El reintento no abre una segunda: dos
                    // alertas por la misma causa duplican el trabajo del comite y
                    // hacen que la segunda se lea como un problema nuevo.
                    continue;
                }
                String severidad = SenalDeRiesgo.severidad(m, escala);
                String mensaje = SenalDeRiesgo.mensajeEnHechos(m);
                String evidencia = ContenidoCanonico.serializar(new LinkedHashMap<>(Map.of(
                        "metrica", m.codigo(),
                        "valor", m.valor().toPlainString(),
                        "umbral", m.umbral().toPlainString(),
                        "modeloVersion", modelo.version())));

                UUID alertaId = riesgos.abrirAlerta(
                        dsl, entrada.ambito(), entrada.ambitoId(), codigo, severidad, mensaje, evidencia, ahora);

                outbox.emitir(
                        dsl,
                        new EventoDominio(
                                "transparencia.alerta_temprana_generada",
                                "alerta_riesgo",
                                alertaId,
                                Map.of(
                                        "ambito",
                                        entrada.ambito(),
                                        "ambitoId",
                                        entrada.ambitoId().toString(),
                                        "codigo",
                                        codigo,
                                        "severidad",
                                        severidad,
                                        // Lo que ve la persona. Sin puntaje adentro: si
                                        // alguien puede leerlo aca, esta mal.
                                        "mensajeAlUsuario",
                                        mensaje,
                                        "accionSugerida",
                                        SenalDeRiesgo.accionSugerida(codigo)),
                                UUID.fromString(ctx.traza().id())));

                alertas.add(new Alerta(
                        alertaId, codigo, severidad, mensaje, SenalDeRiesgo.accionSugerida(codigo), mensaje));
            }

            return new SalidaRiesgo(List.copyOf(alertas), nivel, modelo.version(), enAlerta);
        });
    }

    /** Cerrar exige desenlace. Sin el, la alerta no sirvio para aprender nada. */
    @Transactional
    public void cerrar(UUID alertaId, String desenlace, ContextoSesion ctx) {
        if (!"CONFIRMADA".equals(desenlace) && !"DESCARTADA".equals(desenlace)) {
            throw new ErrorDeNegocio(
                    CodigoError.de(97, 5),
                    "Una alerta se cierra CONFIRMADA o DESCARTADA: sin desenlace no se puede calibrar el modelo.");
        }
        OffsetDateTime ahora = reloj.ahora().atOffset(ZoneOffset.UTC);

        datos.conContexto(ctx, dsl -> {
            if (!riesgos.cerrarAlerta(dsl, alertaId, desenlace, ahora)) {
                throw new ErrorDeNegocio(CodigoError.de(97, 5), "Esa alerta no esta abierta.");
            }
            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "transparencia.alerta_temprana_cerrada",
                            "alerta_riesgo",
                            alertaId,
                            Map.of("desenlace", desenlace),
                            UUID.fromString(ctx.traza().id())));
            return null;
        });
    }

    /**
     * @param codigoDeAlerta de codigo de metrica al codigo de alerta que la taxonomia
     *     cerrada admite. Es catalogo, no constante (invariante 10).
     * @param puntajeDeRiesgo el puntaje del modelo. **No sale de aca hacia el usuario.**
     */
    public record EntradaRiesgo(
            String ambito,
            UUID ambitoId,
            UUID periodoId,
            boolean periodoCerrado,
            List<SenalDeRiesgo.Metrica> metricas,
            Map<String, String> codigoDeAlerta,
            int observaciones,
            BigDecimal puntajeDeRiesgo) {}

    public record Alerta(
            UUID alertaId,
            String codigo,
            String severidad,
            String descripcion,
            String accionSugerida,
            String mensajeAlUsuario) {}

    public record SalidaRiesgo(
            List<Alerta> alertas,
            String nivelRiesgo,
            String versionModelo,
            List<SenalDeRiesgo.Metrica> metricasEnAlerta) {}
}
