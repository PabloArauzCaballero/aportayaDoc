package bo.aportaya.nucleofinanciero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CU-24 · las restricciones de la base, ejercidas saltándose {@code CU24RegistrarAsiento}
 * a propósito (`pruebas-cu`: si la prueba pasa porque la aplicación validó antes, no
 * probó la restricción).
 */
class CU24RechazosTest extends BaseDeCU24 {

    @Test
    @DisplayName("rechaza R-AUD-05: un asiento CONFIRMADO cuyos movimientos no cuadran no puede confirmarse")
    void rechazaDescuadreAlConfirmar() {
        UUID asientoId = UUID.randomUUID();
        UUID cuenta = fixtura.cuentaDeMovimiento(codigoCorto(), "ACTIVO", "DEUDORA");

        // El trigger de R-AUD-05 es DEFERRABLE INITIALLY DEFERRED: solo corre al intentar
        // el COMMIT, así que acá NO se usa `rechazaLaBase` (que hace rollback a propósito
        // y nunca llegaría a confirmar nada).
        assertThatThrownBy(() -> transaccion.execute(estado -> {
                    dsl.execute(
                            """
                            INSERT INTO nucleo_financiero.asiento_contable
                                (id, fecha, glosa, origen_tipo, origen_id, estado)
                            VALUES (?, now(), 'descuadre a proposito', 'AJUSTE', gen_random_uuid(), 'CONFIRMADO')
                            """,
                            asientoId);
                    dsl.execute(
                            """
                            INSERT INTO nucleo_financiero.movimiento_contable
                                (id, asiento_id, cuenta_id, debe, haber, descripcion)
                            VALUES (gen_random_uuid(), ?, ?, 10.00, 0.00, 'solo debe, sin contrapartida')
                            """,
                            asientoId,
                            cuenta);
                    return null;
                }))
                .satisfies(e -> assertThat(raizDe(e)).contains("R-AUD-05"));
    }

    @Test
    @DisplayName(
            "Dado un asiento confirmado · Cuando se intenta hacer UPDATE de un movimiento_contable · Entonces la base de datos lo rechaza")
    // R-AUD-01: append-only por privilegio y por trigger.
    void rechazaEditarUnMovimiento() {
        UUID cuenta = fixtura.cuentaDeMovimiento(codigoCorto(), "ACTIVO", "DEUDORA");
        UUID contrapartida = fixtura.cuentaDeMovimiento(codigoCorto(), "INGRESO", "ACREEDORA");
        UUID movimientoId = UUID.randomUUID();
        UUID asientoId = UUID.randomUUID();
        transaccion.execute(estado -> {
            dsl.execute(
                    """
                    INSERT INTO nucleo_financiero.asiento_contable
                        (id, fecha, glosa, origen_tipo, origen_id, estado)
                    VALUES (?, now(), 'asiento valido', 'AJUSTE', gen_random_uuid(), 'CONFIRMADO')
                    """,
                    asientoId);
            dsl.execute(
                    """
                    INSERT INTO nucleo_financiero.movimiento_contable
                        (id, asiento_id, cuenta_id, debe, haber, descripcion)
                    VALUES (?, ?, ?, 20.00, 0.00, 'linea original')
                    """,
                    movimientoId,
                    asientoId,
                    cuenta);
            dsl.execute(
                    """
                    INSERT INTO nucleo_financiero.movimiento_contable
                        (id, asiento_id, cuenta_id, debe, haber, descripcion)
                    VALUES (gen_random_uuid(), ?, ?, 0.00, 20.00, 'contrapartida')
                    """,
                    asientoId,
                    contrapartida);
            return null;
        });

        assertThat(rechazaLaBase("UPDATE nucleo_financiero.movimiento_contable SET debe = 999.00 WHERE id = '%s'"
                        .formatted(movimientoId)))
                .containsIgnoringCase("append-only");
    }

    @Test
    @DisplayName("rechaza R-AUD-06: un asiento no puede ser su propia reversa")
    void rechazaReversaDeSiMismo() {
        UUID id = UUID.randomUUID();

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.asiento_contable
                            (id, fecha, glosa, origen_tipo, origen_id, estado, asiento_reversa_id)
                        VALUES ('%s', now(), 'reversa de si mismo', 'AJUSTE', gen_random_uuid(), 'BORRADOR', '%s')
                        """
                                .formatted(id, id)))
                .contains("ck_asiento_reversa_distinta");
    }

    @Test
    @DisplayName("rechaza R-CTB-02: una cuenta sumarizadora no recibe movimientos directos")
    void rechazaMovimientoEnCuentaSumarizadora() {
        UUID sumarizadora = fixtura.cuentaSumarizadora(codigoCorto());
        UUID asientoId = UUID.randomUUID();
        transaccion.execute(estado -> {
            dsl.execute(
                    """
                    INSERT INTO nucleo_financiero.asiento_contable
                        (id, fecha, glosa, origen_tipo, origen_id, estado)
                    VALUES (?, now(), 'contra sumarizadora', 'AJUSTE', gen_random_uuid(), 'BORRADOR')
                    """,
                    asientoId);
            return null;
        });

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.movimiento_contable
                            (id, asiento_id, cuenta_id, debe, haber, descripcion)
                        VALUES (gen_random_uuid(), '%s', '%s', 5.00, 0.00, 'no deberia entrar')
                        """
                                .formatted(asientoId, sumarizadora)))
                .contains("R-CTB-02");
    }

    @Test
    @DisplayName("rechaza R-AUD-11: un asiento REVERSADO sin enlace al original no entra")
    void rechazaReversadoSinEnlace() {
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO nucleo_financiero.asiento_contable
                            (id, fecha, glosa, origen_tipo, origen_id, estado)
                        VALUES (gen_random_uuid(), now(), 'reversado sin original', 'AJUSTE',
                                gen_random_uuid(), 'REVERSADO')
                        """))
                .contains("ck_asiento_reversado_enlazado");
    }

    @Test
    @DisplayName("rechaza R-AUD-11: un asiento enlazado a un original pero no marcado REVERSADO tampoco")
    void rechazaEnlaceSinEstadoReversado() {
        UUID originalId = UUID.randomUUID();
        transaccion.execute(estado -> {
            dsl.execute(
                    """
                    INSERT INTO nucleo_financiero.asiento_contable
                        (id, fecha, glosa, origen_tipo, origen_id, estado)
                    VALUES (?, now(), 'original', 'AJUSTE', gen_random_uuid(), 'BORRADOR')
                    """,
                    originalId);
            return null;
        });

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO nucleo_financiero.asiento_contable
                            (id, fecha, glosa, origen_tipo, origen_id, estado, asiento_reversa_id)
                        VALUES (gen_random_uuid(), now(), 'enlazada pero confirmada', 'AJUSTE',
                                gen_random_uuid(), 'CONFIRMADO', '%s')
                        """
                                .formatted(originalId)))
                .contains("ck_asiento_reversado_enlazado");
    }

    @Test
    @DisplayName("rechaza R-AUD-05: una reversa descuadrada tampoco pasa — el cuadre también se le exige a REVERSADO")
    void rechazaReversaDescuadrada() {
        UUID cuenta = fixtura.cuentaDeMovimiento(codigoCorto(), "ACTIVO", "DEUDORA");
        UUID contrapartida = fixtura.cuentaDeMovimiento(codigoCorto(), "INGRESO", "ACREEDORA");
        UUID originalId = UUID.randomUUID();
        transaccion.execute(estado -> {
            dsl.execute(
                    """
                    INSERT INTO nucleo_financiero.asiento_contable
                        (id, fecha, glosa, origen_tipo, origen_id, estado)
                    VALUES (?, now(), 'original cuadrado', 'AJUSTE', gen_random_uuid(), 'CONFIRMADO')
                    """,
                    originalId);
            dsl.execute(
                    """
                    INSERT INTO nucleo_financiero.movimiento_contable
                        (id, asiento_id, cuenta_id, debe, haber, descripcion)
                    VALUES (gen_random_uuid(), ?, ?, 20.00, 0.00, 'debe')
                    """,
                    originalId,
                    cuenta);
            dsl.execute(
                    """
                    INSERT INTO nucleo_financiero.movimiento_contable
                        (id, asiento_id, cuenta_id, debe, haber, descripcion)
                    VALUES (gen_random_uuid(), ?, ?, 0.00, 20.00, 'haber')
                    """,
                    originalId,
                    contrapartida);
            return null;
        });

        UUID reversaId = UUID.randomUUID();
        assertThatThrownBy(() -> transaccion.execute(estado -> {
                    dsl.execute(
                            """
                            INSERT INTO nucleo_financiero.asiento_contable
                                (id, fecha, glosa, origen_tipo, origen_id, estado, asiento_reversa_id)
                            VALUES (?, now(), 'reversa descuadrada', 'AJUSTE', gen_random_uuid(),
                                    'REVERSADO', ?)
                            """,
                            reversaId,
                            originalId);
                    dsl.execute(
                            """
                            INSERT INTO nucleo_financiero.movimiento_contable
                                (id, asiento_id, cuenta_id, debe, haber, descripcion)
                            VALUES (gen_random_uuid(), ?, ?, 0.00, 20.00, 'solo una pata')
                            """,
                            reversaId,
                            cuenta);
                    return null;
                }))
                .satisfies(e -> assertThat(raizDe(e)).contains("R-AUD-05"));
    }
}
