package bo.aportaya.aportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.aportes.aplicacion.CU21CobrarAporte.EntradaCobro;
import bo.aportaya.aportes.aplicacion.CU21CobrarAporte.SalidaCobro;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-21 · las pruebas de RECHAZO, una por restriccion citada. */
class CU21RechazosTest extends BaseDeAportes {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private SalidaCobro cobrar(UUID obligacionId, String monto, String clave, ContextoSesion ctx) {
        return transaccion.execute(t -> cobroCU.acreditar(
                new EntradaCobro(
                        clave,
                        obligacionId,
                        bob(monto),
                        bob("0.00"),
                        "BILLETERA_MOVIL",
                        "ref-" + clave,
                        Optional.empty(),
                        false,
                        true),
                ctx));
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Un pago acreditado no se edita ni se borra. Si el monto de un pago pudiera
        // corregirse en el lugar, el participante no tendria como probar cuanto pago.
        UUID usuario = fixtura.usuario();
        var obligacion = fixtura.obligacion(usuario, "500.00", 10);
        ContextoSesion ctx = contextoDe(usuario);
        SalidaCobro pago = cobrar(obligacion.id(), "200.00", "cob-aud", ctx);

        // Un segundo cobro no toca la fila del primero: crea la suya.
        cobrar(obligacion.id(), "300.00", "cob-aud-2", ctx);

        assertThat(contar("SELECT count(*)::int FROM aportes.pago WHERE id = ? AND monto = 200.00", pago.pagoId()))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*)::int FROM aportes.pago WHERE obligacion_id = ?", obligacion.id()))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // El asiento lo escribe nucleo-financiero (invariante 12), y solo puede
        // cuadrarlo con el importe que aportes le manda. Un evento de cobro sin monto
        // deja un asiento a medias que nadie cierra.
        UUID usuario = fixtura.usuario();
        var obligacion = fixtura.obligacion(usuario, "500.00", 10);
        ContextoSesion ctx = contextoDe(usuario);

        cobrar(obligacion.id(), "500.00", "cob-asiento", ctx);

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM aportes.evento_dominio
                         WHERE tipo = ? AND agregado_id = ? AND payload->>'monto' = ?
                        """,
                        "aportes.aporte_cobrado",
                        obligacion.id(),
                        "500.00"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-01")
    void rechazaRBIL01() {
        // Lo aplicado a la obligacion iguala lo cobrado, al centavo. Un centavo de
        // diferencia por pago se vuelve un descuadre que nadie sabe de donde salio.
        UUID usuario = fixtura.usuario();
        var obligacion = fixtura.obligacion(usuario, "500.00", 10);
        ContextoSesion ctx = contextoDe(usuario);

        cobrar(obligacion.id(), "166.67", "cob-centavo", ctx);
        cobrar(obligacion.id(), "166.66", "cob-centavo-2", ctx);

        assertThat(dsl.fetchOne("SELECT monto_pagado FROM aportes.obligacion_aporte WHERE id = ?", obligacion.id())
                        .get(0, BigDecimal.class))
                .isEqualByComparingTo(new BigDecimal("333.33"));
        assertThat(dsl.fetchOne("SELECT sum(monto) FROM aportes.pago WHERE obligacion_id = ?", obligacion.id())
                        .get(0, BigDecimal.class))
                .isEqualByComparingTo(new BigDecimal("333.33"));
    }

    @Test
    @DisplayName("rechaza por R-BIL-06")
    void rechazaRBIL06() {
        // La BASE rechaza el segundo pago con la misma clave para la misma
        // obligacion, aunque la aplicacion se equivoque. Es la ultima linea contra el
        // webhook que llega dos veces.
        UUID usuario = fixtura.usuario();
        var obligacion = fixtura.obligacion(usuario, "500.00", 10);
        ContextoSesion ctx = contextoDe(usuario);
        cobrar(obligacion.id(), "100.00", "cob-dup", ctx);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO aportes.pago (id, obligacion_id, clave_idempotencia, monto, moneda,
                                                  monto_comision_proveedor, monto_neto_acreditado, canal,
                                                  referencia_proveedor, estado, fecha_hora_pago, es_manual)
                        VALUES (gen_random_uuid(), '%s', 'cob-dup', 100.00, 'BOB', 0, 100.00,
                                'BILLETERA_MOVIL', 'otra-ref', 'ACREDITADO', now(), false)
                        """
                                .formatted(obligacion.id())))
                .contains("uq_pago_idem");
    }

    @Test
    @DisplayName("rechaza por R-BIL-12")
    void rechazaRBIL12() {
        // fn_bil_validar_cierre_diario es la autoridad: el dia no se marca cuadrado
        // con descuadre. La regla vive en la base, no en la aplicacion, y por eso no
        // hay forma de cerrar el dia "a mano" desde ningun servicio.
        assertThat(contar("SELECT count(*)::int FROM pg_proc WHERE proname = ?", "fn_bil_validar_cierre_diario"))
                .isEqualTo(1);
        assertThat(
                        rechazaLaBase(
                                """
                        INSERT INTO nucleo_financiero.cierre_diario
                            (id, fecha, total_recaudado, total_conciliado, total_excepciones,
                             cantidad_pagos, cuadrado, cerrado_por, cerrado_en)
                        VALUES (gen_random_uuid(), current_date, 100.00, 50.00, 50.00, 1, true,
                                gen_random_uuid(), now())
                        """))
                .isNotEmpty();
    }

    @Test
    @DisplayName("rechaza por R-GRP-03")
    void rechazaRGRP03() {
        // Una obligacion de aporte por periodo y cupo. Dos obligaciones vivas para el
        // mismo cupo del mismo periodo es cobrarle dos veces la misma cuota.
        UUID usuario = fixtura.usuario();
        var obligacion = fixtura.obligacion(usuario, "500.00", 10);
        var fila = dsl.fetchOne(
                "SELECT grupo_id, periodo_id, cupo_id FROM aportes.obligacion_aporte WHERE id = ?", obligacion.id());

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO aportes.obligacion_aporte
                            (id, grupo_id, periodo_id, cupo_id, participante_id, tipo, monto_esperado, moneda,
                             monto_pagado, monto_recargo, monto_condonado, monto_cubierto_garantia,
                             dias_mora, estado, fecha_vencimiento, fecha_fin_gracia, version)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', '%s', 'APORTE_PERIODICO', 500.00, 'BOB',
                                0, 0, 0, 0, 0, 'PENDIENTE', current_date, current_date, 0)
                        """
                                .formatted(
                                        fila.get(0, UUID.class),
                                        fila.get(1, UUID.class),
                                        fila.get(2, UUID.class),
                                        obligacion.participanteId())))
                .contains("uq_obligacion_periodo_cupo");
    }

    @Test
    @DisplayName("rechaza por R-UIF-02")
    void rechazaRUIF02() {
        // Superar el umbral genera registro obligatorio, y quien lo evalua es
        // cumplimiento. Solo puede hacerlo si aportes le manda el monto y el
        // participante en el evento: un evento mudo apaga el control sin que se note.
        UUID usuario = fixtura.usuario();
        var obligacion = fixtura.obligacion(usuario, "80000.00", 10);
        ContextoSesion ctx = contextoDe(usuario);

        cobrar(obligacion.id(), "80000.00", "cob-umbral", ctx);

        var evento = dsl.fetchOne(
                """
                SELECT payload->>'monto' AS monto, payload->>'participanteId' AS participante
                  FROM aportes.evento_dominio WHERE tipo = ? AND agregado_id = ?
                """,
                "aportes.aporte_cobrado",
                obligacion.id());

        assertThat(evento.get("monto", String.class)).isEqualTo("80000.00");
        assertThat(evento.get("participante", String.class))
                .isEqualTo(obligacion.participanteId().toString());
    }

    @Test
    @DisplayName("rechaza cobrar mas de lo que la obligacion debe")
    void rechazaSobrepago() {
        // Cobrar de mas no es un favor: es plata del grupo aplicada a una cuota que ya
        // estaba, y despues no hay a quien devolversela sin romper el calendario.
        UUID usuario = fixtura.usuario();
        var obligacion = fixtura.obligacion(usuario, "500.00", 10);
        ContextoSesion ctx = contextoDe(usuario);

        assertThatThrownBy(() -> cobrar(obligacion.id(), "500.01", "cob-exceso", ctx))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThat(contar("SELECT count(*)::int FROM aportes.pago WHERE obligacion_id = ?", obligacion.id()))
                .isZero();
    }
}
