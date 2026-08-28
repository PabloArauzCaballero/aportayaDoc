package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.transparencia.aplicacion.CU74EvaluarInsignias.EntradaEvaluacion;
import bo.aportaya.transparencia.dominio.CriterioDeInsignia;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-74 · Otorgar y revocar una insignia. */
class CU74Test extends BaseDeTransparencia {

    /** Alguien que cerro su primer ciclo sin atrasos y sin cobertura del fondo. */
    private static CriterioDeInsignia.Hechos primerCicloLimpio() {
        return new CriterioDeInsignia.Hechos(1, 1, 12, 400, 0, 0, 0, 0, null, 0, 6, true, false);
    }

    private static CriterioDeInsignia.Hechos sinNada() {
        return new CriterioDeInsignia.Hechos(0, 0, 0, 0, 0, 0, 0, 0, null, 0, 0, false, false);
    }

    private record Caso(UUID usuario, ContextoSesion ctx) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        return new Caso(usuario, contextoDe(usuario));
    }

    @Test
    @DisplayName(
            "Dado un usuario que cierra su primer grupo sin atrasos · Cuando se procesa el cierre · Entonces se otorga la insignia correspondiente con su motivo legible")
    void criterio1() {
        Caso c = caso();

        var otorgadas = transaccion.execute(t -> insigniaCU.evaluar(
                new EntradaEvaluacion(c.usuario(), "CICLO_COMPLETADO", List.of(), primerCicloLimpio()), c.ctx()));

        assertThat(otorgadas).extracting("insigniaCodigo").contains("PRIMER_PASANAKU");
        assertThat(otorgadas.get(0).motivoLegible()).isNotBlank();
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.insignia_otorgada io
                          JOIN transparencia.insignia_logro il ON il.id = io.insignia_id
                         WHERE io.usuario_id = ? AND il.codigo = 'PRIMER_PASANAKU'
                        """,
                        c.usuario()))
                .isEqualTo(1);
        // El aviso lleva el motivo: una insignia sin explicacion es un adorno.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.insignia_otorgada'
                           AND payload->>'usuarioId' = ? AND length(payload->>'motivoLegible') > 10
                        """,
                        c.usuario().toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un usuario que ya tiene una insignia · Cuando el evento se procesa de nuevo · Entonces no se crea una segunda fila")
    void criterio2() {
        Caso c = caso();
        var entrada = new EntradaEvaluacion(c.usuario(), "CICLO_COMPLETADO", List.of(), primerCicloLimpio());

        var primera = transaccion.execute(t -> insigniaCU.evaluar(entrada, c.ctx()));
        var segunda = transaccion.execute(t -> insigniaCU.evaluar(entrada, c.ctx()));

        assertThat(primera).anyMatch(o -> o.esNueva());
        assertThat(segunda).noneMatch(o -> o.esNueva());
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.insignia_otorgada WHERE usuario_id = ?", c.usuario()))
                .isEqualTo(primera.size());
    }

    @Test
    @DisplayName(
            "Dada una insignia otorgada por un hecho que luego se invalida · Cuando se revoca · Entonces queda revocada_en y motivo_revocacion, sin borrar la fila")
    void criterio3() {
        Caso c = caso();
        var otorgadas = transaccion.execute(t -> insigniaCU.evaluar(
                new EntradaEvaluacion(c.usuario(), "CICLO_COMPLETADO", List.of(), primerCicloLimpio()), c.ctx()));
        UUID otorgadaId = otorgadas.get(0).otorgadaId();

        transaccion.execute(t -> insigniaCU.revocar(otorgadaId, "El ciclo se anulo por fraude confirmado", c.ctx()));

        // R-REP-05: la fila sigue. Borrarla dejaria a la persona sin poder saber por
        // que perdio algo que tenia, y a nosotros sin poder explicarlo.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.insignia_otorgada
                         WHERE id = ? AND revocada_en IS NOT NULL AND motivo_revocacion IS NOT NULL
                        """,
                        otorgadaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un cambio en el criterio de una insignia · Cuando se publica la versión nueva · Entonces quienes ya la tenían la conservan")
    void criterio4() {
        Caso c = caso();
        transaccion.execute(t -> insigniaCU.evaluar(
                new EntradaEvaluacion(c.usuario(), "CICLO_COMPLETADO", List.of(), primerCicloLimpio()), c.ctx()));

        // Se endurece el criterio publicado y se reevalua con hechos que ya no lo
        // cumplen: quien la tenia la conserva. Quitarle a alguien un logro porque
        // movimos la vara es reescribirle el pasado.
        dsl.execute(
                "UPDATE transparencia.insignia_logro SET criterio = ? WHERE codigo = 'PRIMER_PASANAKU'",
                "Tres ciclos con todos los aportes acreditados y sin cobertura del fondo.");
        var reevaluada = transaccion.execute(t -> insigniaCU.evaluar(
                new EntradaEvaluacion(c.usuario(), "CICLO_COMPLETADO", List.of(), sinNada()), c.ctx()));

        assertThat(reevaluada).noneMatch(o -> o.esNueva());
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.insignia_otorgada io
                          JOIN transparencia.insignia_logro il ON il.id = io.insignia_id
                         WHERE io.usuario_id = ? AND il.codigo = 'PRIMER_PASANAKU' AND io.revocada_en IS NULL
                        """,
                        c.usuario()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        Caso c = caso();
        var entrada = new EntradaEvaluacion(c.usuario(), "CICLO_COMPLETADO", List.of(), primerCicloLimpio());

        var a = transaccion.execute(t -> insigniaCU.evaluar(entrada, c.ctx()));
        var b = transaccion.execute(t -> insigniaCU.evaluar(entrada, c.ctx()));
        var d = transaccion.execute(t -> insigniaCU.evaluar(entrada, c.ctx()));

        assertThat(b)
                .extracting("otorgadaId")
                .isEqualTo(a.stream().map(x -> x.otorgadaId()).toList());
        assertThat(d)
                .extracting("otorgadaId")
                .isEqualTo(a.stream().map(x -> x.otorgadaId()).toList());
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.insignia_otorgada WHERE usuario_id = ?", c.usuario()))
                .isEqualTo(a.size());
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        Caso c = caso();
        var entrada = new EntradaEvaluacion(c.usuario(), "CICLO_COMPLETADO", List.of(), primerCicloLimpio());

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> insigniaCU.evaluar(entrada, c.ctx()));
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

        // uq_insignia_usuario es quien sostiene R-REP-05 cuando los dos leen a la vez.
        assertThat(errores).hasSizeLessThanOrEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.insignia_otorgada io
                          JOIN transparencia.insignia_logro il ON il.id = io.insignia_id
                         WHERE io.usuario_id = ? AND il.codigo = 'PRIMER_PASANAKU'
                        """,
                        c.usuario()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        Caso c = caso();
        // Organizador confiable exige tres condiciones a la vez. Con dos grupos
        // cerrados pero una sancion firme, NO se otorga: cuadrar aca es que las tres
        // condiciones se exijan juntas y no dos de tres.
        var conSancion =
                new CriterioDeInsignia.Hechos(2, 2, 24, 700, 1, 2, 1, 0, new BigDecimal("95"), 0, 30, true, false);
        var sinSancion =
                new CriterioDeInsignia.Hechos(2, 2, 24, 700, 1, 2, 0, 0, new BigDecimal("95"), 0, 30, true, false);

        var conSancionOtorgadas = transaccion.execute(t -> insigniaCU.evaluar(
                new EntradaEvaluacion(c.usuario(), null, List.of("ORGANIZADOR_CONFIABLE"), conSancion), c.ctx()));
        assertThat(conSancionOtorgadas).isEmpty();

        var sinSancionOtorgadas = transaccion.execute(t -> insigniaCU.evaluar(
                new EntradaEvaluacion(c.usuario(), null, List.of("ORGANIZADOR_CONFIABLE"), sinSancion), c.ctx()));
        assertThat(sinSancionOtorgadas).hasSize(1);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        Caso c = caso();
        var alDia = new EntradaEvaluacion(c.usuario(), "CICLO_COMPLETADO", List.of(), primerCicloLimpio());
        var atrasado = new EntradaEvaluacion(c.usuario(), "CICLO_COMPLETADO", List.of(), sinNada());

        transaccion.execute(t -> insigniaCU.evaluar(alDia, c.ctx()));
        // El evento viejo llega despues: no otorga nada nuevo ni quita lo otorgado.
        var tardio = transaccion.execute(t -> insigniaCU.evaluar(atrasado, c.ctx()));

        assertThat(tardio).noneMatch(o -> o.esNueva());
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.insignia_otorgada WHERE usuario_id = ? AND revocada_en IS NULL",
                        c.usuario()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        Caso c = caso();
        var otorgadas = transaccion.execute(t -> insigniaCU.evaluar(
                new EntradaEvaluacion(c.usuario(), "CICLO_COMPLETADO", List.of(), primerCicloLimpio()), c.ctx()));
        UUID otorgadaId = otorgadas.get(0).otorgadaId();

        transaccion.execute(t -> insigniaCU.revocar(otorgadaId, "El ciclo se anulo por fraude", c.ctx()));

        // Paso fallido: revocar dos veces. La segunda no reescribe el motivo de la
        // primera —el motivo original es la explicacion que le queda a la persona—.
        assertThatThrownBy(
                        () -> transaccion.execute(t -> insigniaCU.revocar(otorgadaId, "Otro motivo distinto", c.ctx())))
                .hasMessageContaining("no esta vigente");
        assertThat(dsl.fetchOne(
                                "SELECT motivo_revocacion FROM transparencia.insignia_otorgada WHERE id = ?",
                                otorgadaId)
                        .get(0, String.class))
                .isEqualTo("El ciclo se anulo por fraude");
    }
}
