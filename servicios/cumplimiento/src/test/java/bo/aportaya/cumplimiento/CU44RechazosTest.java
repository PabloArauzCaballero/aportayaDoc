package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU44InvestigarYReportar.EntradaCaso;
import bo.aportaya.cumplimiento.aplicacion.CU44InvestigarYReportar.EntradaDecision;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-44 · Lo que la base y el caso de uso rechazan. */
class CU44RechazosTest extends BaseDeCumplimiento {

    private static final String CONCLUSION =
            "Se revisaron las doce operaciones del periodo y responden al giro declarado del titular.";

    private UUID titular;
    private UUID analista;
    private UUID revisor;
    private UUID regla;
    private ContextoSesion ctx;

    @BeforeEach
    void escenario() {
        titular = fixtura.usuario();
        analista = fixtura.usuario();
        revisor = fixtura.usuario();
        regla = gobiernoFixtura.reglaDeMonitoreo(
                "RM-" + UUID.randomUUID().toString().substring(0, 8), "ALTA", true);
        ctx = ContextoSesion.de(
                analista, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
    }

    private UUID alerta() {
        return gobiernoFixtura.alerta(regla, titular, "ALTA", "5000.00", OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La alerta y el caso no son append-only —cambian de estado por diseño—, pero el
        // evento que deja constancia de la decision si vive en una bandeja que no se
        // reescribe. Lo que R-AUD-01 protege aca es que el rastro de la decision quede.
        UUID alertaId = alerta();
        var caso = transaccion.execute(
                t -> casoCU.abrirCaso(new EntradaCaso(List.of(alertaId), analista, "Patron inusual"), ctx));
        transaccion.execute(t -> casoCU.decidir(
                new EntradaDecision(caso.casoId(), List.of(alertaId), analista, revisor, "DESCARTAR", CONCLUSION, null),
                ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.caso_investigacion_lft
                         WHERE id = ? AND cerrado_en IS NOT NULL AND hallazgos IS NOT NULL
                        """,
                        caso.casoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-SEG-02")
    void rechazaRSEG02() {
        // Todo acceso a datos sensibles queda registrado. En un caso LFT eso significa
        // que el expediente diga quien analizo y quien reviso: sin nombres, el acceso a
        // la vida financiera de alguien no tiene responsable.
        UUID alertaId = alerta();
        var caso = transaccion.execute(
                t -> casoCU.abrirCaso(new EntradaCaso(List.of(alertaId), analista, "Patron inusual"), ctx));
        transaccion.execute(t -> casoCU.decidir(
                new EntradaDecision(caso.casoId(), List.of(alertaId), analista, revisor, "DESCARTAR", CONCLUSION, null),
                ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.caso_investigacion_lft
                         WHERE id = ? AND analista_id IS NOT NULL AND revisado_por IS NOT NULL
                        """,
                        caso.casoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // Quien analiza no revisa lo suyo. Lo verifica ck_caso_revision, y el caso de
        // uso lo comprueba antes para poder explicarlo.
        UUID alertaId = alerta();
        var caso = transaccion.execute(
                t -> casoCU.abrirCaso(new EntradaCaso(List.of(alertaId), analista, "Patron inusual"), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> casoCU.decidir(
                        new EntradaDecision(
                                caso.casoId(), List.of(alertaId), analista, analista, "DESCARTAR", CONCLUSION, null),
                        ctx)))
                .hasMessageContaining("independiente");

        assertThat(rechazaLaBase(
                        "UPDATE cumplimiento.caso_investigacion_lft SET revisado_por = analista_id WHERE id = ?",
                        caso.casoId()))
                .contains("ck_caso_revision");
    }

    @Test
    @DisplayName("rechaza por R-UIF-07")
    void rechazaRUIF07() {
        // Una alerta no se cierra sin conclusion, y con menos de veinte caracteres
        // tampoco: «revisado» no le sirve a nadie dos años despues.
        UUID alertaId = alerta();
        var caso = transaccion.execute(
                t -> casoCU.abrirCaso(new EntradaCaso(List.of(alertaId), analista, "Patron inusual"), ctx));

        assertThatThrownBy(() -> transaccion.execute(t -> casoCU.decidir(
                        new EntradaDecision(
                                caso.casoId(), List.of(alertaId), analista, revisor, "DESCARTAR", "revisado", null),
                        ctx)))
                .hasMessageContaining("sin conclusion");

        assertThat(rechazaLaBase(
                        "UPDATE cumplimiento.alerta_monitoreo_lft SET estado = 'ESCALADA' WHERE id = ?", alertaId))
                .contains("ck_alerta_conclusion");
    }

    @Test
    @DisplayName("rechaza por R-UIF-08")
    void rechazaRUIF08() {
        // Todo caso lleva plazo, y el plazo tiene que ser posterior a la apertura: un
        // caso que nace vencido no es un plazo, es una formalidad.
        UUID alertaId = alerta();
        var caso = transaccion.execute(
                t -> casoCU.abrirCaso(new EntradaCaso(List.of(alertaId), analista, "Patron inusual"), ctx));

        assertThat(caso.plazoLimite())
                .isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        assertThat(rechazaLaBase(
                        "UPDATE cumplimiento.caso_investigacion_lft SET plazo_limite = abierto_en - interval '1 day' WHERE id = ?",
                        caso.casoId()))
                .contains("ck_caso_plazo");

        // Y REPORTAR exige el reporte enlazado: decidir reportar sin nada que reportar
        // deja a la UIF sin narrativa que leer.
        assertThat(rechazaLaBase(
                        "UPDATE cumplimiento.caso_investigacion_lft SET decision = 'REPORTAR' WHERE id = ?",
                        caso.casoId()))
                .contains("ck_caso_reporte");
    }
}
