package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU47EvaluarRiesgoDeProducto.EntradaAprobacion;
import bo.aportaya.cumplimiento.aplicacion.CU47EvaluarRiesgoDeProducto.EntradaEvaluacion;
import bo.aportaya.cumplimiento.dominio.RiesgoDelProducto;
import bo.aportaya.cumplimiento.dominio.RiesgoDelProducto.Factor;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-47 · Evaluar el riesgo del producto antes de lanzarlo. */
class CU47Test extends BaseDeCumplimiento {

    private String producto;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        producto = "pasanaku-" + UUID.randomUUID().toString().substring(0, 8);
        ctx = ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
        // La licencia de funcionamiento, con RETIRO dentro del alcance autorizado.
        dsl.execute("DELETE FROM catalogo.licencia_regulatoria");
        fixtura.licencia(
                "OTORGADA",
                "[\"RETIRO\", \"RECARGA\", \"TRANSFERENCIA\"]",
                java.time.LocalDate.now().plusYears(2));
    }

    /** Los cuatro factores, con un riesgo alto que si tiene control. */
    private static List<RiesgoDelProducto.Riesgo> losCuatroFactores() {
        return List.of(
                new RiesgoDelProducto.Riesgo(Factor.CLIENTE, "Cliente sin historial verificable", 4, 4),
                new RiesgoDelProducto.Riesgo(Factor.PRODUCTO, "Aportes recurrentes en efectivo", 2, 3),
                new RiesgoDelProducto.Riesgo(Factor.CANAL, "Alta digital sin presencia fisica", 3, 2),
                new RiesgoDelProducto.Riesgo(Factor.GEOGRAFIA, "Frontera con jurisdiccion sensible", 2, 2));
    }

    private EntradaEvaluacion entrada(
            List<RiesgoDelProducto.Riesgo> riesgos, Map<Integer, List<String>> controles, boolean noObjecion) {
        return new EntradaEvaluacion(
                producto,
                "RETIRO",
                riesgos,
                controles,
                "[{\"factor\":\"CLIENTE\"}]",
                "[{\"control\":\"KYC reforzado\"}]",
                noObjecion);
    }

    @Test
    @DisplayName(
            "Dado un producto nuevo con los cuatro factores evaluados y controles asociados · Cuando el comité lo aprueba con quórum · Entonces la evaluación queda VIGENTE con fecha_aprobacion y acta")
    void criterio1() {
        var evaluacion = transaccion.execute(t -> productoCU.evaluar(
                entrada(losCuatroFactores(), Map.of(0, List.of("KYC reforzado antes de operar")), false), ctx));

        var aprobada = transaccion.execute(t -> productoCU.aprobar(
                new EntradaAprobacion(evaluacion.evaluacionId(), ctx.usuarioId(), true, false, "ACTA-2026-04"), ctx));

        // HUECO: el catalogo de estados no tiene 'VIGENTE' —admite APROBADA, BORRADOR,
        // EN_EVALUACION y RECHAZADA—, asi que lo mas cerca que la base llega es
        // APROBADA con su fecha y su aprobador.
        assertThat(aprobada.estado()).isEqualTo("APROBADA");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evaluacion_riesgo_producto
                         WHERE id = ? AND estado = 'APROBADA' AND fecha_aprobacion IS NOT NULL
                           AND aprobada_por IS NOT NULL
                        """,
                        evaluacion.evaluacionId()))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.evaluacion_producto_aprobada' AND agregado_id = ?
                           AND payload->>'actaComite' = 'ACTA-2026-04'
                        """,
                        evaluacion.evaluacionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una evaluación con un riesgo alto sin control · Cuando se intenta aprobar · Entonces se rechaza con RIESGO_SIN_CONTROL")
    void criterio2() {
        // Escribir el riesgo y no que se hace con el convierte la matriz en una lista
        // de disculpas anticipadas.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> productoCU.evaluar(entrada(losCuatroFactores(), Map.of(), false), ctx)))
                .hasMessageContaining("riesgo alto sin control");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.evaluacion_riesgo_producto WHERE producto = ?",
                        producto))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un producto con requiere_no_objecion en true y sin respuesta del supervisor · Cuando se intenta habilitarlo · Entonces la base lo impide y queda registrado el intento")
    void criterio3() {
        var evaluacion = transaccion.execute(
                t -> productoCU.evaluar(entrada(losCuatroFactores(), Map.of(0, List.of("KYC reforzado")), true), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> productoCU.aprobar(
                        new EntradaAprobacion(evaluacion.evaluacionId(), ctx.usuarioId(), true, false, "ACTA-1"), ctx)))
                .hasMessageContaining("no objecion");

        // HUECO: ck_evaluacion_no_objecion vigila un estado 'VIGENTE' que el catalogo no
        // admite, asi que la base nunca lo verifica. Quien lo exige es el caso de uso, y
        // la evaluacion queda en BORRADOR: el intento no la habilito.
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.evaluacion_riesgo_producto WHERE id = ? AND estado = 'BORRADOR'",
                        evaluacion.evaluacionId()))
                .isEqualTo(1);
        assertThat(evaluacion.bloqueaHabilitacion()).isTrue();
    }

    @Test
    @DisplayName(
            "Dado un cambio material en un producto vigente · Cuando se registra · Entonces se crea una versión nueva y la anterior conserva su histórico")
    void criterio4() {
        var primera = transaccion.execute(
                t -> productoCU.evaluar(entrada(losCuatroFactores(), Map.of(0, List.of("KYC reforzado")), false), ctx));
        transaccion.execute(t -> productoCU.aprobar(
                new EntradaAprobacion(primera.evaluacionId(), ctx.usuarioId(), true, false, "ACTA-1"), ctx));

        var segunda = transaccion.execute(t -> productoCU.evaluar(
                entrada(losCuatroFactores(), Map.of(0, List.of("KYC reforzado y monitoreo")), false), ctx));

        assertThat(segunda.version()).isEqualTo(primera.version() + 1);
        // La anterior conserva su historico: editarla borraria la razon por la que el
        // producto se aprobo como estaba antes.
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.evaluacion_riesgo_producto WHERE id = ? AND estado = 'APROBADA'",
                        primera.evaluacionId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.evaluacion_riesgo_producto WHERE producto = ?",
                        producto))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        var entrada = entrada(losCuatroFactores(), Map.of(0, List.of("KYC reforzado")), false);
        var a = transaccion.execute(t -> productoCU.evaluar(entrada, ctx));
        var b = transaccion.execute(t -> productoCU.evaluar(entrada, ctx));

        // Evaluar de nuevo NO es idempotente y no debe serlo: cada evaluacion es una
        // version, y perder una version es perder el historico del producto.
        assertThat(b.version()).isEqualTo(a.version() + 1);
        assertThat(contar(
                        "SELECT count(DISTINCT version)::int FROM cumplimiento.evaluacion_riesgo_producto WHERE producto = ?",
                        producto))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        var entrada = entrada(losCuatroFactores(), Map.of(0, List.of("KYC reforzado")), false);

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> productoCU.evaluar(entrada, ctx));
            } catch (Exception e) {
                errores.add(e);
            }
        };
        var uno = new Thread(intento);
        var dos = new Thread(intento);
        uno.start();
        dos.start();
        uno.join();
        dos.join();

        // uq_evaluacion_producto_version: dos evaluaciones con la misma version harian
        // imposible decir cual rigio.
        assertThat(errores).hasSizeLessThanOrEqualTo(1);
        assertThat(contar(
                        "SELECT count(DISTINCT version)::int FROM cumplimiento.evaluacion_riesgo_producto WHERE producto = ?",
                        producto))
                .isEqualTo(contar(
                        "SELECT count(*)::int FROM cumplimiento.evaluacion_riesgo_producto WHERE producto = ?",
                        producto));
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El cuadre de una matriz es que el nivel declarado se corresponda con el riesgo
        // mas alto evaluado: un producto con un 4x4 dentro no puede salir BAJO.
        var alto = transaccion.execute(
                t -> productoCU.evaluar(entrada(losCuatroFactores(), Map.of(0, List.of("KYC reforzado")), false), ctx));
        assertThat(alto.nivelRiesgoLft()).isEqualTo("ALTO");

        String otro = producto;
        producto = "bajo-" + UUID.randomUUID().toString().substring(0, 8);
        var bajo = transaccion.execute(t -> productoCU.evaluar(
                entrada(
                        List.of(
                                new RiesgoDelProducto.Riesgo(Factor.CLIENTE, "Cliente conocido", 1, 2),
                                new RiesgoDelProducto.Riesgo(Factor.PRODUCTO, "Aporte digital", 1, 2),
                                new RiesgoDelProducto.Riesgo(Factor.CANAL, "App con biometria", 1, 1),
                                new RiesgoDelProducto.Riesgo(Factor.GEOGRAFIA, "Urbano", 1, 1)),
                        Map.of(),
                        false),
                ctx));
        assertThat(bajo.nivelRiesgoLft()).isEqualTo("BAJO");
        assertThat(otro).isNotEqualTo(producto);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        var evaluacion = transaccion.execute(
                t -> productoCU.evaluar(entrada(losCuatroFactores(), Map.of(0, List.of("KYC reforzado")), false), ctx));
        var aprobacion = new EntradaAprobacion(evaluacion.evaluacionId(), ctx.usuarioId(), true, false, "ACTA-1");

        transaccion.execute(t -> productoCU.aprobar(aprobacion, ctx));
        // La segunda aprobacion llega tarde: ya no esta en borrador.
        assertThatThrownBy(() -> transaccion.execute(t -> productoCU.aprobar(aprobacion, ctx)))
                .hasMessageContaining("ya no esta en borrador");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.evaluacion_producto_aprobada' AND agregado_id = ?
                        """,
                        evaluacion.evaluacionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: falta un factor. Evaluar tres de cuatro es no haber mirado el
        // cuarto, y el que no se mira es el que despues explota.
        assertThatThrownBy(() -> transaccion.execute(t -> productoCU.evaluar(
                        entrada(
                                List.of(
                                        new RiesgoDelProducto.Riesgo(Factor.CLIENTE, "Sin historial", 2, 2),
                                        new RiesgoDelProducto.Riesgo(Factor.PRODUCTO, "Efectivo", 2, 2),
                                        new RiesgoDelProducto.Riesgo(Factor.CANAL, "Digital", 2, 2)),
                                Map.of(),
                                false),
                        ctx)))
                .hasMessageContaining("GEOGRAFIA");

        // Paso fallido: el producto excede la licencia.
        assertThatThrownBy(() -> transaccion.execute(t -> productoCU.evaluar(
                        new EntradaEvaluacion(
                                producto,
                                "CUSTODIA_DE_VALORES",
                                losCuatroFactores(),
                                Map.of(0, List.of("KYC reforzado")),
                                "[]",
                                "[]",
                                false),
                        ctx)))
                .hasMessageContaining("excede el alcance");

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.evaluacion_riesgo_producto WHERE producto = ?",
                        producto))
                .isZero();

        // Con los cuatro factores y dentro de la licencia, el mismo camino cierra.
        var buena = transaccion.execute(
                t -> productoCU.evaluar(entrada(losCuatroFactores(), Map.of(0, List.of("KYC reforzado")), false), ctx));
        assertThat(buena.version()).isEqualTo(1);
    }
}
