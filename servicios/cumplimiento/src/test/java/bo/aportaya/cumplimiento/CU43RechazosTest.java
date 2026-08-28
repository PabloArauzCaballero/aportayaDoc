package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU43RemitirReportes.EntradaEnvio;
import bo.aportaya.cumplimiento.aplicacion.CU43RemitirReportes.EntradaReporte;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-43 · Lo que la base y el caso de uso rechazan. */
class CU43RechazosTest extends BaseDeCumplimiento {

    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyy-MM");

    private static final java.util.concurrent.atomic.AtomicInteger MES =
            new java.util.concurrent.atomic.AtomicInteger(20);

    private String codigo;
    private String periodo;
    private ContextoSesion generador;
    private ContextoSesion aprobador;

    @BeforeEach
    void escenario() {
        codigo = "PCC01-" + UUID.randomUUID().toString().substring(0, 6);
        uif.catalogoDeReporte(codigo, "UIF", 15);
        periodo =
                LocalDate.now(ZoneOffset.UTC).minusMonths(MES.getAndIncrement()).format(PERIODO);
        generador = ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
        aprobador = ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    private String constancia() {
        return "C-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        // Nada se depura antes de su fecha de conservacion: el reporte y su constancia
        // son la prueba de que se cumplio, y sin ellos el cumplimiento no es
        // demostrable aunque haya ocurrido.
        var reporte =
                transaccion.execute(t -> reporteCU.generar(new EntradaReporte(periodo, codigo, "PCC-01"), generador));
        String numero = constancia();
        transaccion.execute(t -> reporteCU.aprobarYEnviar(
                new EntradaEnvio(periodo, codigo, aprobador.usuarioId(), "PORTAL_WEB", numero, true, 0), aprobador));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.envio_regulatorio
                         WHERE reporte_regulatorio_id = ? AND numero_constancia = ? AND fecha_envio IS NOT NULL
                        """,
                        reporte.reporteId(),
                        numero))
                .isEqualTo(1);
        // Y el hash del archivo queda con el reporte: sin el, nadie puede comprobar
        // despues que lo enviado es lo que dice el registro.
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.reporte_regulatorio WHERE id = ? AND length(hash_archivo) = 64",
                        reporte.reporteId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // Segregacion: quien autoriza no ejecuta. Si la misma persona genera y aprueba,
        // la firma no agrega nada.
        transaccion.execute(t -> reporteCU.generar(new EntradaReporte(periodo, codigo, "PCC-01"), generador));

        assertThatThrownBy(() -> transaccion.execute(t -> reporteCU.aprobarYEnviar(
                        new EntradaEnvio(periodo, codigo, generador.usuarioId(), "PORTAL_WEB", constancia(), true, 0),
                        generador)))
                .hasMessageContaining("no puede ser quien lo genero");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.reporte_regulatorio r
                          JOIN cumplimiento.catalogo_reporte_regulatorio c ON c.id = r.catalogo_reporte_id
                         WHERE c.codigo = ? AND r.aprobado_por IS NULL
                        """,
                        codigo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-UIF-05")
    void rechazaRUIF05() {
        // Un reporte por catalogo y periodo: dos del mismo mes se contradirian entre si
        // ante el organismo.
        var primero =
                transaccion.execute(t -> reporteCU.generar(new EntradaReporte(periodo, codigo, "PCC-01"), generador));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.reporte_regulatorio
                            (catalogo_reporte_id, generado_por, periodo, fecha_corte, estado,
                             cantidad_registros, reporte_en_cero, fecha_limite)
                        SELECT catalogo_reporte_id, generado_por, periodo, fecha_corte, 'GENERADO',
                               cantidad_registros, reporte_en_cero, fecha_limite
                          FROM cumplimiento.reporte_regulatorio WHERE id = ?
                        """,
                        primero.reporteId()))
                .contains("uq_reporte_catalogo_periodo");
    }

    @Test
    @DisplayName("rechaza por R-UIF-06")
    void rechazaRUIF06() {
        // El numero y la bandera tienen que decir lo mismo: un reporte que dice «en
        // cero» con doce registros adentro miente en la cara del organismo.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.reporte_regulatorio
                            (catalogo_reporte_id, periodo, fecha_corte, estado, cantidad_registros,
                             reporte_en_cero, fecha_limite)
                        SELECT id, ?, current_date, 'GENERADO', 12, true, current_date + 15
                          FROM cumplimiento.catalogo_reporte_regulatorio WHERE codigo = ?
                        """,
                        periodo + "-x",
                        codigo))
                .contains("ck_reporte_en_cero");

        // Y el reporte en cero SI existe: no mandar nada y mandar cero son cosas
        // distintas para el regulador.
        var enCero =
                transaccion.execute(t -> reporteCU.generar(new EntradaReporte(periodo, codigo, "PCC-01"), generador));
        assertThat(enCero.reporteEnCero()).isTrue();
        assertThat(enCero.cantidadRegistros()).isZero();
    }
}
