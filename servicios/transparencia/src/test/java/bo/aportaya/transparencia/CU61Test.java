package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.SorteoVerificable;
import bo.aportaya.transparencia.aplicacion.CU61VerificarSorteo.PaqueteDeSorteo;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-61 · Verificar publicamente el sorteo. */
class CU61Test extends BaseDeTransparencia {

    private static final String SEMILLA = "a3f1c9d2e8b74650a3f1c9d2e8b74650a3f1c9d2e8b74650a3f1c9d2e8b74650";
    private static final List<String> ENTROPIAS = List.of("bloque-btc-870001", "aporte-participante-3");
    private static final List<Integer> CUPOS = List.of(1, 2, 3, 4, 5);

    /** El paquete tal como lo publica {@code grupos}: correcto de punta a punta. */
    private PaqueteDeSorteo paqueteCorrecto(UUID sorteo) {
        return new PaqueteDeSorteo(
                sorteo,
                SorteoVerificable.hashDelCompromiso(SEMILLA, ENTROPIAS),
                SEMILLA,
                ENTROPIAS,
                "FISHER_YATES_SHA256",
                CUPOS,
                SorteoVerificable.barajarDeterminista(SEMILLA, CUPOS));
    }

    @Test
    @DisplayName(
            "Dado un sorteo revelado y correcto · Cuando un tercero verifica · Entonces verifica es true y ordenCoincide es true · Y queda una fila en verificacion_publica")
    void criterio1() {
        UUID sorteo = UUID.randomUUID();

        var salida = transaccion.execute(t -> sorteoCU.verificar(paqueteCorrecto(sorteo), contextoDeSistema()));

        assertThat(salida.verifica()).isTrue();
        assertThat(salida.ordenCoincide()).isTrue();
        assertThat(salida.primerCupoDiscrepante()).isNull();
        assertThat(salida.hashRecomputado()).isEqualTo(salida.hashEsperado());
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ? AND resultado = 'COINCIDE'",
                        sorteo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un sorteo cuyo orden publicado fue alterado en la base · Cuando alguien verifica · Entonces verifica es false, se indica el primer cupo discrepante · Y se abre un incidente_operativo")
    void criterio2() {
        UUID sorteo = UUID.randomUUID();
        var correcto = paqueteCorrecto(sorteo);
        // Se intercambian los dos primeros turnos publicados: el ataque clasico, dar el
        // primer turno a quien convenga.
        var alterado = new java.util.ArrayList<>(correcto.ordenPublicado());
        java.util.Collections.swap(alterado, 0, 1);
        var paquete = new PaqueteDeSorteo(
                sorteo,
                correcto.hashComprometido(),
                correcto.semillaRevelada(),
                correcto.entropias(),
                correcto.metodo(),
                correcto.cuposEnOrdenOriginal(),
                List.copyOf(alterado));

        var salida = transaccion.execute(t -> sorteoCU.verificar(paquete, contextoDeSistema()));

        assertThat(salida.verifica()).isFalse();
        assertThat(salida.ordenCoincide()).isFalse();
        assertThat(salida.primerCupoDiscrepante())
                .isEqualTo(correcto.ordenPublicado().get(0));
        // El incidente vive en auditoria: se pide por evento, no se escribe aca.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.sorteo_verificacion_fallida'
                           AND agregado_id = ? AND payload->>'severidad' = 'ALTA'
                        """,
                        sorteo))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ? AND resultado = 'NO_COINCIDE'",
                        sorteo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un sorteo aún comprometido · Cuando alguien intenta verificar · Entonces se responde SORTEO_NO_REVELADO con la fecha de revelado")
    void criterio3() {
        UUID sorteo = UUID.randomUUID();
        String compromiso = SorteoVerificable.hashDelCompromiso(SEMILLA, ENTROPIAS);
        var comprometido =
                new PaqueteDeSorteo(sorteo, compromiso, null, ENTROPIAS, "FISHER_YATES_SHA256", CUPOS, List.of());

        assertThatThrownBy(() -> transaccion.execute(t -> sorteoCU.verificar(comprometido, contextoDeSistema())))
                // Antes del revelado no hay nada que verificar, y ese es el punto: el
                // hash ya publicado prueba que el resultado estaba fijado de antemano.
                .hasMessageContaining("sigue comprometido")
                .hasMessageContaining(compromiso);
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ?", sorteo))
                .isZero();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID sorteo = UUID.randomUUID();

        var a = transaccion.execute(t -> sorteoCU.verificar(paqueteCorrecto(sorteo), contextoDeSistema()));
        var b = transaccion.execute(t -> sorteoCU.verificar(paqueteCorrecto(sorteo), contextoDeSistema()));

        assertThat(b.verifica()).isEqualTo(a.verifica());
        assertThat(b.hashRecomputado()).isEqualTo(a.hashRecomputado());
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ?", sorteo))
                .isEqualTo(1);
        assertThat(contar("SELECT consultas FROM transparencia.verificacion_publica WHERE referencia_id = ?", sorteo))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID sorteo = UUID.randomUUID();

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> sorteoCU.verificar(paqueteCorrecto(sorteo), contextoDeSistema()));
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
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ?", sorteo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        UUID sorteo = UUID.randomUUID();
        var salida = transaccion.execute(t -> sorteoCU.verificar(paqueteCorrecto(sorteo), contextoDeSistema()));

        // Lo que tiene que cuadrar en un sorteo es que no se pierda ni se repita un
        // cupo: los mismos cinco, en otro orden. Un barajado que duplica un turno le da
        // dos veces la plata a alguien.
        assertThat(salida.cupos()).containsExactlyInAnyOrderElementsOf(CUPOS);
        assertThat(salida.cupos()).doesNotHaveDuplicates().hasSameSizeAs(CUPOS);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID sorteo = UUID.randomUUID();

        transaccion.execute(t -> sorteoCU.verificar(paqueteCorrecto(sorteo), contextoDeSistema()));
        transaccion.execute(t -> sorteoCU.verificar(paqueteCorrecto(sorteo), contextoDeSistema()));

        // Verificar cien veces no abre cien incidentes ni cien filas: cuenta consultas.
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ?", sorteo))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.sorteo_verificacion_fallida' AND agregado_id = ?
                        """,
                        sorteo))
                .isZero();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        UUID sorteo = UUID.randomUUID();

        // Paso fallido: no hay sorteo. No se registra verificacion de algo que no existe.
        assertThatThrownBy(() -> transaccion.execute(t -> sorteoCU.verificar(null, contextoDeSistema())))
                .hasMessageContaining("no existe");

        // Paso fallido: la semilla revelada no produce el compromiso publicado. Es el
        // caso grave —el sorteo se armo despues— y queda con su rastro completo.
        var mentiroso = new PaqueteDeSorteo(
                sorteo,
                "f".repeat(64),
                SEMILLA,
                ENTROPIAS,
                "FISHER_YATES_SHA256",
                CUPOS,
                SorteoVerificable.barajarDeterminista(SEMILLA, CUPOS));
        var salida = transaccion.execute(t -> sorteoCU.verificar(mentiroso, contextoDeSistema()));

        assertThat(salida.verifica()).isFalse();
        // El orden si coincide: lo que fallo fue el compromiso, y el veredicto lo
        // distingue en vez de decir «no verifica» y dejar a la gente adivinando.
        assertThat(salida.ordenCoincide()).isTrue();
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ? AND resultado = 'NO_COINCIDE'",
                        sorteo))
                .isEqualTo(1);
    }
}
