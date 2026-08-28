package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU94ElevarAlComite.Asunto;
import bo.aportaya.cumplimiento.aplicacion.CU94ElevarAlComite.Compromiso;
import bo.aportaya.cumplimiento.aplicacion.CU94ElevarAlComite.EntradaSesion;
import bo.aportaya.cumplimiento.dominio.QuorumDeComite;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Traza;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-94 · Lo que la base y el caso de uso rechazan. */
class CU94RechazosTest extends BaseDeCumplimiento {

    private static final String FUNDAMENTO =
            "El asunto se trato con la documentacion completa y el comite lo aprobo por unanimidad.";

    private UUID presidente;
    private UUID oficial;
    private UUID tecnologia;
    private ContextoSesion ctx;
    private LocalDate hoy;

    @BeforeEach
    void escenario() {
        hoy = LocalDate.now(ZoneOffset.UTC);
        presidente = fixtura.usuario();
        oficial = fixtura.usuario();
        tecnologia = fixtura.usuario();
        ctx = ContextoSesion.de(
                presidente, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
        gobiernoFixtura.comite("CUMPLIMIENTO", 3, "[\"DIRECTORIO\", \"CUMPLIMIENTO\", \"TECNOLOGIA\"]", "TRIMESTRAL");
    }

    private List<QuorumDeComite.Asistente> mesa() {
        return List.of(
                new QuorumDeComite.Asistente(presidente, "DIRECTORIO"),
                new QuorumDeComite.Asistente(oficial, "CUMPLIMIENTO"),
                new QuorumDeComite.Asistente(tecnologia, "TECNOLOGIA"));
    }

    private List<QuorumDeComite.Voto> votos() {
        return List.of(
                new QuorumDeComite.Voto(0, presidente, "A_FAVOR", null),
                new QuorumDeComite.Voto(0, oficial, "A_FAVOR", null),
                new QuorumDeComite.Voto(0, tecnologia, "A_FAVOR", null));
    }

    private EntradaSesion sesion(
            List<QuorumDeComite.Asistente> asistentes,
            List<QuorumDeComite.Voto> votos,
            Map<Integer, Set<UUID>> interesados,
            List<Asunto> asuntos) {
        return new EntradaSesion(
                "CUMPLIMIENTO",
                "ACTA-" + UUID.randomUUID().toString().substring(0, 8),
                hoy,
                asistentes,
                votos,
                interesados,
                asuntos,
                "[]",
                "[]",
                "[]",
                "b".repeat(64));
    }

    private List<Asunto> unAsunto() {
        return List.of(new Asunto("POLITICA", UUID.randomUUID(), "Politica anual", "APROBAR", FUNDAMENTO, List.of()));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El acta conserva su hash y su documento: son lo que hace que la version del
        // acta que se lee sea la que se firmo.
        var salida = transaccion.execute(t -> comiteCU.sesionar(sesion(mesa(), votos(), Map.of(), unAsunto()), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.acta_comite
                         WHERE id = ? AND length(hash_documento) = 64 AND url_documento IS NOT NULL
                        """,
                        salida.actaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        var salida = transaccion.execute(t -> comiteCU.sesionar(sesion(mesa(), votos(), Map.of(), unAsunto()), ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.acta_comite_cerrada' AND agregado_id = ?
                           AND payload->>'asuntosResueltos' IS NOT NULL
                        """,
                        salida.actaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        // Las actas no se depuran: son la unica prueba de que una decision fue del
        // comite y no de una persona.
        var salida = transaccion.execute(t -> comiteCU.sesionar(sesion(mesa(), votos(), Map.of(), unAsunto()), ctx));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO cumplimiento.acta_comite
                            (comite_gobierno_id, numero, fecha, asistentes, cumple_quorum, temas_tratados,
                             decisiones, url_documento, hash_documento)
                        SELECT comite_gobierno_id, numero, fecha, asistentes, cumple_quorum, temas_tratados,
                               decisiones, url_documento, hash_documento
                          FROM cumplimiento.acta_comite WHERE id = ?
                        """,
                        salida.actaId()))
                .contains("uq_acta_comite_comite_gobierno_id_numero");
    }

    @Test
    @DisplayName("rechaza por R-LIC-03")
    void rechazaRLIC03() {
        // Sin quorum no hay acta, y sin acta no hay politica vigente: la cadena entera
        // se corta aca.
        var sinQuorum =
                sesion(List.of(new QuorumDeComite.Asistente(presidente, "DIRECTORIO")), votos(), Map.of(), unAsunto());

        assertThatThrownBy(() -> transaccion.execute(t -> comiteCU.sesionar(sinQuorum, ctx)))
                .hasMessageContaining("Sesionaron 1 de 3");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.acta_comite WHERE numero = ?",
                        sinQuorum.numeroDeActa()))
                .isZero();
    }

    @Test
    @DisplayName("rechaza por R-RIS-01")
    void rechazaRRIS01() {
        // Cada compromiso sale con responsable y fecha: un acta con decisiones y sin
        // compromisos es un acta que no cambia nada.
        var salida = transaccion.execute(t -> comiteCU.sesionar(
                sesion(
                        mesa(),
                        votos(),
                        Map.of(),
                        List.of(new Asunto(
                                "POLITICA",
                                UUID.randomUUID(),
                                "Politica anual",
                                "APROBAR",
                                FUNDAMENTO,
                                List.of(new Compromiso("Publicar la politica", tecnologia, hoy.plusDays(30)))))),
                ctx));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.plan_accion_riesgo
                         WHERE id = ? AND responsable_id IS NOT NULL AND fecha_compromiso IS NOT NULL
                           AND estado = 'PENDIENTE'
                        """,
                        salida.planesGenerados().get(0)))
                .isEqualTo(1);

        assertThat(rechazaLaBase(
                        "UPDATE cumplimiento.plan_accion_riesgo SET estado = 'INVENTADO' WHERE id = ?",
                        salida.planesGenerados().get(0)))
                .contains("ck_plan_accion_riesgo_estado");
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // Nadie decide sobre su propio asunto. Si vota, la decision queda viciada aunque
        // el resultado sea el correcto.
        var conInteres = sesion(mesa(), votos(), Map.of(0, Set.of(oficial)), unAsunto());

        assertThatThrownBy(() -> transaccion.execute(t -> comiteCU.sesionar(conInteres, ctx)))
                .hasMessageContaining("interes directo");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.acta_comite WHERE numero = ?",
                        conInteres.numeroDeActa()))
                .isZero();

        // Y quien se abstiene tiene que decir por que: una abstencion sin motivo no
        // explica nada a quien lea el acta despues.
        var sinMotivo = sesion(
                mesa(),
                List.of(
                        new QuorumDeComite.Voto(0, presidente, "A_FAVOR", null),
                        new QuorumDeComite.Voto(0, oficial, "ABSTENCION", null)),
                Map.of(),
                unAsunto());
        assertThatThrownBy(() -> transaccion.execute(t -> comiteCU.sesionar(sinMotivo, ctx)))
                .hasMessageContaining("abstencion sin motivo");
    }
}
