package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import bo.aportaya.cumplimiento.aplicacion.CU55GestionarIncidente;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-55 · Gestionar un incidente de seguridad de la informacion.
 *
 * <p>Lo que se pone a prueba son los <b>tres relojes</b>: que el plazo de reporte quede
 * guardado y no se recalcule, que el vencimiento no pase inadvertido, y que el incidente
 * en un proveedor quede enlazado a su contrato.
 */
class CU55Test extends BaseDeCumplimiento {

    private static final OffsetDateTime AHORA = OffsetDateTime.now();

    @BeforeEach
    void hayResponsableDesignado() {
        // Precondicion 2 del caso de uso, puesta explicitamente. Que la fixtura tenga
        // que ponerla es la prueba de que el caso de uso no la asume.
        incidentes.designarResponsableDeSeguridad(fixtura.usuario());
    }

    @Test
    @DisplayName("Dado un incidente con datos personales afectados · Cuando se registra · Entonces plazo_reporte queda"
            + " guardado · Y al reportar se completa reportado_al_organismo_en")
    void criterio1() {
        UUID activo = incidentes.activo("ACT-" + corto(), true, Optional.empty());

        var salida = registrar("FUGA_DE_DATOS", "CRITICA", Optional.of(activo), false, 1200);

        // El plazo QUEDA GUARDADO, no se recalcula al consultar: si se recalculara,
        // bastaria cambiar la politica el mes que viene para que este incidente
        // apareciera reportado en plazo cuando no lo estuvo.
        //
        // La tolerancia es de UN MICROSEGUNDO, y esta declarada en vez de disimulada:
        // `timestamptz` guarda microsegundos y REDONDEA el nanosegundo que trae Java.
        // Exigir igualdad exacta haria fallar la prueba por una diferencia que impone el
        // motor; aflojar mas escondería un plazo mal calculado.
        assertThat(incidentes.plazoDeReporte(salida.incidenteId()))
                .isCloseTo(salida.plazoReporte(), within(1, java.time.temporal.ChronoUnit.MICROS));
        assertThat(salida.plazoReporte()).isAfter(AHORA);
        // El activo le gana a lo declarado: se marco false y el inventario dice true.
        assertThat(salida.requiereNotificarTitulares()).isTrue();
        assertThat(incidentes.reportadoEn(salida.incidenteId())).isEmpty();

        transaccion.execute(estado -> {
            incidenteCU.reportarAlOrganismo(salida.incidenteId(), contexto());
            return null;
        });

        assertThat(incidentes.reportadoEn(salida.incidenteId())).isPresent();
    }

    @Test
    @DisplayName("Dado un incidente contenido pero no reportado dentro del plazo · Cuando corre el control diario ·"
            + " Entonces figura como vencido y genera hallazgo")
    void criterio2() {
        // El incidente se detecto hace diez dias y el plazo de un ALTA son 48 horas: ya
        // vencio de verdad. La primera version torcia `plazo_reporte` hacia atras y la
        // base la rechazo —`ck_incidente_plazo` exige que el plazo sea POSTERIOR a la
        // deteccion—, que es exactamente lo que esa restriccion existe para impedir: un
        // expediente que nace vencido.
        var salida = registrarViejo("MALWARE", "ALTA", 10);

        var abiertos = transaccion.execute(estado -> escalarIncidentesCU.ejecutar(deSistema()));

        assertThat(abiertos).isNotEmpty();
        assertThat(riesgos.hallazgoAbiertoDelPlan(salida.incidenteId())).isTrue();
        // El hallazgo NO reemplaza al reporte: el incidente sigue sin reportar, y eso
        // tiene que seguir viendose.
        assertThat(incidentes.reportadoEn(salida.incidenteId())).isEmpty();
    }

    @Test
    @DisplayName("Dado un incidente en un proveedor crítico · Cuando se registra · Entonces queda enlazado al"
            + " contrato_tercero correspondiente")
    void criterio3() {
        UUID contrato = incidentes.contratoTercero("Proveedor Critico " + corto());
        UUID activo = incidentes.activo("ACT-" + corto(), true, Optional.of(contrato));

        var salida = registrar("ACCESO_NO_AUTORIZADO", "ALTA", Optional.of(activo), true, 50);

        // El enlace se resuelve POR EL ACTIVO: `incidente_seguridad` no tiene columna
        // propia para el contrato, y agregarsela duplicaria un dato que ya vive —y se
        // mantiene— en el inventario de activos. La responsabilidad frente al cliente no
        // se terceriza, pero el contrato hay que poder mirarlo.
        assertThat(salida.contratoTerceroId()).contains(contrato);
    }

    @Test
    @DisplayName("rechaza · sin responsable de seguridad designado no se abre el incidente")
    void rechazaSinResponsable() {
        incidentes.bajarResponsablesDeSeguridad();

        // No es burocracia: sin designacion activa no hay a quien le corran los plazos
        // ni quien firme ante el organismo, y el expediente no tendria dueno.
        assertThatThrownBy(() -> registrar("PHISHING", "MEDIA", Optional.empty(), false, 0))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU55-01");
    }

    @Test
    @DisplayName("rechaza · notificar titulares de un incidente que no afectó datos personales")
    void rechazaNotificarSinAfectados() {
        var salida = registrar("DENEGACION_SERVICIO", "BAJA", Optional.empty(), false, 0);

        // Asustar a gente sin motivo gasta la credibilidad del canal para cuando de
        // verdad haga falta.
        assertThatThrownBy(() -> transaccion.execute(estado -> {
                    incidenteCU.notificarTitulares(salida.incidenteId(), contexto());
                    return null;
                }))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU55-03");
    }

    @Test
    @DisplayName("rechaza · un incidente vencido se reporta igual, y queda registrado que llegó tarde")
    void rechazaEsconderElAtraso() {
        var salida = registrarViejo("FRAUDE_TECNOLOGICO", "MEDIA", 10);

        transaccion.execute(estado -> {
            incidenteCU.reportarAlOrganismo(salida.incidenteId(), contexto());
            return null;
        });

        // Llegar tarde es una observacion; no reportar es un incumplimiento. Bloquear el
        // reporte tardio convertiria lo primero en lo segundo.
        assertThat(incidentes.reportadoEn(salida.incidenteId())).isPresent();
        assertThat(incidentes.plazoDeReporte(salida.incidenteId())).isBefore(OffsetDateTime.now());
    }

    @Test
    @DisplayName("rechaza · correr el control dos veces no abre dos hallazgos por el mismo incidente")
    void rechazaDuplicarElHallazgo() {
        var salida = registrarViejo("MALWARE", "CRITICA", 10);

        transaccion.execute(estado -> escalarIncidentesCU.ejecutar(deSistema()));
        transaccion.execute(estado -> escalarIncidentesCU.ejecutar(deSistema()));

        assertThat(riesgos.hallazgosDelPlan(salida.incidenteId())).isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza · el mismo hecho avisado dos veces no abre dos expedientes")
    void rechazaExpedienteDuplicado() {
        UUID activo = incidentes.activo("ACT-" + corto(), false, Optional.empty());
        OffsetDateTime detectado = OffsetDateTime.now().withNano(0);

        registrarEn("MALWARE", "ALTA", Optional.of(activo), detectado);

        // El monitoreo y la persona que lo vio avisan del mismo incidente. Dos
        // expedientes serian dos relojes distintos para el mismo hecho, y uno de los dos
        // vencería sin que nadie lo mirara.
        assertThatThrownBy(() -> registrarEn("MALWARE", "ALTA", Optional.of(activo), detectado))
                .isInstanceOf(RuntimeException.class);
    }

    private CU55GestionarIncidente.SalidaIncidente registrar(
            String tipo, String severidad, Optional<UUID> activo, boolean declaraDatosPersonales, int afectados) {
        return transaccion.execute(estado -> incidenteCU.registrar(
                new CU55GestionarIncidente.EntradaIncidente(
                        tipo,
                        severidad,
                        activo,
                        declaraDatosPersonales,
                        afectados,
                        OffsetDateTime.now(),
                        Optional.empty()),
                contexto()));
    }

    /**
     * Un incidente detectado hace {@code dias} dias, cuyo plazo por lo tanto ya vencio.
     *
     * <p>Es la unica forma honesta de probar el vencimiento: la base no deja mover el
     * plazo por detras de la deteccion, asi que lo que envejece es el hecho, no la fila.
     */
    private CU55GestionarIncidente.SalidaIncidente registrarViejo(String tipo, String severidad, int dias) {
        return registrarEn(
                tipo, severidad, Optional.empty(), OffsetDateTime.now().minusDays(dias));
    }

    private CU55GestionarIncidente.SalidaIncidente registrarEn(
            String tipo, String severidad, Optional<UUID> activo, OffsetDateTime detectadoEn) {
        return transaccion.execute(estado -> incidenteCU.registrar(
                new CU55GestionarIncidente.EntradaIncidente(
                        tipo, severidad, activo, false, 0, detectadoEn, Optional.empty()),
                contexto()));
    }

    private static String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static ContextoSesion deSistema() {
        return ContextoSesion.deSistema(
                UUID.fromString("00000000-0000-4000-8000-000000000055"),
                new Traza(UUID.randomUUID().toString()));
    }
}
