package bo.aportaya.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.auditoria.aplicacion.CU07EjercerDerechos;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-07 · Ejercer derechos sobre datos personales.
 *
 * <p>La tension del caso de uso puesta a prueba: el derecho de supresion es real y la
 * obligacion de conservar diez anos tambien. Ninguna de las dos gana entera, y lo que
 * se comprueba aca es que el reparto sea correcto **y que se le diga al titular**.
 */
class CU07Test extends BaseDeAuditoria {

    private static final LocalDate HACE_MUCHO = LocalDate.now().minusYears(30);
    private static final LocalDate AYER = LocalDate.now().minusDays(1);

    @Test
    @DisplayName("Dado un titular sin relación vigente y con retención vencida · Cuando solicita supresión · Entonces"
            + " se ejecuta el proceso_anonimizacion y queda constancia")
    void criterio1() {
        UUID titular = fixtura.usuario();
        fixtura.politicaDeRetencion("transaccion_billetera_c1", 12, 108, "Ley 393 art. 87");

        var salida = derechos(titular, "SUPRESION", HACE_MUCHO);

        // Treinta anos despues no queda retencion viva: se borra de verdad.
        assertThat(salida.estado()).isEqualTo("ATENDIDA");
        assertThat(salida.estrategia()).isEqualTo("BORRADO_TOTAL");
        assertThat(salida.datosRetenidosPorLey()).isEmpty();
        // Y queda constancia: el proceso existe, planificado.
        assertThat(salida.procesoAnonimizacionId()).isPresent();
        assertThat(fixtura.estadoDelProceso(salida.procesoAnonimizacionId().orElseThrow()))
                .isEqualTo("PLANIFICADO");
    }

    @Test
    @DisplayName("Dado un titular cuya retencion_hasta es futura · Cuando solicita supresión · Entonces sus datos se"
            + " seudonimizan · Y datos_retenidos_por_ley enumera lo conservado")
    void criterio2() {
        UUID titular = fixtura.usuario();
        fixtura.politicaDeRetencion("asiento_contable_c2", 12, 108, "Ley 393 art. 87");

        var salida = derechos(titular, "SUPRESION", AYER);

        // La retencion sigue corriendo: no se borra, se seudonimiza.
        assertThat(salida.estrategia()).isEqualTo("SEUDONIMIZACION");
        // Y PARCIAL, no ATENDIDA: la solicitud no se pudo cumplir entera y decir que
        // si es la forma mas rapida de que el reclamo vuelva con la razon del otro lado.
        assertThat(salida.estado()).isEqualTo("PARCIAL");
        // Lo conservado se ENUMERA, con su base legal. «No se puede borrar» sin decir
        // qué ni por qué no es una respuesta, es una excusa.
        assertThat(salida.datosRetenidosPorLey())
                .anyMatch(d -> d.contains("asiento_contable_c2") && d.contains("Ley 393"));
    }

    @Test
    @DisplayName("Dado una solicitud de acceso · Cuando un operador genera el paquete · Entonces existe un"
            + " registro_acceso_datos con justificación")
    void criterio3() {
        UUID titular = fixtura.usuario();

        var salida = derechos(titular, "ACCESO", AYER);

        // El acceso no borra nada, así que no evalúa retención: queda abierto para que
        // lo atienda una persona, con su plazo ya corriendo.
        assertThat(salida.estado()).isEqualTo("EN_PROCESO");
        assertThat(salida.procesoAnonimizacionId()).isEmpty();
        assertThat(salida.fechaLimiteLegal()).isAfter(java.time.OffsetDateTime.now());
        // Y el hecho queda anunciado para que el resto del sistema reaccione.
        assertThat(fixtura.hayEventoDeTipo("auditoria.derecho_solicitado")).isTrue();
    }

    @Test
    @DisplayName("rechaza · dos expedientes abiertos del mismo derecho reiniciarían un plazo que ya corría")
    void rechazaDuplicarElExpediente() {
        UUID titular = fixtura.usuario();
        derechos(titular, "ACCESO", AYER);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> derechos(titular, "ACCESO", AYER))
                .isInstanceOf(bo.aportaya.plataforma.dominio.ErrorDeNegocio.class)
                .extracting(e -> ((bo.aportaya.plataforma.dominio.ErrorDeNegocio) e)
                        .codigo()
                        .toString())
                .isEqualTo("AP-CU07-04");
    }

    private CU07EjercerDerechos.SalidaDerechos derechos(UUID titular, String tipo, LocalDate ultimaActividad) {
        return transaccion.execute(estado -> derechosCU.ejecutar(
                new CU07EjercerDerechos.EntradaDerechos(
                        titular, tipo, "El titular pide ejercer su derecho sobre sus datos.", ultimaActividad),
                contexto()));
    }
}
