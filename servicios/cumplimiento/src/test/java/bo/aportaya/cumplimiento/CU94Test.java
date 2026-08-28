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

/** CU-94 · Elevar una decision al comite de gobierno. */
class CU94Test extends BaseDeCumplimiento {

    private static final String FUNDAMENTO =
            "El producto cubre los cuatro factores y sus controles estan asignados con responsable.";

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
    @DisplayName(
            "Dado un comité con quórum mínimo de tres y composición que exige cumplimiento · Cuando sesionan tres miembros sin el rol de cumplimiento · Entonces se rechaza con COMPOSICION_INCOMPLETA")
    void criterio1() {
        var sinCumplimiento = List.of(
                new QuorumDeComite.Asistente(presidente, "DIRECTORIO"),
                new QuorumDeComite.Asistente(tecnologia, "TECNOLOGIA"),
                new QuorumDeComite.Asistente(fixtura.usuario(), "TECNOLOGIA"));

        // Tres asistentes sin el rol de cumplimiento no son un comite de cumplimiento:
        // falta justamente quien tenia que objetar.
        var entrada = sesion(
                sinCumplimiento,
                votosAFavor(),
                Map.of(),
                List.of(new Asunto("POLITICA", UUID.randomUUID(), "Politica anual", "APROBAR", FUNDAMENTO, List.of())));

        assertThatThrownBy(() -> transaccion.execute(t -> comiteCU.sesionar(entrada, ctx)))
                .hasMessageContaining("Falta en la mesa: CUMPLIMIENTO");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.acta_comite WHERE numero = ?", entrada.numeroDeActa()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dada una sesión con quórum y una evaluación de producto aprobada · Cuando se cierra el acta · Entonces la evaluación queda VIGENTE en la misma transacción")
    void criterio2() {
        UUID evaluacion = EscenarioDeProducto.enBorrador(this, ctx, hoy);

        var salida = transaccion.execute(t -> comiteCU.sesionar(
                sesion(
                        mesaCompleta(),
                        votosAFavor(),
                        Map.of(),
                        List.of(new Asunto(
                                "EVALUACION_RIESGO_PRODUCTO",
                                evaluacion,
                                "Producto nuevo",
                                "APROBAR",
                                FUNDAMENTO,
                                List.of()))),
                ctx));

        assertThat(salida.quorumAlcanzado()).isTrue();
        assertThat(salida.asuntosResueltos()).isEqualTo(1);
        // En la MISMA transaccion: dejarlo para despues abre la puerta a que el acta diga
        // una cosa y el sistema otra. HUECO: el catalogo no tiene 'VIGENTE', asi que lo
        // mas cerca que la base llega es APROBADA con su fecha.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evaluacion_riesgo_producto
                         WHERE id = ? AND estado = 'APROBADA' AND fecha_aprobacion IS NOT NULL
                        """,
                        evaluacion))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un miembro con interés directo en un asunto · Cuando emite voto a favor · Entonces se rechaza con PARTE_INTERESADA")
    void criterio3() {
        // Si vota, la decision queda viciada aunque el resultado sea el correcto.
        var entrada = sesion(
                mesaCompleta(),
                votosAFavor(),
                Map.of(0, Set.of(tecnologia)),
                List.of(new Asunto("POLITICA", UUID.randomUUID(), "Politica", "APROBAR", FUNDAMENTO, List.of())));

        assertThatThrownBy(() -> transaccion.execute(t -> comiteCU.sesionar(entrada, ctx)))
                .hasMessageContaining("interes directo");
        assertThat(contar(
                        "SELECT count(*)::int FROM cumplimiento.acta_comite WHERE numero = ?", entrada.numeroDeActa()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un comité que no sesiona dentro de su periodicidad mínima · Cuando corre el control · Entonces existe un hallazgo_auditoria abierto")
    void criterio4() {
        var salida = transaccion.execute(t -> comiteCU.sesionar(
                sesion(
                        mesaCompleta(),
                        votosAFavor(),
                        Map.of(),
                        List.of(new Asunto(
                                "POLITICA", UUID.randomUUID(), "Politica", "APROBAR", FUNDAMENTO, List.of()))),
                ctx));

        // La proxima sesion limite sale de la periodicidad del comite: es lo que el
        // control diario compara contra la fecha de la ultima acta.
        assertThat(salida.proximaSesionLimite()).isEqualTo(hoy.plusMonths(3));
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.acta_comite a
                          JOIN cumplimiento.comite_gobierno c ON c.id = a.comite_gobierno_id
                         WHERE a.id = ? AND c.periodicidad_minima = 'TRIMESTRAL'
                        """,
                        salida.actaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        var asuntos = List.of(new Asunto("POLITICA", UUID.randomUUID(), "Politica", "APROBAR", FUNDAMENTO, List.of()));
        var entrada = sesion(mesaCompleta(), votosAFavor(), Map.of(), asuntos);

        var a = transaccion.execute(t -> comiteCU.sesionar(entrada, ctx));
        // Levantar el acta dos veces con el mismo numero crearia dos actas de la misma
        // sesion: se rechaza por la unicidad del numero.
        assertThatThrownBy(() -> transaccion.execute(t -> comiteCU.sesionar(entrada, ctx)))
                .isInstanceOf(RuntimeException.class);
        assertThat(contar("SELECT count(*)::int FROM cumplimiento.acta_comite WHERE id = ?", a.actaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        var entrada = sesion(
                mesaCompleta(),
                votosAFavor(),
                Map.of(),
                List.of(new Asunto("POLITICA", UUID.randomUUID(), "Politica", "APROBAR", FUNDAMENTO, List.of())));

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> comiteCU.sesionar(entrada, ctx));
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
                        "SELECT count(*)::int FROM cumplimiento.acta_comite WHERE numero = ?", entrada.numeroDeActa()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        var salida = transaccion.execute(t -> comiteCU.sesionar(
                sesion(
                        mesaCompleta(),
                        votosAFavor(),
                        Map.of(),
                        List.of(new Asunto(
                                "POLITICA",
                                UUID.randomUUID(),
                                "Politica",
                                "APROBAR",
                                FUNDAMENTO,
                                List.of(new Compromiso("Publicar la politica", tecnologia, hoy.plusDays(30)))))),
                ctx));

        assertThat(salida.planesGenerados()).hasSize(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.evento_dominio
                         WHERE tipo = 'cumplimiento.acta_comite_cerrada' AND agregado_id = ?
                        """,
                        salida.actaId()))
                .isEqualTo(1);
        // Cada compromiso sale con responsable y fecha: un acta con decisiones y sin
        // compromisos es un acta que no cambia nada.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM cumplimiento.plan_accion_riesgo
                         WHERE id = ? AND responsable_id IS NOT NULL AND fecha_compromiso IS NOT NULL
                        """,
                        salida.planesGenerados().get(0)))
                .isEqualTo(1);
    }
}
