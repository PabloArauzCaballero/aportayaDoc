package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-60 · Lo que la base y el caso de uso rechazan, una prueba por restriccion citada. */
class CU60RechazosTest extends BaseDeCU60 {

    @Test
    @DisplayName("rechaza por R-GRP-05")
    void rechazaRGRP05() {
        // Un solo sorteo por grupo. Si se pudiera comprometer dos veces, quien ejecuta
        // se quedaria con el compromiso que le convenga y descartaria el otro — que es
        // exactamente lo que el protocolo de compromiso existe para impedir.
        UUID grupo = fixtura.grupoConformado(6);
        fixtura.cuposOcupados(grupo, 6);
        transaccion.execute(e -> sortear.comprometer(grupo, List.of(), Optional.empty(), contexto()));

        assertThatThrownBy(() ->
                        transaccion.execute(e -> sortear.comprometer(grupo, List.of(), Optional.empty(), contexto())))
                .isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("rechaza por R-GRP-05 · el compromiso no se edita")
    void rechazaRGRP05Edicion() {
        // El hash publicado no se reescribe. Poder cambiarlo despues de conocer el
        // orden convierte el compromiso en una promesa sin garantia.
        UUID grupo = fixtura.grupoConformado(6);
        fixtura.cuposOcupados(grupo, 6);
        var compromiso = transaccion.execute(e -> sortear.comprometer(grupo, List.of(), Optional.empty(), contexto()));

        String error = rechazaLaBase("UPDATE grupos.sorteo_turnos SET hash_semilla = repeat('f', 64) WHERE id = '"
                + compromiso.sorteoId() + "'");

        assertThat(error).isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-GRP-06")
    void rechazaRGRP06() {
        // Un turno por periodo y un orden unico por grupo. Dos turnos con el mismo
        // orden dejan el calendario sin decir quien cobra primero.
        UUID grupo = fixtura.grupoConformado(6);
        var cupos = fixtura.cuposOcupados(grupo, 6);
        List<UUID> periodos = fixtura.periodos(grupo, 6, new BigDecimal("3000.00"));
        var turnos = fixtura.turnos(grupo, periodos, cupos);

        String error = rechazaLaBase("UPDATE grupos.turno SET orden = 1 WHERE id = '" + turnos.get(1) + "'");

        assertThat(error).isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La bitacora es append-only: se corrige con un registro inverso, nunca
        // editando. Una bitacora que se puede editar no prueba nada.
        dejarUnaFilaEnLaBitacora();

        String error = rechazaLaBase("DELETE FROM comun.bitacora_evento WHERE entidad = 'prueba_sorteo'");

        assertThat(error).contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Todo cambio relevante emite su evento EN LA MISMA transaccion. Un sorteo
        // revelado que no deja evento es un sorteo que ningun otro servicio se entera
        // que ocurrio.
        UUID grupo = fixtura.grupoConformado(6);
        fixtura.cuposOcupados(grupo, 6);
        List<UUID> periodos = fixtura.periodos(grupo, 6, new BigDecimal("3000.00"));
        var compromiso = transaccion.execute(e -> sortear.comprometer(grupo, List.of(), Optional.empty(), contexto()));
        transaccion.execute(e -> sortear.revelar(
                compromiso.sorteoId(),
                compromiso.semilla(),
                List.of(),
                periodos,
                new BigDecimal("500.00"),
                Optional.empty(),
                contexto()));

        Integer eventos = dsl.fetchOne(
                        "SELECT count(*)::int FROM comun.outbox WHERE agregado_id = ? AND tipo = 'grupos.sorteo_revelado'",
                        compromiso.sorteoId())
                .get(0, Integer.class);

        assertThat(eventos).isEqualTo(1);
    }
}
