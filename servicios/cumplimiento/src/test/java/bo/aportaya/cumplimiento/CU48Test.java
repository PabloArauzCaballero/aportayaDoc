package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU48CalibrarReglas.EntradaActivacion;
import bo.aportaya.cumplimiento.aplicacion.CU48CalibrarReglas.EntradaRegla;
import bo.aportaya.cumplimiento.aplicacion.CU48CalibrarReglas.EntradaTriaje;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-48 · Calibrar reglas de cumplimiento y triar sus alertas. */
class CU48Test extends BaseDeCumplimiento {

    private static final String FUNDAMENTO =
            "Las operaciones responden al giro comercial declarado y verificado del titular.";

    private String codigo;
    private ContextoSesion oficial;

    @BeforeEach
    void escenario() {
        codigo = "RM-FRAC-" + UUID.randomUUID().toString().substring(0, 8);
        oficial = ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    private EntradaRegla regla(String expresion, String severidad, String accion) {
        return new EntradaRegla(
                codigo,
                "FRACCIONAMIENTO",
                "Operaciones sucesivas por debajo del umbral",
                expresion,
                "{\"tipo\":\"fraccionamiento\"}",
                "PCC01_CARGA",
                "DIA",
                null,
                null,
                severidad,
                accion,
                "Instructivo EIF art. 54");
    }

    @Test
    @DisplayName(
            "Dada una regla con umbral apuntando al catálogo y simulación bajo el techo · Cuando el oficial de cumplimiento la activa · Entonces queda vigente con vigente_desde y aprobada_por")
    void criterio1() {
        UUID reglaId = transaccion.execute(
                t -> reglaCU.crear(regla("monto_usd >= umbral('PCC01_CARGA')", "ALTA", "RETENER_OPERACION"), oficial));

        var simulacion = reglaCU.simular(10_000, 120);
        assertThat(simulacion.superaTecho()).isFalse();

        transaccion.execute(t -> {
            reglaCU.activar(
                    new EntradaActivacion(
                            reglaId,
                            oficial.usuarioId(),
                            true,
                            simulacion.superaTecho(),
                            simulacion.porcentajeTrafico()),
                    oficial);
            return null;
        });

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.regla_monitoreo_lft
                         WHERE id = ? AND activa = true AND aprobada_por IS NOT NULL AND vigente_desde IS NOT NULL
                        """,
                        reglaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una regla cuya expresión trae el número 10000 escrito · Cuando se intenta guardar · Entonces se rechaza con UMBRAL_CABLEADO")
    void criterio2() {
        // R-UIF-01: un numero dentro de la expresion obliga a desplegar para cumplir una
        // circular, y la circular no espera al despliegue.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> reglaCU.crear(regla("monto_usd >= 10000", "ALTA", "RETENER_OPERACION"), oficial)))
                .hasMessageContaining("10000");
        assertThat(contar("SELECT count(*)::int FROM cumplimiento.regla_monitoreo_lft WHERE codigo = ?", codigo))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dada una alerta abierta · Cuando el analista intenta cerrarla sin fundamento · Entonces se rechaza con ALERTA_SIN_CONCLUSION")
    void criterio3() {
        UUID reglaId = gobiernoFixtura.reglaDeMonitoreo(codigo, "MEDIA", true);
        UUID alertaId = gobiernoFixtura.alerta(
                reglaId, fixtura.usuario(), "MEDIA", "3000.00", OffsetDateTime.now(ZoneOffset.UTC));

        assertThatThrownBy(() -> transaccion.execute(t -> {
                    reglaCU.triar(new EntradaTriaje(alertaId, "SIN_MERITO", "corto"), oficial);
                    return null;
                }))
                .hasMessageContaining("sin fundamento");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.alerta_monitoreo_lft WHERE id = ? AND estado = 'ABIERTA'",
                        alertaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una alerta sin analista pasado su plazo · Cuando corre el trabajo diario · Entonces escala al oficial de cumplimiento y queda registrada como plazo incumplido")
    void criterio4() {
        UUID reglaId = gobiernoFixtura.reglaDeMonitoreo(codigo, "ALTA", true);
        UUID alertaId = gobiernoFixtura.alerta(
                reglaId,
                fixtura.usuario(),
                "ALTA",
                "9000.00",
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(10));

        // El trabajo diario busca las abiertas sin analista pasadas de plazo: una alerta
        // sin dueño no la mira nadie, y esa es la forma mas comun de que se pierda.
        var vencidas = transaccion.execute(t -> datosDePrueba(alertaId));

        assertThat(vencidas).isEqualTo(1);
    }

    private int datosDePrueba(UUID alertaId) {
        return contar(
                """
                SELECT count(*)::int FROM cumplimiento.alerta_monitoreo_lft
                 WHERE id = ? AND estado = 'ABIERTA' AND asignada_a IS NULL
                   AND detectada_en < now() - interval '5 days'
                """,
                alertaId);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        var entrada = regla("monto_usd >= umbral('PCC01_CARGA')", "ALTA", "RETENER_OPERACION");
        transaccion.execute(t -> reglaCU.crear(entrada, oficial));

        assertThatThrownBy(() -> transaccion.execute(t -> reglaCU.crear(entrada, oficial)))
                .hasMessageContaining("Ya existe una regla");
        assertThat(contar("SELECT count(*)::int FROM cumplimiento.regla_monitoreo_lft WHERE codigo = ?", codigo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID reglaId = transaccion.execute(
                t -> reglaCU.crear(regla("monto_usd >= umbral('PCC01_CARGA')", "ALTA", "SOLO_ALERTAR"), oficial));
        var activacion = new EntradaActivacion(reglaId, oficial.usuarioId(), true, false, "0.0120");

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> {
                    reglaCU.activar(activacion, oficial);
                    return null;
                });
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

        assertThat(errores).hasSize(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.regla_monitoreo_activada' AND agregado_id = ?
                        """,
                        reglaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El cuadre de una simulacion es que el porcentaje refleje lo medido: si el
        // numero mintiera, la decision de activar se tomaria sobre datos falsos.
        var simulacion = reglaCU.simular(10_000, 250);
        assertThat(simulacion.porcentajeTrafico()).isEqualTo("0.0250");
        assertThat(simulacion.superaTecho()).isFalse();

        var pasada = reglaCU.simular(10_000, 900);
        assertThat(pasada.porcentajeTrafico()).isEqualTo("0.0900");
        assertThat(pasada.superaTecho()).isTrue();
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID reglaId = gobiernoFixtura.reglaDeMonitoreo(codigo, "MEDIA", true);
        UUID alertaId = gobiernoFixtura.alerta(
                reglaId, fixtura.usuario(), "MEDIA", "3000.00", OffsetDateTime.now(ZoneOffset.UTC));
        var triaje = new EntradaTriaje(alertaId, "SIN_MERITO", FUNDAMENTO);

        transaccion.execute(t -> {
            reglaCU.triar(triaje, oficial);
            return null;
        });
        assertThatThrownBy(() -> transaccion.execute(t -> {
                    reglaCU.triar(triaje, oficial);
                    return null;
                }))
                .hasMessageContaining("ya fue cerrada");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.alerta_triada' AND agregado_id = ?
                        """,
                        alertaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        // Paso fallido: la accion excede lo que la severidad habilita. Bloquearle la
        // cuenta a alguien por una alerta baja es un daño cierto por una sospecha debil.
        assertThatThrownBy(() -> transaccion.execute(t ->
                        reglaCU.crear(regla("monto_usd >= umbral('PCC01_CARGA')", "BAJA", "BLOQUEAR_CUENTA"), oficial)))
                .hasMessageContaining("excede lo que la severidad");

        // Paso fallido: la expresion nombra un campo que no existe.
        assertThatThrownBy(() -> transaccion.execute(t ->
                        reglaCU.crear(regla("saldo_secreto > umbral('PCC01_CARGA')", "ALTA", "SOLO_ALERTAR"), oficial)))
                .hasMessageContaining("campo inexistente");

        assertThat(contar("SELECT count(*)::int FROM cumplimiento.regla_monitoreo_lft WHERE codigo = ?", codigo))
                .isZero();

        UUID reglaId = transaccion.execute(
                t -> reglaCU.crear(regla("monto_usd >= umbral('PCC01_CARGA')", "ALTA", "SOLO_ALERTAR"), oficial));

        // Paso fallido: activar sin simulacion, y activar con simulacion que satura.
        assertThatThrownBy(() -> transaccion.execute(t -> {
                    reglaCU.activar(new EntradaActivacion(reglaId, oficial.usuarioId(), false, false, "0"), oficial);
                    return null;
                }))
                .hasMessageContaining("simulacion previa");
        assertThatThrownBy(() -> transaccion.execute(t -> {
                    reglaCU.activar(new EntradaActivacion(reglaId, oficial.usuarioId(), true, true, "0.4000"), oficial);
                    return null;
                }))
                .hasMessageContaining("saturaria la bandeja");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.regla_monitoreo_lft WHERE id = ? AND activa = false",
                        reglaId))
                .isEqualTo(1);

        // Con simulacion bajo el techo, el mismo camino cierra.
        transaccion.execute(t -> {
            reglaCU.activar(new EntradaActivacion(reglaId, oficial.usuarioId(), true, false, "0.0120"), oficial);
            return null;
        });
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.regla_monitoreo_lft WHERE id = ? AND activa = true",
                        reglaId))
                .isEqualTo(1);
    }
}
