package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.grupos.dominio.TraspasoAdmisible;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-64 · Traspasar un cupo. */
class CU64Test extends BaseDeCU64 {

    @Test
    @DisplayName(
            "Dado un cupo con turno futuro y su titular al día · Cuando se ejecuta el traspaso al entrante · Entonces el cupo cambia de participante y el turno conserva su orden · Y las obligaciones vencidas siguen apuntando al saliente")
    void criterio1() {
        Escenario escenario = escenarioConTurno();
        short ordenAntes = ordenDelCupo(escenario.cupo());

        traspasar(escenario, true, "COMPLETO", 0);

        assertThat(titularDelCupo(escenario.cupo())).isEqualTo(escenario.entrante());
        assertThat(ordenDelCupo(escenario.cupo())).isEqualTo(ordenAntes);
        assertThat(deudaTransferida(escenario.cupo())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName(
            "Dado un saliente con deuda vigente · Cuando intenta traspasar · Entonces se rechaza con SALIENTE_CON_DEUDA")
    void criterio2() {
        Escenario escenario = escenarioConTurno();

        assertThatThrownBy(() -> traspasar(escenario, false, "COMPLETO", 0))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("Salda tus aportes");
    }

    @Test
    @DisplayName(
            "Dado un entrante con KYC por debajo del mínimo del grupo · Cuando acepta la invitación · Entonces se rechaza con ENTRANTE_SIN_KYC y se le ofrece elevar su nivel")
    void criterio3() {
        Escenario escenario = escenarioConTurno();
        exigirKyc(escenario.grupo(), "COMPLETO");

        assertThatThrownBy(() -> traspasar(escenario, true, "BASICO", 0))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("nivel de verificacion");
    }

    @Test
    @DisplayName("rechaza por R-GRP-10")
    void rechazaRGRP10() {
        // Si el reglamento exige acuerdo, sin acuerdo no hay traspaso.
        assertThat(TraspasoAdmisible.impedimento("OCUPADO", false, true, "COMPLETO", "BASICO", 100, 0, false))
                .contains(TraspasoAdmisible.Motivo.ACUERDO_REQUERIDO);
    }

    @Test
    @DisplayName("rechaza por R-GRP-11")
    void rechazaRGRP11() {
        // La deuda NO viaja con el cupo: se registra cero, y es una afirmacion.
        Escenario escenario = escenarioConTurno();

        traspasar(escenario, true, "COMPLETO", 0);

        assertThat(deudaTransferida(escenario.cupo())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("rechaza por R-UIF-09")
    void rechazaRUIF09() {
        // El nivel de conocimiento del cliente es una escala, no un si/no: cada
        // nivel alcanza a los de abajo y a ninguno de arriba.
        assertThat(TraspasoAdmisible.NivelDeKyc.COMPLETO.alcanza(TraspasoAdmisible.NivelDeKyc.BASICO))
                .isTrue();
        assertThat(TraspasoAdmisible.NivelDeKyc.BASICO.alcanza(TraspasoAdmisible.NivelDeKyc.COMPLETO))
                .isFalse();
        assertThat(TraspasoAdmisible.NivelDeKyc.NINGUNO.alcanza(TraspasoAdmisible.NivelDeKyc.BASICO))
                .isFalse();
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Un traspaso sin motivo escrito no es auditable.
        Escenario escenario = escenarioConTurno();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO grupos.traspaso_cupo
                            (id, cupo_id, participante_origen_id, participante_destino_id,
                             deuda_transferida, derecho_cobro_transferido, fecha)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', 0.00, true, now())
                        """
                                .formatted(escenario.cupo(), escenario.saliente(), escenario.entrante())))
                .contains("motivo");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Un cupo ya traspasado no se vuelve a traspasar: su titular cambio.
        Escenario escenario = escenarioConTurno();
        traspasar(escenario, true, "COMPLETO", 0);

        assertThat(titularDelCupo(escenario.cupo())).isEqualTo(escenario.entrante());
        assertThat(estadoDelParticipante(escenario.saliente())).isEqualTo("RETIRADO");
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // El turno cobrado cierra la puerta: el pasado no se traspasa.
        assertThat(TraspasoAdmisible.impedimento("OCUPADO", true, true, "COMPLETO", "BASICO", 100, 0, true))
                .contains(TraspasoAdmisible.Motivo.CUPO_NO_TRASPASABLE);
        assertThat(TraspasoAdmisible.impedimento("LIBRE", false, true, "COMPLETO", "BASICO", 100, 0, true))
                .contains(TraspasoAdmisible.Motivo.CUPO_NO_TRASPASABLE);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "transparencia"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "transparencia"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("cuadre: el grupo no gana ni pierde integrantes al traspasar un cupo")
    void cuadre() {
        Escenario escenario = escenarioConTurno();
        int cuposAntes = cuposDelGrupo(escenario.grupo());

        traspasar(escenario, true, "COMPLETO", 0);

        assertThat(cuposDelGrupo(escenario.grupo())).isEqualTo(cuposAntes);
        assertThat(cuposOcupadosDelGrupo(escenario.grupo())).isEqualTo(cuposAntes);
    }
}
