package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;

import bo.aportaya.plataforma.dominio.SorteoVerificable;
import bo.aportaya.transparencia.aplicacion.CU61VerificarSorteo.PaqueteDeSorteo;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-61 · Lo que la base y el caso de uso rechazan. */
class CU61RechazosTest extends BaseDeTransparencia {

    private static final String SEMILLA = "b7e2d4a1f0c93856b7e2d4a1f0c93856b7e2d4a1f0c93856b7e2d4a1f0c93856";
    private static final List<String> ENTROPIAS = List.of("bloque-btc-870555");
    private static final List<Integer> CUPOS = List.of(1, 2, 3, 4);

    private PaqueteDeSorteo paquete(UUID sorteo) {
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
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // Lo que R-AUD-01 protege en este esquema es la evidencia sellada: un registro
        // sellado no se corrige en el lugar ni se borra. Es lo que sostiene que la
        // historia publicada sea la misma que se puede auditar despues.
        UUID grupo = fixtura.grupo();
        var bloque = transaccion.execute(t -> bloqueCU.sellar(
                new bo.aportaya.transparencia.aplicacion.CU72SellarBloque.EntradaBloque(
                        grupo,
                        "HITO",
                        List.of(new bo.aportaya.transparencia.aplicacion.CU72SellarBloque.Hecho(
                                "PAGO",
                                UUID.randomUUID(),
                                java.util.Map.of("monto", "500.00"),
                                java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))),
                        java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).minusDays(1),
                        java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC),
                        0),
                contextoDeSistema()));

        assertThat(rechazaLaBase(
                        "UPDATE transparencia.registro_sellado SET hash_contenido = repeat('0', 64) WHERE bloque_id = ?",
                        bloque.bloqueId()))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM transparencia.registro_sellado WHERE bloque_id = ?", bloque.bloqueId()))
                .contains("R-AUD-01");

        // HUECO H-6: `verificacion_publica` NO esta en la lista de append-only, asi que
        // el resultado de una verificacion fallida se puede borrar. Queda declarado en
        // el informe del carril; aca no se afirma lo contrario para no probar un deseo.
    }

    @Test
    @DisplayName("rechaza por R-AUD-02")
    void rechazaRAUD02() {
        // La verificacion se registra con el hash esperado y el recomputado. Guardar
        // solo el veredicto dejaria una afirmacion sin prueba: quien audite tiene que
        // poder rehacer la comparacion con los mismos dos valores.
        UUID sorteo = UUID.randomUUID();
        transaccion.execute(t -> sorteoCU.verificar(paquete(sorteo), contextoDeSistema()));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.verificacion_publica
                         WHERE referencia_id = ? AND length(hash_esperado) = 64 AND length(hash_recomputado) = 64
                           AND verificado_en IS NOT NULL
                        """,
                        sorteo))
                .isEqualTo(1);
        assertThat(rechazaLaBase(
                        "INSERT INTO transparencia.verificacion_publica (codigo, tipo_documento, referencia_id, hash_esperado, consultas) VALUES ('X', 'SORTEO', ?, 'abc', 1)",
                        sorteo))
                .contains("ck_verificacion_publica_tipo_documento");
    }

    @Test
    @DisplayName("rechaza por R-GRP-06")
    void rechazaRGRP06() {
        // Un orden unico por grupo: el barajado no puede perder ni repetir un cupo. Si
        // repitiera, alguien cobraria dos veces; si perdiera uno, alguien nunca cobra.
        UUID sorteo = UUID.randomUUID();
        var faltante = new PaqueteDeSorteo(
                sorteo,
                paquete(sorteo).hashComprometido(),
                SEMILLA,
                ENTROPIAS,
                "FISHER_YATES_SHA256",
                CUPOS,
                List.of(2, 1, 3)); // se publicaron tres turnos para cuatro cupos

        var salida = transaccion.execute(t -> sorteoCU.verificar(faltante, contextoDeSistema()));

        assertThat(salida.verifica()).isFalse();
        assertThat(salida.ordenCoincide()).isFalse();
        assertThat(salida.cupos()).containsExactlyInAnyOrderElementsOf(CUPOS);
    }
}
