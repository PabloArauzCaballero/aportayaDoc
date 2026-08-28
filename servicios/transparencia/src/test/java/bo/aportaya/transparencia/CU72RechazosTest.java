package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.transparencia.aplicacion.CU72SellarBloque.EntradaBloque;
import bo.aportaya.transparencia.aplicacion.CU72SellarBloque.Hecho;
import bo.aportaya.transparencia.dominio.CadenaDeBloques;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-72 · Lo que la base y el caso de uso rechazan. */
class CU72RechazosTest extends BaseDeTransparencia {

    private EntradaBloque entrada(UUID grupo, int excepciones) {
        OffsetDateTime hasta = OffsetDateTime.now(ZoneOffset.UTC);
        return new EntradaBloque(
                grupo,
                "CIERRE_PERIODO",
                List.of(new Hecho("PAGO", UUID.randomUUID(), Map.of("monto", "500.00"), hasta.minusDays(1))),
                hasta.minusDays(30),
                hasta,
                excepciones);
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        // El registro sellado es la evidencia: no se corrige en el lugar ni se borra.
        // Si se pudiera, sellar no probaria nada — se reescribiria el pasado y el hash
        // seguiria coincidiendo con el pasado nuevo.
        UUID grupo = fixtura.grupo();
        var bloque = transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 0), contextoDeSistema()));

        assertThat(rechazaLaBase(
                        "UPDATE transparencia.registro_sellado SET resumen_publico = '{}'::jsonb WHERE bloque_id = ?",
                        bloque.bloqueId()))
                .contains("R-AUD-01");
        assertThat(rechazaLaBase("DELETE FROM transparencia.registro_sellado WHERE bloque_id = ?", bloque.bloqueId()))
                .contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-02")
    void rechazaRAUD02() {
        // La cadena vive de que cada eslabon apunte al anterior por hash. Un bloque con
        // hash_bloque_anterior que no coincide con el previo se rechaza al escribir, no
        // al auditar: para cuando se audita ya seria tarde.
        UUID grupo = fixtura.grupo();
        var primero = transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 0), contextoDeSistema()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO transparencia.bloque_transparencia
                            (grupo_id, numero_bloque, hash_bloque_anterior, raiz_merkle, hash_bloque,
                             cantidad_eventos, periodo_cubierto_desde, periodo_cubierto_hasta, sellado_en)
                        VALUES (?, ?, repeat('9', 64), repeat('b', 64), repeat('c', 64), 0, now(), now(), now())
                        """,
                        grupo,
                        primero.numeroBloque() + 1))
                .contains("R-REP-04");
    }

    @Test
    @DisplayName("rechaza por R-BIL-12")
    void rechazaRBIL12() {
        // No se sella el dia con problemas abiertos. Un bloque con datos provisorios
        // miente con firma, y eso es peor que no firmar.
        UUID grupo = fixtura.grupo();

        assertThatThrownBy(() -> transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 3), contextoDeSistema())))
                .hasMessageContaining("excepciones de conciliacion abiertas");
        assertThat(contar("SELECT count(*)::int FROM transparencia.bloque_transparencia WHERE grupo_id = ?", grupo))
                .isZero();

        // Resueltas las excepciones, el mismo periodo se sella sin cambiar nada mas.
        var bloque = transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 0), contextoDeSistema()));
        assertThat(bloque.numeroBloque()).isEqualTo(CadenaDeBloques.PRIMER_NUMERO);
    }

    @Test
    @DisplayName("rechaza por R-REP-04")
    void rechazaRREP04() {
        // Cadena unica y encadenada por grupo: ni salto de numeracion ni numero
        // repetido. Una bifurcacion haria imposible decir cual es la historia buena.
        UUID grupo = fixtura.grupo();
        var primero = transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 0), contextoDeSistema()));

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO transparencia.bloque_transparencia
                            (grupo_id, numero_bloque, hash_bloque_anterior, raiz_merkle, hash_bloque,
                             cantidad_eventos, periodo_cubierto_desde, periodo_cubierto_hasta, sellado_en)
                        VALUES (?, ?, ?, repeat('b', 64), repeat('d', 64), 0, now(), now(), now())
                        """,
                        grupo,
                        primero.numeroBloque() + 5,
                        primero.hashBloque()))
                .contains("R-REP-04");

        // HUECO H-1: ck_bloque_genesis exige que el bloque 1 lleve hash anterior NULL,
        // pero la columna es NOT NULL. El genesis es inescribible y la cadena arranca
        // en 2. Se demuestra el rechazo en vez de dejarlo dicho.
        assertThat(rechazaLaBase(
                        """
                        INSERT INTO transparencia.bloque_transparencia
                            (grupo_id, numero_bloque, hash_bloque_anterior, raiz_merkle, hash_bloque,
                             cantidad_eventos, periodo_cubierto_desde, periodo_cubierto_hasta, sellado_en)
                        VALUES (?, 1, repeat('0', 64), repeat('b', 64), repeat('e', 64), 0, now(), now(), now())
                        """,
                        fixtura.grupo()))
                .contains("ck_bloque_genesis");
    }
}
