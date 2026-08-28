package bo.aportaya.organizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.organizador.aplicacion.CU91FirmarContrato.EntradaEmision;
import bo.aportaya.organizador.aplicacion.CU91FirmarContrato.EntradaRescision;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-91 · las pruebas de RECHAZO, una por restriccion citada. */
class CU91RechazosTest extends BaseDeOrganizador {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(UUID usuario, UUID organizadorId, ContextoSesion ctx) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        return new Caso(usuario, fixtura.organizadorHabilitado(usuario), contextoDe(usuario));
    }

    private UUID emitirYFirmar(Caso c) {
        UUID contratoId = transaccion
                .execute(t -> contratoCU.emitir(
                        new EntradaEmision(
                                c.organizadorId(), "v-" + corto(), "a".repeat(64), "Obligaciones", "Causales"),
                        c.ctx()))
                .contratoId();
        transaccion.execute(t -> contratoCU.firmar(contratoId, fixtura.tokenDeFirma(c.usuario()), c.ctx()));
        return contratoId;
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Un contrato rescindido no se borra: queda con su fecha y su motivo. Borrarlo
        // dejaria a la plataforma sin poder mostrar por que se termino el vinculo.
        Caso c = caso();
        UUID contratoId = emitirYFirmar(c);
        transaccion.execute(t -> contratoCU.rescindir(new EntradaRescision(contratoId, "Fraude"), c.ctx()));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM organizador.contrato_organizador
                         WHERE id = ? AND rescindido_en IS NOT NULL AND motivo_rescision = 'Fraude'
                           AND firmado_en IS NOT NULL
                        """,
                        contratoId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Emitir, firmar y rescindir emiten cada uno su evento en la misma transaccion.
        Caso c = caso();
        UUID contratoId = emitirYFirmar(c);
        transaccion.execute(t -> contratoCU.rescindir(new EntradaRescision(contratoId, "Se retira"), c.ctx()));

        assertThat(contar("SELECT count(*)::int FROM organizador.evento_dominio WHERE agregado_id = ?", contratoId))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("rechaza por R-AUD-08")
    void rechazaRAUD08() {
        // El contrato se conserva: su hash sigue apuntando al texto firmado, y sin el
        // no hay forma de probar sobre que se firmo.
        Caso c = caso();
        UUID contratoId = emitirYFirmar(c);

        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.contrato_organizador WHERE id = ? AND length(contenido_hash) = 64",
                        contratoId))
                .isEqualTo(1);
        // La vigencia no puede quedar invertida: un contrato que termina antes de
        // empezar es un contrato que nunca existio, y eso no es lo que paso.
        assertThat(rechazaLaBase(
                        "UPDATE organizador.contrato_organizador SET vigente_hasta = vigente_desde - 1 WHERE id = '%s'"
                                .formatted(contratoId)))
                .contains("ck_contrato_org_vigencia");
    }

    @Test
    @DisplayName("rechaza por R-CON-06")
    void rechazaRCON06() {
        // El contrato dice sus obligaciones y sus causales de rescision, y las dos son
        // obligatorias: un contrato que no dice por que se puede terminar deja al
        // organizador sin saber que lo expone a perder el vinculo.
        Caso c = caso();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.contrato_organizador
                            (id, organizador_id, version, contenido_hash, obligaciones, causales_rescision,
                             vigente_desde)
                        VALUES (gen_random_uuid(), '%s', 'v-mudo', repeat('c', 64), 'Obligaciones', NULL,
                                current_date)
                        """
                                .formatted(c.organizadorId())))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-ORG-02")
    void rechazaRORG02() {
        // Un solo contrato vigente. Dos permiten invocar el texto que mas convenga ante
        // un incumplimiento.
        Caso c = caso();
        emitirYFirmar(c);

        assertThatThrownBy(() -> transaccion.execute(t -> contratoCU.emitir(
                        new EntradaEmision(c.organizadorId(), "v-" + corto(), "b".repeat(64), "Otras", "Otras"),
                        c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya tiene contrato vigente");
    }

    @Test
    @DisplayName("rechaza por R-ORG-03")
    void rechazaRORG03() {
        // Un contrato firmado no se modifica: se emite version nueva. Firmar dos veces
        // sobrescribiria la evidencia de cuando y con que token se firmo de verdad.
        Caso c = caso();
        UUID contratoId = emitirYFirmar(c);
        var firmadoEn = dsl.fetchOne("SELECT firmado_en FROM organizador.contrato_organizador WHERE id = ?", contratoId)
                .get("firmado_en", java.time.OffsetDateTime.class);

        var segunda =
                transaccion.execute(t -> contratoCU.firmar(contratoId, fixtura.tokenDeFirma(c.usuario()), c.ctx()));

        assertThat(segunda.esNueva()).isFalse();
        assertThat(dsl.fetchOne("SELECT firmado_en FROM organizador.contrato_organizador WHERE id = ?", contratoId)
                        .get("firmado_en", java.time.OffsetDateTime.class))
                .isEqualTo(firmadoEn);
    }
}
