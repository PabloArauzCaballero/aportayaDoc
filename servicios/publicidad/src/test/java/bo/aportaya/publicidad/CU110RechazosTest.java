package bo.aportaya.publicidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.publicidad.aplicacion.CU110AltaDeAnunciante.EntradaAnunciante;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-110 · Lo que la base y el caso de uso rechazan. */
class CU110RechazosTest extends BaseDePublicidad {

    private ContextoSesion operaciones;

    @BeforeEach
    void escenario() {
        operaciones = contextoDe(fixtura.usuario());
    }

    @Test
    @DisplayName("rechaza por R-PUB-01")
    void rechazaRPUB01() {
        UUID organizadorId = fixtura.organizador("HABILITADO");
        UUID socioId = fixtura.socio("ACTIVO");

        // Un anunciante sin dueno no le pertenece a nadie: el gasto que genere no se le
        // puede cobrar a nadie tampoco.
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO publicidad.anunciante
                            (tipo, organizador_id, socio_comercial_id, razon_social_facturacion, estado, creado_en)
                        VALUES ('ORGANIZADOR', NULL, NULL, 'Sin dueno', 'ACTIVO', now())
                        """))
                .contains("ck_anunciante_tipo_exclusivo");

        // Ni dos duenos.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.anunciante
                            (tipo, organizador_id, socio_comercial_id, razon_social_facturacion, estado, creado_en)
                        VALUES ('SOCIO_COMERCIAL', ?, ?, 'Dos duenos', 'ACTIVO', now())
                        """,
                        organizadorId,
                        socioId))
                .contains("ck_anunciante_tipo_exclusivo");

        // Ni una referencia que no concuerde con el tipo.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.anunciante
                            (tipo, organizador_id, socio_comercial_id, razon_social_facturacion, estado, creado_en)
                        VALUES ('SOCIO_COMERCIAL', ?, NULL, 'Tipo cruzado', 'ACTIVO', now())
                        """,
                        organizadorId))
                .contains("ck_anunciante_tipo_exclusivo");

        // Y el caso de uso lo dice antes de escribir, con el nombre del problema.
        assertThatThrownBy(() -> transaccion.execute(t -> anuncianteCU.darDeAlta(
                        new EntradaAnunciante("SOCIO_COMERCIAL", organizadorId, null, "Tipo cruzado", null, "BOB"),
                        operaciones)))
                .satisfies(e -> assertThat(raizDe(e)).contains("REFERENCIA_AJENA_AL_TIPO"));
    }

    @Test
    @DisplayName("rechaza por R-PUB-02")
    void rechazaRPUB02() {
        UUID organizadorId = fixtura.organizador("HABILITADO");
        var alta = transaccion.execute(t -> anuncianteCU.darDeAlta(
                new EntradaAnunciante(
                        "ORGANIZADOR", organizadorId, null, "Pasanaku del barrio", new BigDecimal("400.00"), "BOB"),
                operaciones));

        // Una cuenta por anunciante: con dos, el limite mensual se duplica sin que
        // nadie lo haya autorizado.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO publicidad.cuenta_publicitaria
                            (anunciante_id, limite_gasto_mensual, moneda, saldo_consumido_mes, estado, creada_en)
                        VALUES (?, 400, 'BOB', 0, 'ACTIVA', now())
                        """,
                        alta.anuncianteId()))
                .contains("uq_cuenta_publicitaria_anunciante");

        // Y el consumido no pasa del limite ni baja de cero.
        assertThat(rechazaLaBase(
                        "UPDATE publicidad.cuenta_publicitaria SET saldo_consumido_mes = 400.01 WHERE id = ?",
                        alta.cuentaPublicitariaId()))
                .contains("ck_cuenta_publicitaria_consumo");
        assertThat(rechazaLaBase(
                        "UPDATE publicidad.cuenta_publicitaria SET saldo_consumido_mes = -1 WHERE id = ?",
                        alta.cuentaPublicitariaId()))
                .contains("ck_cuenta_publicitaria_consumo");
    }
}
