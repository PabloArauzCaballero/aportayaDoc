package bo.aportaya.cumplimiento.aplicacion;

import bo.aportaya.cumplimiento.dominio.ClasificacionDeEvento;
import bo.aportaya.cumplimiento.infraestructura.RiesgoOperativoRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import bo.aportaya.plataforma.dominio.Reloj;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-54 · Registrar un evento de riesgo operativo.
 *
 * <p>Ponerle numero a lo que cuesta hacer las cosas mal, con las categorias y factores
 * que exige la norma, y <b>cerrar el circulo con un plan de accion con responsable y
 * plazo</b>. Una base de perdidas sin plan de accion es una coleccion de anecdotas
 * caras.
 *
 * <p><b>Vive en `cumplimiento` y no en `auditoria`</b>, aunque su ficha diga
 * {@code openapi/auditoria.yaml}. Las cuatro tablas que escribe —evento, plan, hallazgo
 * y acta— estan en el esquema {@code cumplimiento}, y {@code svc_auditoria} no tiene
 * {@code GRANT} sobre el. Implementarlo alla habria exigido leer el esquema de otro
 * servicio, que es el invariante 11. Donde la ficha y el modelo no coinciden, manda el
 * modelo: es el que rechaza.
 *
 * <p><b>El evento se registra aunque no haya perdida.</b> Una casi-perdida entra con
 * {@code perdida_bruta = 0}: la frecuencia tambien es informacion, y filtrarla por no
 * costar plata deja el analisis ciego justo para los eventos que todavia no salieron
 * caros.
 */
@Service
public class CU54RegistrarRiesgoOperativo {

    private final Datos datos;
    private final RiesgoOperativoRepositorio riesgos;
    private final Outbox outbox;
    private final Reloj reloj;

    public CU54RegistrarRiesgoOperativo(Datos datos, RiesgoOperativoRepositorio riesgos, Outbox outbox, Reloj reloj) {
        this.datos = datos;
        this.riesgos = riesgos;
        this.outbox = outbox;
        this.reloj = reloj;
    }

    /**
     * Registra el evento y, si viene responsable, su plan de accion — <b>en la misma
     * transaccion</b>.
     *
     * <p>Partirlas dejaria eventos sin plan cada vez que el proceso muriera en el medio,
     * y esos son justo los que nadie vuelve a mirar.
     */
    @Transactional
    public SalidaEvento ejecutar(EntradaEvento entrada, ContextoSesion ctx) {
        Moneda moneda = monedaDe(entrada.moneda());
        Dinero perdidaBruta = importe(entrada.perdidaBruta(), moneda);
        Dinero recuperacion = importe(entrada.recuperacion().orElse("0.00"), moneda);

        ClasificacionDeEvento.Clasificado clasificado;
        try {
            clasificado = ClasificacionDeEvento.clasificar(
                    entrada.categoriaEvento(),
                    entrada.factorRiesgo(),
                    entrada.fechaOcurrencia(),
                    entrada.fechaDeteccion(),
                    perdidaBruta,
                    recuperacion);
        } catch (ClasificacionDeEvento.FechasIncoherentes fechas) {
            throw new ErrorDeNegocio(CodigoError.de(54, 1), fechas.getMessage());
        } catch (ClasificacionDeEvento.RecuperacionExcesiva excesiva) {
            throw new ErrorDeNegocio(CodigoError.de(54, 2), excesiva.getMessage());
        } catch (ClasificacionDeEvento.TaxonomiaInvalida taxonomia) {
            throw new ErrorDeNegocio(CodigoError.de(54, 3), taxonomia.getMessage());
        }

        return datos.conContexto(ctx, dsl -> {
            var registrado = riesgos.registrar(
                    dsl,
                    codigoDe(entrada, clasificado),
                    ctx.usuarioId(),
                    clasificado.categoria(),
                    clasificado.factor(),
                    entrada.lineaNegocio(),
                    entrada.descripcion(),
                    entrada.fechaOcurrencia(),
                    entrada.fechaDeteccion(),
                    perdidaBruta.monto(),
                    recuperacion.monto(),
                    moneda.name());

            Optional<UUID> planId = entrada.responsableId()
                    .map(responsable -> riesgos.planificar(
                            dsl,
                            registrado.id(),
                            responsable,
                            entrada.accionComprometida().orElse(entrada.descripcion()),
                            entrada.fechaCompromiso().orElse(reloj.hoy().plusDays(DIAS_POR_OMISION))));

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "riesgo.evento_registrado",
                            "evento_riesgo_operativo",
                            registrado.id(),
                            Map.of(
                                    "categoria", clasificado.categoria(),
                                    "factor", clasificado.factor(),
                                    "lineaNegocio", entrada.lineaNegocio()),
                            UUID.fromString(ctx.traza().id())));

            // La perdida neta que se devuelve es la que devolvio el motor, no la que
            // calculo el atomo: la columna es GENERATED y la verdad es suya.
            return new SalidaEvento(
                    registrado.id(),
                    codigoDe(entrada, clasificado),
                    registrado.perdidaNeta().toPlainString(),
                    moneda.name(),
                    planId);
        });
    }

    /**
     * El codigo del evento — <b>derivado del hecho, no de la ejecucion</b>.
     *
     * <p>Es la clave de idempotencia de este caso de uso. Dos cargas del mismo hecho
     * producen el mismo codigo y la segunda choca contra
     * {@code uq_evento_riesgo_operativo_codigo} en vez de duplicar la base de perdidas;
     * dos hechos distintos del mismo dia y la misma categoria producen codigos
     * distintos. Un {@code UUID.randomUUID()} aca haria que cada reintento de red
     * agregara una perdida que nunca ocurrio.
     *
     * <p>La primera version derivaba solo de fecha y categoria, y eso NO alcanza: dos
     * fraudes externos el mismo martes son dos eventos, y el segundo habria quedado
     * rechazado por duplicado — perdiendo justo el dato que la norma pide conservar.
     */
    private static String codigoDe(EntradaEvento entrada, ClasificacionDeEvento.Clasificado clasificado) {
        String hecho = String.join(
                SEPARADOR,
                entrada.fechaOcurrencia().toString(),
                clasificado.categoria(),
                clasificado.factor(),
                entrada.lineaNegocio(),
                entrada.descripcion(),
                entrada.perdidaBruta());
        return "ERO-%s-%s"
                .formatted(entrada.fechaDeteccion().toLocalDate().toString().replace("-", ""), huella(hecho));
    }

    /**
     * Separa los campos del hecho con un caracter que no puede aparecer dentro de
     * ninguno. Con una coma, «Custodia, conciliacion» + «X» y «Custodia» +
     * «conciliacion, X» darian la misma huella, y dos eventos distintos colisionarian.
     */
    private static final String SEPARADOR = String.valueOf((char) 0x1f);

    /** Seis caracteres del SHA-256 del hecho: entran en el VARCHAR(20) de la columna. */
    private static String huella(String hecho) {
        try {
            byte[] digerido = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(hecho.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 3; i++) {
                hex.append("%02X".formatted(digerido[i]));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("esta JVM no trae SHA-256", imposible);
        }
    }

    private static Moneda monedaDe(String moneda) {
        try {
            return Moneda.valueOf(moneda.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException noEsMoneda) {
            throw new ErrorDeNegocio(CodigoError.de(54, 3), "La moneda '" + moneda + "' no existe.");
        }
    }

    private static Dinero importe(String monto, Moneda moneda) {
        try {
            return Dinero.de(new BigDecimal(monto), moneda);
        } catch (NumberFormatException noEsNumero) {
            throw new ErrorDeNegocio(CodigoError.de(54, 2), "El importe '" + monto + "' no es un decimal.");
        }
    }

    /** Cuanto se da de plazo cuando el registro no lo trae. Un mes calendario. */
    private static final int DIAS_POR_OMISION = 30;

    /**
     * @param recuperacion lo ya recuperado al momento de registrar. Suele ser cero: la
     *     recuperacion posterior <b>no edita este evento</b> —la tabla es append-only—
     *     sino que se registra como un movimiento propio (`R-RIS-02`).
     * @param responsableId sin responsable no hay plan de accion, y el evento queda
     *     registrado igual. Es peor perder el registro que registrar sin plan.
     */
    public record EntradaEvento(
            String categoriaEvento,
            String factorRiesgo,
            String lineaNegocio,
            String descripcion,
            OffsetDateTime fechaOcurrencia,
            OffsetDateTime fechaDeteccion,
            String perdidaBruta,
            Optional<String> recuperacion,
            String moneda,
            Optional<UUID> responsableId,
            Optional<String> accionComprometida,
            Optional<LocalDate> fechaCompromiso) {}

    public record SalidaEvento(
            UUID eventoId, String codigo, String perdidaNeta, String moneda, Optional<UUID> planAccionId) {}
}
