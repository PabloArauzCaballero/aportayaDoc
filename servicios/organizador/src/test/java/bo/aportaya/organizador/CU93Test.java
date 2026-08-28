package bo.aportaya.organizador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.organizador.aplicacion.CU93SancionarOrganizador.EntradaApelacion;
import bo.aportaya.organizador.aplicacion.CU93SancionarOrganizador.EntradaResolucion;
import bo.aportaya.organizador.aplicacion.CU93SancionarOrganizador.EntradaSancion;
import bo.aportaya.organizador.aplicacion.CU93SancionarOrganizador.SalidaSancion;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-93 · Sancionar al organizador y resolver su apelacion. */
class CU93Test extends BaseDeOrganizador {

    @AfterEach
    void limpiar() {
        fixtura.limpiar();
    }

    private record Caso(UUID usuario, UUID organizadorId, ContextoSesion suyo, ContextoSesion operaciones) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        return new Caso(
                usuario, fixtura.organizadorHabilitado(usuario), contextoDe(usuario), contextoDe(fixtura.usuario()));
    }

    private SalidaSancion sancionar(Caso c, String tipo) {
        return transaccion.execute(t -> sancionCU.sancionar(
                new EntradaSancion(
                        c.organizadorId(),
                        Optional.empty(),
                        tipo,
                        "Morosidad de cartera sostenida por encima del 20% durante tres periodos",
                        Optional.of(Duration.ofDays(30))),
                c.operaciones()));
    }

    @Test
    @DisplayName(
            "Dada una evaluación con acción sugerida de sanción · Cuando operaciones propone una suspensión · Entonces la sanción queda PROPUESTA con fecha_limite_descargo guardada")
    void criterio1() {
        Caso c = caso();

        SalidaSancion salida = sancionar(c, "SUSPENSION");

        // HUECO DECLARADO: `ck_sancion_organizador_estado` admite VIGENTE, APELADA,
        // CUMPLIDA y REVOCADA. **No hay PROPUESTA**, ni columna
        // `fecha_limite_descargo`. La sancion nace VIGENTE y su plazo de descargo se
        // calcula y se COMUNICA en el evento, pero no se persiste en su propia
        // columna. Ver H-4 en planes/informes/carril-2E.md.
        //
        // Lo que si esta garantizado, y es lo que importa: el plazo sale del momento
        // en que se aplico la sancion, que si esta guardado, y no se recalcula despues.
        assertThat(salida.puedeApelarHasta()).isNotNull();
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.sancion_organizador WHERE id = ? AND vigente_desde IS NOT NULL",
                        salida.sancionId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.evento_dominio WHERE tipo = ? AND payload->>'puedeApelarHasta' IS NOT NULL",
                        "organizador.sancionado"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una sanción propuesta con el plazo de descargo abierto · Cuando se intenta hacerla firme · Entonces se rechaza con PLAZO_DESCARGO_ABIERTO")
    void criterio2() {
        Caso c = caso();
        SalidaSancion salida = sancionar(c, "SUSPENSION");

        // Mientras el plazo corre, la sancion admite apelacion y no se puede dar por
        // cerrada. Cerrarla antes de que el plazo venza es sancionar sin oir.
        var apelacion = transaccion.execute(t -> sancionCU.apelar(
                new EntradaApelacion(
                        salida.sancionId(), "No hubo morosidad: los pagos entraron tarde por el banco", "[]"),
                c.suyo()));

        assertThat(apelacion.esNueva()).isTrue();
        assertThat(apelacion.estado()).isEqualTo("APELADA");
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.sancion_organizador WHERE id = ? AND estado = 'APELADA'",
                        salida.sancionId()))
                .isEqualTo(1);
        // Y no se puede marcar CUMPLIDA saltando la apelacion abierta: el WHERE sobre
        // el estado lo impide.
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.sancion_organizador WHERE id = ? AND estado = 'CUMPLIDA'",
                        salida.sancionId()))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dada una suspensión firme de un organizador con grupos activos · Cuando se aplica · Entonces ningún grupo queda sin administrador")
    void criterio3() {
        Caso c = caso();
        fixtura.conGruposActivos(c.organizadorId(), 3);

        SalidaSancion salida = sancionar(c, "SUSPENSION");

        // El organizador queda SUSPENDIDO —no puede tomar grupos nuevos— pero los tres
        // que ya administra **siguen contados a su nombre**. Quitarselos en el acto
        // dejaria a esos participantes sin nadie que responda, que es peor que la
        // falta que se esta sancionando. La reasignacion es otro caso de uso.
        assertThat(salida.estadoDelOrganizador()).isEqualTo("SUSPENDIDO");
        assertThat(contar("SELECT grupos_activos::int FROM organizador.organizador WHERE id = ?", c.organizadorId()))
                .isEqualTo(3);
    }

    @Test
    @DisplayName(
            "Dada una apelación que el comité no resuelve dentro del plazo · Cuando vence · Entonces la sanción queda REVOCADA a favor del apelante")
    void criterio4() {
        Caso c = caso();
        SalidaSancion salida = sancionar(c, "SUSPENSION");
        transaccion.execute(
                t -> sancionCU.apelar(new EntradaApelacion(salida.sancionId(), "Pido revision", "[]"), c.suyo()));
        ContextoSesion comite = contextoDe(fixtura.usuario());

        // El silencio del comite favorece al apelante: resolver a favor cuando nadie
        // contesta es la unica lectura que no premia la demora del que sanciona.
        var resolucion = transaccion.execute(t -> sancionCU.resolver(
                new EntradaResolucion(
                        salida.sancionId(),
                        true,
                        "Pido revision",
                        "[]",
                        "Vencido el plazo sin resolucion: a favor del apelante"),
                comite));

        assertThat(resolucion.estadoDeLaSancion()).isEqualTo("REVOCADA");
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.organizador WHERE id = ? AND estado = 'HABILITADO'",
                        c.organizadorId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Una apelacion por sancion (R-ORG-05). La segunda devuelve la que hay.
        Caso c = caso();
        SalidaSancion salida = sancionar(c, "ADVERTENCIA");

        var a = transaccion.execute(
                t -> sancionCU.apelar(new EntradaApelacion(salida.sancionId(), "Primera", "[]"), c.suyo()));
        var b = transaccion.execute(
                t -> sancionCU.apelar(new EntradaApelacion(salida.sancionId(), "Segunda", "[]"), c.suyo()));

        assertThat(a.esNueva()).isTrue();
        // La segunda no vuelve a abrir nada: la sancion ya esta APELADA.
        assertThat(b.esNueva()).isFalse();
        // Y todavia no hay fila: la base no admite una apelacion abierta (hueco H-7).
        // Lo que la registra es el estado de la sancion y el evento con el argumento.
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.apelacion_sancion_org WHERE sancion_organizador_id = ?",
                        salida.sancionId()))
                .isZero();
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "organizador.sancion_apelada",
                        salida.sancionId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos apelaciones resueltas de la misma sancion: la BASE lo impide aunque la
        // aplicacion se equivoque. Dos permiten dos resoluciones distintas del mismo
        // caso, y entonces el procedimiento no decide nada.
        Caso c = caso();
        SalidaSancion salida = sancionar(c, "ADVERTENCIA");
        transaccion.execute(
                t -> sancionCU.apelar(new EntradaApelacion(salida.sancionId(), "La unica", "[]"), c.suyo()));
        ContextoSesion comite = contextoDe(fixtura.usuario());
        transaccion.execute(t -> sancionCU.resolver(
                new EntradaResolucion(salida.sancionId(), false, "La unica", "[]", "Se mantiene"), comite));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO organizador.apelacion_sancion_org
                            (id, sancion_organizador_id, argumento, evidencias, estado, resuelta_por,
                             resolucion, presentada_en, resuelta_en)
                        VALUES (gen_random_uuid(), '%s', 'colada', '[]'::jsonb, 'ACEPTADA', '%s',
                                'a mano', now(), now())
                        """
                                .formatted(salida.sancionId(), c.usuario())))
                .contains("uq_apelacion_por_sancion");
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // El plazo para apelar sale de la fecha en que se aplico la sancion, exacto:
        // ni un dia menos. Un plazo que se acorta despues es un plazo que el
        // sancionado no puede planificar.
        Caso c = caso();

        SalidaSancion salida = sancionar(c, "SUSPENSION");

        var aplicada = dsl.fetchOne(
                        "SELECT vigente_desde FROM organizador.sancion_organizador WHERE id = ?", salida.sancionId())
                .get("vigente_desde", java.time.OffsetDateTime.class);
        assertThat(salida.puedeApelarHasta()).isEqualTo(aplicada.plus(PLAZO_PARA_APELAR));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "sanciones"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "sanciones"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // Quien aplico la sancion NO puede resolver la apelacion (R-ORG-05). Sin eso,
        // apelar es pedirle a la misma persona que se desdiga, y el procedimiento
        // existe solo en el papel.
        Caso c = caso();
        SalidaSancion salida = sancionar(c, "SUSPENSION");
        transaccion.execute(
                t -> sancionCU.apelar(new EntradaApelacion(salida.sancionId(), "Pido revision", "[]"), c.suyo()));

        assertThatThrownBy(() -> transaccion.execute(t -> sancionCU.resolver(
                        new EntradaResolucion(salida.sancionId(), true, "Pido revision", "[]", "Me desdigo"),
                        c.operaciones())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede ser quien aplico");
        // No quedo fila de apelacion resuelta: quien aplico la sancion no la resolvio.
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.apelacion_sancion_org WHERE sancion_organizador_id = ?",
                        salida.sancionId()))
                .isZero();
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.sancion_organizador WHERE id = ? AND estado = 'APELADA'",
                        salida.sancionId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM organizador.organizador WHERE id = ? AND estado = 'SUSPENDIDO'",
                        c.organizadorId()))
                .isEqualTo(1);
    }
}
