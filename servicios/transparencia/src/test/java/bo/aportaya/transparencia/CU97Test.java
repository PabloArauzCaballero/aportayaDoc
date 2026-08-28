package bo.aportaya.transparencia;

import static bo.aportaya.transparencia.EscenarioDeRiesgo.CODIGOS;
import static bo.aportaya.transparencia.EscenarioDeRiesgo.tasaDePago;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.transparencia.aplicacion.CU97EvaluarRiesgo.EntradaRiesgo;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-97 · Anticipar el riesgo con alertas tempranas. */
class CU97Test extends BaseDeTransparencia {

    @Test
    @DisplayName(
            "Dado un grupo cuya tasa de pago en término cae bajo el umbral · Cuando se cierra el período · Entonces existe una metrica_grupo con en_alerta en true · Y una alerta_temprana de ámbito grupo")
    void criterio1() {
        UUID grupo = fixtura.grupo();
        UUID periodo = fixtura.periodo(grupo, 1);

        var salida = transaccion.execute(t -> riesgoCU.evaluar(
                new EntradaRiesgo(
                        "GRUPO",
                        grupo,
                        periodo,
                        true,
                        List.of(tasaDePago("0.4000", "0.8000")),
                        CODIGOS,
                        12,
                        new BigDecimal("55")),
                contextoDeSistema()));

        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.metrica_grupo WHERE grupo_id = ? AND en_alerta = true",
                        grupo))
                .isEqualTo(1);
        assertThat(salida.alertas()).hasSize(1);
        assertThat(salida.alertas().get(0).codigo()).isEqualTo("GRUPO_INVIABLE");
        // HUECO H-9: alerta_temprana vive en el esquema de garantia y no se escribe
        // desde aca (invariante 11). Lo que este servicio guarda es alerta_riesgo, de
        // ambito GRUPO, y pide la temprana por evento.
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.alerta_riesgo WHERE ambito = 'GRUPO' AND ambito_id = ? AND estado = 'ABIERTA'",
                        grupo))
                .isEqualTo(1);
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.alerta_temprana_generada' AND payload->>'ambito' = 'GRUPO'
                           AND payload->>'ambitoId' = ?
                        """,
                        grupo.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dado un participante sin historial · Cuando se evalúa su riesgo · Entonces el nivel es SIN_DATOS y no se genera alerta por eso")
    void criterio2() {
        UUID usuario = fixtura.usuario();

        var salida = transaccion.execute(t -> riesgoCU.evaluar(
                new EntradaRiesgo("USUARIO", usuario, null, true, List.of(), CODIGOS, 0, null), contextoDeSistema()));

        // Sin historial no hay riesgo alto. Tratar a quien recien llega como probable
        // incumplidor es la exclusion que este producto existe para no repetir.
        assertThat(salida.nivelRiesgo()).isEqualTo("SIN_DATOS");
        assertThat(salida.alertas()).isEmpty();
        assertThat(contar("SELECT count(*)::int FROM transparencia.alerta_riesgo WHERE ambito_id = ?", usuario))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dada una alerta temprana sobre un participante · Cuando se le notifica · Entonces el mensaje habla de hechos concretos y no menciona ningún puntaje")
    void criterio3() {
        UUID usuario = fixtura.usuario();
        var caida = EscenarioDeRiesgo.caidaDePuntaje("120", "50");

        var salida = transaccion.execute(t -> riesgoCU.evaluar(
                new EntradaRiesgo("USUARIO", usuario, null, true, List.of(caida), CODIGOS, 20, new BigDecimal("42")),
                contextoDeSistema()));

        String mensaje = salida.alertas().get(0).mensajeAlUsuario();
        // Hechos con numeros, y ningun rastro del modelo: quien conoce el puntaje lo
        // puede jugar, y a quien lo recibe no le sirve para nada.
        assertThat(mensaje).contains("120");
        assertThat(mensaje).doesNotContain("42").doesNotContainIgnoringCase("riesgo");
        assertThat(mensaje).doesNotContainIgnoringCase("puntaje de");
        assertThat(salida.alertas().get(0).accionSugerida()).isNotBlank();
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.alerta_temprana_generada'
                           AND payload->>'mensajeAlUsuario' NOT LIKE '%42%'
                           AND payload->>'ambitoId' = ?
                        """,
                        usuario.toString()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName(
            "Dada una alerta abierta · Cuando se intenta cerrarla sin desenlace · Entonces se rechaza con CIERRE_SIN_DESENLACE")
    void criterio4() {
        UUID grupo = fixtura.grupo();
        UUID periodo = fixtura.periodo(grupo, 1);
        var salida = transaccion.execute(t -> riesgoCU.evaluar(
                new EntradaRiesgo(
                        "GRUPO", grupo, periodo, true, List.of(tasaDePago("0.4000", "0.8000")), CODIGOS, 12, null),
                contextoDeSistema()));
        UUID alertaId = salida.alertas().get(0).alertaId();

        // Una alerta cerrada sin desenlace no deja calibrar nada: el modelo se queda
        // con el mismo error para siempre.
        assertThatThrownBy(() -> transaccion.execute(t -> {
                    riesgoCU.cerrar(alertaId, "CERRADA", contextoDeSistema());
                    return null;
                }))
                .hasMessageContaining("CONFIRMADA o DESCARTADA");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.alerta_riesgo WHERE id = ? AND estado = 'ABIERTA'",
                        alertaId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID grupo = fixtura.grupo();
        UUID periodo = fixtura.periodo(grupo, 1);
        var entrada = new EntradaRiesgo(
                "GRUPO", grupo, periodo, true, List.of(tasaDePago("0.4000", "0.8000")), CODIGOS, 12, null);

        var a = transaccion.execute(t -> riesgoCU.evaluar(entrada, contextoDeSistema()));
        var b = transaccion.execute(t -> riesgoCU.evaluar(entrada, contextoDeSistema()));

        // R-GAR-07: una alerta abierta por causa. La segunda evaluacion no abre otra;
        // dos alertas por lo mismo duplican el trabajo del comite y la segunda se lee
        // como un problema nuevo.
        assertThat(a.alertas()).hasSize(1);
        assertThat(b.alertas()).isEmpty();
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.alerta_riesgo WHERE ambito_id = ? AND codigo = 'GRUPO_INVIABLE'",
                        grupo))
                .isEqualTo(1);
        // Y la metrica se actualiza en su fila, no se duplica.
        assertThat(contar("SELECT count(*)::int FROM transparencia.metrica_grupo WHERE grupo_id = ?", grupo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID grupo = fixtura.grupo();
        UUID periodo = fixtura.periodo(grupo, 1);
        var entrada = new EntradaRiesgo(
                "GRUPO", grupo, periodo, true, List.of(tasaDePago("0.4000", "0.8000")), CODIGOS, 12, null);

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> riesgoCU.evaluar(entrada, contextoDeSistema()));
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

        // HUECO H-8: alerta_temprana tiene indice unico parcial para la abierta
        // (uq_alerta_temprana_abierta) y alerta_riesgo NO tiene ninguno. Con las dos
        // evaluaciones a la vez, lo unico que separa es la comprobacion previa, y puede
        // no alcanzar. Se afirma lo que es cierto y el hueco queda declarado.
        assertThat(errores).hasSizeLessThanOrEqualTo(1);
        assertThat(contar("SELECT count(*)::int FROM transparencia.alerta_riesgo WHERE ambito_id = ?", grupo))
                .isBetween(1, 2);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        UUID grupo = fixtura.grupo();
        UUID periodo = fixtura.periodo(grupo, 1);

        // Una metrica dentro de umbral y otra fuera: se guardan las dos, se alerta por
        // una sola. Guardar solo las que alertan perderia la serie con la que despues
        // se ve que algo venia empeorando desde hace meses.
        var salida = transaccion.execute(t -> riesgoCU.evaluar(
                new EntradaRiesgo(
                        "GRUPO",
                        grupo,
                        periodo,
                        true,
                        List.of(tasaDePago("0.9500", "0.8000"), EscenarioDeRiesgo.moraConcentrada("0.7000", "0.5000")),
                        CODIGOS,
                        12,
                        null),
                contextoDeSistema()));

        assertThat(contar("SELECT count(*)::int FROM transparencia.metrica_grupo WHERE grupo_id = ?", grupo))
                .isEqualTo(2);
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.metrica_grupo WHERE grupo_id = ? AND en_alerta = true",
                        grupo))
                .isEqualTo(1)
                .isEqualTo(salida.alertas().size());
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID grupo = fixtura.grupo();
        UUID periodo = fixtura.periodo(grupo, 1);
        var mala = new EntradaRiesgo(
                "GRUPO", grupo, periodo, true, List.of(tasaDePago("0.4000", "0.8000")), CODIGOS, 12, null);
        var buena = new EntradaRiesgo(
                "GRUPO", grupo, periodo, true, List.of(tasaDePago("0.9500", "0.8000")), CODIGOS, 12, null);

        transaccion.execute(t -> riesgoCU.evaluar(mala, contextoDeSistema()));
        // Llega una medicion vieja y buena despues de la mala: la metrica se corrige
        // pero la alerta abierta NO se cierra sola. Cerrarla sin que nadie mire seria
        // dar por resuelto lo que solo dejo de medirse.
        transaccion.execute(t -> riesgoCU.evaluar(buena, contextoDeSistema()));

        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.metrica_grupo WHERE grupo_id = ? AND en_alerta = false",
                        grupo))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.alerta_riesgo WHERE ambito_id = ? AND estado = 'ABIERTA'",
                        grupo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        UUID grupo = fixtura.grupo();
        UUID periodo = fixtura.periodo(grupo, 1);

        // Paso fallido: el periodo no esta cerrado. Con el periodo abierto la tasa
        // todavia va a subir, y alertar es alarmar por algo que aun no paso.
        assertThatThrownBy(() -> transaccion.execute(t -> riesgoCU.evaluar(
                        new EntradaRiesgo(
                                "GRUPO",
                                grupo,
                                periodo,
                                false,
                                List.of(tasaDePago("0.4000", "0.8000")),
                                CODIGOS,
                                12,
                                null),
                        contextoDeSistema())))
                .hasMessageContaining("periodo cerrado");
        assertThat(contar("SELECT count(*)::int FROM transparencia.metrica_grupo WHERE grupo_id = ?", grupo))
                .isZero();

        // Con el periodo cerrado, se abre la alerta y se cierra con desenlace.
        var salida = transaccion.execute(t -> riesgoCU.evaluar(
                new EntradaRiesgo(
                        "GRUPO", grupo, periodo, true, List.of(tasaDePago("0.4000", "0.8000")), CODIGOS, 12, null),
                contextoDeSistema()));
        UUID alertaId = salida.alertas().get(0).alertaId();
        transaccion.execute(t -> {
            riesgoCU.cerrar(alertaId, "DESCARTADA", contextoDeSistema());
            return null;
        });

        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.alerta_riesgo WHERE id = ? AND estado = 'DESCARTADA' AND cerrada_en IS NOT NULL",
                        alertaId))
                .isEqualTo(1);
        // Cerrar dos veces no reescribe el desenlace de la primera.
        assertThatThrownBy(() -> transaccion.execute(t -> {
                    riesgoCU.cerrar(alertaId, "CONFIRMADA", contextoDeSistema());
                    return null;
                }))
                .hasMessageContaining("no esta abierta");
    }
}
