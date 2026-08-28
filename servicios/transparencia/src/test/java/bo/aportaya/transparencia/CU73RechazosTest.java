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

/** CU-73 · Lo que la base y el caso de uso rechazan. */
class CU73RechazosTest extends BaseDeTransparencia {

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
    @DisplayName("rechaza por R-AUD-02")
    void rechazaRAUD02() {
        // La verificacion recomputa desde los datos publicados. Si alguien reescribe el
        // hash de un bloque para que «cuadre», el eslabon siguiente deja de encajar: eso
        // es lo que hace que reescribir la historia obligue a reescribirla entera.
        UUID grupo = fixtura.grupo();
        sellar(grupo, 3);
        long segundo = dsl.fetchOne(
                        "SELECT min(numero_bloque) + 1 FROM transparencia.bloque_transparencia WHERE grupo_id = ?",
                        grupo)
                .get(0, Long.class);
        dsl.execute(
                "UPDATE transparencia.bloque_transparencia SET hash_bloque = repeat('c', 64) WHERE grupo_id = ? AND numero_bloque = ?",
                grupo,
                segundo);

        var salida = transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));

        assertThat(salida.integra()).isFalse();
        assertThat(salida.primerBloqueFallido()).isEqualTo(segundo);
        assertThat(salida.componenteFallido()).isEqualTo("HASH_BLOQUE");
    }

    @Test
    @DisplayName("rechaza por R-AUD-03")
    void rechazaRAUD03() {
        // El hash cubre TODO lo que hay que poder probar, no un subconjunto comodo.
        // Mover el periodo que un bloque dice cubrir tambien lo rompe: si no entrara al
        // hash, se podria recortar un mes de historia sin que nada se notara.
        UUID grupo = fixtura.grupo();
        sellar(grupo, 2);
        long primero = dsl.fetchOne(
                        "SELECT min(numero_bloque) FROM transparencia.bloque_transparencia WHERE grupo_id = ?", grupo)
                .get(0, Long.class);
        dsl.execute(
                """
                UPDATE transparencia.bloque_transparencia
                   SET periodo_cubierto_desde = periodo_cubierto_desde + interval '10 days'
                 WHERE grupo_id = ? AND numero_bloque = ?
                """,
                grupo,
                primero);

        var salida = transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));

        assertThat(salida.integra()).isFalse();
        assertThat(salida.componenteFallido()).isEqualTo("HASH_BLOQUE");
    }

    @Test
    @DisplayName("rechaza por R-AUD-09")
    void rechazaRAUD09() {
        // Los hashes de la cadena no los firma quien podria querer alterarla: la
        // aplicacion los calcula con el mismo atomo publicado, y la base rechaza un
        // eslabon que no encaje. Un hash que la aplicacion pudiera poner a mano no
        // probaria nada contra la aplicacion.
        UUID grupo = fixtura.grupo();
        sellar(grupo, 1);
        long numero = dsl.fetchOne(
                        "SELECT max(numero_bloque) FROM transparencia.bloque_transparencia WHERE grupo_id = ?", grupo)
                .get(0, Long.class);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO transparencia.bloque_transparencia
                            (grupo_id, numero_bloque, hash_bloque_anterior, raiz_merkle, hash_bloque,
                             cantidad_eventos, periodo_cubierto_desde, periodo_cubierto_hasta, sellado_en)
                        VALUES (?, ?, repeat('1', 64), repeat('2', 64), repeat('3', 64), 0, now(), now(), now())
                        """,
                        grupo,
                        numero + 1))
                .contains("R-REP-04");
    }

    @Test
    @DisplayName("rechaza por R-AUD-10")
    void rechazaRAUD10() {
        // Las cadenas se verifican en el control diario, no solo al auditar. Cada
        // verificacion deja su fila con fecha: es lo que permite decir «esta cadena no
        // se mira hace ocho meses».
        UUID grupo = fixtura.grupo();
        sellar(grupo, 2);

        transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.verificacion_publica
                         WHERE referencia_id = ? AND verificado_en IS NOT NULL AND ultima_consulta_en IS NOT NULL
                        """,
                        grupo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("rechaza por R-REP-04")
    void rechazaRREP04() {
        // Cadena unica y encadenada. Un bloque cuyo hash anterior no es el del previo
        // se rechaza al escribir; si aun asi llegara, la verificacion lo encuentra.
        UUID grupo = fixtura.grupo();
        sellar(grupo, 3);
        long tercero = dsl.fetchOne(
                        "SELECT min(numero_bloque) + 2 FROM transparencia.bloque_transparencia WHERE grupo_id = ?",
                        grupo)
                .get(0, Long.class);
        dsl.execute(
                "UPDATE transparencia.bloque_transparencia SET hash_bloque_anterior = repeat('7', 64) WHERE grupo_id = ? AND numero_bloque = ?",
                grupo,
                tercero);

        var salida = transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));

        assertThat(salida.integra()).isFalse();
        assertThat(salida.componenteFallido()).isEqualTo("HASH_ANTERIOR");
        assertThat(salida.primerBloqueFallido()).isEqualTo(tercero);
    }

    @Test
    @DisplayName("rechaza por R-RIS-01")
    void rechazaRRIS01() {
        // Todo evento de riesgo lleva categoria y factor de la taxonomia cerrada. Una
        // cadena rota es un evento de riesgo operativo, no una curiosidad, y el aviso
        // sale con los dos campos puestos para que el registro no quede a medias.
        UUID grupo = fixtura.grupo();
        sellar(grupo, 2);
        dsl.execute(
                "UPDATE transparencia.bloque_transparencia SET raiz_merkle = repeat('5', 64) WHERE grupo_id = ?",
                grupo);

        transaccion.execute(t -> cadenaCU.verificar(grupo, contextoDeSistema()));

        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.evento_dominio
                         WHERE tipo = 'transparencia.cadena_rota' AND agregado_id = ?
                           AND payload->>'categoriaEvento' = 'FALLAS_SISTEMAS'
                           AND payload->>'factorRiesgo' = 'TECNOLOGIA_INFORMACION'
                        """,
                        grupo))
                .isEqualTo(1);
        // Y un grupo sin bloques no es una cadena rota: no se abre incidente por algo
        // que todavia no existe.
        UUID vacio = fixtura.grupo();
        assertThatThrownBy(() -> transaccion.execute(t -> cadenaCU.verificar(vacio, contextoDeSistema())))
                .hasMessageContaining("todavia no sello historia");
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.evento_dominio WHERE tipo = 'transparencia.cadena_rota' AND agregado_id = ?",
                        vacio))
                .isZero();
    }
}
