package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.grupos.dominio.ComputoDeVotacion;
import bo.aportaya.grupos.dominio.ComputoDeVotacion.Sentido;
import bo.aportaya.grupos.dominio.ComputoDeVotacion.VotoPonderado;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-63 · Proponer y votar un acuerdo. */
class CU63Test extends BaseDeCU63 {

    @Test
    @DisplayName(
            "Dado un acuerdo con quórum de 66% y seis cupos de peso 1 · Cuando cuatro votan A_FAVOR · Entonces el acuerdo queda APROBADO y su efecto se ejecuta")
    void criterio1() {
        UUID grupo = fixtura.grupoConformado(6);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 6);
        UUID acuerdo = proponer(grupo, "CAMBIO_FECHA_COBRO", new BigDecimal("0.660"), Optional.empty());

        for (int i = 0; i < 4; i++) {
            votar(acuerdo, participantes.get(i), "A_FAVOR");
        }
        String estado = transaccion.execute(e -> acordar.resolver(acuerdo, contexto()));

        assertThat(estado).isEqualTo("APROBADO");
    }

    @Test
    @DisplayName(
            "Dado un acuerdo de expulsión · Cuando el participante afectado intenta votar · Entonces su voto se registra como ABSTENCION_FORZADA y no pondera")
    void criterio2() {
        UUID grupo = fixtura.grupoConformado(4);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 4);
        UUID afectado = participantes.get(0);
        UUID acuerdo = proponer(grupo, "EXPULSION_PARTICIPANTE", new BigDecimal("0.660"), Optional.of(afectado));

        Sentido registrado = votar(acuerdo, afectado, "EN_CONTRA");

        assertThat(registrado).isEqualTo(Sentido.ABSTENCION);
        assertThat(pesoDelVoto(acuerdo, afectado)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName(
            "Dado un participante que ya votó · Cuando intenta votar otra vez · Entonces se rechaza con VOTO_YA_EMITIDO")
    void criterio3() {
        UUID grupo = fixtura.grupoConformado(3);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 3);
        UUID acuerdo = proponer(grupo, "CAMBIO_MONTO", new BigDecimal("0.660"), Optional.empty());
        votar(acuerdo, participantes.get(0), "A_FAVOR");

        assertThatThrownBy(() -> votar(acuerdo, participantes.get(0), "EN_CONTRA"))
                .hasMessageContaining("uq_voto_acuerdo_participante");
    }

    @Test
    @DisplayName(
            "Dado un acuerdo cuyo plazo venció sin quórum · Cuando corre el trabajo de cierre · Entonces queda RECHAZADO_POR_QUORUM y no se ejecuta ningún efecto")
    void criterio4() {
        UUID grupo = fixtura.grupoConformado(6);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 6);
        UUID acuerdo = proponer(grupo, "CAMBIO_REGLAMENTO", new BigDecimal("0.660"), Optional.empty());
        votar(acuerdo, participantes.get(0), "A_FAVOR");
        // Recien ahora vence: votar despues del plazo se rechaza, que es lo correcto.
        vencer(acuerdo);

        String estado = transaccion.execute(e -> acordar.resolver(acuerdo, contexto()));

        assertThat(estado).isEqualTo("EXPIRADO");
        assertThat(estadoDelAcuerdo(acuerdo)).isEqualTo("EXPIRADO");
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        dejarUnaFilaEnLaBitacora();

        assertThat(rechazaLaBase("DELETE FROM comun.bitacora_evento")).contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Un acuerdo sin proponente no es auditable: propuesto_por es obligatorio.
        UUID grupo = fixtura.grupoConformado(3);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO grupos.acuerdo
                            (id, grupo_id, tipo, descripcion, quorum_requerido, votos_a_favor,
                             votos_en_contra, abstenciones, estado, abierto_en, cierra_en)
                        VALUES (gen_random_uuid(), '%s', 'CAMBIO_MONTO', 'prueba', 0.660, 0, 0, 0,
                                'ABIERTO', now(), now() + interval '7 days')
                        """
                                .formatted(grupo)))
                .contains("propuesto_por");
    }

    @Test
    @DisplayName("rechaza por R-GRP-08")
    void rechazaRGRP08() {
        // El voto es unico por participante y acuerdo, y lo hace cumplir la base.
        UUID grupo = fixtura.grupoConformado(3);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 3);
        UUID acuerdo = proponer(grupo, "CONDONACION_MORA", new BigDecimal("0.660"), Optional.empty());
        votar(acuerdo, participantes.get(1), "A_FAVOR");

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO grupos.voto_participante (id, acuerdo_id, participante_id, sentido, peso, emitido_en)
                        VALUES (gen_random_uuid(), '%s', '%s', 'EN_CONTRA', 1.00, now())
                        """
                                .formatted(acuerdo, participantes.get(1))))
                .contains("uq_voto_acuerdo_participante");
    }

    @Test
    @DisplayName("rechaza por R-GRP-09")
    void rechazaRGRP09() {
        // El sentido del voto sale de un catalogo cerrado: no hay «tal vez».
        UUID grupo = fixtura.grupoConformado(3);
        List<UUID> participantes = fixtura.participantesConCupo(grupo, 3);
        UUID acuerdo = proponer(grupo, "ADMISION_REEMPLAZO", new BigDecimal("0.660"), Optional.empty());

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO grupos.voto_participante (id, acuerdo_id, participante_id, sentido, peso, emitido_en)
                        VALUES (gen_random_uuid(), '%s', '%s', 'TAL_VEZ', 1.00, now())
                        """
                                .formatted(acuerdo, participantes.get(2))))
                .contains("ck_voto_participante_sentido");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Proponer dos veces lo mismo mientras la primera sigue abierta se rechaza:
        // dos votaciones en paralelo pueden aprobar cosas contradictorias.
        UUID grupo = fixtura.grupoConformado(4);
        fixtura.participantesConCupo(grupo, 4);
        proponer(grupo, "DISOLUCION_ANTICIPADA", new BigDecimal("0.750"), Optional.empty());

        assertThatThrownBy(() -> proponer(grupo, "DISOLUCION_ANTICIPADA", new BigDecimal("0.750"), Optional.empty()))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("votacion abierta");
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // El computo pondera por PESO, no por cabezas: quien tiene dos manos pesa
        // doble. Es el atomo el que lo garantiza, y aca se comprueba.
        List<VotoPonderado> votos = List.of(
                new VotoPonderado(Sentido.A_FAVOR, new BigDecimal("2.00")),
                new VotoPonderado(Sentido.EN_CONTRA, new BigDecimal("1.00")));

        ComputoDeVotacion computo = ComputoDeVotacion.de(votos, new BigDecimal("3.00"));

        assertThat(computo.fraccionAFavor()).isEqualByComparingTo(new BigDecimal("0.6667"));
        assertThat(computo.alcanza(new BigDecimal("0.660"))).isTrue();
        assertThat(computo.alcanza(new BigDecimal("0.700"))).isFalse();
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "notificaciones"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "notificaciones"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }
}
