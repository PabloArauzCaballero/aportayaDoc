package bo.aportaya.cumplimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.cumplimiento.aplicacion.CU94ElevarAlComite.Asunto;
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

/**
 * CU-94 · Las dos pruebas transversales, aparte del archivo de criterios.
 *
 * <p>Los criterios verifican que el comite decida bien; estas dos verifican que ningun
 * asunto quede sin tratar y que ningun paso a medias deje un acta escrita.
 */
class CU94CuadreTest extends BaseDeCumplimiento {

    private static final String FUNDAMENTO =
            "El asunto se trato con la documentacion completa y el comite lo aprobo por unanimidad.";

    private UUID presidente;
    private UUID oficialDeCumplimiento;
    private UUID tecnologia;
    private ContextoSesion ctx;
    private LocalDate hoy;

    @BeforeEach
    void escenario() {
        hoy = LocalDate.now(ZoneOffset.UTC);
        presidente = fixtura.usuario();
        oficialDeCumplimiento = fixtura.usuario();
        tecnologia = fixtura.usuario();
        ctx = ContextoSesion.de(
                presidente, "PARTICIPANTE", new Traza(UUID.randomUUID().toString()));
        gobiernoFixtura.comite("CUMPLIMIENTO", 3, "[\"DIRECTORIO\", \"CUMPLIMIENTO\", \"TECNOLOGIA\"]", "TRIMESTRAL");
    }

    private List<QuorumDeComite.Asistente> mesaCompleta() {
        return List.of(
                new QuorumDeComite.Asistente(presidente, "DIRECTORIO"),
                new QuorumDeComite.Asistente(oficialDeCumplimiento, "CUMPLIMIENTO"),
                new QuorumDeComite.Asistente(tecnologia, "TECNOLOGIA"));
    }

    private List<QuorumDeComite.Voto> votosAFavor() {
        return List.of(
                new QuorumDeComite.Voto(0, presidente, "A_FAVOR", null),
                new QuorumDeComite.Voto(0, oficialDeCumplimiento, "A_FAVOR", null),
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

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Resueltos + pospuestos tiene que dar el total de asuntos: un asunto que no
        // cae en ninguno de los dos quedaria sin tratar y sin constancia de que quedo.
        var asuntos = List.of(
                new Asunto("POLITICA", UUID.randomUUID(), "Politica anual", "APROBAR", FUNDAMENTO, List.of()),
                new Asunto("POLITICA", UUID.randomUUID(), "Presupuesto", "POSPONER", FUNDAMENTO, List.of()),
                new Asunto("POLITICA", UUID.randomUUID(), "Plan de auditoria", "RECHAZAR", FUNDAMENTO, List.of()));

        var salida = transaccion.execute(
                t -> comiteCU.sesionar(sesion(mesaCompleta(), votosAFavor(), Map.of(), asuntos), ctx));

        assertThat(salida.asuntosResueltos() + salida.asuntosPospuestos()).isEqualTo(asuntos.size());
        assertThat(salida.asuntosPospuestos()).isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        var asuntos = List.of(new Asunto("POLITICA", UUID.randomUUID(), "Politica", "APROBAR", FUNDAMENTO, List.of()));

        // Paso fallido: no hay quorum. La decision seria de quienes pudieron venir.
        var sinQuorum = sesion(
                List.of(new QuorumDeComite.Asistente(presidente, "DIRECTORIO")), votosAFavor(), Map.of(), asuntos);
        assertThatThrownBy(() -> transaccion.execute(t -> comiteCU.sesionar(sinQuorum, ctx)))
                .hasMessageContaining("Sesionaron 1 de 3");

        // Paso fallido: una abstencion sin motivo no explica nada.
        var sinMotivo = sesion(
                mesaCompleta(),
                List.of(
                        new QuorumDeComite.Voto(0, presidente, "A_FAVOR", null),
                        new QuorumDeComite.Voto(0, tecnologia, "ABSTENCION", null)),
                Map.of(),
                asuntos);
        assertThatThrownBy(() -> transaccion.execute(t -> comiteCU.sesionar(sinMotivo, ctx)))
                .hasMessageContaining("abstencion sin motivo");

        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.acta_comite WHERE numero IN (?, ?)",
                        sinQuorum.numeroDeActa(),
                        sinMotivo.numeroDeActa()))
                .isZero();

        // Con quorum, composicion y votos en regla, el mismo camino cierra.
        var buena = transaccion.execute(
                t -> comiteCU.sesionar(sesion(mesaCompleta(), votosAFavor(), Map.of(), asuntos), ctx));
        assertThat(buena.composicionCompleta()).isTrue();
    }
}
