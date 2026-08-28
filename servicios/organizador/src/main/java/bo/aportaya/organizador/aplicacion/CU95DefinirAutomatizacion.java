package bo.aportaya.organizador.aplicacion;

import bo.aportaya.organizador.dominio.AccionSensible;
import bo.aportaya.organizador.infraestructura.AutomatizacionRepositorio;
import bo.aportaya.plataforma.datos.Datos;
import bo.aportaya.plataforma.dominio.CodigoError;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.mensajeria.EventoDominio;
import bo.aportaya.plataforma.mensajeria.Outbox;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CU-95 · Definir una regla de automatizacion.
 *
 * <p>Automatizar es delegar decisiones. **Las que mueven plata ajena exigen
 * confirmacion humana** (R-ORG-06): una regla que ejecuta la entrega sola es una regla
 * que un dia le entrega el fondo al participante equivocado, y nadie se entera hasta
 * el reclamo.
 *
 * <p>Una regla nace **inactiva**. Publicarla y encenderla en el mismo acto no deja
 * momento para revisar que la condicion diga lo que se cree que dice.
 */
@Service
public class CU95DefinirAutomatizacion {

    /** Los dos que admite {@code ck_regla_automatizacion_disparador}. */
    private static final Set<String> DISPARADORES = Set.of("CRON", "EVENTO");

    private final Datos datos;
    private final AutomatizacionRepositorio automatizaciones;
    private final Outbox outbox;

    public CU95DefinirAutomatizacion(Datos datos, AutomatizacionRepositorio automatizaciones, Outbox outbox) {
        this.datos = datos;
        this.automatizaciones = automatizaciones;
        this.outbox = outbox;
    }

    @Transactional
    public SalidaRegla definir(EntradaRegla entrada, ContextoSesion ctx) {
        // Se comprueba ANTES de tocar la base: una regla sensible marcada como
        // automatica que llega a la tabla puede dispararse antes de que alguien la
        // revise.
        // AP-CU95-03 · R-ORG-06.
        if (!AccionSensible.esCoherente(entrada.accion(), entrada.requiereConfirmacionHumana())) {
            throw new ErrorDeNegocio(
                    CodigoError.de(95, 3),
                    "La accion " + entrada.accion() + " mueve plata ajena: exige confirmacion humana.",
                    Map.of("accionesSensibles", AccionSensible.EXIGEN_CONFIRMACION.toString()));
        }
        // AP-CU95-04.
        if (!DISPARADORES.contains(entrada.disparador())) {
            throw new ErrorDeNegocio(CodigoError.de(95, 4), "Disparador no admitido: " + entrada.disparador() + ".");
        }
        // AP-CU95-05: una condicion vacia dispara siempre. Es lo mismo que no tener
        // condicion, y no se parece en nada a lo que quien la escribio quiso decir.
        if (entrada.condicion() == null || entrada.condicion().isBlank()) {
            throw new ErrorDeNegocio(CodigoError.de(95, 5), "Una regla sin condicion se dispara siempre: escribila.");
        }

        return datos.conContexto(ctx, dsl -> {
            // AP-CU95-01: dos reglas con el mismo codigo hacen imposible saber cual
            // corrio cuando algo sale mal.
            if (automatizaciones.existeCodigoDeRegla(dsl, entrada.codigo())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(95, 1), "Ya hay una regla con el codigo " + entrada.codigo() + ".");
            }

            UUID reglaId = automatizaciones.crearRegla(
                    dsl,
                    entrada.codigo(),
                    entrada.descripcion(),
                    entrada.disparador(),
                    entrada.expresionDisparo(),
                    entrada.condicion(),
                    entrada.accion(),
                    entrada.requiereConfirmacionHumana(),
                    entrada.prioridad(),
                    // Nace inactiva, siempre. Encenderla es un acto aparte y deliberado.
                    false);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.regla_definida",
                            "regla_automatizacion",
                            reglaId,
                            Map.of(
                                    "codigo", entrada.codigo(),
                                    "accion", entrada.accion(),
                                    "requiereConfirmacionHumana",
                                            Boolean.toString(entrada.requiereConfirmacionHumana())),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaRegla(reglaId, entrada.codigo(), false, entrada.requiereConfirmacionHumana());
        });
    }

    /**
     * Enciende la regla.
     *
     * <p>Se vuelve a comprobar la coherencia: una regla puede haber quedado guardada
     * antes de que la accion pasara a ser sensible, y encenderla sin revisar seria
     * exactamente el caso que R-ORG-06 quiere evitar.
     */
    @Transactional
    public SalidaRegla activar(UUID reglaId, ContextoSesion ctx) {
        return datos.conContexto(ctx, dsl -> {
            var regla = automatizaciones
                    .verRegla(dsl, reglaId)
                    .orElseThrow(() -> new ErrorDeNegocio(CodigoError.de(95, 2), "Esa regla no existe."));

            if (!AccionSensible.esCoherente(regla.accion(), regla.requiereConfirmacionHumana())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(95, 3),
                        "La regla " + regla.codigo() + " ejecuta " + regla.accion()
                                + " sin confirmacion humana: no se enciende.");
            }
            if (regla.activa()) {
                return new SalidaRegla(reglaId, regla.codigo(), true, regla.requiereConfirmacionHumana());
            }
            // AP-CU95-06: dos reglas activas con el mismo disparador y prioridad hacen
            // que el orden de ejecucion dependa de como la base devuelva las filas. Lo
            // impide `uq_regla_automatizacion_prioridad`; se comprueba aca para que sea
            // una regla de negocio con su mensaje y no un error 500.
            if (automatizaciones.hayPrioridadActiva(dsl, regla.disparador(), regla.prioridad())) {
                throw new ErrorDeNegocio(
                        CodigoError.de(95, 6),
                        "Ya hay una regla " + regla.disparador() + " activa con prioridad " + regla.prioridad() + ".");
            }

            dsl.execute("UPDATE organizador.regla_automatizacion SET activa = true WHERE id = ?", reglaId);

            outbox.emitir(
                    dsl,
                    new EventoDominio(
                            "organizador.regla_activada",
                            "regla_automatizacion",
                            reglaId,
                            Map.of("codigo", regla.codigo(), "accion", regla.accion()),
                            UUID.fromString(ctx.traza().id())));

            return new SalidaRegla(reglaId, regla.codigo(), true, regla.requiereConfirmacionHumana());
        });
    }

    public record EntradaRegla(
            String codigo,
            String descripcion,
            String disparador,
            String expresionDisparo,
            String condicion,
            String accion,
            boolean requiereConfirmacionHumana,
            int prioridad) {}

    public record SalidaRegla(UUID reglaId, String codigo, boolean activa, boolean requiereConfirmacionHumana) {}
}
