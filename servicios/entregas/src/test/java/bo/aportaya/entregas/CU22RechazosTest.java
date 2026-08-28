package bo.aportaya.entregas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.entregas.aplicacion.CU22LiquidarEntrega.EntradaLiquidacion;
import bo.aportaya.entregas.aplicacion.CU22LiquidarEntrega.SalidaLiquidacion;
import bo.aportaya.entregas.dominio.LiquidacionDeEntrega;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-22 · las pruebas de RECHAZO, una por restriccion citada. */
class CU22RechazosTest extends BaseDeEntregas {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private record Caso(UUID usuario, FixturaDeEntregas.Escenario escenario, ContextoSesion ctx) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        return new Caso(usuario, fixtura.escenario(usuario), contextoDe(usuario));
    }

    private SalidaLiquidacion liquidar(Caso c, List<LiquidacionDeEntrega.Deduccion> deducciones) {
        return transaccion.execute(t -> entregaCU.liquidar(
                new EntradaLiquidacion(
                        c.escenario().grupoId(),
                        c.escenario().periodoId(),
                        c.escenario().turnoId(),
                        c.escenario().cupoId(),
                        c.escenario().participanteId(),
                        bob("6000.00"),
                        bob("6000.00"),
                        deducciones,
                        "BILLETERA_MOVIL",
                        LocalDate.now()),
                c.ctx()));
    }

    private List<LiquidacionDeEntrega.Deduccion> comision() {
        return List.of(new LiquidacionDeEntrega.Deduccion(
                "COMISION_PLATAFORMA", "Comision", bob("18.00"), UUID.randomUUID(), true));
    }

    @Test
    @DisplayName("rechaza por R-AUD-05")
    void rechazaRAUD05() {
        // El asiento lo escribe nucleo-financiero (invariante 12), y solo puede
        // cuadrarlo con los importes que esta entrega le manda. Un evento sin el neto
        // deja un asiento a medias que nadie cierra.
        Caso c = caso();
        SalidaLiquidacion entrega = liquidar(c, comision());

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM entregas.evento_dominio
                         WHERE tipo = ? AND agregado_id = ? AND payload->>'neto' = ?
                        """,
                        "entregas.entrega_liquidada",
                        entrega.entregaId(),
                        "5982.00"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-BIL-01")
    void rechazaRBIL01() {
        // Bruto menos deducciones igual neto, al centavo, y lo recalcula la BASE. Si
        // se pudiera escribir la cabecera a mano, un neto y sus deducciones podrian
        // dejar de coincidir sin que nada avise.
        Caso c = caso();
        SalidaLiquidacion entrega = liquidar(
                c,
                List.of(
                        new LiquidacionDeEntrega.Deduccion(
                                "COMISION_PLATAFORMA", "Comision", bob("18.33"), UUID.randomUUID(), true),
                        new LiquidacionDeEntrega.Deduccion(
                                "APORTE_PROPIO_DEL_PERIODO", "Aporte propio", bob("500.67"), UUID.randomUUID(), true)));

        var fila = dsl.fetchOne(
                "SELECT monto_bolsa_bruto AS b, total_deducciones AS d, monto_neto_a_entregar AS n FROM entregas.entrega_fondo WHERE id = ?",
                entrega.entregaId());
        assertThat(fila.get("b", java.math.BigDecimal.class).subtract(fila.get("d", java.math.BigDecimal.class)))
                .isEqualByComparingTo(fila.get("n", java.math.BigDecimal.class));
        assertThat(fila.get("d", java.math.BigDecimal.class)).isEqualByComparingTo(new java.math.BigDecimal("519.00"));
    }

    @Test
    @DisplayName("rechaza por R-GRP-01")
    void rechazaRGRP01() {
        // Una entrega por turno. Dos es pagar dos veces el mismo premio, y el grupo se
        // queda sin fondo para el siguiente.
        Caso c = caso();
        liquidar(c, comision());

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO entregas.entrega_fondo
                            (id, grupo_id, periodo_id, turno_id, cupo_id, beneficiario_participante_id,
                             monto_bolsa_bruto, total_deducciones, monto_neto_a_entregar,
                             monto_efectivamente_entregado, moneda, estado, metodo_desembolso,
                             fecha_programada, version)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', '%s', '%s', 6000.00, 0, 6000.00, 0,
                                'BOB', 'PROGRAMADA', 'BILLETERA_MOVIL', current_date, 0)
                        """
                                .formatted(
                                        c.escenario().grupoId(),
                                        c.escenario().periodoId(),
                                        c.escenario().turnoId(),
                                        c.escenario().cupoId(),
                                        c.escenario().participanteId())))
                .contains("uq_entrega_");
    }

    @Test
    @DisplayName("rechaza por R-GRP-02")
    void rechazaRGRP02() {
        // Una entrega por periodo tambien: el calendario del grupo tiene un beneficiario
        // por vez, y dos entregas del mismo periodo lo rompen.
        Caso c = caso();
        liquidar(c, comision());

        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.entrega_fondo WHERE periodo_id = ?",
                        c.escenario().periodoId()))
                .isEqualTo(1);
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO entregas.entrega_fondo
                            (id, grupo_id, periodo_id, turno_id, cupo_id, beneficiario_participante_id,
                             monto_bolsa_bruto, total_deducciones, monto_neto_a_entregar,
                             monto_efectivamente_entregado, moneda, estado, metodo_desembolso,
                             fecha_programada, version)
                        VALUES (gen_random_uuid(), '%s', '%s', gen_random_uuid(), '%s', '%s', 6000.00, 0,
                                6000.00, 0, 'BOB', 'PROGRAMADA', 'BILLETERA_MOVIL', current_date, 0)
                        """
                                .formatted(
                                        c.escenario().grupoId(),
                                        c.escenario().periodoId(),
                                        c.escenario().cupoId(),
                                        c.escenario().participanteId())))
                .contains("uq_entrega_periodo");
    }

    @Test
    @DisplayName("rechaza por R-SEG-04")
    void rechazaRSEG04() {
        // Quien autoriza no ejecuta. Una sola persona que hace las dos cosas puede
        // sacar el fondo entero de un grupo sin que nadie mas lo vea pasar.
        Caso c = caso();
        SalidaLiquidacion entrega = liquidar(c, comision());
        ContextoSesion supervisor = contextoDe(fixtura.usuario());
        transaccion.execute(t -> entregaCU.autorizar(entrega.entregaId(), supervisor));

        assertThatThrownBy(() ->
                        transaccion.execute(t -> entregaCU.ejecutar(entrega.entregaId(), bob("5982.00"), supervisor)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede ser quien la autorizo");
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.entrega_fondo WHERE id = ? AND ejecutada_por IS NULL",
                        entrega.entregaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-TAR-04")
    void rechazaRTAR04() {
        // La deduccion de comision referencia el cargo que la origina. Sin esa
        // referencia no hay forma de atar el descuento al devengo, y un mismo hecho
        // podria descontarse dos veces sin que se note.
        Caso c = caso();
        UUID cargoId = UUID.randomUUID();
        SalidaLiquidacion entrega = liquidar(
                c,
                List.of(new LiquidacionDeEntrega.Deduccion(
                        "COMISION_PLATAFORMA", "Comision", bob("18.00"), cargoId, true)));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM entregas.deduccion_entrega
                         WHERE entrega_id = ? AND tipo = 'COMISION_PLATAFORMA' AND referencia_origen_id = ?
                        """,
                        entrega.entregaId(),
                        cargoId))
                .isEqualTo(1);
        // Y el tipo sale de una lista cerrada: una deduccion inventada no se puede
        // contar, y lo que no se cuenta no aparece en el resultado del mes.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO entregas.deduccion_entrega
                            (id, entrega_id, tipo, descripcion, monto, es_obligatoria, aplicada_en)
                        VALUES (gen_random_uuid(), '%s', 'PROPINA', 'inventada', 1.00, false, now())
                        """
                                .formatted(entrega.entregaId())))
                .contains("ck_deduccion_entrega_tipo");
    }

    @Test
    @DisplayName("rechaza por R-TAR-06")
    void rechazaRTAR06() {
        // Una deduccion de entrega respalda un solo cargo de comision. Aca se verifica
        // la mitad que le toca a este servicio: la deduccion existe con su origen, y
        // es la fila que `tarifas` referencia desde `uq_cargo_deduccion`.
        Caso c = caso();
        UUID cargoId = UUID.randomUUID();
        SalidaLiquidacion entrega = liquidar(
                c,
                List.of(new LiquidacionDeEntrega.Deduccion(
                        "COMISION_PLATAFORMA", "Comision", bob("18.00"), cargoId, true)));
        UUID deduccionId = dsl.fetchOne(
                        "SELECT id FROM entregas.deduccion_entrega WHERE entrega_id = ? AND tipo = 'COMISION_PLATAFORMA'",
                        entrega.entregaId())
                .get("id", UUID.class);

        assertThat(contar("SELECT count(*)::int FROM pg_indexes WHERE indexname = ?", "uq_cargo_deduccion"))
                .isEqualTo(1);
        assertThat(deduccionId).isNotNull();
        // El monto de la deduccion es positivo: una «deduccion» negativa seria un
        // pago encubierto al beneficiario, no un descuento.
        assertThat(rechazaLaBase(
                        "UPDATE entregas.deduccion_entrega SET monto = -1 WHERE id = '%s'".formatted(deduccionId)))
                .contains("ck_deduccion_entrega_monto");
    }
}
