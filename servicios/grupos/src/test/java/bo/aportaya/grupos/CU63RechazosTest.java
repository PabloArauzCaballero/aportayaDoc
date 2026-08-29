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

/** CU-63 · Lo que la base y el caso de uso rechazan, una prueba por restriccion citada. */
class CU63RechazosTest extends BaseDeCU63 {

    @Test
    @DisplayName("rechaza por R-GRP-08")
    void rechazaRGRP08() {
        // Un voto por participante y acuerdo. Poder votar dos veces convierte el
        // quorum en un numero que depende de quien insiste mas.
        UUID grupo = fixtura.grupoConformado(6);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 6);
        UUID acuerdo = proponer(grupo, "CAMBIO_FECHA_COBRO", new BigDecimal("0.660"), Optional.empty());
        votar(acuerdo, participantes.get(0), "A_FAVOR");

        String error = rechazaLaBase("INSERT INTO grupos.voto_participante (id, acuerdo_id, participante_id, sentido,"
                + " peso, emitido_en) VALUES (gen_random_uuid(), '" + acuerdo + "', '" + participantes.get(0)
                + "', 'EN_CONTRA', 1.000, now())");

        assertThat(error).isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-GRP-08 · el voto es inmutable")
    void rechazaRGRP08Edicion() {
        // Un voto emitido no se cambia. Si se pudiera, el resultado de un acuerdo
        // dependeria de quien edita la tabla al final y no de lo que la gente voto.
        UUID grupo = fixtura.grupoConformado(6);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 6);
        UUID acuerdo = proponer(grupo, "CAMBIO_FECHA_COBRO", new BigDecimal("0.660"), Optional.empty());
        votar(acuerdo, participantes.get(0), "A_FAVOR");

        String error = rechazaLaBase(
                "UPDATE grupos.voto_participante SET sentido = 'EN_CONTRA' WHERE acuerdo_id = '" + acuerdo + "'");

        assertThat(error).isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-GRP-09")
    void rechazaRGRP09() {
        // Un acuerdo abierto por tipo y objeto. Dos propuestas vivas sobre lo mismo
        // parten el quorum en dos y ninguna llega: la gobernanza se traba sola.
        UUID grupo = fixtura.grupoConformado(6);
        fixtura.participantesConCupo(grupo, 6);
        proponer(grupo, "CAMBIO_FECHA_COBRO", new BigDecimal("0.660"), Optional.empty());

        assertThatThrownBy(() -> proponer(grupo, "CAMBIO_FECHA_COBRO", new BigDecimal("0.660"), Optional.empty()))
                .isInstanceOf(ErrorDeNegocio.class);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // La bitacora es append-only: se corrige con un registro inverso, nunca
        // borrando. Una bitacora que se puede borrar no prueba nada.
        dejarUnaFilaEnLaBitacora();

        String error = rechazaLaBase("DELETE FROM comun.bitacora_evento WHERE entidad = 'prueba_acuerdo'");

        assertThat(error).contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Todo cambio relevante emite su evento EN LA MISMA transaccion. Un acuerdo
        // propuesto que no deja evento es un acuerdo del que nadie se entera.
        UUID grupo = fixtura.grupoConformado(6);
        fixtura.participantesConCupo(grupo, 6);
        UUID acuerdo = proponer(grupo, "CAMBIO_FECHA_COBRO", new BigDecimal("0.660"), Optional.empty());

        Integer eventos = dsl.fetchOne(
                        "SELECT count(*)::int FROM comun.outbox WHERE agregado_id = ? AND tipo = 'grupos.acuerdo_propuesto'",
                        acuerdo)
                .get(0, Integer.class);

        assertThat(eventos).isEqualTo(1);
    }
}
