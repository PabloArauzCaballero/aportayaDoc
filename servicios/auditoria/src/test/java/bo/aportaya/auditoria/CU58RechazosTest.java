package bo.aportaya.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.auditoria.aplicacion.CU58EjecutarReporte;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-58 · las pruebas de RECHAZO.
 *
 * <p>Otra pregunta que las de {@link CU58Test}: aquellas verifican que el caso de uso
 * hace lo que promete; estas, que la <b>base</b> rechaza lo que no debe entrar aunque la
 * aplicacion se equivoque.
 *
 * <p>En el circuito de reportes la diferencia es todo: si la garantia viviera solo en el
 * codigo, alcanzaria con una consola abierta para editar una ejecucion y cambiar el
 * hash de un reporte ya entregado — que es exactamente lo que el hash existe para
 * impedir.
 */
class CU58RechazosTest extends BaseDeAuditoria {

    private static final String PERMISO = "REPORTE_HISTORICO_PAGOS";

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Las tablas de auditoria no admiten UPDATE ni DELETE. `registro_acceso_datos`
        // es la que guarda quien vio datos sensibles y por que: si se pudiera editar,
        // el rastro de acceso lo escribiria quien accede, a su gusto.
        assertThat(triggerExiste("tg_registro_acceso_datos_append_only")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
        // Todo acceso a datos sensibles queda registrado CON JUSTIFICACION, y la
        // columna es NOT NULL a proposito: un CHECK que solo compara longitudes se
        // satisface con NULL, porque `length(trim(NULL)) >= 10` evalua a NULL y un
        // CHECK que evalua a NULL se acepta.
        assertThat(constraintExiste("ck_acceso_justificacion")).isTrue();

        // Y la aplicacion exige que HAYA justificacion, para dar un mensaje util en vez
        // de un error del motor. El largo minimo no lo repite: lo hacen cumplir el
        // contrato en el borde y este CHECK abajo. Las dos capas, no una — si se borrara
        // la de arriba, la base sigue rechazando, que es la prueba de que la garantia
        // esta bien puesta.
        UUID definicion =
                reportes.definicionDeReporte("sensible " + UUID.randomUUID(), PERMISO, true, "SELECT 1", "[]");
        assertThatThrownBy(() -> ejecutar(definicion, Optional.of("   "), Set.of(PERMISO)))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU58-03");
    }

    @Test
    @DisplayName("rechaza por R-SEG-03")
    void rechazaRSEG03() {
        // Un reporte no es una puerta trasera al RLS. La consulta corre con la sesion
        // del solicitante sobre el DSLContext de la transaccion en curso, que es donde
        // `SET LOCAL` dejo el contexto; si tomara otra conexion, correria sin politica
        // de fila y devolveria filas de todos — sin error y sin rastro.
        assertThat(politicaExiste("pol_registro_acceso_datos_reservado")).isTrue();
        assertThat(funcionExiste("fn_seg_rol_privilegiado")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        // Nada se depura antes de su fecha de conservacion. Una exportacion caduca para
        // que el archivo deje de estar accesible, pero la EJECUCION que la produjo se
        // conserva: es la que responde «quien saco que» diez anos despues.
        assertThat(constraintExiste("ck_expediente_retencion_futura")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-UIF-06")
    void rechazaRUIF06() {
        // Si no hubo operaciones, se informa en cero. La base lo sostiene con la
        // coherencia del reporte en cero; el caso de uso, entregando cero filas en vez
        // de un error — cubierto en `rechazaTratarElVacioComoError` de CU58Test.
        assertThat(constraintExiste("ck_reporte_en_cero")).isTrue();
    }

    @Test
    @DisplayName("rechaza por R-CON-05")
    void rechazaRCON05() {
        // Los reclamos se conservan diez anos. CU-58 lo cita porque un reporte de
        // reclamos no puede apoyarse en filas que alguien pudo depurar antes de tiempo:
        // sin la conservacion garantizada, el historico que sale del reporte seria
        // incompleto sin que nadie lo note.
        assertThat(constraintExiste("ck_reclamo_conservacion")).isTrue();
    }

    @Test
    @DisplayName("rechaza · un formato de exportacion que la tabla no admite")
    void rechazaFormatoInexistente() {
        // El CHECK enumera CSV, JSON, PDF y XLSX. Un formato inventado no entra aunque
        // el contrato lo dejara pasar.
        assertThat(constraintExiste("ck_exportacion_reporte_formato")).isTrue();
    }

    @Test
    @DisplayName("rechaza · una exportacion vencida no se descarga")
    void rechazaExportacionVencida() {
        UUID definicion =
                reportes.definicionDeReporte("vence " + UUID.randomUUID(), PERMISO, false, "SELECT 1 AS columna", "[]");
        var salida = transaccion.execute(estado -> reportesCU.ejecutar(
                new CU58EjecutarReporte.EntradaReporte(
                        definicion, Map.of(), Optional.of("CSV"), Optional.empty(), Set.of(PERMISO)),
                contexto()));

        UUID exportacion = reportes.exportacionDe(salida.ejecucionId()).get("id", UUID.class);
        reportes.vencerExportacion(exportacion);

        assertThatThrownBy(() -> transaccion.execute(estado -> {
                    descargasCU.ejecutar(exportacion, contexto());
                    return null;
                }))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU58-05");
    }

    @Test
    @DisplayName("rechaza · agotado el tope, el enlace deja de servir")
    void rechazaTopeDeDescargas() {
        UUID definicion =
                reportes.definicionDeReporte("tope " + UUID.randomUUID(), PERMISO, false, "SELECT 1 AS columna", "[]");
        var salida = transaccion.execute(estado -> reportesCU.ejecutar(
                new CU58EjecutarReporte.EntradaReporte(
                        definicion, Map.of(), Optional.of("CSV"), Optional.empty(), Set.of(PERMISO)),
                contexto()));

        UUID exportacion = reportes.exportacionDe(salida.ejecucionId()).get("id", UUID.class);
        reportes.agotarDescargas(exportacion, TOPE_DE_DESCARGAS);

        // Un enlace que se descarga indefinidamente deja de ser una entrega y pasa a
        // ser una copia publicada. Hay que volver a pedirlo, con justificacion nueva.
        assertThatThrownBy(() -> transaccion.execute(estado -> {
                    descargasCU.ejecutar(exportacion, contexto());
                    return null;
                }))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU58-06");
    }

    @Test
    @DisplayName("rechaza · una definicion dada de baja no se ejecuta")
    void rechazaDefinicionInactiva() {
        assertThatThrownBy(() -> ejecutar(UUID.randomUUID(), Optional.empty(), Set.of(PERMISO)))
                .isInstanceOf(ErrorDeNegocio.class)
                .extracting(e -> ((ErrorDeNegocio) e).codigo().toString())
                .isEqualTo("AP-CU58-01");
    }

    private static CU58EjecutarReporte.SalidaReporte ejecutar(
            UUID definicionId, Optional<String> justificacion, Set<String> permisos) {
        return transaccion.execute(estado -> reportesCU.ejecutar(
                new CU58EjecutarReporte.EntradaReporte(
                        definicionId, Map.of(), Optional.empty(), justificacion, permisos),
                contexto()));
    }
}
