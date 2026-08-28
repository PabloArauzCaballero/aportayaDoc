package bo.aportaya.organizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.organizador.aplicacion.CU93SancionarOrganizador.EntradaApelacion;
import bo.aportaya.organizador.aplicacion.CU93SancionarOrganizador.EntradaResolucion;
import bo.aportaya.organizador.aplicacion.CU93SancionarOrganizador.EntradaSancion;
import bo.aportaya.organizador.aplicacion.CU93SancionarOrganizador.SalidaSancion;
import bo.aportaya.organizador.dominio.DebidoProceso;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-93 · las pruebas de RECHAZO, una por restriccion citada. */
class CU93RechazosTest extends BaseDeOrganizador {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private record Caso(UUID usuario, UUID organizadorId, ContextoSesion suyo, ContextoSesion operaciones) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        return new Caso(
                usuario, fixtura.organizadorHabilitado(usuario), contextoDe(usuario), contextoDe(fixtura.usuario()));
    }

    private SalidaSancion sancionar(Caso c, String tipo) {
        return transaccion.execute(t -> sancionCU.sancionar(
                new EntradaSancion(
                        c.organizadorId(),
                        Optional.empty(),
                        tipo,
                        "Motivo escrito y verificable",
                        Optional.of(Duration.ofDays(30))),
                c.operaciones()));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Una sancion no se borra ni pierde quien la aplico: sin ese nombre no hay a
        // quien preguntarle por que.
        Caso c = caso();
        SalidaSancion salida = sancionar(c, "ADVERTENCIA");

        assertThat(rechazaLaBase("UPDATE organizador.sancion_organizador SET aplicada_por = NULL WHERE id = '%s'"
                        .formatted(salida.sancionId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        Caso c = caso();
        SalidaSancion salida = sancionar(c, "SUSPENSION");

        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "organizador.sancionado",
                        salida.sancionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-CON-01")
    void rechazaRCON01() {
        // El plazo para apelar se calcula al sancionar y no se recalcula despues. Un
        // plazo que se mueve es un plazo que se le puede acortar al sancionado sin que
        // se entere.
        Caso c = caso();
        SalidaSancion salida = sancionar(c, "SUSPENSION");
        var aplicada = dsl.fetchOne(
                        "SELECT vigente_desde FROM organizador.sancion_organizador WHERE id = ?", salida.sancionId())
                .get("vigente_desde", OffsetDateTime.class);

        assertThat(salida.puedeApelarHasta()).isEqualTo(aplicada.plus(PLAZO_PARA_APELAR));

        // Vencido el plazo, la apelacion no entra. El atomo lo dice sin base de datos.
        var vencido = new DebidoProceso(
                OffsetDateTime.now(ZoneOffset.UTC).minus(PLAZO_PARA_APELAR).minusDays(1), PLAZO_PARA_APELAR);
        assertThat(vencido.admiteApelacionEn(OffsetDateTime.now(ZoneOffset.UTC)))
                .isFalse();

        dsl.execute(
                "UPDATE organizador.sancion_organizador SET vigente_desde = now() - interval '30 days' WHERE id = ?",
                salida.sancionId());
        assertThatThrownBy(() -> transaccion.execute(
                        t -> sancionCU.apelar(new EntradaApelacion(salida.sancionId(), "Tarde", "[]"), c.suyo())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("plazo para apelar vencio");
    }

    @Test
    @DisplayName("rechaza por R-ORG-05")
    void rechazaRORG05() {
        // Una apelacion por sancion, y la resuelve quien NO la aplico. Sin lo segundo,
        // apelar es pedirle a la misma persona que se desdiga.
        Caso c = caso();
        SalidaSancion salida = sancionar(c, "SUSPENSION");
        transaccion.execute(
                t -> sancionCU.apelar(new EntradaApelacion(salida.sancionId(), "Pido revision", "[]"), c.suyo()));

        assertThatThrownBy(() -> transaccion.execute(t -> sancionCU.resolver(
                        new EntradaResolucion(salida.sancionId(), true, "Pido revision", "[]", "Me desdigo"),
                        c.operaciones())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede ser quien aplico");
        // El atomo lo dice igual, sin base de datos.
        UUID mismo = UUID.randomUUID();
        assertThatThrownBy(() -> DebidoProceso.exigirRevisorDistinto(mismo, mismo))
                .isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("rechaza por R-RIS-01")
    void rechazaRRIS01() {
        // El tipo de sancion sale de una taxonomia cerrada: una sancion inventada no se
        // puede contar, y lo que no se cuenta no aparece en el riesgo operativo.
        Caso c = caso();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.sancion_organizador
                            (id, organizador_id, tipo, motivo, vigente_desde, estado, aplicada_por)
                        VALUES (gen_random_uuid(), '%s', 'DESTIERRO', 'inventada', now(), 'VIGENTE', '%s')
                        """
                                .formatted(c.organizadorId(), c.usuario())))
                .contains("ck_sancion_organizador_tipo");
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // Nadie se sanciona a si mismo, y una sancion sin motivo escrito no se puede
        // apelar porque no dice contra que defenderse.
        Caso c = caso();

        assertThatThrownBy(() -> transaccion.execute(t -> sancionCU.sancionar(
                        new EntradaSancion(
                                c.organizadorId(), Optional.empty(), "SUSPENSION", "Me suspendo", Optional.empty()),
                        c.suyo())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("Nadie se sanciona a si mismo");
        assertThatThrownBy(() -> transaccion.execute(t -> sancionCU.sancionar(
                        new EntradaSancion(c.organizadorId(), Optional.empty(), "SUSPENSION", "   ", Optional.empty()),
                        c.operaciones())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin motivo escrito");
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.sancion_organizador WHERE organizador_id = ?",
                        c.organizadorId()))
                .isZero();
    }
}
