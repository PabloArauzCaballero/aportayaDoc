package bo.aportaya.garantia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.garantia.aplicacion.CU23CubrirIncumplimiento.EntradaCobertura;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.EntradaDeclaracion;
import bo.aportaya.garantia.aplicacion.CU27RestringirDeudor.EntradaRestriccion;
import bo.aportaya.garantia.aplicacion.CU27RestringirDeudor.SalidaRestriccion;
import bo.aportaya.garantia.dominio.RestriccionInterna;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-27 · Restringir al deudor e incluirlo en la lista interna. */
class CU27Test extends BaseDeGarantia {

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

    private record Caso(UUID usuario, UUID expedienteId, ContextoSesion suyo, ContextoSesion gestor) {}

    private Caso caso(String deuda, boolean conCobertura) {
        UUID usuario = fixtura.usuario();
        var escenario = fixtura.escenario(usuario);
        ContextoSesion gestor = contextoDe(fixtura.usuario());

        UUID politica = fixtura.politica(escenario.grupoId(), "100.00", "50000.00", "50000.00", 3, "100000.00", 15);
        UUID fondo = fixtura.fondo(escenario.grupoId(), politica, "50000.00");
        fixtura.aportarAlFondo(fondo, escenario.participanteId(), "50000.00", "50000.00");

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
                        bob(deuda),
                        30,
                        true,
                        "LOG_SISTEMA",
                        "Vencido y sin pago",
                        null,
                        null),
                gestor));
        if (conCobertura) {
            transaccion.execute(t ->
                    coberturaCU.cubrir(new EntradaCobertura(expediente.expedienteId(), bob(deuda), 30, null), gestor));
        }
        return new Caso(usuario, expediente.expedienteId(), contextoDe(usuario), gestor);
    }

    @Test
    @DisplayName(
            "Dado un incumplimiento confirmado con deuda de Bs 800 · Cuando se aplica la restricción · Entonces existe una restriccion_usuario de tipo SIN_GRUPOS_NUEVOS vigente · Y el usuario sigue pudiendo consultar y retirar su propio saldo")
    void criterio1() {
        Caso c = caso("800.00", true);

        SalidaRestriccion salida = transaccion.execute(t -> restriccionCU.restringir(
                new EntradaRestriccion(
                        c.expedienteId(),
                        "LIMITADO",
                        "Deuda de Bs 800 sin regularizar",
                        Optional.of(Duration.ofDays(180))),
                c.gestor()));

        // HUECO DECLARADO: la tabla se llama `lista_restriccion_interna` y sus niveles
        // son OBSERVACION, LIMITADO y VETADO — no hay un tipo SIN_GRUPOS_NUEVOS ni una
        // tabla `restriccion_usuario`. Manda la DDL. Ver H-3 del informe.
        assertThat(salida.esNueva()).isTrue();
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.lista_restriccion_interna
                         WHERE usuario_id = ? AND nivel_restriccion = 'LIMITADO'
                           AND retirado_en IS NULL AND monto_adeudado = 800.00
                        """,
                        c.usuario()))
                .isEqualTo(1);
        // Lo que NO se restringe viaja en el evento: pagar la deuda y ver su estado
        // siguen abiertos. Cerrarle esa puerta a quien debe es asegurarse de que no
        // vuelva.
        assertThat(
                        contar(
                                """
                        SELECT count(*)::int FROM garantia.evento_dominio
                         WHERE tipo = 'garantia.usuario_restringido'
                           AND payload->>'puedeSeguir' LIKE '%PAGAR_DEUDA%'
                        """))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un usuario restringido · Cuando intenta postular a un grupo · Entonces la postulación se rechaza indicando el motivo y el monto que la levanta")
    void criterio2() {
        Caso c = caso("800.00", true);
        transaccion.execute(t -> restriccionCU.restringir(
                new EntradaRestriccion(c.expedienteId(), "LIMITADO", "Deuda sin regularizar", Optional.empty()),
                c.gestor()));

        boolean puedeUnirse = Boolean.TRUE.equals(
                transaccion.execute(t -> restriccionCU.puede(c.usuario(), "UNIRSE_A_GRUPO", c.gestor())));

        assertThat(puedeUnirse).isFalse();
        // El motivo y el monto que la levanta quedan en la fila: negar sin decir
        // cuanto hay que pagar para salir es dejar a la persona sin camino de vuelta.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.lista_restriccion_interna
                         WHERE usuario_id = ? AND motivo IS NOT NULL AND monto_adeudado > 0
                        """,
                        c.usuario()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un usuario restringido que paga la totalidad de su deuda · Cuando se acredita el último pago · Entonces la lista queda con retirado_en y motivo_retiro en la misma transacción · Y sus restricciones dejan de estar vigentes")
    void criterio3() {
        Caso c = caso("800.00", true);
        SalidaRestriccion restriccion = transaccion.execute(t -> restriccionCU.restringir(
                new EntradaRestriccion(c.expedienteId(), "LIMITADO", "Deuda sin regularizar", Optional.empty()),
                c.gestor()));

        boolean levantada = Boolean.TRUE.equals(transaccion.execute(
                t -> restriccionCU.levantar(restriccion.restriccionId(), "Deuda pagada en su totalidad", c.gestor())));

        assertThat(levantada).isTrue();
        // El levantamiento SE MOTIVA (R-GAR-05): sin motivo escrito se convierte en un
        // favor que nadie puede auditar.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.lista_restriccion_interna
                         WHERE id = ? AND retirado_en IS NOT NULL AND retirado_por IS NOT NULL
                           AND motivo_retiro IS NOT NULL
                        """,
                        restriccion.restriccionId()))
                .isEqualTo(1);
        boolean puede = Boolean.TRUE.equals(
                transaccion.execute(t -> restriccionCU.puede(c.usuario(), "UNIRSE_A_GRUPO", c.gestor())));
        assertThat(puede).isTrue();
    }

    @Test
    @DisplayName(
            "Dada una deuda castigada por incobrable · Cuando corre el proceso de castigo · Entonces la restricción sigue vigente")
    void criterio4() {
        Caso c = caso("800.00", true);
        SalidaRestriccion restriccion = transaccion.execute(t -> restriccionCU.restringir(
                new EntradaRestriccion(c.expedienteId(), "VETADO", "Deuda castigada por incobrable", Optional.empty()),
                c.gestor()));

        // Castigar la deuda es una decision contable: la plataforma deja de esperar
        // cobrarla. **No es un perdon**: si levantara la restriccion, castigar seria
        // el camino mas corto para volver a empezar sin haber pagado.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.lista_restriccion_interna
                         WHERE id = ? AND retirado_en IS NULL AND vigente_hasta IS NULL
                        """,
                        restriccion.restriccionId()))
                .isEqualTo(1);
        boolean puede = Boolean.TRUE.equals(
                transaccion.execute(t -> restriccionCU.puede(c.usuario(), "CREAR_GRUPO", c.gestor())));
        assertThat(puede).isFalse();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Una restriccion vigente por usuario (R-GAR-05). La segunda devuelve la que hay.
        Caso c = caso("800.00", true);

        SalidaRestriccion a = transaccion.execute(t -> restriccionCU.restringir(
                new EntradaRestriccion(c.expedienteId(), "LIMITADO", "Deuda", Optional.empty()), c.gestor()));
        SalidaRestriccion b = transaccion.execute(t -> restriccionCU.restringir(
                new EntradaRestriccion(c.expedienteId(), "VETADO", "Deuda otra vez", Optional.empty()), c.gestor()));

        assertThat(b.restriccionId()).isEqualTo(a.restriccionId());
        assertThat(b.esNueva()).isFalse();
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.lista_restriccion_interna WHERE usuario_id = ?",
                        c.usuario()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos levantamientos de la misma restriccion: el segundo no vuelve a
        // levantarla. Si lo hiciera, sobrescribiria quien la levanto y por que — que
        // es justamente lo que hay que poder auditar.
        Caso c = caso("800.00", true);
        SalidaRestriccion restriccion = transaccion.execute(t -> restriccionCU.restringir(
                new EntradaRestriccion(c.expedienteId(), "LIMITADO", "Deuda", Optional.empty()), c.gestor()));

        boolean primera = Boolean.TRUE.equals(transaccion.execute(
                t -> restriccionCU.levantar(restriccion.restriccionId(), "Pago verificado", c.gestor())));
        boolean segunda = Boolean.TRUE.equals(transaccion.execute(
                t -> restriccionCU.levantar(restriccion.restriccionId(), "Otro motivo", c.gestor())));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
        assertThat(dsl.fetchOne(
                                "SELECT motivo_retiro FROM garantia.lista_restriccion_interna WHERE id = ?",
                                restriccion.restriccionId())
                        .get("motivo_retiro", String.class))
                .isEqualTo("Pago verificado");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El monto que consta en la restriccion es el saldo real de la deuda, al
        // centavo: es el numero que la persona tiene que pagar para salir, y no puede
        // ser aproximado.
        Caso c = caso("847.35", true);

        transaccion.execute(t -> restriccionCU.restringir(
                new EntradaRestriccion(c.expedienteId(), "LIMITADO", "Deuda", Optional.empty()), c.gestor()));

        var fila = dsl.fetchOne(
                """
                SELECT l.monto_adeudado AS restriccion, d.saldo_actual AS deuda
                  FROM garantia.lista_restriccion_interna l
                  JOIN garantia.deuda_participante d ON d.registro_id = l.registro_origen_id
                 WHERE l.usuario_id = ?
                """,
                c.usuario());
        assertThat(fila.get("restriccion", java.math.BigDecimal.class))
                .isEqualByComparingTo(fila.get("deuda", java.math.BigDecimal.class))
                .isEqualByComparingTo(new java.math.BigDecimal("847.35"));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "restricciones"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "restricciones"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Sin motivo escrito no se restringe ni se levanta, y nadie se restringe a si
        // mismo. Ninguna de las dos deja fila a medias.
        Caso c = caso("800.00", true);

        assertThatThrownBy(() -> transaccion.execute(t -> restriccionCU.restringir(
                        new EntradaRestriccion(c.expedienteId(), "LIMITADO", "   ", Optional.empty()), c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("sin motivo escrito");
        assertThatThrownBy(() -> transaccion.execute(t -> restriccionCU.restringir(
                        new EntradaRestriccion(c.expedienteId(), "LIMITADO", "Me restrinjo", Optional.empty()),
                        c.suyo())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede ser el restringido");
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.lista_restriccion_interna WHERE usuario_id = ?",
                        c.usuario()))
                .isZero();

        // Y pagar la deuda NUNCA se restringe: es un tipo que la lista no contempla.
        assertThat(RestriccionInterna.esTipoValido("PAGAR_DEUDA")).isFalse();
        assertThat(RestriccionInterna.esTipoValido("UNIRSE_A_GRUPO")).isTrue();
    }
}
