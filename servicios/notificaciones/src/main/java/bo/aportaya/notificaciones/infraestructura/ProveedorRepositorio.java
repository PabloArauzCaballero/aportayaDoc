package bo.aportaya.notificaciones.infraestructura;

import bo.aportaya.notificaciones.dominio.EleccionDeProveedor.Candidato;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** {@code proveedor_mensajeria}: quienes pueden llevar el mensaje, y en que estado estan. */
@Component
public class ProveedorRepositorio {

    public List<Candidato> activos(DSLContext dsl) {
        return dsl.select(
                        DSL.field("codigo", String.class),
                        DSL.field("canales_soportados", String.class),
                        DSL.field("prioridad", Short.class),
                        DSL.field("costo_por_mensaje", BigDecimal.class),
                        DSL.field("salud_porcentaje", BigDecimal.class),
                        DSL.field("activo", Boolean.class))
                .from(DSL.table(DSL.name("notificaciones", "proveedor_mensajeria")))
                .orderBy(DSL.field("prioridad").asc())
                .fetch(fila -> new Candidato(
                        fila.get("codigo", String.class),
                        // `canales_soportados` es una lista separada por comas, no un
                        // arreglo: se parte tal cual la guarda el modelo.
                        Arrays.stream(fila.get("canales_soportados", String.class)
                                        .split(","))
                                .map(String::trim)
                                .toList(),
                        fila.get("prioridad", Short.class),
                        fila.get("costo_por_mensaje", BigDecimal.class),
                        fila.get("salud_porcentaje", BigDecimal.class).intValue(),
                        fila.get("activo", Boolean.class)));
    }

    public Optional<UUID> idDe(DSLContext dsl, String codigo) {
        return Optional.ofNullable(dsl.select(DSL.field("id", UUID.class))
                .from(DSL.table(DSL.name("notificaciones", "proveedor_mensajeria")))
                .where(DSL.field("codigo").eq(codigo))
                .fetchOne(DSL.field("id", UUID.class)));
    }

    /**
     * Actualiza la salud tras un envio.
     *
     * <p>Ventana movil simple: la salud nueva pesa un decimo. Asi una caida se nota en
     * pocos mensajes pero un fallo aislado no tumba a un proveedor que venia bien.
     */
    public void registrarResultado(DSLContext dsl, String codigo, boolean entregado) {
        dsl.execute(
                """
                UPDATE notificaciones.proveedor_mensajeria
                   SET salud_porcentaje = round(salud_porcentaje * 0.9 + (CASE WHEN ? THEN 100 ELSE 0 END) * 0.1, 2)
                 WHERE codigo = ?
                """,
                entregado,
                codigo);
    }
}
