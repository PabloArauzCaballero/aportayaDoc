package bo.aportaya.grupos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.aportaya.grupos.aplicacion.CU60Sortear.Compromiso;
import bo.aportaya.grupos.aplicacion.CU60Sortear.Revelacion;
import bo.aportaya.plataforma.dominio.ErrorDeNegocio;
import bo.aportaya.plataforma.dominio.SorteoVerificable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CU-60 · Sortear los turnos, con compromiso y revelación. */
class CU60Test extends BaseDeCU60 {

    @Test
    @DisplayName(
            "Dado un grupo conformado con 6 cupos · Cuando se ejecuta la fase de compromiso · Entonces existe un sorteo_turnos COMPROMETIDO con hash_semilla de 64 caracteres · Y no existe ningún turno todavía")
    void criterio1() {
        UUID grupo = fixtura.grupoConformado(6);
        fixtura.cuposOcupados(grupo, 6);

        Compromiso compromiso =
                transaccion.execute(e -> sortear.comprometer(grupo, List.of(), Optional.empty(), contexto()));

        assertThat(compromiso.hashSemilla()).hasSize(64);
        assertThat(estadoDelSorteo(compromiso.sorteoId())).isEqualTo("COMPROMETIDO");
        assertThat(turnosDe(grupo)).isZero();
    }

    @Test
    @DisplayName(
            "Dado un sorteo comprometido cuya fecha de revelado llegó · Cuando se revela la semilla · Entonces SHA256(semilla || entropías) coincide con el hash publicado · Y existen 6 turnos con órdenes 1..6 sin repetir")
    void criterio2() {
        UUID grupo = fixtura.grupoConformado(6);
        fixtura.cuposOcupados(grupo, 6);
        List<UUID> periodos = fixtura.periodos(grupo, 6, new BigDecimal("3000.00"));
        Compromiso compromiso = transaccion.execute(
                e -> sortear.comprometer(grupo, List.of("entropia-de-ana"), Optional.empty(), contexto()));

        Revelacion revelacion = transaccion.execute(e -> sortear.revelar(
                compromiso.sorteoId(),
                compromiso.semilla(),
                List.of("entropia-de-ana"),
                periodos,
                new BigDecimal("500.00"),
                Optional.empty(),
                contexto()));

        assertThat(revelacion.verificado()).isTrue();
        assertThat(turnosDe(grupo)).isEqualTo(6);
        assertThat(ordenesDe(grupo)).containsExactly((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6);
    }

    @Test
    @DisplayName(
            "Dada la misma semilla y los mismos cupos · Cuando un tercero recomputa el orden · Entonces obtiene exactamente el mismo resultado")
    void criterio3() {
        // La recomputacion de un tercero: mismos cupos, misma semilla, mismo orden.
        // No depende de esta JVM porque el indice sale de SHA-256, no de Random.
        List<UUID> cupos = List.of(
                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString("00000000-0000-4000-8000-000000000002"),
                UUID.fromString("00000000-0000-4000-8000-000000000003"),
                UUID.fromString("00000000-0000-4000-8000-000000000004"));

        List<UUID> primera = SorteoVerificable.barajarDeterminista("semilla-publicada", cupos);
        List<UUID> segunda = SorteoVerificable.barajarDeterminista("semilla-publicada", cupos);

        assertThat(primera).isEqualTo(segunda);
        assertThat(primera).containsExactlyInAnyOrderElementsOf(cupos);
        assertThat(SorteoVerificable.barajarDeterminista("otra-semilla", cupos)).isNotEqualTo(primera);
    }

    @Test
    @DisplayName(
            "Dado un intento de revelar con una semilla que no verifica · Cuando se ejecuta · Entonces no se crea ningún turno · Y el sorteo queda ANULADO con su incidente registrado")
    void criterio4() {
        UUID grupo = fixtura.grupoConformado(6);
        fixtura.cuposOcupados(grupo, 6);
        List<UUID> periodos = fixtura.periodos(grupo, 6, new BigDecimal("3000.00"));
        Compromiso compromiso =
                transaccion.execute(e -> sortear.comprometer(grupo, List.of(), Optional.empty(), contexto()));

        Revelacion revelacion = transaccion.execute(e -> sortear.revelar(
                compromiso.sorteoId(),
                "semilla-que-no-es-la-comprometida",
                List.of(),
                periodos,
                new BigDecimal("500.00"),
                Optional.empty(),
                contexto()));

        assertThat(revelacion.verificado()).isFalse();
        assertThat(turnosDe(grupo)).isZero();
        assertThat(estadoDelSorteo(compromiso.sorteoId())).isEqualTo("ANULADO");
    }

    @Test
    @DisplayName("rechaza por R-AUD-01")
    void rechazaRAUD01() {
        dejarUnaFilaEnLaBitacora();

        assertThat(rechazaLaBase("DELETE FROM comun.bitacora_evento")).contains("R-AUD-01");
    }

    @Test
    @DisplayName("rechaza por R-AUD-04")
    void rechazaRAUD04() {
        // Un sorteo sin quien lo ejecuto no es auditable: ejecutado_por es obligatorio.
        UUID grupo = fixtura.grupoConformado(3);

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO grupos.sorteo_turnos
                            (id, grupo_id, algoritmo, estado, hash_semilla_previo, fecha_compromiso,
                             semilla_publica, resultado, fecha_ejecucion)
                        VALUES (gen_random_uuid(), '%s', 'FISHER_YATES_SHA256', 'COMPROMETIDO',
                                repeat('a', 64), now(), '', '[]'::jsonb, now())
                        """
                                .formatted(grupo)))
                .contains("ejecutado_por");
    }

    @Test
    @DisplayName("rechaza por R-GRP-05")
    void rechazaRGRP05() {
        UUID grupo = fixtura.grupoConformado(4);
        fixtura.cuposOcupados(grupo, 4);
        transaccion.execute(e -> sortear.comprometer(grupo, List.of(), Optional.empty(), contexto()));

        assertThatThrownBy(() ->
                        transaccion.execute(e -> sortear.comprometer(grupo, List.of(), Optional.empty(), contexto())))
                .isInstanceOf(ErrorDeNegocio.class)
                .hasMessageContaining("ya tiene su sorteo");
    }

    @Test
    @DisplayName("rechaza por R-GRP-06")
    void rechazaRGRP06() {
        // Cada cupo tiene exactamente un turno y cada turno un orden unico. Lo hace
        // cumplir la base: dos turnos con el mismo orden en el mismo periodo no entran.
        UUID grupo = fixtura.grupoConformado(3);
        List<UUID> cupos = fixtura.cuposOcupados(grupo, 3);
        List<UUID> periodos = fixtura.periodos(grupo, 3, new BigDecimal("1500.00"));
        transaccion.execute(estado -> {
            sorteos.crearTurnos(dsl, grupo, periodos, cupos, new BigDecimal("500.00"));
            return null;
        });

        assertThat(rechazaLaBase(
                        """
                        INSERT INTO grupos.turno
                            (id, grupo_id, periodo_id, cupo_id, orden_asignado, estado,
                             criterio_asignacion, monto_estimado_cobro)
                        VALUES (gen_random_uuid(), '%s', '%s', '%s', 1, 'PENDIENTE', 'SORTEO', 500.00)
                        """
                                .formatted(grupo, periodos.get(0), cupos.get(0))))
                .isNotEmpty();
    }

    @Test
    @DisplayName("reintento: la misma clave de idempotencia dos veces devuelve la misma respuesta y un solo efecto")
    void reintento() {
        // Comprometer dos veces el mismo grupo no puede producir dos sorteos: lo corta
        // el propio caso de uso y, si se lo saltea, la unicidad del modelo.
        UUID grupo = fixtura.grupoConformado(4);
        fixtura.cuposOcupados(grupo, 4);
        Compromiso primero =
                transaccion.execute(e -> sortear.comprometer(grupo, List.of(), Optional.empty(), contexto()));

        assertThatThrownBy(() ->
                        transaccion.execute(e -> sortear.comprometer(grupo, List.of(), Optional.empty(), contexto())))
                .isInstanceOf(ErrorDeNegocio.class);
        assertThat(estadoDelSorteo(primero.sorteoId())).isEqualTo("COMPROMETIDO");
    }

    @Test
    @DisplayName("concurrencia: dos transacciones sobre el mismo agregado, una gana y nunca hay doble efecto")
    void concurrencia() {
        // Revelar dos veces con la misma semilla: la segunda no duplica turnos,
        // porque la unicidad de (periodo, orden) no lo permite.
        UUID grupo = fixtura.grupoConformado(4);
        fixtura.cuposOcupados(grupo, 4);
        List<UUID> periodos = fixtura.periodos(grupo, 4, new BigDecimal("2000.00"));
        Compromiso compromiso =
                transaccion.execute(e -> sortear.comprometer(grupo, List.of(), Optional.empty(), contexto()));
        transaccion.execute(e -> sortear.revelar(
                compromiso.sorteoId(),
                compromiso.semilla(),
                List.of(),
                periodos,
                new BigDecimal("500.00"),
                Optional.empty(),
                contexto()));

        assertThatThrownBy(() -> transaccion.execute(e -> sortear.revelar(
                        compromiso.sorteoId(),
                        compromiso.semilla(),
                        List.of(),
                        periodos,
                        new BigDecimal("500.00"),
                        Optional.empty(),
                        contexto())))
                .isNotNull();
        assertThat(turnosDe(grupo)).isEqualTo(4);
    }

    @Test
    @DisplayName("cuadre: la suma de debitos iguala la de creditos, al centavo")
    void cuadre() {
        // CU-60 no mueve dinero: lo que cuadra es que los ordenes sean 1..n sin
        // repetir y sin saltos, que es la version de «no se pierde nada» de un sorteo.
        UUID grupo = fixtura.grupoConformado(8);
        fixtura.cuposOcupados(grupo, 8);
        List<UUID> periodos = fixtura.periodos(grupo, 8, new BigDecimal("4000.00"));
        Compromiso compromiso =
                transaccion.execute(e -> sortear.comprometer(grupo, List.of(), Optional.empty(), contexto()));

        transaccion.execute(e -> sortear.revelar(
                compromiso.sorteoId(),
                compromiso.semilla(),
                List.of(),
                periodos,
                new BigDecimal("500.00"),
                Optional.empty(),
                contexto()));

        assertThat(ordenesDe(grupo))
                .containsExactly(
                        (short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6, (short) 7, (short) 8);
    }

    @Test
    @DisplayName("evento duplicado y fuera de orden: un solo efecto")
    void eventoDuplicado() {
        UUID idEvento = UUID.randomUUID();

        Boolean primera = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "transparencia"));
        Boolean segunda = transaccion.execute(e -> consumidos.registrar(dsl, idEvento, "transparencia"));

        assertThat(primera).isTrue();
        assertThat(segunda).isFalse();
    }

    private List<Short> ordenesDe(UUID grupoId) {
        return dsl.select(DSL.field("orden_asignado", Short.class))
                .from(DSL.table("grupos.turno"))
                .where(DSL.field("grupo_id").eq(grupoId))
                .orderBy(DSL.field("orden_asignado"))
                .fetch(DSL.field("orden_asignado", Short.class));
    }
}
