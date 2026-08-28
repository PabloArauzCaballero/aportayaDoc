package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU47EvaluarRiesgoDeProducto.EntradaAprobacion;
import bo.aportaya.cumplimiento.aplicacion.CU47EvaluarRiesgoDeProducto.EntradaEvaluacion;
import bo.aportaya.cumplimiento.dominio.RiesgoDelProducto;
import bo.aportaya.cumplimiento.dominio.RiesgoDelProducto.Factor;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-47 · Lo que la base y el caso de uso rechazan. */
class CU47RechazosTest extends BaseDeCumplimiento {

    private String producto;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        producto = "pasanaku-" + UUID.randomUUID().toString().substring(0, 8);
        ctx = ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
        dsl.execute("DELETE FROM catalogo.licencia_regulatoria");
        fixtura.licencia("OTORGADA", "[\"RETIRO\"]", LocalDate.now().plusYears(2));
    }

    private static List<RiesgoDelProducto.Riesgo> losCuatro() {
        return List.of(
                new RiesgoDelProducto.Riesgo(Factor.CLIENTE, "Sin historial", 4, 4),
                new RiesgoDelProducto.Riesgo(Factor.PRODUCTO, "Efectivo", 2, 2),
                new RiesgoDelProducto.Riesgo(Factor.CANAL, "Digital", 2, 2),
                new RiesgoDelProducto.Riesgo(Factor.GEOGRAFIA, "Frontera", 2, 2));
    }

    private EntradaEvaluacion entrada(boolean noObjecion) {
        return new EntradaEvaluacion(
                producto, "RETIRO", losCuatro(), Map.of(0, List.of("KYC reforzado")), "[]", "[]", noObjecion);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Cada version conserva su historico: editarla borraria la razon por la que el
        // producto se aprobo como estaba antes, que es justo lo que un supervisor pide
        // ver cuando algo sale mal.
        var primera = transaccion.execute(t -> productoCU.evaluar(entrada(false), ctx));
        transaccion.execute(t -> productoCU.aprobar(
                new EntradaAprobacion(primera.evaluacionId(), ctx.usuarioId(), true, false, "ACTA-1"), ctx));
        transaccion.execute(t -> productoCU.evaluar(entrada(false), ctx));

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.evaluacion_riesgo_producto WHERE id = ? AND version = 1 AND estado = 'APROBADA'",
                        primera.evaluacionId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.evaluacion_riesgo_producto WHERE producto = ?",
                        producto))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Todo cambio relevante emite su evento en la MISMA transaccion.
        var evaluacion = transaccion.execute(t -> productoCU.evaluar(entrada(false), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.evaluacion_producto_creada' AND agregado_id = ?
                           AND payload->>'nivelRiesgoLft' IS NOT NULL
                        """,
                        evaluacion.evaluacionId()))
                .isEqualTo(1);

        // Y si la transaccion se deshace, el evento se va con ella.
        try {
            transaccion.execute(t -> {
                productoCU.evaluar(entrada(false), ctx);
                throw new IllegalStateException("fallo despues de emitir");
            });
        } catch (RuntimeException esperado) {
            // el rollback es el punto de la prueba
        }
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.evaluacion_riesgo_producto WHERE producto = ?",
                        producto))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-LIC-01")
    void rechazaRLIC01() {
        // No se habilita un servicio fuera del alcance autorizado: lanzar fuera de la
        // licencia no es un riesgo del producto, es operar sin permiso.
        assertThatThrownBy(() -> transaccion.execute(t -> productoCU.evaluar(
                        new EntradaEvaluacion(
                                producto,
                                "CUSTODIA_DE_VALORES",
                                losCuatro(),
                                Map.of(0, List.of("KYC reforzado")),
                                "[]",
                                "[]",
                                false),
                        ctx)))
                .hasMessageContaining("excede el alcance");

        // Y con la licencia revocada, ni siquiera lo autorizado pasa.
        dsl.execute("UPDATE catalogo.licencia_regulatoria SET estado = 'REVOCADA'");
        assertThatThrownBy(() -> transaccion.execute(t -> productoCU.evaluar(entrada(false), ctx)))
                .hasMessageContaining("excede el alcance");
    }

    @Test
    @DisplayName("rechaza por R-LIC-03")
    void rechazaRLIC03() {
        // Toda politica vigente tiene acta de aprobacion. En este caso de uso, la
        // aprobacion viene de un comite con quorum, y sin el no se aprueba.
        var evaluacion = transaccion.execute(t -> productoCU.evaluar(entrada(false), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> productoCU.aprobar(
                        new EntradaAprobacion(evaluacion.evaluacionId(), ctx.usuarioId(), false, false, "ACTA-1"),
                        ctx)))
                .hasMessageContaining("comite con quorum");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.evaluacion_riesgo_producto WHERE id = ? AND estado = 'BORRADOR'",
                        evaluacion.evaluacionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-LIC-04")
    void rechazaRLIC04() {
        // Un producto que exige no objecion no entra en vigencia sin ella.
        var evaluacion = transaccion.execute(t -> productoCU.evaluar(entrada(true), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> productoCU.aprobar(
                        new EntradaAprobacion(evaluacion.evaluacionId(), ctx.usuarioId(), true, false, "ACTA-1"), ctx)))
                .hasMessageContaining("no objecion");

        // HUECO: `ck_evaluacion_no_objecion` y `ck_evaluacion_vigente_aprobada` vigilan
        // el estado 'VIGENTE', que `ck_evaluacion_riesgo_producto_estado` no admite
        // (APROBADA, BORRADOR, EN_EVALUACION, RECHAZADA). **No pueden dispararse nunca.**
        // La regla la sostiene el caso de uso, no la base. Se demuestra: la fila con
        // requiere_no_objecion y sin fecha de aprobacion entra sin protesta.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.evaluacion_riesgo_producto
                            (producto, version, riesgos_identificados, nivel_riesgo_lft, controles_definidos,
                             requiere_no_objecion, estado)
                        VALUES (?, 99, '[]'::jsonb, 'ALTO', '[]'::jsonb, true, 'APROBADA')
                        """,
                        producto))
                .isEmpty();
    }

    @Test
    @DisplayName("rechaza por R-UIF-09")
    void rechazaRUIF09() {
        // No se opera sin debida diligencia vigente. En la evaluacion del producto eso
        // se traduce en que el factor CLIENTE es obligatorio: es donde se declara que
        // clase de cliente va a poder usarlo.
        assertThatThrownBy(() -> transaccion.execute(t -> productoCU.evaluar(
                        new EntradaEvaluacion(
                                producto,
                                "RETIRO",
                                List.of(
                                        new RiesgoDelProducto.Riesgo(Factor.PRODUCTO, "Efectivo", 2, 2),
                                        new RiesgoDelProducto.Riesgo(Factor.CANAL, "Digital", 2, 2),
                                        new RiesgoDelProducto.Riesgo(Factor.GEOGRAFIA, "Frontera", 2, 2)),
                                Map.of(),
                                "[]",
                                "[]",
                                false),
                        ctx)))
                .hasMessageContaining("CLIENTE");
    }
}
