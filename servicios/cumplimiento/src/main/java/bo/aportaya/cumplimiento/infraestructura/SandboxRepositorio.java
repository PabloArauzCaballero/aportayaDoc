package bo.aportaya.cumplimiento.infraestructura;

import bo.aportaya.cumplimiento.dominio.HabilitacionDeServicio.Sandbox;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** Lee y actualiza {@code cumplimiento.entorno_prueba_regulado}. */
@Component
public class SandboxRepositorio {

    /**
     * El entorno de prueba de ese servicio, si esta activo hoy.
     *
     * <p>{@code usuariosRegistrados} no sale de esta tabla: el conteo de usuarios de
     * un servicio vive donde vive ese servicio, y cumplimiento no lee esquemas
     * ajenos (invariante 11). Lo recibe quien llama, que si lo sabe.
     */
    public Optional<Sandbox> activoPara(DSLContext dsl, String servicio, LocalDate hoy, int usuariosRegistrados) {
        Record fila = dsl.select(
                        DSL.field("servicio_en_prueba", String.class),
                        DSL.field("limite_usuarios", Integer.class),
                        DSL.field("limite_monto_operacion", BigDecimal.class),
                        DSL.field("fecha_inicio", LocalDate.class),
                        DSL.field("fecha_fin", LocalDate.class))
                .from(DSL.table(DSL.name("cumplimiento", "entorno_prueba_regulado")))
                .where(DSL.field("servicio_en_prueba").eq(servicio))
                .and(DSL.field("estado").eq("ACTIVO"))
                .orderBy(DSL.field("fecha_fin").desc())
                .limit(1)
                .fetchOne();

        if (fila == null) {
            return Optional.empty();
        }
        LocalDate inicio = fila.get("fecha_inicio", LocalDate.class);
        LocalDate fin = fila.get("fecha_fin", LocalDate.class);
        boolean dentroDeVentana = !hoy.isBefore(inicio) && !hoy.isAfter(fin);

        return Optional.of(new Sandbox(
                fila.get("servicio_en_prueba", String.class),
                dentroDeVentana,
                // ck_sandbox_limites garantiza que un ACTIVO tiene los dos limites,
                // asi que aca no hay NULL que defender.
                fila.get("limite_usuarios", Integer.class),
                fila.get("limite_monto_operacion", BigDecimal.class),
                usuariosRegistrados));
    }

    /** CU-46 paso 4: los servicios en sandbox rinden cuentas periodicamente. */
    public void contarInforme(DSLContext dsl, String servicio) {
        dsl.update(DSL.table(DSL.name("cumplimiento", "entorno_prueba_regulado")))
                .set(
                        DSL.field("informes_remitidos", Integer.class),
                        DSL.field("informes_remitidos", Integer.class).plus(1))
                .where(DSL.field("servicio_en_prueba").eq(servicio))
                .and(DSL.field("estado").eq("ACTIVO"))
                .execute();
    }
}
