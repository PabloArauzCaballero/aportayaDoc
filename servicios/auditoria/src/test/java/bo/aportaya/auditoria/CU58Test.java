package bo.aportaya.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.auditoria.aplicacion.CU58EjecutarReporte;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-58 · Definir, programar y exportar un reporte.
 *
 * <p>Lo que se pone a prueba no es que el reporte devuelva filas: es que <b>sacar datos
 * del sistema deje rastro</b>. Permiso verificado contra la definicion, huella
 * reproducible del resultado, y un archivo que se cifra y caduca cuando lleva datos de
 * personas.
 *
 * <p>La consulta que corren estas pruebas es real y se ejecuta contra el contenedor. Una
 * consulta de mentira comprobaria que el caso de uso guarda bien un resultado inventado,
 * que es justo lo que no interesa.
 */
class CU58Test extends BaseDeAuditoria {

    private static final String PERMISO = "REPORTE_HISTORICO_PAGOS";
    private static final String ESPERA_UN_CODIGO =
            "[{\"nombre\": \"codigo\", \"tipo\": \"TEXTO\", \"obligatorio\": true}]";

    @Test
    @DisplayName("Dada una definición con permiso requerido que el solicitante no tiene · Cuando intenta ejecutarla ·"
            + " Entonces se rechaza con SIN_PERMISO y queda registrado el intento")
    void criterio1() {
        UUID definicion =
                reportes.definicionDeReporte("sin permiso " + UUID.randomUUID(), PERMISO, false, "SELECT 1", "[]");

        assertThatThrownBy(
                        () -> ejecutar(definicion, Map.of(), Optional.empty(), Optional.empty(), Set.of("OTRA_COSA")))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU58-01");

        // Y el intento QUEDA, aunque el caso de uso haya lanzado y su transaccion se
        // haya revertido. Es la mitad del criterio que se pierde si la constancia se
        // escribe en la misma transaccion que el rechazo se lleva puesta.
        assertThat(reportes.ejecucionesDe(definicion)).hasSize(1).allMatch(e -> e.startsWith("FALLIDA:SIN_PERMISO"));
    }

    @Test
    @DisplayName("Dada una definición con contiene_datos_sensibles en true · Cuando se ejecuta sin justificación ·"
            + " Entonces se rechaza con JUSTIFICACION_REQUERIDA")
    void criterio2() {
        UUID definicion = reportes.definicionDeReporte(
                "sensible sin motivo " + UUID.randomUUID(), PERMISO, true, "SELECT 1", "[]");

        assertThatThrownBy(() -> ejecutar(definicion, Map.of(), Optional.empty(), Optional.empty(), Set.of(PERMISO)))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU58-03");
    }

    @Test
    @DisplayName("Dada una exportación con datos personales · Cuando se genera el archivo · Entonces esta_cifrado es"
            + " true y expira_en no es nulo")
    void criterio3() {
        UUID definicion = reportes.definicionDeReporte(
                "sensible con motivo " + UUID.randomUUID(), PERMISO, true, "SELECT 'x' AS columna", "[]");

        var salida = ejecutar(
                definicion,
                Map.of(),
                Optional.of("CSV"),
                Optional.of("Requerimiento de la autoridad, expediente 2026-114."),
                Set.of(PERMISO));

        assertThat(salida.exportacionId()).isPresent();
        var exportacion = reportes.exportacionDe(salida.ejecucionId());
        // El cifrado no lo elige quien exporta: lo decide la definicion. Quien saca
        // datos que no son suyos no decide como se protegen.
        assertThat(exportacion.get("esta_cifrado", Boolean.class)).isTrue();
        // Y caduca. Un enlace eterno a un archivo con datos personales es una fuga
        // futura con fecha abierta.
        assertThat(exportacion.get("expira_en", OffsetDateTime.class)).isAfter(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Dada una misma ejecución con los mismos parámetros y datos · Cuando se repite · Entonces"
            + " hash_resultado coincide con el de la primera")
    void criterio4() {
        UUID definicion = reportes.definicionDeReporte(
                "reproducible " + UUID.randomUUID(),
                PERMISO,
                false,
                "SELECT :codigo AS columna, generate_series(1, 3) AS n ORDER BY n",
                ESPERA_UN_CODIGO);

        Map<String, String> parametros = Map.of("codigo", "AP-2026");

        var primera = ejecutar(definicion, parametros, Optional.empty(), Optional.empty(), Set.of(PERMISO));
        var segunda = ejecutar(definicion, parametros, Optional.empty(), Optional.empty(), Set.of(PERMISO));

        assertThat(primera.filasGeneradas()).isEqualTo(3);
        // Es lo que permite probar que el archivo que alguien presenta es el que salio
        // de aca. Si el hash cambiara entre dos corridas iguales, no probaria nada.
        assertThat(segunda.hashResultado()).isEqualTo(primera.hashResultado());
        assertThat(primera.hashResultado()).hasSize(64);
        assertThat(primera.ejecucionId()).isNotEqualTo(segunda.ejecucionId());
    }

    @Test
    @DisplayName("rechaza · un parámetro que la definición no declara no llega a la consulta")
    void rechazaParametroNoDeclarado() {
        UUID definicion = reportes.definicionDeReporte(
                "lista blanca " + UUID.randomUUID(), PERMISO, false, "SELECT :codigo AS columna", ESPERA_UN_CODIGO);

        // Lista blanca, no lista negra: no se intenta reconocer lo peligroso, se
        // rechaza lo no declarado. Filtrar lo peligroso siempre deja pasar un caso.
        assertThatThrownBy(() -> ejecutar(
                        definicion,
                        Map.of("codigo", "AP-2026", "orden", "1; DROP TABLE auditoria.ejecucion_reporte"),
                        Optional.empty(),
                        Optional.empty(),
                        Set.of(PERMISO)))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU58-02");
    }

    @Test
    @DisplayName("rechaza · el valor de un parámetro se liga, nunca se concatena")
    void rechazaLaInyeccion() {
        UUID definicion = reportes.definicionDeReporte(
                "ligadura " + UUID.randomUUID(), PERMISO, false, "SELECT :codigo AS columna", ESPERA_UN_CODIGO);

        // El texto entra entero como VALOR. Si en algun punto se concatenara, la
        // consulta traeria otra cosa o fallaria; que vuelva tal cual, en una sola fila,
        // es la prueba de que viajo ligado.
        String hostil = "'; DROP TABLE auditoria.ejecucion_reporte; --";
        var salida =
                ejecutar(definicion, Map.of("codigo", hostil), Optional.empty(), Optional.empty(), Set.of(PERMISO));

        assertThat(salida.filasGeneradas()).isEqualTo(1);
        assertThat(reportes.estadoDeLaEjecucion(salida.ejecucionId())).isEqualTo("COMPLETADA");
    }

    @Test
    @DisplayName("rechaza · una consulta que tarda de mas se corta y deja constancia")
    void rechazaLaConsultaEterna() {
        UUID definicion =
                reportes.definicionDeReporte("eterna " + UUID.randomUUID(), PERMISO, false, "SELECT pg_sleep(5)", "[]");

        assertThatThrownBy(() -> ejecutar(definicion, Map.of(), Optional.empty(), Optional.empty(), Set.of(PERMISO)))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU58-04");

        // La constancia sobrevive a una transaccion ABORTADA, que es mas duro que
        // sobrevivir a un rollback: en una transaccion abortada PostgreSQL no acepta ni
        // un INSERT. Sin el rastro, el reporte que hay que acotar se vuelve a pedir y la
        // base se vuelve a colgar.
        assertThat(reportes.ejecucionesDe(definicion)).contains("FALLIDA:TIEMPO_EXCEDIDO");
    }

    @Test
    @DisplayName("rechaza · un reporte sin filas se entrega igual, con cero")
    void rechazaTratarElVacioComoError() {
        UUID definicion = reportes.definicionDeReporte(
                "vacio " + UUID.randomUUID(), PERMISO, false, "SELECT 1 AS columna WHERE false", "[]");

        var salida = ejecutar(definicion, Map.of(), Optional.empty(), Optional.empty(), Set.of(PERMISO));

        // «No hubo» es una respuesta, y para los regulatorios es la obligatoria
        // (`R-UIF-06`). Devolver error por lista vacia obligaria a quien reporta a
        // distinguir «no hubo» de «fallo», y ahi es donde se pierde el envio.
        assertThat(salida.filasGeneradas()).isZero();
        assertThat(salida.estado()).isEqualTo("COMPLETADA");
        assertThat(salida.hashResultado()).hasSize(64);
    }

    private static CU58EjecutarReporte.SalidaReporte ejecutar(
            UUID definicionId,
            Map<String, String> parametros,
            Optional<String> formato,
            Optional<String> justificacion,
            Set<String> permisos) {
        return transaccion.execute(estado -> reportesCU.ejecutar(
                new CU58EjecutarReporte.EntradaReporte(definicionId, parametros, formato, justificacion, permisos),
                contexto()));
    }
}
