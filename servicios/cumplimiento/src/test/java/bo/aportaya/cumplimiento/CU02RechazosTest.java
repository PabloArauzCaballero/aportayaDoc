package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.cumplimiento.aplicacion.CU03DeclararPep.EntradaDeclaracion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-02 · las pruebas de RECHAZO, en su propio archivo.
 *
 * <p>Separadas de {@link CU02Test} no por estetica: son otra pregunta. Aquellas
 * verifican que el caso de uso hace lo que promete; estas, que la BASE rechaza lo
 * que no debe entrar aunque la aplicacion se equivoque.
 */
class CU02RechazosTest extends BaseDeCumplimiento {

    @Test
    @DisplayName("rechaza por R-UIF-09")
    void rechazaRUIF09() {
        ContextoSesion ctx = contexto();

        assertThat(rechazaLaBase("SELECT fn_uif_exigir_ddd('%s')".formatted(ctx.usuarioId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-UIF-10")
    void rechazaRUIF10() {
        ContextoSesion ctx = contexto();
        transaccion.execute(e -> pepCU.ejecutar(
                new EntradaDeclaracion(
                        ctx.usuarioId(),
                        true,
                        Optional.of("NACIONAL"),
                        Optional.of("Director"),
                        Optional.of("Ministerio"),
                        List.of()),
                ctx));
        UUID unico = fixtura.usuario();

        // Completar con la MISMA persona en las dos firmas: el trigger lo rechaza.
        assertThat(rechazaLaBase(
                        """
                        UPDATE cumplimiento.debida_diligencia
                           SET estado = 'COMPLETA', aprobada_por = '%s', segunda_revision_por = '%s'
                         WHERE usuario_id = '%s'
                        """
                                .formatted(unico, unico, ctx.usuarioId())))
                .contains("R-UIF-10");
    }

    @Test
    @DisplayName("rechaza por R-UIF-11")
    void rechazaRUIF11() {
        ContextoSesion ctx = contexto();
        transaccion.execute(e -> pepCU.ejecutar(EntradaDeclaracion.noPep(ctx.usuarioId()), ctx));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.calificacion_riesgo_cliente
                            (id, usuario_id, nivel, puntaje_total, nivel_dd_requerido,
                             periodicidad_revision_meses, vigente_desde, proxima_revision, es_automatica)
                        VALUES (gen_random_uuid(), '%s', 'MEDIO', 0, 'ESTANDAR', 12, now(),
                                current_date + 365, true)
                        """
                                .formatted(ctx.usuarioId())))
                .contains("ex_calificacion_vigente");
    }

    @Test
    @DisplayName("rechaza por R-LIM-01")
    void rechazaRLIM01() {
        // fn_lim_evaluar deniega por omision: sin limite configurado para el concepto
        // y el nivel, la operacion no pasa. La autoridad es la base.
        assertThat(rechazaLaBase(
                        "SELECT fn_lim_evaluar('%s', 'CONCEPTO_INEXISTENTE', 1.00)".formatted(UUID.randomUUID())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-LIM-03")
    void rechazaRLIM03() {
        // R-LIM-03 quiere que dos limites activos del mismo concepto, nivel y ventana
        // no solapen vigencias, y lo escribe como un EXCLUDE sobre daterange.
        //
        // HALLAZGO: ese EXCLUDE esta TAPADO. Sobre la misma tabla hay un indice unico
        // en (nivel_debida_diligencia, ventana, concepto) **sin la fecha**, que es
        // estrictamente mas estricto y salta primero. En la practica la regla que
        // rige es «un limite por combinacion, punto», no «vigencias sin solape»:
        // hoy es imposible cargar el limite del mes que viene antes de que empiece.
        // Se prueba lo que la base hace de verdad, y queda declarado en el informe
        // del carril: bajar el indice unico seria una decision de modelo, troncal, y
        // no de este carril.
        dslFixtura.execute(
                """
                INSERT INTO catalogo.limite_operativo_billetera
                    (id, concepto, nivel_debida_diligencia, ventana, monto_maximo, moneda,
                     base_normativa, vigente_desde, vigente_hasta, activo)
                VALUES (gen_random_uuid(), 'TRANSFERENCIA', 'ESTANDAR', 'MES', 1000, 'BOB',
                        'ASFI 540/2025', current_date - 10, current_date + 10, true)
                ON CONFLICT DO NOTHING
                """);

        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO catalogo.limite_operativo_billetera
                            (id, concepto, nivel_debida_diligencia, ventana, monto_maximo, moneda,
                             base_normativa, vigente_desde, vigente_hasta, activo)
                        VALUES (gen_random_uuid(), 'TRANSFERENCIA', 'ESTANDAR', 'MES', 2000, 'BOB',
                                'ASFI 540/2025', current_date, current_date + 20, true)
                        """))
                .contains("uq_limite_operativo_billetera");

        dslFixtura.execute("DELETE FROM catalogo.limite_operativo_billetera WHERE concepto = 'TRANSFERENCIA'");
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        ContextoSesion ctx = contexto();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.caso_investigacion_lft
                            (id, codigo, usuario_id, analista_id, revisado_por, origen, estado,
                             prioridad, resumen, abierto_en, plazo_limite)
                        VALUES (gen_random_uuid(), 'LFT-DOS', '%s', '%s', '%s', 'ALERTA', 'ABIERTO',
                                'ALTA', 'Cuatro ojos', now(), now() + interval '30 days')
                        """
                                .formatted(ctx.usuarioId(), ctx.usuarioId(), ctx.usuarioId())))
                .contains("ck_caso_revision");
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        ContextoSesion ctx = contexto();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.expediente_cliente
                            (id, usuario_id, completitud_porcentaje, documentos, estado,
                             retencion_hasta, ultima_actualizacion)
                        VALUES (gen_random_uuid(), '%s', 100, '{}'::jsonb, 'COMPLETO',
                                current_date - 1, now())
                        """
                                .formatted(ctx.usuarioId())))
                .contains("ck_expediente_retencion_futura");
    }
}
