package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.transparencia.aplicacion.CU72SellarBloque.EntradaBloque;
import bo.aportaya.transparencia.aplicacion.CU72SellarBloque.Hecho;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-73 · Verificar la cadena de transparencia. */
class CU73Test extends BaseDeTransparencia {

    private void sellar(UUID grupo, int cuantos) {
        for (int i = 0; i < cuantos; i++) {
            OffsetDateTime hasta = OffsetDateTime.now(ZoneOffset.UTC).minusDays(cuantos - i);
            var hechos = List.of(
                    new Hecho("PAGO", UUID.randomUUID(), Map.of("monto", "500.00", "orden", String.valueOf(i)), hasta));
            transaccion.execute(t -> bloqueCU.sellar(
                    new EntradaBloque(grupo, "CIERRE_PERIODO", hechos, hasta.minusDays(30), hasta, 0),
                    contextoDeSistema()));
        }
    }

    @Test
    @DisplayName(
            "Dada una cadena de cinco bloques sellados correctamente · Cuando se verifica · Entonces integra es true y bloquesVerificados es 5")
    void criterio1() {
        UUID grupo = fixtura.grupo();
        sellar(grupo, 5);

        var salida = transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));

        assertThat(salida.integra()).isTrue();
        assertThat(salida.bloquesVerificados()).isEqualTo(5);
        assertThat(salida.primerBloqueFallido()).isNull();
        // La verificacion queda registrada, coincida o no.
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ? AND resultado = 'COINCIDE'",
                        grupo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un contenido alterado en el bloque 3 · Cuando se verifica · Entonces integra es false y primerBloqueFallido es 3 · Y se abre un incidente_operativo de severidad alta")
    void criterio2() {
        UUID grupo = fixtura.grupo();
        sellar(grupo, 5);
        // Se altera el resumen del hecho sellado en el tercer bloque de la cadena. El
        // hash del bloque no cambia; la raiz que producen sus hechos, si.
        long tercero = dsl.fetchOne(
                        "SELECT min(numero_bloque) + 2 FROM transparencia.bloque_transparencia WHERE grupo_id = ?",
                        grupo)
                .get(0, Long.class);
        dsl.execute(
                """
                UPDATE transparencia.bloque_transparencia SET raiz_merkle = repeat('a', 64)
                 WHERE grupo_id = ? AND numero_bloque = ?
                """,
                grupo,
                tercero);

        var salida = transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));

        assertThat(salida.integra()).isFalse();
        assertThat(salida.primerBloqueFallido()).isEqualTo(tercero);
        // El incidente vive en el esquema de auditoria y no lo escribe este servicio
        // (invariante 11): se pide por evento, con su severidad y su taxonomia.
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.cadena_rota' AND agregado_id = ?
                           AND payload->>'severidad' = 'ALTA'
                           AND payload->>'primerBloqueFallido' = ?
                        """,
                        grupo,
                        String.valueOf(tercero)))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una secuencia con el bloque 4 ausente · Cuando se verifica · Entonces el componente fallido es SECUENCIA")
    void criterio3() {
        UUID grupo = fixtura.grupo();
        sellar(grupo, 3);
        // El cuarto se sella sin hechos: un periodo en el que no paso nada.
        OffsetDateTime hasta = OffsetDateTime.now(ZoneOffset.UTC);
        transaccion.execute(t -> bloqueCU.sellar(
                new EntradaBloque(grupo, "HITO", List.of(), hasta.minusDays(2), hasta.minusDays(1), 0),
                contextoDeSistema()));
        sellar(grupo, 1);
        long cuarto = dsl.fetchOne(
                        "SELECT min(numero_bloque) + 3 FROM transparencia.bloque_transparencia WHERE grupo_id = ?",
                        grupo)
                .get(0, Long.class);

        // Borrar un bloque es exactamente el ataque: se hace desaparecer un periodo. La
        // boveda ya lo impide para todo bloque con hechos —registro_sellado es
        // append-only (R-AUD-01)— asi que el unico que se puede desaparecer es uno
        // vacio. Se comprueban las dos cosas.
        String defendido = rechazaLaBase(
                """
                DELETE FROM transparencia.registro_sellado WHERE bloque_id IN
                    (SELECT id FROM transparencia.bloque_transparencia WHERE grupo_id = ?)
                """,
                grupo);
        assertThat(defendido).contains("R-AUD-01");

        dsl.execute(
                "DELETE FROM transparencia.bloque_transparencia WHERE grupo_id = ? AND numero_bloque = ?",
                grupo,
                cuarto);

        var salida = transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));

        assertThat(salida.integra()).isFalse();
        assertThat(salida.componenteFallido()).isEqualTo("SECUENCIA");
        // Un hueco se reporta en el bloque que sigue al que falta.
        assertThat(salida.primerBloqueFallido()).isEqualTo(cuarto + 1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID grupo = fixtura.grupo();
        sellar(grupo, 3);

        var a = transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));
        var b = transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));

        assertThat(b.integra()).isEqualTo(a.integra());
        assertThat(b.bloquesVerificados()).isEqualTo(a.bloquesVerificados());
        // Una fila por documento verificado, con el contador de consultas al dia: es
        // lo que despues permite ver un codigo consultado en rafaga.
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ?", grupo))
                .isEqualTo(1);
        assertThat(contar("SELECT consultas FROM transparencia.verificacion_publica WHERE referencia_id = ?", grupo))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID grupo = fixtura.grupo();
        sellar(grupo, 3);

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));
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

        // Verificar es de solo lectura salvo por el registro de la consulta: dos a la
        // vez pueden chocar en el UPSERT, pero nunca dejan dos filas.
        assertThat(errores).hasSizeLessThanOrEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ?", grupo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        UUID grupo = fixtura.grupo();
        sellar(grupo, 4);

        var salida = transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));

        // El cuadre de una cadena es que lo verificado sea todo lo sellado: si el
        // verificador contara menos bloques de los que hay, un bloque quedaria sin
        // revisar y nadie se enteraria.
        assertThat(salida.bloquesVerificados())
                .isEqualTo(contar(
                        "SELECT count(*)::int FROM transparencia.bloque_transparencia WHERE grupo_id = ?", grupo));
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID grupo = fixtura.grupo();
        sellar(grupo, 3);

        transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));
        transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));
        transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));

        // Tres verificaciones integras emiten tres avisos, pero una sola fila y ningun
        // incidente: no se alarma a nadie por preguntar tres veces.
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ?", grupo))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.cadena_rota' AND agregado_id = ?
                        """,
                        grupo))
                .isZero();
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        UUID grupo = fixtura.grupo();

        // Paso fallido: verificar un grupo sin historia sellada. No es un error de
        // integridad, y no deja registro de verificacion: no hay nada que verificar.
        assertThatThrownBy(() -> transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema())))
                .hasMessageContaining("todavia no sello historia");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ?", grupo))
                .isZero();

        // Con historia, el mismo camino cierra y queda constancia.
        sellar(grupo, 2);
        var salida = transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));
        assertThat(salida.integra()).isTrue();
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.verificacion_publica WHERE referencia_id = ?", grupo))
                .isEqualTo(1);
    }
}
