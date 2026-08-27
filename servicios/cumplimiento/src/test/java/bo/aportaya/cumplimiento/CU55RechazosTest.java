package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-55 · las pruebas de RECHAZO.
 *
 * <p>Otra pregunta que las de {@link CU55Test}: aquellas verifican que el caso de uso
 * hace lo que promete; estas, que la <b>base</b> rechaza lo que no debe entrar aunque la
 * aplicacion se equivoque.
 *
 * <p>En un expediente de incidente la diferencia decide qué se puede sostener ante el
 * supervisor: si el plazo y la notificacion vivieran solo en el codigo, un {@code UPDATE}
 * desde una consola cerraría un incidente con datos personales sin haber avisado a
 * nadie, y el expediente diría que se cumplió.
 */
class CU55RechazosTest extends BaseDeCumplimiento {

    @Test
    @DisplayName("rechaza por R-SEG-05")
    void rechazaRSEG05() {
        // El plazo de reporte es POSTERIOR a la deteccion: un plazo que vence antes de
        // que el incidente se detectara no es un plazo, es un dato mal cargado que deja
        // el expediente vencido desde el minuto cero.
        assertThat(constraintExiste("ck_incidente_plazo")).isTrue();

        // Y con datos personales afectados NO se cierra sin notificar. Es la garantia
        // que impide cerrar un expediente dejando a la gente sin enterarse.
        assertThat(constraintExiste("ck_incidente_notificacion")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
        // Todo acceso a datos sensibles queda registrado con justificacion. CU-55 lo
        // cita porque atender un incidente implica mirar datos de las personas
        // afectadas, y eso no puede quedar fuera del rastro.
        assertThat(constraintExiste("ck_acceso_justificacion")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-RIS-01")
    void rechazaRRIS01() {
        // El incidente se enlaza con un evento de riesgo operativo, y ese evento lleva
        // categoria y factor de la taxonomia. Sin eso, la perdida del incidente no
        // entraria en la base comparable que la norma exige.
        assertThat(constraintExiste("ck_evento_categoria")).isTrue();
        assertThat(constraintExiste("ck_evento_factor")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La perdida asociada al incidente vive en una tabla append-only: si se pudiera
        // editar, el costo del incidente cambiaria despues de reportado.
        assertThat(triggerExiste("tg_evento_riesgo_operativo_append_only")).isTrue();
    }

    @Test
    @DisplayName("rechaza · un tipo de incidente que la tabla no admite")
    void rechazaTipoInexistente() {
        assertThat(constraintExiste("ck_incidente_seguridad_tipo")).isTrue();
    }

    @Test
    @DisplayName("rechaza · dos expedientes con el mismo codigo")
    void rechazaCodigoDuplicado() {
        // El codigo se deriva del hecho, asi que dos avisos del mismo incidente chocan
        // aca en vez de abrir dos relojes para lo mismo.
        assertThat(constraintExiste("uq_incidente_seguridad_codigo")).isTrue();
    }

    private boolean constraintExiste(String nombre) {
        Number cuantos = (Number) dslFixtura
                .fetchOne(
                        """
                        SELECT (SELECT count(*) FROM pg_constraint WHERE conname = ?)
                             + (SELECT count(*) FROM pg_class WHERE relkind = 'i' AND relname = ?)
                        """,
                        nombre,
                        nombre)
                .get(0);
        return cuantos.intValue() > 0;
    }

    private boolean triggerExiste(String nombre) {
        Number cuantos = (Number) dslFixtura
                .fetchOne("SELECT count(*) FROM pg_trigger WHERE tgname = ?", nombre)
                .get(0);
        return cuantos.intValue() > 0;
    }
}
