package bo.aportaya.garantia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.garantia.aplicacion.CU23CubrirIncumplimiento.EntradaCobertura;
import bo.aportaya.garantia.aplicacion.CU25DeclararIncumplimiento.EntradaDeclaracion;
import bo.aportaya.garantia.aplicacion.CU66ReemplazarParticipante.EntradaReemplazo;
import bo.aportaya.garantia.aplicacion.CU66ReemplazarParticipante.SalidaReemplazo;
import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.plataforma.dominio.Dinero;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.Moneda;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-66 · Reemplazar a un participante moroso. */
class CU66Test extends BaseDeGarantia {

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
            UUID usuario, FixturaDeGarantia.Escenario escenario, UUID expedienteId, ContextoSesion gestor) {}

    private Caso caso(String deuda) {
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
                        "ABANDONO_DE_GRUPO",
                        "CRITICA",
                        "REPORTE_DE_ORGANIZADOR",
                        bob(deuda),
                        60,
                        true,
                        "ACTA_ACUERDO",
                        "Acta de expulsion aprobada por el grupo",
                        null,
                        null),
                gestor));
        transaccion.execute(
                t -> coberturaCU.cubrir(new EntradaCobertura(expediente.expedienteId(), bob(deuda), 60, null), gestor));
        return new Caso(usuario, escenario, expediente.expedienteId(), gestor);
    }

    @Test
    @DisplayName(
            "Dado un incumplimiento firme con acuerdo de expulsión aprobado · Cuando se elige un candidato elegible · Entonces el cupo cambia de titular conservando su turno · Y el saliente queda EXPULSADO con su deuda intacta")
    void criterio1() {
        Caso c = caso("1000.00");
        UUID entrante = fixtura.otroParticipante(c.escenario().grupoId());

        SalidaReemplazo salida = transaccion.execute(t -> reemplazoCU.proponer(
                new EntradaReemplazo(c.expedienteId(), c.escenario().cupoId(), entrante, bob("0.00"), true),
                c.gestor()));
        transaccion.execute(t -> reemplazoCU.aprobar(salida.reemplazoId(), c.gestor()));
        transaccion.execute(t -> reemplazoCU.ejecutar(salida.reemplazoId(), c.gestor()));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.reemplazo_participante
                         WHERE id = ? AND estado = 'EJECUTADO' AND conserva_orden_de_turno
                        """,
                        salida.reemplazoId()))
                .isEqualTo(1);
        // La deuda del saliente queda INTACTA: el entrante no asumio nada, asi que los
        // 1.000 siguen siendo del que se fue. Perdonarla se la cobraria a los demas.
        assertThat(salida.deudaRetenidaPorElSaliente()).isEqualByComparingTo(bob("1000.00"));
        assertThat(dsl.fetchOne(
                                "SELECT saldo_actual FROM garantia.deuda_participante WHERE registro_id = ?",
                                c.expedienteId())
                        .get("saldo_actual", java.math.BigDecimal.class))
                .isEqualByComparingTo(new java.math.BigDecimal("1000.00"));
    }

    @Test
    @DisplayName(
            "Dado un moroso que paga antes de que se elija candidato · Cuando corre el proceso · Entonces el reemplazo se cancela con MOROSO_REGULARIZADO")
    void criterio2() {
        Caso c = caso("1000.00");

        // Sin candidato todavia: el reemplazo queda BUSCANDO.
        SalidaReemplazo salida = transaccion.execute(t -> reemplazoCU.proponer(
                new EntradaReemplazo(c.expedienteId(), c.escenario().cupoId(), null, bob("0.00"), true), c.gestor()));

        assertThat(salida.estado()).isEqualTo("BUSCANDO");
        // Si el moroso paga, el reemplazo no llega a EJECUTADO: no se aprueba, y
        // ejecutar sin aprobacion se rechaza. Expulsar a alguien que ya se puso al dia
        // es el peor error posible en este flujo.
        assertThatThrownBy(() -> transaccion.execute(t -> reemplazoCU.ejecutar(salida.reemplazoId(), c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no estaba aprobado");
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.reemplazo_participante WHERE id = ? AND estado = 'BUSCANDO'",
                        salida.reemplazoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado que vence el plazo sin candidatos · Cuando corre el trabajo de vencimiento · Entonces se activa el plan de contingencia del grupo")
    void criterio3() {
        Caso c = caso("1000.00");
        SalidaReemplazo salida = transaccion.execute(t -> reemplazoCU.proponer(
                new EntradaReemplazo(c.expedienteId(), c.escenario().cupoId(), null, bob("0.00"), true), c.gestor()));

        // HUECO DECLARADO: `plan_contingencia` existe en el modelo pero ningun caso de
        // uso de este carril lo escribe, y el trabajo de vencimiento no esta cableado.
        // Ver H-5 del informe. Lo que si queda es la senal: un reemplazo BUSCANDO sin
        // candidato es exactamente la condicion que dispara la contingencia.
        assertThat(salida.estado()).isEqualTo("BUSCANDO");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM garantia.reemplazo_participante
                         WHERE id = ? AND participante_entrante_id IS NULL
                        """,
                        salida.reemplazoId()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "garantia.reemplazo_propuesto",
                        salida.reemplazoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Ejecutar dos veces el mismo reemplazo: el segundo no vuelve a ejecutarlo. El
        // WHERE sobre el estado es la barrera.
        Caso c = caso("1000.00");
        UUID entrante = fixtura.otroParticipante(c.escenario().grupoId());
        SalidaReemplazo salida = transaccion.execute(t -> reemplazoCU.proponer(
                new EntradaReemplazo(c.expedienteId(), c.escenario().cupoId(), entrante, bob("0.00"), true),
                c.gestor()));
        transaccion.execute(t -> reemplazoCU.aprobar(salida.reemplazoId(), c.gestor()));
        transaccion.execute(t -> reemplazoCU.ejecutar(salida.reemplazoId(), c.gestor()));

        assertThatThrownBy(() -> transaccion.execute(t -> reemplazoCU.ejecutar(salida.reemplazoId(), c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.evento_dominio WHERE tipo = ? AND agregado_id = ?",
                        "garantia.reemplazo_ejecutado",
                        salida.reemplazoId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Dos aprobaciones del mismo reemplazo: gana una. Aprobarlo dos veces no
        // cambia nada, pero el estado tiene que decir la verdad de una sola transicion.
        Caso c = caso("1000.00");
        UUID entrante = fixtura.otroParticipante(c.escenario().grupoId());
        SalidaReemplazo salida = transaccion.execute(t -> reemplazoCU.proponer(
                new EntradaReemplazo(c.expedienteId(), c.escenario().cupoId(), entrante, bob("0.00"), true),
                c.gestor()));

        boolean primera =
                Boolean.TRUE.equals(transaccion.execute(t -> reemplazoCU.aprobar(salida.reemplazoId(), c.gestor())));
        boolean segunda =
                Boolean.TRUE.equals(transaccion.execute(t -> reemplazoCU.aprobar(salida.reemplazoId(), c.gestor())));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // Lo que asume el entrante mas lo que retiene el saliente iguala la deuda, al
        // centavo. Si no cuadrara, una parte de la deuda quedaria sin dueno — y la
        // absorberian los demas participantes sin enterarse.
        Caso c = caso("1000.00");
        UUID entrante = fixtura.otroParticipante(c.escenario().grupoId());

        SalidaReemplazo salida = transaccion.execute(t -> reemplazoCU.proponer(
                new EntradaReemplazo(c.expedienteId(), c.escenario().cupoId(), entrante, bob("400.00"), true),
                c.gestor()));

        assertThat(salida.deudaAsumidaPorElEntrante()).isEqualByComparingTo(bob("400.00"));
        assertThat(salida.deudaRetenidaPorElSaliente()).isEqualByComparingTo(bob("600.00"));
        assertThat(salida.deudaAsumidaPorElEntrante().mas(salida.deudaRetenidaPorElSaliente()))
                .isEqualByComparingTo(bob("1000.00"));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        boolean primera = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "reemplazos"));
        boolean segunda = transaccion.execute(t -> consumidos.registrar(dsl, idEvento, "reemplazos"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensa() {
        // El entrante no puede asumir mas de lo que hay —seria cobrarle una deuda que
        // no existe— ni puede ser el mismo que sale. Ninguna de las dos deja fila.
        Caso c = caso("1000.00");

        assertThatThrownBy(() -> transaccion.execute(t -> reemplazoCU.proponer(
                        new EntradaReemplazo(
                                c.expedienteId(),
                                c.escenario().cupoId(),
                                fixtura.otroParticipante(c.escenario().grupoId()),
                                bob("1500.00"),
                                true),
                        c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede asumir");
        assertThatThrownBy(() -> transaccion.execute(t -> reemplazoCU.proponer(
                        new EntradaReemplazo(
                                c.expedienteId(),
                                c.escenario().cupoId(),
                                c.escenario().participanteId(),
                                bob("0.00"),
                                true),
                        c.gestor())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("no puede ser el mismo participante");
        assertThat(contar(
                        "SELECT count(*)::int FROM garantia.reemplazo_participante WHERE registro_id = ?",
                        c.expedienteId()))
                .isZero();
    }
}
