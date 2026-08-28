package bo.aportaya.garantia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.EntradaDeclaracion;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.SalidaDeclaracion;
import bo.aportaya.garantia.dominio.PlazoDeDescargo;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-25 · las pruebas de RECHAZO, una por restriccion citada. */
class CU25RechazosTest extends BaseDeGarantia {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(
            UUID usuario, FixturaDeGarantia.Escenario escenario, ContextoSesion suyo, ContextoSesion gestor) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        return new Caso(usuario, fixtura.escenario(usuario), contextoDe(usuario), contextoDe(fixtura.usuario()));
    }

    private SalidaDeclaracion declarar(Caso c) {
        return transaccion.execute(t -> expedienteCU.declarar(
                new EntradaDeclaracion(
                        "EXP-" + corto(),
                        c.usuario(),
                        c.escenario().participanteId(),
                        c.escenario().grupoId(),
                        c.escenario().periodoId(),
                        c.escenario().cupoId(),
                        c.escenario().obligacionId(),
                        "APORTE_IMPAGO",
                        "GRAVE",
                        "AUTOMATICO_VENCIMIENTO",
                        bob("500.00"),
                        30,
                        true,
                        "LOG_SISTEMA",
                        "Tres avisos acusados",
                        null,
                        null),
                c.gestor()));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El expediente es append-only: su estado al detectar no se puede reescribir.
        // Un expediente cuyo estado se puede editar no prueba nada, y la persona
        // sancionada no tiene contra que defenderse.
        Caso c = caso();
        SalidaDeclaracion expediente = declarar(c);

        assertThat(rechazaLaBase("UPDATE garantia.registro_incumplimiento SET estado = 'SUBSANADO' WHERE id = '%s'"
                        .formatted(expediente.expedienteId())))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM garantia.registro_incumplimiento WHERE id = '%s'"
                        .formatted(expediente.expedienteId())))
                .contains("R-AUD-01");
        // Y el historial tampoco: es la maquina de estados, no un registro auxiliar.
        assertThat(rechazaLaBase(
                        "UPDATE garantia.historial_estado_incumplimiento SET estado_nuevo = 'SUBSANADO' WHERE registro_id = '%s'"
                                .formatted(expediente.expedienteId())))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        Caso c = caso();
        SalidaDeclaracion expediente = declarar(c);

        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "garantia.incumplimiento_declarado",
                        expediente.expedienteId()))
                .isEqualTo(1);
        assertThat(
                        rechazaLaBase(
                                "UPDATE garantia.evento_dominio SET estado = 'INVENTADO' WHERE tipo = 'garantia.incumplimiento_declarado'"))
                .contains("ck_garantia_evtdom_estado");
    }

    @Test
    @DisplayName("rechaza por R-CON-01")
    void rechazaRCON01() {
        // El plazo se calcula al notificar y se GUARDA. Un plazo que se recalcula al
        // mirarlo se le puede acortar a alguien despues de habersele comunicado, y eso
        // no lo puede probar nadie.
        Caso c = caso();
        SalidaDeclaracion expediente = declarar(c);

        var fila = dsl.fetchOne(
                "SELECT notificado_en, fecha_limite_subsanacion FROM garantia.registro_incumplimiento WHERE id = ?",
                expediente.expedienteId());
        assertThat(fila.get("fecha_limite_subsanacion", OffsetDateTime.class))
                .isEqualTo(fila.get("notificado_en", OffsetDateTime.class).plus(PLAZO_DE_DESCARGO));
        // Y no se puede mover despues: la tabla es append-only.
        assertThat(rechazaLaBase(
                        "UPDATE garantia.registro_incumplimiento SET fecha_limite_subsanacion = now() WHERE id = '%s'"
                                .formatted(expediente.expedienteId())))
                .contains("R-AUD-01");

        // El atomo lo dice sin base de datos: vencido el plazo, el descargo no entra.
        var vencido = new PlazoDeDescargo(
                OffsetDateTime.now(ZoneOffset.UTC).minus(PLAZO_DE_DESCARGO).minusDays(1), PLAZO_DE_DESCARGO);
        assertThat(vencido.admiteDescargoEn(OffsetDateTime.now(ZoneOffset.UTC))).isFalse();
        assertThat(vencido.restanteEn(OffsetDateTime.now(ZoneOffset.UTC))).isZero();
    }

    @Test
    @DisplayName("rechaza por R-GAR-01")
    void rechazaRGAR01() {
        // El plazo corre desde la NOTIFICACION, no desde la deteccion: nadie puede
        // defenderse de algo que todavia no sabe que se le imputa.
        Caso c = caso();
        SalidaDeclaracion expediente = declarar(c);

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.registro_incumplimiento
                         WHERE id = ? AND notificado_en IS NOT NULL AND fecha_limite_subsanacion > notificado_en
                        """,
                        expediente.expedienteId()))
                .isEqualTo(1);

        // Vencido el plazo, el descargo se rechaza con la fecha a la vista.
        dsl.execute(
                "INSERT INTO garantia.historial_estado_incumplimiento (id, registro_id, estado_anterior, estado_nuevo, motivo, es_automatico, fecha_hora) VALUES (gen_random_uuid(), ?, 'NOTIFICADO', 'EN_GESTION_COBRANZA', 'plazo vencido', true, now())",
                expediente.expedienteId());
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.descargo_participante WHERE registro_id = ?",
                        expediente.expedienteId()))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-GAR-02")
    void rechazaRGAR02() {
        // La evidencia es inmutable. Una prueba que se puede editar despues de
        // presentada no es una prueba, y el descargo pasaria a ser contra un blanco
        // movil.
        Caso c = caso();
        SalidaDeclaracion expediente = declarar(c);
        UUID evidenciaId = dsl.fetchOne(
                        "SELECT id FROM garantia.evidencia_incumplimiento WHERE registro_id = ?",
                        expediente.expedienteId())
                .get("id", UUID.class);

        assertThat(rechazaLaBase("UPDATE garantia.evidencia_incumplimiento SET descripcion = 'otra' WHERE id = '%s'"
                        .formatted(evidenciaId)))
                .contains("R-GAR-02");
        assertThat(rechazaLaBase(
                        "DELETE FROM garantia.evidencia_incumplimiento WHERE id = '%s'".formatted(evidenciaId)))
                .contains("R-GAR-02");
        // Sin evidencia no se declara: declarar sin con que probarlo deja al
        // participante sin nada contra que defenderse.
        Caso otro = caso();
        assertThatThrownBy(() -> transaccion.execute(t -> expedienteCU.declarar(
                        new EntradaDeclaracion(
                                "EXP-" + corto(),
                                otro.usuario(),
                                otro.escenario().participanteId(),
                                otro.escenario().grupoId(),
                                otro.escenario().periodoId(),
                                otro.escenario().cupoId(),
                                otro.escenario().obligacionId(),
                                "APORTE_IMPAGO",
                                "GRAVE",
                                "AUTOMATICO_VENCIMIENTO",
                                bob("500.00"),
                                30,
                                true,
                                "LOG_SISTEMA",
                                null,
                                null,
                                null),
                        otro.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin evidencia");
    }
}
