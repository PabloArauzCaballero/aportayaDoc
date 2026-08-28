package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.transparencia.aplicacion.CU70RegistrarEventoReputacion.EntradaEvento;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-70 · Registrar un evento de reputacion. */
class CU70Test extends BaseDeTransparencia {

    private record Caso(UUID usuario, UUID grupo, UUID participante, ContextoSesion ctx) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        UUID grupo = fixtura.grupo();
        UUID participante = fixtura.participante(grupo, usuario);
        return new Caso(usuario, grupo, participante, contextoDe(usuario));
    }

    private EntradaEvento aporteAnticipado(Caso c, UUID obligacion) {
        return new EntradaEvento(
                c.usuario(),
                c.grupo(),
                c.participante(),
                "APORTE_ANTICIPADO",
                "OBLIGACION_APORTE",
                obligacion,
                "Aporte acreditado antes del vencimiento",
                true,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName(
            "Dado un aporte pagado antes del vencimiento · Cuando se procesa el evento de dominio · Entonces existe un evento_reputacion positivo con referencia a la obligación")
    void criterio1() {
        Caso c = caso();
        UUID obligacion = UUID.randomUUID();

        var salida = transaccion.execute(t -> eventoCU.registrar(aporteAnticipado(c, obligacion), c.ctx()));

        assertThat(salida.esNuevo()).isTrue();
        // El impacto sale de regla_impacto_evento, no de una constante: 7,00 para
        // APORTE_ANTICIPADO en el modelo v1.
        assertThat(salida.impacto()).isEqualByComparingTo("7.00");
        assertThat(salida.factorAfectado()).isEqualTo("PUNTUALIDAD_DE_APORTE");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_reputacion
                         WHERE usuario_id = ? AND referencia_origen_id = ?
                           AND referencia_tipo = 'OBLIGACION_APORTE' AND impacto > 0
                        """,
                        c.usuario(),
                        obligacion))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado el mismo evento de dominio reprocesado · Cuando se consume otra vez · Entonces no se crea un segundo evento_reputacion")
    void criterio2() {
        Caso c = caso();
        UUID obligacion = UUID.randomUUID();

        var primera = transaccion.execute(t -> eventoCU.registrar(aporteAnticipado(c, obligacion), c.ctx()));
        var segunda = transaccion.execute(t -> eventoCU.registrar(aporteAnticipado(c, obligacion), c.ctx()));

        // R-REP-01: el mismo hecho puntua una sola vez. La segunda devuelve el evento
        // que ya existia, y con impacto cero: no vuelve a mover el puntaje.
        assertThat(segunda.eventoId()).isEqualTo(primera.eventoId());
        assertThat(segunda.esNuevo()).isFalse();
        assertThat(segunda.impacto()).isEqualByComparingTo("0");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_reputacion WHERE referencia_origen_id = ?",
                        obligacion))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una entrega anulada que había sumado puntos · Cuando se procesa la reversa · Entonces existe un evento compensatorio con revertido_por_id · Y el evento original sigue existiendo")
    void criterio3() {
        Caso c = caso();
        UUID obligacion = UUID.randomUUID();
        var original = transaccion.execute(t -> eventoCU.registrar(aporteAnticipado(c, obligacion), c.ctx()));

        var compensacion =
                transaccion.execute(t -> eventoCU.compensar(original.eventoId(), "La entrega se anulo", c.ctx()));

        assertThat(compensacion.impacto()).isEqualByComparingTo("-7.00");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_reputacion WHERE revertido_por_id = ?",
                        original.eventoId()))
                .isEqualTo(1);
        // El original sigue: evento_reputacion es append-only, y borrarlo dejaria un
        // puntaje que nadie puede reconstruir.
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_reputacion WHERE id = ?", original.eventoId()))
                .isEqualTo(1);
        // La suma de los dos es cero: la reversa deshace exactamente lo que se aplico.
        assertThat(original.impacto().add(compensacion.impacto())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        Caso c = caso();
        UUID obligacion = UUID.randomUUID();

        var a = transaccion.execute(t -> eventoCU.registrar(aporteAnticipado(c, obligacion), c.ctx()));
        var b = transaccion.execute(t -> eventoCU.registrar(aporteAnticipado(c, obligacion), c.ctx()));
        var d = transaccion.execute(t -> eventoCU.registrar(aporteAnticipado(c, obligacion), c.ctx()));

        assertThat(a.eventoId()).isEqualTo(b.eventoId()).isEqualTo(d.eventoId());
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_reputacion WHERE referencia_origen_id = ?",
                        obligacion))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        Caso c = caso();
        UUID obligacion = UUID.randomUUID();

        // La comprobacion previa de la aplicacion no basta cuando las dos leen a la vez:
        // el que sostiene la regla es uq_evento_reputacion_hecho.
        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> eventoCU.registrar(aporteAnticipado(c, obligacion), c.ctx()));
            } catch (Exception e) {
                errores.add(e);
            }
        };
        var uno = new Thread(intento);
        var dos = new Thread(intento);
        uno.start();
        dos.start();
        uno.join();
        dos.join();

        assertThat(errores).hasSizeLessThanOrEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_reputacion WHERE referencia_origen_id = ?",
                        obligacion))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        Caso c = caso();
        UUID obligacion = UUID.randomUUID();
        var original = transaccion.execute(t -> eventoCU.registrar(aporteAnticipado(c, obligacion), c.ctx()));
        transaccion.execute(t -> eventoCU.compensar(original.eventoId(), "Anulada", c.ctx()));

        // Este servicio no mueve plata; lo que tiene que cuadrar es el impacto: un
        // evento y su compensacion suman exactamente cero.
        var suma = dsl.fetchOne(
                        "SELECT COALESCE(SUM(impacto), 0) FROM transparencia.evento_reputacion WHERE usuario_id = ?",
                        c.usuario())
                .get(0, java.math.BigDecimal.class);
        assertThat(suma).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        Caso c = caso();
        UUID obligacion = UUID.randomUUID();
        var original = transaccion.execute(t -> eventoCU.registrar(aporteAnticipado(c, obligacion), c.ctx()));

        // La compensacion llega dos veces, y una tercera vez despues de que el evento
        // original se reprocesa. Ninguna crea una segunda fila.
        var primera = transaccion.execute(t -> eventoCU.compensar(original.eventoId(), "Anulada", c.ctx()));
        var repetida = transaccion.execute(t -> eventoCU.compensar(original.eventoId(), "Anulada", c.ctx()));
        transaccion.execute(t -> eventoCU.registrar(aporteAnticipado(c, obligacion), c.ctx()));
        var tardia = transaccion.execute(t -> eventoCU.compensar(original.eventoId(), "Anulada", c.ctx()));

        assertThat(repetida.eventoId()).isEqualTo(primera.eventoId());
        assertThat(tardia.eventoId()).isEqualTo(primera.eventoId());
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_reputacion WHERE usuario_id = ?", c.usuario()))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        Caso c = caso();
        UUID obligacion = UUID.randomUUID();

        // Paso 1 fallido: un tipo sin regla en el modelo. No se escribe nada.
        assertThatThrownBy(() -> transaccion.execute(t -> eventoCU.registrar(
                        new EntradaEvento(
                                c.usuario(),
                                c.grupo(),
                                c.participante(),
                                "KYC_COMPLETADO",
                                "PARTICIPANTE",
                                obligacion,
                                "Sin regla en el modelo",
                                true,
                                OffsetDateTime.now(ZoneOffset.UTC)),
                        c.ctx())))
                .hasMessageContaining("no tiene regla");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_reputacion WHERE usuario_id = ?", c.usuario()))
                .isZero();

        // Paso 2 fallido: compensar un evento que no existe. Tampoco escribe nada.
        assertThatThrownBy(() -> transaccion.execute(t -> eventoCU.compensar(UUID.randomUUID(), "No existe", c.ctx())))
                .hasMessageContaining("No existe el evento");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_reputacion WHERE usuario_id = ?", c.usuario()))
                .isZero();
    }
}
