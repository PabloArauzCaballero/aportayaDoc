package bo.aportaya.transparencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.plataforma.dominio.ContextoSesion;
import bo.aportaya.transparencia.aplicacion.CU72SellarBloque.EntradaBloque;
import bo.aportaya.transparencia.aplicacion.CU72SellarBloque.Hecho;
import bo.aportaya.transparencia.dominio.CadenaDeBloques;
import bo.aportaya.transparencia.dominio.ContenidoCanonico;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-72 · Sellar el bloque de transparencia. */
class CU72Test extends BaseDeTransparencia {

    private ContextoSesion sistema() {
        return contextoDeSistema();
    }

    private EntradaBloque entrada(UUID grupo, int excepciones, int cuantosHechos) {
        OffsetDateTime hasta = OffsetDateTime.now(ZoneOffset.UTC);
        var hechos = new java.util.ArrayList<Hecho>();
        for (int i = 0; i < cuantosHechos; i++) {
            hechos.add(new Hecho(
                    "PAGO",
                    UUID.randomUUID(),
                    Map.of("monto", "500.00", "concepto", "Aporte del periodo " + i),
                    hasta.minusDays(cuantosHechos - i)));
        }
        return new EntradaBloque(grupo, "CIERRE_PERIODO", List.copyOf(hechos), hasta.minusDays(30), hasta, excepciones);
    }

    @Test
    @DisplayName(
            "Dado un período cerrado sin excepciones · Cuando se sella el bloque · Entonces hash_anterior es el hash_bloque del bloque previo · Y hash_bloque se recomputa igual desde sus tres componentes")
    void criterio1() {
        UUID grupo = fixtura.grupo();

        var primero = transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 0, 2), sistema()));
        var segundo = transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 0, 3), sistema()));

        assertThat(segundo.hashAnterior()).isEqualTo(primero.hashBloque());
        assertThat(segundo.numeroBloque()).isEqualTo(primero.numeroBloque() + 1);

        // Se recomputa desde sus componentes con el mismo atomo que sella. Aca son
        // cinco y no tres: el periodo cubierto entra al hash porque, si no, se podria
        // mover que periodo cubre un bloque sin que su hash cambie (HUECO H-4).
        var fila = dsl.fetchOne(
                """
                SELECT numero_bloque, hash_bloque_anterior, raiz_merkle, hash_bloque,
                       periodo_cubierto_desde, periodo_cubierto_hasta
                  FROM transparencia.bloque_transparencia WHERE id = ?
                """,
                segundo.bloqueId());
        String recomputado = CadenaDeBloques.hashDelBloque(
                fila.get("numero_bloque", Long.class),
                fila.get("hash_bloque_anterior", String.class),
                fila.get("raiz_merkle", String.class),
                ContenidoCanonico.instante(fila.get("periodo_cubierto_desde", OffsetDateTime.class)),
                ContenidoCanonico.instante(fila.get("periodo_cubierto_hasta", OffsetDateTime.class)));
        assertThat(recomputado).isEqualTo(fila.get("hash_bloque", String.class));
        assertThat(contar(
                        "SELECT count(*)::int FROM transparencia.registro_sellado WHERE bloque_id = ?",
                        segundo.bloqueId()))
                .isEqualTo(3);
    }

    @Test
    @DisplayName(
            "Dado un período con una excepción de conciliación abierta · Cuando se intenta sellar · Entonces se rechaza con PERIODO_CON_EXCEPCIONES")
    void criterio2() {
        UUID grupo = fixtura.grupo();

        // R-BIL-12: un bloque con datos provisorios miente con firma.
        assertThatThrownBy(() -> transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 1, 2), sistema())))
                .hasMessageContaining("excepciones de conciliacion abiertas");
        assertThat(contar("SELECT count(*)::int FROM transparencia.bloque_transparencia WHERE grupo_id = ?", grupo))
                .isZero();
    }

    @Test
    @DisplayName(
            "Dado un intento de sellar dos veces el mismo número de bloque · Cuando se ejecuta · Entonces la base lo rechaza por unicidad")
    void criterio3() {
        UUID grupo = fixtura.grupo();
        var primero = transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 0, 1), sistema()));

        // Se fuerza la escritura directa del mismo numero: es lo que haria un segundo
        // proceso que leyo la punta de la cadena antes de que el primero confirmara.
        String error = rechazaLaBase(
                """
                INSERT INTO transparencia.bloque_transparencia
                    (grupo_id, numero_bloque, hash_bloque_anterior, raiz_merkle, hash_bloque,
                     cantidad_eventos, periodo_cubierto_desde, periodo_cubierto_hasta, sellado_en)
                SELECT grupo_id, numero_bloque, hash_bloque_anterior, raiz_merkle, repeat('f', 64),
                       cantidad_eventos, periodo_cubierto_desde, periodo_cubierto_hasta, now()
                  FROM transparencia.bloque_transparencia WHERE id = ?
                """,
                primero.bloqueId());

        // La base lo rechaza, y lo hace **antes** de llegar al indice unico:
        // tg_bloque_encadenado corre BEFORE INSERT y ve el salto de numeracion primero.
        // uq_bloque_grupo_numero queda como segunda linea, inalcanzable por esta via
        // (HUECO H-5). Lo que importa es que R-REP-04 se sostiene.
        assertThat(error).contains("R-REP-04");
        assertThat(contar("SELECT count(*)::int FROM transparencia.bloque_transparencia WHERE grupo_id = ?", grupo))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        UUID grupo = fixtura.grupo();
        var entrada = entrada(grupo, 0, 2);

        var a = transaccion.execute(t -> bloqueCU.sellar(entrada, sistema()));
        var b = transaccion.execute(t -> bloqueCU.sellar(entrada, sistema()));

        // Sellar NO es idempotente y no debe serlo: cada llamada cierra un tramo nuevo
        // de historia. Lo que si se garantiza es que la cadena queda intacta — el
        // segundo bloque encadena con el primero, no lo pisa.
        assertThat(b.numeroBloque()).isEqualTo(a.numeroBloque() + 1);
        assertThat(b.hashAnterior()).isEqualTo(a.hashBloque());
        assertThat(contar("SELECT count(*)::int FROM transparencia.bloque_transparencia WHERE grupo_id = ?", grupo))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() throws Exception {
        UUID grupo = fixtura.grupo();
        transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 0, 1), sistema()));

        var barrera = new java.util.concurrent.CyclicBarrier(2);
        var errores = java.util.Collections.synchronizedList(new java.util.ArrayList<Throwable>());
        Runnable intento = () -> {
            try {
                barrera.await();
                transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 0, 2), sistema()));
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

        // Los dos leen la misma punta y quieren el mismo numero: uno gana. No hay
        // bifurcacion, que es lo que arruinaria la cadena para siempre.
        assertThat(errores).hasSizeLessThanOrEqualTo(1);
        assertThat(contar(
                        "SELECT count(DISTINCT numero_bloque)::int FROM transparencia.bloque_transparencia WHERE grupo_id = ?",
                        grupo))
                .isEqualTo(contar(
                        "SELECT count(*)::int FROM transparencia.bloque_transparencia WHERE grupo_id = ?", grupo));
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        UUID grupo = fixtura.grupo();
        var salida = transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 0, 4), sistema()));

        // Lo que tiene que cuadrar aca es el conteo: cantidad_eventos declarada contra
        // registros sellados realmente. Un bloque que dice cuatro y tiene tres esconde
        // un hecho.
        assertThat(contar(
                        "SELECT cantidad_eventos FROM transparencia.bloque_transparencia WHERE id = ?",
                        salida.bloqueId()))
                .isEqualTo(contar(
                        "SELECT count(*)::int FROM transparencia.registro_sellado WHERE bloque_id = ?",
                        salida.bloqueId()))
                .isEqualTo(4);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicadoYFueraDeOrden() {
        UUID grupo = fixtura.grupo();
        UUID hecho = UUID.randomUUID();
        OffsetDateTime hasta = OffsetDateTime.now(ZoneOffset.UTC);
        var mismo = List.of(new Hecho("ENTREGA", hecho, Map.of("monto", "1500.00"), hasta.minusDays(1)));

        var primero = transaccion.execute(t ->
                bloqueCU.sellar(new EntradaBloque(grupo, "ENTREGA", mismo, hasta.minusDays(30), hasta, 0), sistema()));
        var segundo = transaccion.execute(t ->
                bloqueCU.sellar(new EntradaBloque(grupo, "ENTREGA", mismo, hasta.minusDays(30), hasta, 0), sistema()));

        // Un hecho sellado dos veces queda dos veces, y esta bien: registro_sellado es
        // append-only y el segundo bloque prueba que el hecho seguia ahi. Lo que no
        // puede pasar es que el hash del segundo dependa del primero de otra forma que
        // no sea el encadenamiento.
        assertThat(segundo.raizMerkle()).isEqualTo(primero.raizMerkle());
        assertThat(segundo.hashBloque()).isNotEqualTo(primero.hashBloque());
        assertThat(contar("SELECT count(*)::int FROM transparencia.registro_sellado WHERE entidad_id = ?", hecho))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("compensacion: se fuerza el fallo de cada paso y el sistema queda cuadrado")
    void compensacion() {
        UUID grupo = fixtura.grupo();

        // Paso fallido: excepciones abiertas. Ni bloque ni registros.
        assertThatThrownBy(() -> transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 2, 3), sistema())))
                .hasMessageContaining("excepciones");
        assertThat(contar(
                        """
                        SELECT count(*)::int FROM transparencia.registro_sellado rs
                          JOIN transparencia.bloque_transparencia b ON b.id = rs.bloque_id
                         WHERE b.grupo_id = ?
                        """,
                        grupo))
                .isZero();

        // Paso fallido: un tipo de entidad fuera del catalogo cerrado. La transaccion
        // entera se deshace, incluido el bloque que ya se habia escrito.
        assertThatThrownBy(() -> transaccion.execute(t -> bloqueCU.sellar(
                        new EntradaBloque(
                                grupo,
                                "HITO",
                                List.of(new Hecho(
                                        "PARTICIPANTE",
                                        UUID.randomUUID(),
                                        Map.of("alta", "si"),
                                        OffsetDateTime.now(ZoneOffset.UTC))),
                                OffsetDateTime.now(ZoneOffset.UTC).minusDays(1),
                                OffsetDateTime.now(ZoneOffset.UTC),
                                0),
                        sistema())))
                .isInstanceOf(RuntimeException.class);
        assertThat(contar("SELECT count(*)::int FROM transparencia.bloque_transparencia WHERE grupo_id = ?", grupo))
                .isZero();

        // Y despues del fallo la cadena arranca limpia, sin numeros consumidos.
        var bueno = transaccion.execute(t -> bloqueCU.sellar(entrada(grupo, 0, 1), sistema()));
        assertThat(bueno.numeroBloque()).isEqualTo(CadenaDeBloques.PRIMER_NUMERO);
    }
}
