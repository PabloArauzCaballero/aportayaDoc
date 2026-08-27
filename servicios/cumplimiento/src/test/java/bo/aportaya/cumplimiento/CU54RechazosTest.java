package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-54 · las pruebas de RECHAZO.
 *
 * <p>Otra pregunta que las de {@link CU54Test}: aquellas verifican que el caso de uso
 * hace lo que promete; estas, que la <b>base</b> rechaza lo que no debe entrar aunque la
 * aplicacion se equivoque.
 *
 * <p>En una base de perdidas la diferencia es la que decide si el registro sirve ante
 * una inspeccion. Si la taxonomia y la inmutabilidad vivieran solo en el codigo, una
 * consola abierta bastaria para reclasificar un evento ya reportado al supervisor, y el
 * dato que se envio dejaria de coincidir con el que esta guardado.
 */
class CU54RechazosTest extends BaseDeCumplimiento {

    @Test
    @DisplayName("rechaza por R-RIS-01")
    void rechazaRRIS01() {
        // Todo evento lleva categoria y factor de la taxonomia, y las fechas cuadran.
        // Son las tres cosas que hacen comparable esta base de perdidas con la de
        // cualquier otra entidad; sin ellas, el registro es prosa.
        assertThat(constraintExiste("ck_evento_categoria")).isTrue();
        assertThat(constraintExiste("ck_evento_factor")).isTrue();
        assertThat(constraintExiste("ck_evento_fechas")).isTrue();

        // Y la base lo verifica de verdad, no solo lo declara.
        assertThat(rechazaLaBase(insertarEventoCon("CATEGORIA_INVENTADA", "PERSONAS")))
                .contains("ck_evento");
    }

    @Test
    @DisplayName("rechaza por R-RIS-02")
    void rechazaRRIS02() {
        // La perdida neta se DERIVA: es una columna GENERATED, no un campo que la
        // aplicacion calcula y guarda. Por eso no puede quedar desfasada, y por eso
        // tampoco se puede escribir directamente.
        assertThat(columnaEsGenerada("evento_riesgo_operativo", "perdida_neta")).isTrue();
        // Y la recuperacion no supera la perdida: si lo hiciera, la base de perdidas
        // mostraria una ganancia.
        assertThat(constraintExiste("ck_evento_recuperacion")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Append-only por privilegios, no por convencion. `evento_riesgo_operativo` es
        // la base de la que sale el envio a la central de riesgo operativo: si se
        // pudiera editar, lo enviado y lo guardado podrian dejar de coincidir sin que
        // quede rastro de cual cambio.
        assertThat(triggerExiste("tg_evento_riesgo_operativo_append_only")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-LIC-03")
    void rechazaRLIC03() {
        // Toda politica vigente tiene acta de aprobacion. CU-54 lo cita porque el
        // comite de riesgos revisa los eventos del periodo y deja constancia: una
        // politica de riesgo operativo vigente sin acta seria una politica que nadie
        // aprobo, y el evento se estaria clasificando contra una regla sin respaldo.
        assertThat(constraintExiste("ck_politica_acta")).isTrue();
    }

    @Test
    @DisplayName("rechaza · dos eventos con el mismo codigo no entran")
    void rechazaCodigoDuplicado() {
        // El codigo se deriva del hecho, asi que dos cargas del mismo hecho chocan aca
        // en vez de duplicar la base de perdidas.
        assertThat(constraintExiste("uq_evento_riesgo_operativo_codigo")
                        || constraintExiste("ux_evento_riesgo_operativo_codigo"))
                .isTrue();
    }

    private String insertarEventoCon(String categoria, String factor) {
        return """
            INSERT INTO cumplimiento.evento_riesgo_operativo
                (id, codigo, registrado_por, categoria_evento, factor_riesgo, linea_negocio,
                 descripcion, fecha_ocurrencia, fecha_deteccion, perdida_bruta, recuperacion,
                 moneda, estado)
            SELECT gen_random_uuid(), 'ERO-PRUEBA-%s', id, '%s', '%s', 'Custodia',
                   'Prueba de taxonomia', now(), now(), 0, 0, 'BOB', 'REGISTRADO'
              FROM identidad.usuario WHERE id = '%s'
            """
                .formatted(UUID.randomUUID().toString().substring(0, 6), categoria, factor, fixtura.usuario());
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

    /**
     * Que la columna sea GENERATED es la garantia, no que el {@code INSERT} la omita.
     * Preguntarselo al catalogo es lo unico que lo comprueba: un valor correcto podria
     * venir de la aplicacion y verse igual.
     */
    private boolean columnaEsGenerada(String tabla, String columna) {
        Number cuantas = (Number) dslFixtura
                .fetchOne(
                        """
                        SELECT count(*) FROM information_schema.columns
                         WHERE table_schema = 'cumplimiento' AND table_name = ?
                           AND column_name = ? AND is_generated = 'ALWAYS'
                        """,
                        tabla,
                        columna)
                .get(0);
        return cuantas.intValue() > 0;
    }
}
