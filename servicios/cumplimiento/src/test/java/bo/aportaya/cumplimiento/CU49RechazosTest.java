package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU49DesignarOficial.EntradaBaja;
import bo.aportaya.cumplimiento.aplicacion.CU49DesignarOficial.EntradaDesignacion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-49 · Lo que la base y el caso de uso rechazan. */
class CU49RechazosTest extends BaseDeCumplimiento {

    private ContextoSesion ctx;
    private LocalDate hoy;

    @BeforeEach
    void escenario() {
        dsl.execute("DELETE FROM cumplimiento.oficial_cumplimiento");
        ctx = ContextoSesion.de(
                fixtura.usuario(), "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
        hoy = LocalDate.now(ZoneOffset.UTC);
    }

    private EntradaDesignacion designacion(UUID usuario, String tipo, Set<String> roles) {
        return new EntradaDesignacion(usuario, tipo, hoy, "ACTA-DIR-" + tipo, roles);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La designacion conserva su acta y su fecha: sin ellas, nadie puede probar
        // desde cuando esta persona responde ante el regulador.
        var salida = transaccion.execute(
                t -> oficialCU.designar(designacion(fixtura.usuario(), "TITULAR", Set.of("CUMPLIMIENTO")), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.oficial_cumplimiento
                         WHERE id = ? AND acta_designacion IS NOT NULL AND fecha_designacion IS NOT NULL
                        """,
                        salida.oficialId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        var salida = transaccion.execute(
                t -> oficialCU.designar(designacion(fixtura.usuario(), "TITULAR", Set.of("CUMPLIMIENTO")), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.oficial_designado' AND agregado_id = ?
                           AND payload->>'plazoComunicacionHasta' IS NOT NULL
                        """,
                        salida.oficialId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        // La baja no borra al oficial saliente: queda con su fecha. Borrarlo dejaria un
        // periodo sin nadie responsable en el papel.
        transaccion.execute(
                t -> oficialCU.designar(designacion(fixtura.usuario(), "TITULAR", Set.of("CUMPLIMIENTO")), ctx));
        transaccion.execute(
                t -> oficialCU.designar(designacion(fixtura.usuario(), "SUPLENTE", Set.of("CUMPLIMIENTO")), ctx));
        transaccion.execute(t -> oficialCU.darDeBajaAlTitular(new EntradaBaja(hoy, "ACTA-DIR-RELEVO"), ctx));

        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM cumplimiento.oficial_cumplimiento
                         WHERE activo = false AND fecha_baja IS NOT NULL
                        """))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-LIC-03")
    void rechazaRLIC03() {
        // Sin acta del directorio no hay designacion: una designacion sin respaldo no
        // es oponible a nadie.
        assertThatThrownBy(() -> transaccion.execute(t -> oficialCU.designar(
                        new EntradaDesignacion(fixtura.usuario(), "TITULAR", hoy, "  ", Set.of("CUMPLIMIENTO")), ctx)))
                .hasMessageContaining("acta del directorio");
        assertThat(contar("SELECT count(*)::int FROM cumplimiento.oficial_cumplimiento"))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // Quien opera no puede ser quien controla que se opere bien.
        assertThatThrownBy(() -> transaccion.execute(t -> oficialCU.designar(
                        designacion(fixtura.usuario(), "TITULAR", Set.of("CUMPLIMIENTO", "TESORERIA")), ctx)))
                .hasMessageContaining("incompatibles");
        assertThat(contar("SELECT count(*)::int FROM cumplimiento.oficial_cumplimiento"))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-UIF-12")
    void rechazaRUIF12() {
        // Un titular activo por vez, y la baja exige fecha. Las dos mitades las sostiene
        // la base.
        UUID usuario = fixtura.usuario();
        transaccion.execute(t -> oficialCU.designar(designacion(usuario, "TITULAR", Set.of("CUMPLIMIENTO")), ctx));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.oficial_cumplimiento
                            (usuario_id, tipo, fecha_designacion, acta_designacion, activo)
                        VALUES (?, 'TITULAR', current_date, 'ACTA-X', true)
                        """,
                        fixtura.usuario()))
                .contains("uq_oficial_titular_activo");

        assertThat(rechazaLaBase(
                        "UPDATE cumplimiento.oficial_cumplimiento SET activo = false WHERE usuario_id = ?", usuario))
                .contains("ck_oficial_baja_coherente");

        // Y la baja no puede ser anterior a la designacion: un oficial que cesa antes de
        // asumir no existio nunca.
        assertThat(rechazaLaBase(
                        """
                        UPDATE cumplimiento.oficial_cumplimiento
                           SET activo = false, fecha_baja = fecha_designacion - 1
                         WHERE usuario_id = ?
                        """,
                        usuario))
                .contains("ck_oficial_baja_posterior");
    }
}
