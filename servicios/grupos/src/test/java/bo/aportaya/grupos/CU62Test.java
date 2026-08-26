package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.grupos.dominio.PermutaPosible;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-62 · Permutar turnos entre participantes. */
class CU62Test extends BaseDeCU62 {

    @Test
    @DisplayName(
            "Dados dos turnos PROGRAMADOS y ambos participantes al día · Cuando la contraparte acepta la permuta · Entonces los dos turnos intercambian orden_asignado y periodo_id · Y cada uno referencia al otro en permutado_con_turno_id")
    void criterio1() {
        UUID grupo = fixtura.grupoConformado(4);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 4);
        List<UUID> periodos = fixtura.periodos(grupo, 4, new BigDecimal("2000.00"));
        List<UUID> turnos = fixtura.turnos(grupo, periodos, cuposDe(grupo));
        UUID cupoAntesDelPrimero = cupoDe(turnos.get(0));
        UUID cupoAntesDelSegundo = cupoDe(turnos.get(1));

        UUID solicitud = solicitar(turnos.get(0), turnos.get(1), participantes, true, true, true);
        transaccion.execute(e -> {
            permutar.aceptar(solicitud, turnos.get(0), turnos.get(1), contexto());
            return null;
        });

        // Quien iba a recibir en el primer periodo ahora recibe en el segundo.
        assertThat(cupoDe(turnos.get(0))).isEqualTo(cupoAntesDelSegundo);
        assertThat(cupoDe(turnos.get(1))).isEqualTo(cupoAntesDelPrimero);
        assertThat(permutadoConDe(turnos.get(0))).isEqualTo(turnos.get(1));
        assertThat(permutadoConDe(turnos.get(1))).isEqualTo(turnos.get(0));
    }

    @Test
    @DisplayName(
            "Dado un turno ya COBRADO · Cuando se intenta permutarlo · Entonces la operación se rechaza con TURNO_NO_PERMUTABLE")
    void criterio2() {
        UUID grupo = fixtura.grupoConformado(3);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 3);
        List<UUID> periodos = fixtura.periodos(grupo, 3, new BigDecimal("1500.00"));
        List<UUID> turnos = fixtura.turnos(grupo, periodos, cuposDe(grupo));
        cobrar(turnos.get(0));

        assertThatThrownBy(() -> solicitar(turnos.get(0), turnos.get(1), participantes, true, true, true))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya no se puede permutar");
    }

    @Test
    @DisplayName(
            "Dado un solicitante con deuda vigente · Cuando propone la permuta · Entonces se rechaza con SOLICITANTE_EN_MORA")
    void criterio3() {
        UUID grupo = fixtura.grupoConformado(3);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 3);
        List<UUID> periodos = fixtura.periodos(grupo, 3, new BigDecimal("1500.00"));
        List<UUID> turnos = fixtura.turnos(grupo, periodos, cuposDe(grupo));

        assertThatThrownBy(() -> solicitar(turnos.get(0), turnos.get(1), participantes, false, true, true))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("Ponete al dia");
    }

    @Test
    @DisplayName("rechaza por R-GRP-06")
    void rechazaRGRP06() {
        // Cada turno un orden unico: el intercambio pasa por un valor libre porque
        // escribir el destino sobre el origen choca contra el indice a mitad de camino.
        UUID grupo = fixtura.grupoConformado(3);
        List<UUID> periodos = fixtura.periodos(grupo, 3, new BigDecimal("1500.00"));
        fixtura.participantesConCupo(grupo, 3);
        List<UUID> turnos = fixtura.turnos(grupo, periodos, cuposDe(grupo));

        assertThat(rechazaLaBase("UPDATE grupos.turno SET orden_asignado = %d WHERE id = '%s'"
                        .formatted(ordenDe(turnos.get(1)), turnos.get(0))))
                .contains("orden_asignado");
    }

    @Test
    @DisplayName("rechaza por R-GRP-07")
    void rechazaRGRP07() {
        // El pasado no se reordena: el atomo lo decide sin consultar nada.
        assertThat(PermutaPosible.impedimento("COBRADO", "PROGRAMADO", true, true, true))
                .contains(PermutaPosible.Motivo.TURNO_NO_PERMUTABLE);
        assertThat(PermutaPosible.impedimento("PROGRAMADO", "EN_CURSO", true, true, true))
                .contains(PermutaPosible.Motivo.TURNO_NO_PERMUTABLE);
        assertThat(PermutaPosible.impedimento("PROGRAMADO", "PROGRAMADO", true, true, true))
                .isEmpty();
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Una solicitud sin motivo escrito no es auditable.
        UUID grupo = fixtura.grupoConformado(3);
        List<UUID> periodos = fixtura.periodos(grupo, 3, new BigDecimal("1500.00"));
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 3);
        List<UUID> turnos = fixtura.turnos(grupo, periodos, cuposDe(grupo));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO grupos.solicitud_permuta
                            (id, turno_origen_id, turno_destino_id, solicitante_id, contraparte_id,
                             estado, aprobada_por_organizador, fecha_solicitud)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', '%s', 'PENDIENTE', false, now())
                        """
                                .formatted(turnos.get(0), turnos.get(1), participantes.get(0), participantes.get(1))))
                .contains("motivo");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Las cuatro razones por las que una permuta no procede, en orden: el atomo
        // devuelve la PRIMERA, para que el mensaje diga lo que hay que arreglar antes.
        assertThat(PermutaPosible.impedimento("COBRADO", "PROGRAMADO", false, false, false))
                .contains(PermutaPosible.Motivo.TURNO_NO_PERMUTABLE);
        assertThat(PermutaPosible.impedimento("PROGRAMADO", "PROGRAMADO", false, false, false))
                .contains(PermutaPosible.Motivo.SOLICITANTE_EN_MORA);
        assertThat(PermutaPosible.impedimento("PROGRAMADO", "PROGRAMADO", true, false, false))
                .contains(PermutaPosible.Motivo.CONTRAPARTE_EN_MORA);
        assertThat(PermutaPosible.impedimento("PROGRAMADO", "PROGRAMADO", true, true, false))
                .contains(PermutaPosible.Motivo.REGLAMENTO_NO_PERMITE);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Permutar dos veces los mismos turnos los devuelve a su lugar: el
        // intercambio es su propia inversa, y eso hace que reintentar no rompa nada.
        UUID grupo = fixtura.grupoConformado(4);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 4);
        List<UUID> periodos = fixtura.periodos(grupo, 4, new BigDecimal("2000.00"));
        List<UUID> turnos = fixtura.turnos(grupo, periodos, cuposDe(grupo));
        UUID cupoOriginal = cupoDe(turnos.get(0));

        UUID primera = solicitar(turnos.get(0), turnos.get(1), participantes, true, true, true);
        transaccion.execute(e -> {
            permutar.aceptar(primera, turnos.get(0), turnos.get(1), contexto());
            return null;
        });
        UUID segunda = solicitar(turnos.get(0), turnos.get(1), participantes, true, true, true);
        transaccion.execute(e -> {
            permutar.aceptar(segunda, turnos.get(0), turnos.get(1), contexto());
            return null;
        });

        assertThat(cupoDe(turnos.get(0))).isEqualTo(cupoOriginal);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "entregas"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "entregas"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("cuadre: permutar no cambia cuantos turnos hay ni deja un periodo sin turno")
    void cuadre() {
        UUID grupo = fixtura.grupoConformado(4);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 4);
        List<UUID> periodos = fixtura.periodos(grupo, 4, new BigDecimal("2000.00"));
        List<UUID> turnos = fixtura.turnos(grupo, periodos, cuposDe(grupo));

        UUID solicitud = solicitar(turnos.get(0), turnos.get(2), participantes, true, true, true);
        transaccion.execute(e -> {
            permutar.aceptar(solicitud, turnos.get(0), turnos.get(2), contexto());
            return null;
        });

        assertThat(turnosDe(grupo)).isEqualTo(4);
        assertThat(periodosSinTurno(grupo)).isZero();
    }
}
