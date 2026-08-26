package bo.aportaya.grupos.infraestructura;

import bo.aportaya.grupos.dominio.AlcanceDeCalendario;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * Lee {@code catalogo.dia_no_habil}.
 *
 * <p>Solo lectura, y no por prudencia: {@code catalogo} es «lo sembrado que muchos
 * leen y nadie escribe en caliente» ([[ADR-029]]), y el {@code GRANT} de cada
 * servicio sobre ese esquema es {@code SELECT} y nada mas.
 */
@Component
public class CalendarioRepositorio {

    /** Los dias no habiles del rango, con su descripcion, para poder explicar el calculo. */
    public Map<LocalDate, String> noHabilesEntre(
            DSLContext dsl, LocalDate desde, LocalDate hasta, AlcanceDeCalendario alcance, Optional<UUID> referencia) {
        var tabla = DSL.table(DSL.name("catalogo", "dia_no_habil"));
        var condicion = DSL.field("fecha", LocalDate.class)
                .between(desde, hasta)
                // El alcance mas amplio siempre aplica: un feriado nacional lo es
                // tambien para un grupo. Filtrar solo por el alcance pedido dejaria
                // fuera los que valen para todos.
                .and(DSL.field("alcance").in("NACIONAL", "PLATAFORMA", alcance.name()));
        if (alcance == AlcanceDeCalendario.GRUPO && referencia.isPresent()) {
            condicion = condicion.and(
                    DSL.field("grupo_id").isNull().or(DSL.field("grupo_id").eq(referencia.get())));
        } else {
            condicion = condicion.and(DSL.field("grupo_id").isNull());
        }

        Map<LocalDate, String> encontrados = new LinkedHashMap<>();
        for (Record fila : dsl.select(DSL.field("fecha", LocalDate.class), DSL.field("descripcion", String.class))
                .from(tabla)
                .where(condicion)
                .orderBy(DSL.field("fecha"))
                .fetch()) {
            encontrados.put(
                    fila.get(DSL.field("fecha", LocalDate.class)), fila.get(DSL.field("descripcion", String.class)));
        }
        return encontrados;
    }

    /** Si el año no tiene ni un dia cargado, el calendario esta vacio. */
    public boolean hayCalendarioPara(DSLContext dsl, int anio) {
        return dsl.fetchCount(
                        DSL.table(DSL.name("catalogo", "dia_no_habil")),
                        DSL.field("fecha", LocalDate.class)
                                .between(LocalDate.of(anio, 1, 1), LocalDate.of(anio, 12, 31)))
                > 0;
    }
}
