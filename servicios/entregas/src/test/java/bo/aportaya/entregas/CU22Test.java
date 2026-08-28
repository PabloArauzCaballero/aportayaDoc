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

/** CU-22 · Liquidar y entregar el fondo. */
class CU22Test extends BaseDeEntregas {

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

    /** Las deducciones del CU: comision 18 y aporte propio del periodo 500. */
    private List<LiquidacionDeEntrega.Deduccion> deducciones() {
        return List.of(
                new LiquidacionDeEntrega.Deduccion(
                        "COMISION_PLATAFORMA", "Comision de la plataforma", bob("18.00"), UUID.randomUUID(), true),
                new LiquidacionDeEntrega.Deduccion(
                        "APORTE_PROPIO_DEL_PERIODO",
                        "Su propio aporte del periodo",
                        bob("500.00"),
                        UUID.randomUUID(),
                        true));
    }

    private EntradaLiquidacion entrada(Caso c, String bruto, String recaudado, List<LiquidacionDeEntrega.Deduccion> d) {
        return new EntradaLiquidacion(
                c.escenario().grupoId(),
                c.escenario().periodoId(),
                c.escenario().turnoId(),
                c.escenario().cupoId(),
                c.escenario().participanteId(),
                bob(bruto),
                bob(recaudado),
                d,
                "BILLETERA_MOVIL",
                LocalDate.now());
    }

    @Test
    @DisplayName(
            "Dada una bolsa bruta de Bs 6.000 y deducciones por Bs 518 · Cuando se liquida la entrega · Entonces monto_neto_a_entregar es 5.482 · Y existe una deduccion_entrega de tipo COMISION_PLATAFORMA con referencia a cargo_comision")
    void criterio1() {
        Caso c = caso();

        SalidaLiquidacion salida =
                transaccion.execute(t -> entregaCU.liquidar(entrada(c, "6000.00", "6000.00", deducciones()), c.ctx()));

        assertThat(salida.neto()).isEqualByComparingTo(bob("5482.00"));
        assertThat(salida.totalDeducciones()).isEqualByComparingTo(bob("518.00"));
        // Cada deduccion con su origen: «le descontamos 518» no se puede explicar;
        // «comision 18, aporte propio 500» si.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM entregas.deduccion_entrega
                         WHERE entrega_id = ? AND tipo = 'COMISION_PLATAFORMA'
                           AND referencia_origen_id IS NOT NULL
                        """,
                        salida.entregaId()))
                .isEqualTo(1);
        // Y la cabecera la recalcula la BASE: los totales no se escriben a mano.
        assertThat(dsl.fetchOne(
                                "SELECT monto_neto_a_entregar FROM entregas.entrega_fondo WHERE id = ?",
                                salida.entregaId())
                        .get(0, java.math.BigDecimal.class))
                .isEqualByComparingTo(new java.math.BigDecimal("5482.00"));
    }

    @Test
    @DisplayName(
            "Dado un turno con entrega ya ejecutada · Cuando se intenta crear otra entrega para el mismo turno · Entonces la base de datos lo rechaza (R-GRP-01)")
    void criterio2() {
        Caso c = caso();
        transaccion.execute(t -> entregaCU.liquidar(entrada(c, "6000.00", "6000.00", deducciones()), c.ctx()));

        // Dos entregas del mismo turno es pagar dos veces el mismo premio, y el grupo
        // se queda sin fondo para el siguiente.
        assertThatThrownBy(() -> transaccion.execute(
                        t -> entregaCU.liquidar(entrada(c, "6000.00", "6000.00", deducciones()), c.ctx())))
                .satisfies(e -> assertThat(raizDe(e)).contains("uq_entrega_turno"));
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.entrega_fondo WHERE turno_id = ?",
                        c.escenario().turnoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una entrega autorizada · Cuando la ejecuta el mismo usuario que la autorizó · Entonces la operación se rechaza por segregación de funciones")
    void criterio3() {
        Caso c = caso();
        SalidaLiquidacion entrega =
                transaccion.execute(t -> entregaCU.liquidar(entrada(c, "6000.00", "6000.00", deducciones()), c.ctx()));
        ContextoSesion supervisor = contextoDe(fixtura.usuario());
        transaccion.execute(t -> entregaCU.autorizar(entrega.entregaId(), supervisor));

        // Una sola persona que autoriza y ejecuta puede sacar el fondo entero de un
        // grupo sin que nadie mas lo vea pasar.
        assertThatThrownBy(() ->
                        transaccion.execute(t -> entregaCU.ejecutar(entrega.entregaId(), bob("5482.00"), supervisor)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede ser quien la autorizo");
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.entrega_fondo WHERE id = ? AND estado = 'AUTORIZADA'",
                        entrega.entregaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La clave es el TURNO, y la sostiene la base. Autorizar dos veces tampoco
        // vuelve a mover el estado.
        Caso c = caso();
        SalidaLiquidacion entrega =
                transaccion.execute(t -> entregaCU.liquidar(entrada(c, "6000.00", "6000.00", deducciones()), c.ctx()));
        ContextoSesion supervisor = contextoDe(fixtura.usuario());

        transaccion.execute(t -> entregaCU.autorizar(entrega.entregaId(), supervisor));

        assertThatThrownBy(() -> transaccion.execute(t -> entregaCU.autorizar(entrega.entregaId(), supervisor)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya no admite autorizacion");
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "entregas.entrega_autorizada",
                        entrega.entregaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos ejecuciones de la misma entrega: la version optimista deja pasar una.
        // Sin eso se pagaria dos veces el mismo turno.
        Caso c = caso();
        SalidaLiquidacion entrega =
                transaccion.execute(t -> entregaCU.liquidar(entrada(c, "6000.00", "6000.00", deducciones()), c.ctx()));
        ContextoSesion supervisor = contextoDe(fixtura.usuario());
        ContextoSesion cajero = contextoDe(fixtura.usuario());
        transaccion.execute(t -> entregaCU.autorizar(entrega.entregaId(), supervisor));

        transaccion.execute(t -> entregaCU.ejecutar(entrega.entregaId(), bob("5482.00"), cajero));

        assertThatThrownBy(
                        () -> transaccion.execute(t -> entregaCU.ejecutar(entrega.entregaId(), bob("5482.00"), cajero)))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no estaba lista");
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.entrega_fondo WHERE id = ? AND estado = 'ENTREGADA'",
                        entrega.entregaId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Bruto menos deducciones igual neto, al centavo, y la suma de las filas de
        // deduccion iguala el total de la cabecera. Si no cuadraran, nadie podria
        // explicar la diferencia.
        Caso c = caso();

        SalidaLiquidacion salida =
                transaccion.execute(t -> entregaCU.liquidar(entrada(c, "6000.00", "6000.00", deducciones()), c.ctx()));

        var fila = dsl.fetchOne(
                """
                SELECT e.monto_bolsa_bruto AS bruto, e.total_deducciones AS deducciones,
                       e.monto_neto_a_entregar AS neto,
                       (SELECT COALESCE(sum(monto), 0) FROM entregas.deduccion_entrega d
                         WHERE d.entrega_id = e.id) AS suma_filas
                  FROM entregas.entrega_fondo e WHERE e.id = ?
                """,
                salida.entregaId());
        assertThat(fila.get("bruto", java.math.BigDecimal.class)
                        .subtract(fila.get("deducciones", java.math.BigDecimal.class)))
                .isEqualByComparingTo(fila.get("neto", java.math.BigDecimal.class));
        assertThat(fila.get("suma_filas", java.math.BigDecimal.class))
                .isEqualByComparingTo(fila.get("deducciones", java.math.BigDecimal.class));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "liquidacion"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "liquidacion"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Deducciones que superan la bolsa: no queda entrega ni deduccion. El
        // beneficiario terminaria debiendo por cobrar su turno, y eso no es una
        // entrega — es un error de calculo que hay que resolver antes.
        Caso c = caso();
        var imposibles = List.of(new LiquidacionDeEntrega.Deduccion(
                "DEUDA_VENCIDA_PROPIA", "Deuda mayor que la bolsa", bob("6000.01"), UUID.randomUUID(), true));

        assertThatThrownBy(() -> transaccion.execute(
                        t -> entregaCU.liquidar(entrada(c, "6000.00", "6000.00", imposibles), c.ctx())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("terminaria debiendo");
        assertThat(contar(
                        "SELECT count(*)::int FROM entregas.entrega_fondo WHERE turno_id = ?",
                        c.escenario().turnoId()))
                .isZero();
        assertThat(contar("SELECT count(*)::int FROM entregas.deduccion_entrega"))
                .isZero();
    }
}
