package bo.aportaya.garantia;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.garantia.aplicacion.CU23CubrirIncumplimiento.EntradaCobertura;
import bo.aportaya.garantia.aplicacion.CU23CubrirIncumplimiento.SalidaCobertura;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.EntradaDeclaracion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.Moneda;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-23 · Cubrir un incumplimiento con el fondo. */
class CU23Test extends BaseDeGarantia {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private Dinero bob(String monto) {
        return Dinero.de(monto, Moneda.BOB);
    }

    private String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Caso(
            UUID usuario,
            FixturaDeGarantia.Escenario escenario,
            UUID fondoId,
            UUID expedienteId,
            ContextoSesion gestor) {}

    /** Un expediente notificado y un fondo con la politica que la prueba necesita. */
    private Caso caso(
            String saldoDelFondo,
            String porcentajeMaximo,
            String topeParticipante,
            String topePeriodo,
            int maxCoberturas,
            String desdeAprobacionManual,
            int diasMoraParaActivar) {

        UUID usuario = fixtura.usuario();
        var escenario = fixtura.escenario(usuario);
        ContextoSesion gestor = contextoDe(fixtura.usuario());

        UUID politica = fixtura.politica(
                escenario.grupoId(),
                porcentajeMaximo,
                topeParticipante,
                topePeriodo,
                maxCoberturas,
                desdeAprobacionManual,
                diasMoraParaActivar);
        UUID fondo = fixtura.fondo(escenario.grupoId(), politica, saldoDelFondo);
        fixtura.aportarAlFondo(fondo, escenario.participanteId(), saldoDelFondo, saldoDelFondo);

        var expediente = transaccion.execute(t -> expedienteCU.declarar(
                new EntradaDeclaracion(
                        "EXP-" + corto(),
                        usuario,
                        escenario.participanteId(),
                        escenario.grupoId(),
                        escenario.periodoId(),
                        escenario.cupoId(),
                        escenario.obligacionId(),
                        "APORTE_IMPAGO",
                        "GRAVE",
                        "AUTOMATICO_VENCIMIENTO",
                        bob("500.00"),
                        30,
                        true,
                        "LOG_SISTEMA",
                        "Vencido y sin pago",
                        null,
                        null),
                gestor));

        return new Caso(usuario, escenario, fondo, expediente.expedienteId(), gestor);
    }

    @Test
    @DisplayName(
            "Dada una obligación vencida de Bs 500 y fondo con saldo suficiente · Cuando se ejecuta la cobertura · Entonces existe cobertura_incumplimiento por 500 · Y existe deuda_participante por 500 con subrogación al fondo · Y la cuenta del grupo aumentó 500")
    void criterio1() {
        Caso c = caso("5000.00", "100.00", "5000.00", "5000.00", 3, "100000.00", 15);

        SalidaCobertura salida = transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(c.expedienteId(), bob("500.00"), 30, null), c.gestor()));

        assertThat(salida.montoCubierto()).isEqualByComparingTo(bob("500.00"));
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.cobertura_incumplimiento WHERE registro_id = ? AND monto_cubierto = 500.00",
                        c.expedienteId()))
                .isEqualTo(1);
        // Cubrir NO perdona: queda la deuda contra quien incumplio. Si la cobertura
        // borrara la obligacion, el fondo seria un seguro gratuito pagado por los que
        // si pagan.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.deuda_participante
                         WHERE registro_id = ? AND capital_original = 500.00 AND acreedor = 'FONDO_GARANTIA'
                        """,
                        c.expedienteId()))
                .isEqualTo(1);
        // El movimiento del fondo lo escribe este servicio; el saldo de la cuenta del
        // grupo lo mueve nucleo-financiero al consumir el evento (invariante 12).
        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM garantia.evento_dominio
                         WHERE tipo = 'garantia.incumplimiento_cubierto' AND payload->>'montoCubierto' = '500.00'
                        """))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un fondo sin saldo suficiente · Cuando se evalúa la cobertura · Entonces la entrega queda BLOQUEADA_POR_FONDO_INCOMPLETO")
    void criterio2() {
        // El fondo tiene 120 y se piden 500: el saldo es el limite que manda.
        Caso c = caso("120.00", "100.00", "5000.00", "5000.00", 3, "100000.00", 15);

        SalidaCobertura salida = transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(c.expedienteId(), bob("500.00"), 30, null), c.gestor()));

        // El fondo NO se descubre: cubrir mas de lo que tiene lo dejaria en negativo, y
        // el siguiente incumplimiento no encontraria nada.
        assertThat(salida.montoCubierto()).isEqualByComparingTo(bob("120.00"));
        assertThat(salida.limiteQueMando()).isEqualTo("SALDO_DEL_FONDO");
        // El estado BLOQUEADA_POR_FONDO_INCOMPLETO es de `entrega_fondo`, en otro
        // esquema: este servicio publica el evento con lo que si pudo cubrir, y
        // entregas decide (invariante 11).
        assertThat(
                        contar(
                                "SELECT count(*)::int FROM garantia.evento_dominio WHERE payload->>'limiteQueMando' = 'SALDO_DEL_FONDO'"))
                .isEqualTo(1);
        assertThat(contar("SELECT saldo_disponible::int FROM garantia.fondo_garantia WHERE id = ?", c.fondoId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un deudor que cobra su turno · Cuando se liquida su entrega · Entonces existe una deducción REPOSICION_FONDO_GARANTIA por el saldo de su deuda")
    void criterio3() {
        Caso c = caso("5000.00", "100.00", "5000.00", "5000.00", 3, "100000.00", 15);
        SalidaCobertura salida = transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(c.expedienteId(), bob("500.00"), 30, null), c.gestor()));

        // La deduccion la aplica `entregas` al liquidar (invariante 11). Lo que este
        // servicio aporta es la deuda con su saldo: es contra ella que se descuenta.
        // Sin eso, el deudor cobraria su turno completo mientras el fondo sigue en
        // rojo por su culpa.
        var deuda = dsl.fetchOne(
                "SELECT saldo_actual, estado FROM garantia.deuda_participante WHERE registro_id = ?", c.expedienteId());
        assertThat(deuda.get("saldo_actual", java.math.BigDecimal.class))
                .isEqualByComparingTo(new java.math.BigDecimal("500.00"));
        assertThat(deuda.get("estado", String.class)).isEqualTo("VIGENTE");
        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM garantia.evento_dominio
                         WHERE tipo = 'garantia.incumplimiento_cubierto' AND payload->>'deudaId' IS NOT NULL
                        """))
                .isEqualTo(1);
        assertThat(salida.deudaId()).isNotNull();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // La clave es el expediente: cubrir dos veces el mismo incumplimiento vacia el
        // fondo por un solo caso.
        Caso c = caso("5000.00", "100.00", "5000.00", "5000.00", 3, "100000.00", 15);

        SalidaCobertura a = transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(c.expedienteId(), bob("500.00"), 30, null), c.gestor()));
        SalidaCobertura b = transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(c.expedienteId(), bob("500.00"), 30, null), c.gestor()));

        assertThat(b.coberturaId()).isEqualTo(a.coberturaId());
        assertThat(b.esNueva()).isFalse();
        assertThat(contar("SELECT saldo_disponible::int FROM garantia.fondo_garantia WHERE id = ?", c.fondoId()))
                .isEqualTo(4500);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // La BASE lo sostiene: una cobertura por expediente, aunque la aplicacion se
        // equivoque.
        Caso c = caso("5000.00", "100.00", "5000.00", "5000.00", 3, "100000.00", 15);
        transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(c.expedienteId(), bob("500.00"), 30, null), c.gestor()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO garantia.cobertura_incumplimiento
                            (id, fondo_id, registro_id, obligacion_id, periodo_id, monto_solicitado,
                             monto_cubierto, porcentaje_cobertura, estado, requirio_aprobacion_manual,
                             solicitada_en)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', '%s', 500.00, 500.00, 100.00,
                                'APLICADA', false, now())
                        """
                                .formatted(
                                        c.fondoId(),
                                        c.expedienteId(),
                                        c.escenario().obligacionId(),
                                        c.escenario().periodoId())))
                .contains("uq_cobertura_incumplimiento_");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El saldo del fondo despues de cubrir es exactamente el de antes menos lo
        // cubierto, y el movimiento guarda ese saldo resultante. Es lo que permite
        // reconstruir el fondo en cualquier fecha sin recalcular desde el principio.
        Caso c = caso("5000.00", "60.00", "5000.00", "5000.00", 3, "100000.00", 15);

        SalidaCobertura salida = transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(c.expedienteId(), bob("500.00"), 30, null), c.gestor()));

        assertThat(salida.montoCubierto()).isEqualByComparingTo(bob("300.00"));
        var fila = dsl.fetchOne(
                """
                SELECT f.saldo_disponible AS saldo, m.saldo_resultante AS resultante, m.monto AS movido
                  FROM garantia.fondo_garantia f
                  JOIN garantia.movimiento_fondo m ON m.fondo_id = f.id AND m.tipo = 'COBERTURA_APLICADA'
                 WHERE f.id = ?
                """,
                c.fondoId());
        assertThat(fila.get("saldo", java.math.BigDecimal.class))
                .isEqualByComparingTo(fila.get("resultante", java.math.BigDecimal.class))
                .isEqualByComparingTo(new java.math.BigDecimal("4700.00"));
        assertThat(fila.get("movido", java.math.BigDecimal.class))
                .isEqualByComparingTo(new java.math.BigDecimal("300.00"));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "coberturas"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "coberturas"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Antes del plazo de mora el fondo no se toca: si cubriera el primer dia de
        // atraso, dejaria de ser una garantia y pasaria a ser un adelanto automatico
        // — y nadie volveria a pagar a tiempo.
        Caso c = caso("5000.00", "100.00", "5000.00", "5000.00", 3, "100000.00", 15);

        SalidaCobertura salida = transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(c.expedienteId(), bob("500.00"), 3, null), c.gestor()));

        assertThat(salida.montoCubierto()).isEqualByComparingTo(bob("0.00"));
        assertThat(salida.limiteQueMando()).isEqualTo("DIAS_DE_MORA_INSUFICIENTES");
        // El rechazo queda registrado con su motivo: sin fila, el grupo no sabe por que
        // no se cubrio y no tiene a quien reclamarle.
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.cobertura_incumplimiento WHERE registro_id = ? AND estado = 'RECHAZADA'",
                        c.expedienteId()))
                .isEqualTo(1);
        assertThat(contar("SELECT saldo_disponible::int FROM garantia.fondo_garantia WHERE id = ?", c.fondoId()))
                .isEqualTo(5000);
    }
}
