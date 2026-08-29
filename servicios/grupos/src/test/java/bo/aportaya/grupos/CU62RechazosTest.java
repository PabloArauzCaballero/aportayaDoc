package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-62 · Lo que la base y el caso de uso rechazan, una prueba por restriccion citada. */
class CU62RechazosTest extends BaseDeCU62 {

    private record Escenario(UUID grupo, List<UUID> turnos, List<UUID> participantes) {}

    private Escenario escenario() {
        UUID grupo = fixtura.grupoConformado(6);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 6);
        List<UUID> periodos = fixtura.periodos(grupo, 6, new BigDecimal("3000.00"));
        List<UUID> turnos = fixtura.turnos(grupo, periodos, cuposDe(grupo));
        return new Escenario(grupo, turnos, participantes);
    }

    @Test
    @DisplayName("rechaza por R-GRP-07")
    void rechazaRGRP07() {
        // Un turno cobrado o en curso no se permuta. Permutar un turno ya entregado
        // dejaria a alguien cobrando dos veces y a otro sin cobrar nunca.
        Escenario e = escenario();
        transaccion.execute(t -> {
            dsl.execute(
                    "UPDATE grupos.turno SET estado = 'ENTREGADO' WHERE id = ?",
                    e.turnos().get(0));
            return null;
        });

        assertThatThrownBy(() -> solicitar(e.turnos().get(0), e.turnos().get(1), e.participantes(), true, true, true))
                .isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("rechaza por R-GRP-06")
    void rechazaRGRP06() {
        // Un orden unico por grupo. Dos turnos con el mismo orden dejan el calendario
        // sin decir quien cobra primero.
        Escenario e = escenario();

        String error = rechazaLaBase("UPDATE grupos.turno SET orden = (SELECT orden FROM grupos.turno WHERE id = '"
                + e.turnos().get(0) + "') WHERE id = '" + e.turnos().get(1) + "'");

        assertThat(error).isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // La permuta emite su evento en la misma transaccion: es un cambio del
        // calendario, y el calendario lo miran otros servicios.
        Escenario e = escenario();
        UUID permuta = solicitar(e.turnos().get(0), e.turnos().get(1), e.participantes(), true, true, true);

        Integer eventos = dsl.fetchOne(
                        "SELECT count(*)::int FROM comun.outbox WHERE agregado_id = ? AND tipo LIKE 'grupos.permuta%'",
                        permuta)
                .get(0, Integer.class);

        assertThat(eventos).isGreaterThanOrEqualTo(1);
    }
}
