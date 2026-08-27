package bo.aportaya.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.auditoria.aplicacion.CU98PublicarTablero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Lo que CU-98 rechaza, y por qué. */
class CU98RechazosTest extends BaseDeAuditoria {

    @Test
    @DisplayName("rls · R-SEG-03: por debajo del mínimo de casos no sale el valor, ni por la serie ni por el detalle")
    void rlsElValorPorDebajoDelMinimoNoSalePorNingunLado() {
        // `R-SEG-03` — un usuario solo ve lo que le corresponde. En un tablero la
        // amenaza no es leer la fila ajena: es DEDUCIRLA. Un promedio de tres personas
        // identifica a las tres, y por eso el piso de muestra es parte de la
        // definición del indicador y no una opción de la interfaz.
        UUID definicion = fixtura.definicion("PUNTUALIDAD_MEDIA_71", "RIESGO", "MAYOR_ES_MEJOR", 10, "v1");
        fixtura.indicador(definicion, "PUNTUALIDAD_MEDIA_71", "2071-01", "88.0000", null, 9, false);
        fixtura.indicador(definicion, "PUNTUALIDAD_MEDIA_71", "2071-02", "91.0000", null, 9, false);

        var indicador = tablero("2071-02").indicadores().get(0);

        assertThat(indicador.suprimidoPorPrivacidad()).isTrue();
        assertThat(indicador.valor()).isEmpty();
        // La serie es la puerta de atrás más obvia: con dos puntos publicados, el valor
        // suprimido se lee igual. Por eso también se calla.
        assertThat(indicador.serie()).isEmpty();
        // Y la variación es la segunda puerta: publicar «+3,4 % contra enero» sobre un
        // valor oculto permite reconstruirlo en cuanto se conozca cualquier punto.
        assertThat(indicador.variacionPeriodoAnterior()).isEmpty();
        assertThat(indicador.cumpleMeta()).isEmpty();
    }

    @Test
    @DisplayName("rls · sin casos registrados la duda se resuelve suprimiendo, no publicando")
    void sinCasosSeSuprime() {
        // Un indicador con mínimo declarado y sin `casos` es un valor cuyo tamaño de
        // muestra nadie sabe. Publicarlo sería apostar con datos de personas.
        UUID definicion = fixtura.definicion("PUNTUALIDAD_MEDIA_72", "RIESGO", "MAYOR_ES_MEJOR", 10, "v1");
        fixtura.indicador(definicion, "PUNTUALIDAD_MEDIA_72", "2072-02", "91.0000", null, null, false);

        assertThat(tablero("2072-02").indicadores().get(0).suprimidoPorPrivacidad())
                .isTrue();
    }

    @Test
    @DisplayName("cuadre · R-BIL-12 y R-AUD-07: un período sin cuadrar sale marcado, no sale limpio")
    void cuadreUnPeriodoSinCuadrarSaleMarcado() {
        // `R-BIL-12` no se cierra el día con descuadre abierto, y `R-AUD-07` los saldos
        // diarios se cierran encadenados: las dos son de CU-51 y `auditoria` NO puede
        // comprobarlas —viven en el esquema de `nucleo-financiero`, que no puede leer—.
        //
        // Lo que sí puede, y es lo que se prueba acá, es no mentir: quien calculó el
        // indicador marca `provisorio`, y el tablero lo propaga en vez de presentar el
        // número como definitivo. Un indicador sobre datos sin cuadrar es una opinión.
        UUID definicion = fixtura.definicion("VOLUMEN_APORTADO_73", "FINANZAS", "MAYOR_ES_MEJOR", 0, "v1");
        fixtura.indicador(definicion, "VOLUMEN_APORTADO_73", "2073-02", "100.0000", null, 300, true);
        fixtura.indicador(definicion, "GRUPOS_ACTIVOS_73", "2073-02", "12.0000", null, 12, false);

        var salida = tablero("2073-02");

        // Basta uno sin cuadrar para que el tablero entero quede condicionado: citar el
        // definitivo de al lado como si el período estuviera cerrado es el error que
        // esta marca existe para impedir.
        assertThat(salida.provisorio()).isTrue();
    }

    @Test
    @DisplayName("rechaza · R-LIC-03: la BASE impide que un indicador quede sin su definición")
    void actaUnIndicadorNoPuedeQuedarSinDefinicion() {
        // `R-LIC-03` toda política vigente tiene acta de aprobación. La definición de un
        // indicador ES una política: fija qué se mide, con qué fórmula y con qué meta se
        // juzga. Un número publicado sin ella es un número que nadie aprobó.
        //
        // Y no lo hace cumplir la aplicación: lo hace cumplir el motor. Se intenta dejar
        // el indicador huérfano y PostgreSQL lo rechaza — que es donde tiene que estar
        // la garantía, porque un `UPDATE` desde una consola también pasa por ahí.
        UUID definicion = fixtura.definicion("TASA_DE_MOROSIDAD_74", "RIESGO", "MENOR_ES_MEJOR", 0, "v1");
        fixtura.indicador(definicion, "TASA_DE_MOROSIDAD_74", "2074-02", "5.0000", null, 400, false);

        assertThatThrownBy(() -> fixtura.desligarDeSuDefinicion()).hasMessageContaining("definicion_indicador_id");

        // Y el indicador sigue publicándose, con su definición intacta.
        assertThat(tablero("2074-02").indicadores()).hasSize(1);
    }

    @Test
    @DisplayName("rechaza · R-AUD-01: la BASE no deja pisar un indicador ya publicado")
    void rechazaModificarUnIndicadorPublicado() {
        // `R-AUD-01` las tablas de dinero y auditoría no admiten UPDATE ni DELETE.
        //
        // Es lo que sostiene la promesa de CU-98 de que la serie anterior sigue
        // disponible: si un recálculo pudiera pisar la fila, el número viejo
        // desaparecería sin dejar rastro y el corte de serie sería imposible de
        // señalar. Un indicador corregido entra como fila NUEVA, con la versión de
        // definición con que se recalculó.
        //
        // Y no lo hace cumplir la aplicación: lo hace cumplir el motor, que es donde
        // tiene que estar — un UPDATE desde una consola también pasa por ahí.
        UUID definicion = fixtura.definicion("CIERRES_CUADRADOS_60", "OPERACION", "MAYOR_ES_MEJOR", 0, "v1");
        fixtura.indicador(definicion, "CIERRES_CUADRADOS_60", "2060-01", "99.0000", null, 30, false);

        assertThatThrownBy(() -> fixtura.pisarElValor("CIERRES_CUADRADOS_60"))
                .hasMessageMatching("(?s).*(append|R-AUD-01|no admite|UPDATE).*");

        assertThatThrownBy(() -> fixtura.borrarIndicador("CIERRES_CUADRADOS_60"))
                .hasMessageMatching("(?s).*(append|R-AUD-01|no admite|DELETE).*");
    }

    @Test
    @DisplayName("rechaza una dimensión que la tabla no admite, en vez de devolver cero indicadores")
    void rechazaDimensionInexistente() {
        assertThatThrownBy(() -> transaccion.execute(estado -> tableroCU.ejecutar(
                        new CU98PublicarTablero.EntradaTablero("2075-02", "PLATAFORMA", Optional.empty(), 6),
                        contexto())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("GLOBAL");
    }

    @Test
    @DisplayName("rechaza pedir una dimensión de grupo sin decir de qué grupo")
    void rechazaDimensionSinIdentificador() {
        assertThatThrownBy(() -> transaccion.execute(estado -> tableroCU.ejecutar(
                        new CU98PublicarTablero.EntradaTablero("2076-02", "POR_GRUPO", Optional.empty(), 6),
                        contexto())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("identificador");
    }
}
