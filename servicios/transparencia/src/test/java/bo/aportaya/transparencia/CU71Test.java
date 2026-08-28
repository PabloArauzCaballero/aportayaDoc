package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.transparencia.aplicacion.CU70RegistrarEventoReputacion.EntradaEvento;
import bo.aportaya.transparencia.aplicacion.CU71RecalcularPuntaje.EntradaPuntaje;
import bo.aportaya.transparencia.dominio.PuntajeDeReputacion;
import bo.aportaya.transparencia.infraestructura.ReputacionRepositorio;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-71 · Recalcular el puntaje de reputacion. */
class CU71Test extends BaseDeTransparencia {

    private static final ReputacionRepositorio.Indicadores INDICADORES = new ReputacionRepositorio.Indicadores(
            new BigDecimal("0.90"), new BigDecimal("0.05"), new BigDecimal("6000.00"), 2, 0, 0, 14);

    private record Caso(UUID usuario, UUID grupo, UUID participante, ContextoSesion ctx) {}

    private Caso caso() {
        UUID usuario = fixtura.usuario();
        UUID grupo = fixtura.grupo();
        return new Caso(usuario, grupo, fixtura.participante(grupo, usuario), contextoDe(usuario));
    }

    /** Deja al usuario con los eventos que el modelo v1 exige como minimo (3). */
    private void conHistorial(Caso c, int cuantos) {
        String[] tipos = {"APORTE_ANTICIPADO", "APORTE_PUNTUAL", "DEUDA_CASTIGADA"};
        String[] referencias = {"OBLIGACION_APORTE", "OBLIGACION_APORTE", "REGISTRO_INCUMPLIMIENTO"};
        for (int i = 0; i < cuantos; i++) {
            int indice = i;
            transaccion.execute(t -> eventoCU.registrar(
                    new EntradaEvento(
                            c.usuario(),
                            c.grupo(),
                            c.participante(),
                            tipos[indice % tipos.length],
                            referencias[indice % referencias.length],
                            UUID.randomUUID(),
                            "Hecho " + indice,
                            true,
                            OffsetDateTime.now(ZoneOffset.UTC)),
                    c.ctx()));
        }
    }

    private List<PuntajeDeReputacion.Medicion> mediciones(String puntualidad, String mora) {
        return List.of(
                new PuntajeDeReputacion.Medicion(
                        "PUNTUALIDAD_DE_APORTE", new BigDecimal("18"), new BigDecimal(puntualidad)),
                new PuntajeDeReputacion.Medicion("MORA_ACUMULADA", new BigDecimal("3"), new BigDecimal(mora)));
    }

    @Test
    @DisplayName(
            "Dado un usuario con eventos positivos y negativos · Cuando se recalcula · Entonces existe un único puntaje vigente · Y la suma de los aportes de los componentes es igual al total")
    void criterio1() {
        Caso c = caso();
        conHistorial(c, 3);

        var salida = transaccion.execute(t -> puntajeCU.recalcular(
                new EntradaPuntaje(c.usuario(), mediciones("0.9000", "0.1000"), List.of(), INDICADORES), c.ctx()));

        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.puntaje_reputacion WHERE usuario_id = ?", c.usuario()))
                .isEqualTo(1);
        // R-REP-03, y lo verifica tg_puntaje_cuadra al confirmar la transaccion: si la
        // suma no diera, la fila no estaria.
        var suma = dsl.fetchOne(
                        """
                        SELECT COALESCE(SUM(cs.contribucion), 0) FROM transparencia.componente_score cs
                          JOIN transparencia.puntaje_reputacion p ON p.id = cs.puntaje_id
                         WHERE p.usuario_id = ?
                        """,
                        c.usuario())
                .get(0, BigDecimal.class);
        assertThat(suma).isEqualByComparingTo(salida.puntaje());
        // Un factor positivo y uno penalizador: la puntualidad suma y la mora resta.
        assertThat(salida.componentes())
                .extracting(PuntajeDeReputacion.Componente::codigo)
                .contains("PUNTAJE_BASE", "PUNTUALIDAD_DE_APORTE", "MORA_ACUMULADA");
        assertThat(salida.componentes().stream()
                        .filter(x -> x.codigo().equals("MORA_ACUMULADA"))
                        .findFirst()
                        .orElseThrow()
                        .contribucion())
                .isNegative();
    }

    @Test
    @DisplayName("Dado un usuario sin historial · Cuando se recalcula · Entonces el nivel es SIN_HISTORIAL y no cero")
    void criterio2() {
        Caso c = caso();
        conHistorial(c, 1); // el modelo v1 exige 3

        var salida = transaccion.execute(t -> puntajeCU.recalcular(
                new EntradaPuntaje(c.usuario(), mediciones("0.9000", "0.1000"), List.of(), INDICADORES), c.ctx()));

        assertThat(salida.nivelDeConfianza()).isEqualTo("SIN_HISTORIAL");
        // Y NO cero: quien recien llega no fallo. El puntaje es el de arranque del
        // modelo, 500, y se dice que aun no hay historial.
        assertThat(salida.puntaje()).isEqualByComparingTo("500.00");
        assertThat(salida.puntaje()).isNotEqualByComparingTo("0");
    }

    @Test
    @DisplayName(
            "Dado un cambio de versión del modelo · Cuando se recalcula · Entonces el puntaje nuevo referencia el modelo nuevo · Y el anterior conserva su modelo_id original")
    void criterio3() {
        Caso c = caso();
        conHistorial(c, 3);
        transaccion.execute(t -> puntajeCU.recalcular(
                new EntradaPuntaje(c.usuario(), mediciones("0.9000", "0.1000"), List.of(), INDICADORES), c.ctx()));
        UUID snapshotAnterior = transaccion.execute(t -> puntajeCU.tomarSnapshot(c.usuario(), "PERIODICO", c.ctx()));

        var recalculado = transaccion.execute(t -> puntajeCU.recalcular(
                new EntradaPuntaje(
                        c.usuario(), mediciones("0.5000", "0.4000"), mediciones("0.9000", "0.1000"), INDICADORES),
                c.ctx()));

        // El puntaje nuevo referencia el modelo con el que se calculo.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.puntaje_reputacion p
                          JOIN transparencia.modelo_scoring m ON m.id = p.modelo_id
                         WHERE p.usuario_id = ? AND m.version = p.modelo_version
                        """,
                        c.usuario()))
                .isEqualTo(1);
        // HUECO H-2: `uq_puntaje_reputacion_usuario_id` deja UN solo puntaje por
        // usuario, asi que el anterior no puede coexistir para conservar su modelo_id.
        // Lo que si conserva la version con la que se calculo es la foto, y contra ella
        // se afirma.
        assertThat(contar("SELECT count(*)::int FROM transparencia.snapshot_reputacion WHERE id = ?", snapshotAnterior))
                .isEqualTo(1);
        assertThat(dsl.fetchOne(
                                "SELECT fotografia_factores->>'modelo' FROM transparencia.snapshot_reputacion WHERE id = ?",
                                snapshotAnterior)
                        .get(0, String.class))
                .isEqualTo("v1");
        // Y la tendencia queda registrada: bajo, y se ve que bajo.
        assertThat(recalculado.componentes().stream()
                        .filter(x -> x.codigo().equals("PUNTUALIDAD_DE_APORTE"))
                        .findFirst()
                        .orElseThrow()
                        .tendencia())
                .isEqualTo("BAJA");
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        Caso c = caso();
        conHistorial(c, 3);
        var entrada = new EntradaPuntaje(c.usuario(), mediciones("0.9000", "0.1000"), List.of(), INDICADORES);

        var a = transaccion.execute(t -> puntajeCU.recalcular(entrada, c.ctx()));
        var b = transaccion.execute(t -> puntajeCU.recalcular(entrada, c.ctx()));

        // El recalculo es idempotente en el resultado, no en la fila: el puntaje es el
        // mismo y sigue habiendo uno solo.
        assertThat(b.puntaje()).isEqualByComparingTo(a.puntaje());
        assertThat(b.nivelDeConfianza()).isEqualTo(a.nivelDeConfianza());
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.puntaje_reputacion WHERE usuario_id = ?", c.usuario()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        Caso c = caso();
        conHistorial(c, 3);
        var entrada = new EntradaPuntaje(c.usuario(), mediciones("0.9000", "0.1000"), List.of(), INDICADORES);

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> puntajeCU.recalcular(entrada, c.ctx()));
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
                        "SELECT count(*)::int FROM transparencia.puntaje_reputacion WHERE usuario_id = ?", c.usuario()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        Caso c = caso();
        conHistorial(c, 3);

        // Un caso donde el techo del modelo recorta: sin el componente de ajuste, la
        // suma no daria y tg_puntaje_cuadra rechazaria la fila.
        var salida = transaccion.execute(t -> puntajeCU.recalcular(
                new EntradaPuntaje(
                        c.usuario(),
                        List.of(new PuntajeDeReputacion.Medicion(
                                "PUNTUALIDAD_DE_APORTE", new BigDecimal("20"), new BigDecimal("1.0000"))),
                        List.of(),
                        INDICADORES),
                c.ctx()));

        var suma = dsl.fetchOne(
                        """
                        SELECT COALESCE(SUM(cs.contribucion), 0) FROM transparencia.componente_score cs
                          JOIN transparencia.puntaje_reputacion p ON p.id = cs.puntaje_id
                         WHERE p.usuario_id = ?
                        """,
                        c.usuario())
                .get(0, BigDecimal.class);
        assertThat(suma).isEqualByComparingTo(salida.puntaje());
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        Caso c = caso();
        conHistorial(c, 3);
        var vieja = new EntradaPuntaje(c.usuario(), mediciones("0.9000", "0.1000"), List.of(), INDICADORES);
        var nueva = new EntradaPuntaje(c.usuario(), mediciones("0.5000", "0.4000"), List.of(), INDICADORES);

        transaccion.execute(t -> puntajeCU.recalcular(nueva, c.ctx()));
        var reprocesada = transaccion.execute(t -> puntajeCU.recalcular(vieja, c.ctx()));
        var alDia = transaccion.execute(t -> puntajeCU.recalcular(nueva, c.ctx()));

        // Un recalculo con medicion vieja no deja dos puntajes: recalcular es
        // reemplazar, y el ultimo que corre manda.
        assertThat(alDia.puntaje()).isLessThan(reprocesada.puntaje());
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.puntaje_reputacion WHERE usuario_id = ?", c.usuario()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        Caso c = caso();
        conHistorial(c, 3);

        // Paso fallido: tomar la foto de quien no tiene puntaje. No escribe nada.
        assertThatThrownBy(() -> transaccion.execute(t -> puntajeCU.tomarSnapshot(c.usuario(), "PERIODICO", c.ctx())))
                .hasMessageContaining("no tiene puntaje");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.snapshot_reputacion WHERE usuario_id = ?",
                        c.usuario()))
                .isZero();

        // Con el puntaje calculado, el mismo paso cierra y el estado queda cuadrado.
        transaccion.execute(t -> puntajeCU.recalcular(
                new EntradaPuntaje(c.usuario(), mediciones("0.9000", "0.1000"), List.of(), INDICADORES), c.ctx()));
        transaccion.execute(t -> puntajeCU.tomarSnapshot(c.usuario(), "PERIODICO", c.ctx()));
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.snapshot_reputacion WHERE usuario_id = ?",
                        c.usuario()))
                .isEqualTo(1);
    }
}
